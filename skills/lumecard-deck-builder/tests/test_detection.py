import contextlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import shutil

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
spec = importlib.util.spec_from_file_location("installer_detection", ROOT / "scripts/install.py")
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class DetectionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="agent detection ")
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)
        self.project = self.home / "project"
        self.project.mkdir()
        self.commands = set()
        patches = [mock.patch.object(installer.Path, "home", return_value=self.home),
                   mock.patch.object(installer.shutil, "which", side_effect=lambda name: "/bin/" + name if name in self.commands else None),
                   mock.patch.dict(os.environ, {"CODEX_HOME": str(self.home / ".codex"), "HERMES_HOME": str(self.home / ".hermes"), "XDG_CONFIG_HOME": str(self.home / ".config"),
                                                "OPENCODE_DISABLE_EXTERNAL_SKILLS": "", "OPENCODE_DISABLE_CLAUDE_CODE": "", "OPENCODE_DISABLE_CLAUDE_CODE_SKILLS": ""})]
        for patch in patches:
            patch.start()
            self.addCleanup(patch.stop)

    def run_main(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(sys, "argv", ["install.py", *args]), contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = installer.main()
        return code, out.getvalue(), err.getvalue()

    def test_no_evidence_means_no_detection_and_no_created_paths(self):
        self.assertFalse(any(row["detected"] for row in installer.detect_agents()))
        self.assertFalse((self.home / ".agents").exists())

    def test_path_detects_cli_without_config(self):
        self.commands.update({"codex", "codebuddy"})
        found = [row["agent"] for row in installer.detect_agents() if row["detected"]]
        self.assertEqual(found, ["codex", "codebuddy"])

    def test_config_only_and_project_evidence(self):
        (self.home / ".claude.json").write_text("{}")
        (self.project / ".opencode").mkdir()
        rows = {r["agent"]: r for r in installer.detect_agents("project", str(self.project))}
        self.assertTrue(rows["claude"]["detected"])
        self.assertTrue(rows["opencode"]["detected"])
        self.assertFalse(rows["hermes"]["supported"])

    def test_shared_agents_directory_is_not_codex_identity(self):
        (self.home / ".agents/skills").mkdir(parents=True)
        self.assertFalse(any(r["detected"] for r in installer.detect_agents()))

    def test_custom_homes_and_xdg(self):
        config = self.home / "custom-config"
        config.mkdir()
        with mock.patch.dict(os.environ, {"CODEX_HOME": str(config), "HERMES_HOME": str(config), "XDG_CONFIG_HOME": str(config)}):
            (config / "opencode").mkdir()
            rows = {r["agent"]: r for r in installer.detect_agents()}
            for agent in ("codex", "hermes", "opencode"):
                self.assertTrue(rows[agent]["detected"])
            self.assertEqual(rows["hermes"]["target"], str(config / "skills" / installer.NAME))

    def test_interactive_selection_and_cancellation(self):
        self.commands.update({"claude", "codex"})
        rows = installer.detect_agents()
        with mock.patch.object(sys.stdin, "isatty", return_value=True):
            with mock.patch("builtins.input", return_value=""):
                self.assertEqual(installer.choose_agents(rows), ["codex", "claude"])
            with mock.patch("builtins.input", return_value="claude"):
                self.assertEqual(installer.choose_agents(rows), ["claude"])
            with mock.patch("builtins.input", return_value="none"):
                self.assertEqual(installer.choose_agents(rows), [])
            with mock.patch("builtins.input", return_value="unknown"):
                with self.assertRaises(ValueError):
                    installer.choose_agents(rows)

    def test_no_evidence_errors_without_fallback(self):
        code, _, error = self.run_main("--yes")
        self.assertEqual(code, 2)
        self.assertIn("no eligible", error)
        self.assertFalse((self.home / ".claude").exists())

    def test_noninteractive_needs_explicit_yes(self):
        self.commands.add("codex")
        with mock.patch.object(sys.stdin, "isatty", return_value=False):
            code, _, error = self.run_main()
        self.assertEqual(code, 2)
        self.assertIn("--yes", error)
        self.assertFalse((self.home / ".agents").exists())

    def test_dry_run_and_list_never_install(self):
        self.commands.update({"codex", "hermes"})
        for flag in ("--dry-run", "--list-agents"):
            code, output, error = self.run_main(flag)
            self.assertEqual(code, 0, error)
            self.assertIn("codex: detected", output)
            self.assertFalse((self.home / ".agents").exists())

    def test_multi_install_reuses_opencode_compatibility_and_uninstalls(self):
        self.commands.update({"codex", "claude", "opencode"})
        code, output, error = self.run_main("--yes")
        self.assertEqual(code, 0, error)
        self.assertIn("opencode: uses compatible", output)
        self.assertTrue((self.home / ".agents/skills" / installer.NAME / "SKILL.md").is_file())
        self.assertTrue((self.home / ".claude/skills" / installer.NAME / "SKILL.md").is_file())
        self.assertFalse((self.home / ".config/opencode/skills" / installer.NAME).exists())
        self.commands.clear()  # Managed installations remain discoverable without CLIs.
        code, _, error = self.run_main("--yes", "--uninstall")
        self.assertEqual(code, 0, error)
        self.assertFalse((self.home / ".agents/skills" / installer.NAME).exists())

    def test_existing_managed_install_is_kept(self):
        self.commands.add("codex")
        self.assertEqual(self.run_main("--yes")[0], 0)
        code, output, error = self.run_main("--yes")
        self.assertEqual(code, 0, error)
        self.assertIn("already installed", output)

    def test_disabled_compatibility_uses_opencode_native_directory(self):
        cases = (("OPENCODE_DISABLE_EXTERNAL_SKILLS", "codex"),
                 ("OPENCODE_DISABLE_CLAUDE_CODE", "claude"),
                 ("OPENCODE_DISABLE_CLAUDE_CODE_SKILLS", "claude"))
        for flag, agent in cases:
            for value in ("1", "true", "TRUE"):
                self.commands = {agent, "opencode"}
                with self.subTest(flag=flag, value=value), mock.patch.dict(os.environ, {flag: value}):
                    code, output, error = self.run_main("--yes")
                    self.assertEqual(code, 0, error)
                    target = self.home / ".config/opencode/skills" / installer.NAME
                    self.assertTrue((target / "SKILL.md").is_file())
                    self.assertNotIn("uses compatible", output)
                    self.assertEqual(self.run_main("--yes", "--uninstall")[0], 0)

    def test_disabled_claude_compatibility_can_still_reuse_codex(self):
        self.commands = {"codex", "claude", "opencode"}
        with mock.patch.dict(os.environ, {"OPENCODE_DISABLE_CLAUDE_CODE_SKILLS": "1"}):
            code, output, error = self.run_main("--yes")
        self.assertEqual(code, 0, error)
        self.assertIn("opencode: uses compatible", output)
        self.assertFalse((self.home / ".config/opencode/skills" / installer.NAME).exists())

    def test_modified_managed_install_blocks_auto_update(self):
        self.commands.add("codex")
        self.assertEqual(self.run_main("--yes")[0], 0)
        edited = self.home / ".agents/skills" / installer.NAME / "README.md"
        edited.write_text("local edits", encoding="utf-8")
        code, _, error = self.run_main("--yes")
        self.assertEqual(code, 2)
        self.assertIn("modified", error)
        self.assertEqual(edited.read_text(encoding="utf-8"), "local edits")

    @unittest.skipIf(os.name == "nt", "symlink creation may require Windows privileges")
    def test_blocked_destination_does_not_hide_other_agents(self):
        self.commands.update({"codex", "claude"})
        (self.home / ".agents").symlink_to(self.project, target_is_directory=True)
        rows = {row["agent"]: row for row in installer.detect_agents()}
        self.assertFalse(rows["codex"]["supported"])
        self.assertIn("symlink", rows["codex"]["blocked"])
        self.assertTrue(rows["claude"]["supported"])

    def test_project_excludes_hermes(self):
        self.commands.update({"codex", "hermes"})
        code, output, error = self.run_main("--scope", "project", "--project", str(self.project), "--yes")
        self.assertEqual(code, 0, error)
        self.assertIn("hermes: detected; scope unsupported", output)
        self.assertFalse((self.home / ".hermes").exists())

    def test_batch_conflict_is_caught_before_any_install(self):
        self.commands.update({"codex", "claude"})
        conflict = self.home / ".claude/skills" / installer.NAME
        conflict.mkdir(parents=True)
        code, _, error = self.run_main("--yes")
        self.assertEqual(code, 2)
        self.assertIn("destination exists", error)
        self.assertFalse((self.home / ".agents").exists())

    def test_explicit_multiple_agents_and_dest_validation(self):
        self.assertEqual(self.run_main("--agent", "codex,claude", "--dry-run")[0], 0)
        self.assertEqual(self.run_main("--dest", str(self.project), "--yes")[0], 2)
        self.assertEqual(self.run_main("--agent", "codex,claude", "--dest", str(self.project))[0], 2)


class WrapperDetectionTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("sh"), "POSIX shell unavailable")
    def test_shell_without_agent(self):
        result = subprocess.run(["sh", str(ROOT / "install.sh"), "--list-agents"], capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(b"codex:", result.stdout)

    @unittest.skipUnless(shutil.which("pwsh"), "PowerShell unavailable")
    def test_powershell_without_agent(self):
        result = subprocess.run(["pwsh", "-NoProfile", "-File", str(ROOT / "install.ps1"), "-ListAgents"], capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(b"codex:", result.stdout)


if __name__ == "__main__":
    unittest.main()
