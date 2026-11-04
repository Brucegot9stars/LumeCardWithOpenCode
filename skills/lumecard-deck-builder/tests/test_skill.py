import copy
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))


def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / (name + ".py"))
    obj = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(obj)
    return obj


builder = module("build_deck")
installer = module("install")


class WorkflowTests(unittest.TestCase):
    def setUp(self):
        self.plan = builder.load(ROOT / "examples/plan.json")
        self.data = builder.build(self.plan)

    def test_end_to_end(self):
        self.assertEqual(builder.validate(self.data), {"knowledgeBases": 1, "decks": 1, "cards": 4})
        self.assertEqual([c["type"] for c in self.data["cards"]], ["BASIC", "CLOZE", "MULTIPLE_CHOICE", "MARKDOWN"])
        self.assertTrue(self.data["cards"][2]["back"].startswith("+"))
        self.assertNotIn("reviewLogs", self.data)

    def test_multilevel_references_and_uuid(self):
        self.plan["knowledgeBases"].append(copy.deepcopy(self.plan["knowledgeBases"][0]))
        data = builder.build(self.plan)
        self.assertEqual(builder.validate(data)["cards"], 8)
        self.assertNotEqual(data["cards"][0]["id"], self.data["cards"][0]["id"])

    def test_explicit_style_and_source(self):
        card = self.plan["knowledgeBases"][0]["decks"][0]["cards"][0]
        card["style"] = {"fontSize": 32, "hcenter": False, "fontFamily": "serif"}
        meta = builder.build(self.plan)["cards"][0]["metadata"]
        self.assertEqual(meta["fontSize"], "32")
        self.assertEqual(meta["hcenter"], "false")
        self.assertEqual(meta["fontFamily"], "serif")
        self.assertIn("source", meta)

    def test_reversed_and_long_plain_text(self):
        card = {"front": "hello", "back": "你好", "bidirectional": True}
        self.assertEqual(builder.card_content(card)[2], "REVERSED")
        self.assertEqual(builder.style("a" * 101, "b", "BASIC", {})["hcenter"], "false")

    def test_invalid_plans(self):
        for value in (None, {}, {"knowledgeBases": []}, {"knowledgeBases": ["bad"]}):
            with self.subTest(value=value), self.assertRaises(ValueError):
                builder.build(value)

    def test_invalid_cards(self):
        cases = [{"front": "", "back": "a"}, {"front": "q"},
                 {"front": "q", "back": "a", "type": "RICH_TEXT"},
                 {"front": "q", "back": "a", "type": "CLOZE"},
                 {"front": "q", "back": "a\nb", "type": "MULTIPLE_CHOICE"},
                 {"front": "q", "options": [{"text": "a\nb", "correct": True}]},
                 {"front": "q", "back": "+a\na", "type": "MULTIPLE_CHOICE"}]
        for card in cases:
            with self.subTest(card=card), self.assertRaises(ValueError):
                builder.card_content(card)

    def test_dangling_and_duplicate_ids(self):
        self.data["cards"][0]["deckId"] = "missing"
        with self.assertRaisesRegex(ValueError, "dangling"):
            builder.validate(self.data)
        self.data = builder.build(self.plan)
        self.data["cards"][1]["id"] = self.data["cards"][0]["id"]
        with self.assertRaisesRegex(ValueError, "duplicate ID"):
            builder.validate(self.data)

    def test_metadata_history_and_dates(self):
        for key, value in (("metadata", {"fontSize": 16}), ("lastReviewedAt", "2026-01-01T00:00:00Z"), ("createdAt", "not a date")):
            data = copy.deepcopy(self.data)
            data["cards"][0][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                builder.validate(data)

    def test_style_errors(self):
        for style in ({"fontSize": True}, {"fontSize": 121}, {"fontFamily": "download-me"}, {"hcenter": "true"}, {"unknown": 1}):
            with self.subTest(style=style), self.assertRaises(ValueError):
                builder.style("q", "a", "BASIC", style)

    def test_export_requires_actual_back_even_for_cloze(self):
        del self.data["cards"][1]["back"]
        with self.assertRaisesRegex(ValueError, "card.back"):
            builder.validate(self.data)

    def test_typed_optional_fields(self):
        self.data["cards"][0]["title"] = 123
        with self.assertRaisesRegex(ValueError, "title"):
            builder.validate(self.data)

    def test_share_fields_and_long_version_boundaries(self):
        for field, value in (("deviceId", {}), ("deviceId", 123), ("backupFormatVersion", 99),
                             ("extra", "unexpected")):
            data = copy.deepcopy(self.data)
            data[field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                builder.validate(data)
        for collection in ("knowledgeBases", "decks", "cards"):
            for value in (0, True, 2**63):
                data = copy.deepcopy(self.data)
                data[collection][0]["version"] = value
                with self.subTest(collection=collection, value=value), self.assertRaises(ValueError):
                    builder.validate(data)
            data = copy.deepcopy(self.data)
            data[collection][0]["version"] = 2**63 - 1
            builder.validate(data)
        for value in (None, "device"):
            data = copy.deepcopy(self.data)
            data["deviceId"] = value
            builder.validate(data)

    def test_plan_fields_cannot_override_validation_of_export(self):
        for field, value in (("options", [{"text": "A", "correct": True}, {"text": "B", "correct": False}]),
                             ("style", {}), ("bidirectional", True)):
            data = copy.deepcopy(self.data)
            data["cards"][2]["back"] = "broken"
            data["cards"][2][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                builder.validate(data)

    def test_choice_newlines_match_app_renderer(self):
        for separator in ("\r", "\r\n"):
            card = {"front": "Q?", "back": "+A" + separator + "B", "type": "MULTIPLE_CHOICE"}
            self.assertEqual(builder.card_content(card)[1], "+A\nB")
            plan = copy.deepcopy(self.plan)
            plan["knowledgeBases"][0]["decks"][0]["cards"] = [card]
            data = builder.build(plan)
            self.assertEqual(data["cards"][0]["back"].split("\n"), ["+A", "B"])
            builder.validate(data)
        for separator in ("\r", "\r\n", "\u2028", "\u2029", "\x85", "\v"):
            data = copy.deepcopy(self.data)
            data["cards"][2]["back"] = "+A" + separator + "B"
            with self.subTest(separator=repr(separator)), self.assertRaises(ValueError):
                builder.validate(data)
        for separator in ("\n", "\r", "\u2028", "\u2029", "\x85", "\v"):
            card = {"front": "Q?", "options": [{"text": "A" + separator, "correct": True},
                                                {"text": "B", "correct": False}]}
            with self.subTest(separator=repr(separator)), self.assertRaises(ValueError):
                builder.card_content(card)

    def test_cli_roundtrip_overwrite_and_errors(self):
        with tempfile.TemporaryDirectory(prefix="lumecard 中文 ") as tmp:
            out = Path(tmp) / "卡牌.json"
            command = [sys.executable, str(ROOT / "scripts/build_deck.py")]
            first = subprocess.run(command + ["build", str(ROOT / "examples/plan.json"), str(out)], capture_output=True)
            self.assertEqual(first.returncode, 0, first.stderr)
            self.assertEqual(subprocess.run(command + ["validate", str(out)], capture_output=True).returncode, 0)
            original = out.read_bytes()
            self.assertEqual(subprocess.run(command + ["build", str(ROOT / "examples/plan.json"), str(out)], capture_output=True).returncode, 2)
            self.assertEqual(out.read_bytes(), original)
            self.assertEqual(subprocess.run(command + ["validate", str(out) + ".missing"], capture_output=True).returncode, 2)

    def test_duplicate_keys(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "bad.json"
            file.write_text('{"a":1,"a":2}', encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "duplicate JSON"):
                builder.load(file)

    def test_non_finite_json_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "invalid.json"
            for value in ("NaN", "Infinity", "-Infinity"):
                file.write_text('{"value":' + value + '}', encoding="utf-8")
                with self.subTest(value=value), self.assertRaisesRegex(ValueError, "non-finite"):
                    builder.load(file)

    def test_stdlib_only(self):
        result = subprocess.run([sys.executable, "-S", str(ROOT / "scripts/build_deck.py"), "validate", str(ROOT / "examples/import.json")], capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)


class InstallationTests(unittest.TestCase):
    def test_installed_adapter_can_uninstall_itself(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / installer.NAME
            installer.install(target)
            result = subprocess.run([sys.executable, str(target / "scripts/install.py"),
                                     "--agent", "custom", "--dest", tmp, "--uninstall"], capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertFalse(target.exists())

    def test_install_refuses_overwrite_and_preserves_modified(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / installer.NAME
            installer.install(target)
            with self.assertRaises(ValueError):
                installer.install(target)
            file = target / "README.md"
            file.write_text("user edit", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "modified"):
                installer.uninstall(target)
            self.assertTrue(file.exists())

    def test_install_uninstall_and_unrelated_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            sibling = Path(tmp) / "unrelated.txt"
            sibling.write_text("keep", encoding="utf-8")
            target = Path(tmp) / installer.NAME
            installer.install(target)
            installer.uninstall(target)
            self.assertFalse(target.exists())
            self.assertEqual(sibling.read_text(), "keep")

    def test_unmanaged_directory_not_removed(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                installer.uninstall(Path(tmp))

    def test_native_project_destinations(self):
        with tempfile.TemporaryDirectory() as tmp:
            for agent, (_, local) in installer.PATHS.items():
                if local:
                    self.assertEqual(installer.destination(agent, "project", tmp), Path(tmp) / local / installer.NAME)
                else:
                    with self.assertRaises(ValueError):
                        installer.destination(agent, "project", tmp)

    @unittest.skipIf(os.name == "nt", "symlink creation requires privileges on some Windows runners")
    def test_symlink_destination_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            link = Path(tmp) / "alias"
            link.symlink_to(Path(tmp), target_is_directory=True)
            with self.assertRaises(ValueError):
                installer.destination("custom", "user", dest=str(link))

    @unittest.skipUnless(shutil.which("sh"), "POSIX shell unavailable")
    def test_shell_wrapper_and_missing_python(self):
        with tempfile.TemporaryDirectory(prefix="skill install ") as tmp:
            args = ["sh", str(ROOT / "install.sh"), "--agent", "claude", "--scope", "project", "--project", tmp]
            result = subprocess.run(args, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(subprocess.run(args + ["--uninstall"], capture_output=True).returncode, 0)
            if os.name == "nt":
                return  # Missing-Python simulation below uses POSIX symlinks.
            # Empty PATH retains only tools needed to locate the wrapper; no Python.
            tools = Path(tmp) / "bin"
            tools.mkdir()
            for name in ("dirname",):
                os.symlink(shutil.which(name), tools / name)
            result = subprocess.run([shutil.which("sh"), str(ROOT / "install.sh"), "--agent", "codex"], env={**os.environ, "PATH": str(tools)}, capture_output=True)
            self.assertEqual(result.returncode, 127, result.stderr)
            self.assertIn(b"manually", result.stderr)

    @unittest.skipUnless(shutil.which("pwsh"), "PowerShell unavailable; NOT verified")
    def test_powershell_wrapper(self):
        with tempfile.TemporaryDirectory() as tmp:
            args = ["pwsh", "-NoProfile", "-File", str(ROOT / "install.ps1"), "-Agent", "codex", "-Scope", "project", "-Project", tmp]
            result = subprocess.run(args, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(subprocess.run(args + ["-Uninstall"], capture_output=True).returncode, 0)


class StructureTests(unittest.TestCase):
    def test_app_contract_fixtures_are_generated_and_classified(self):
        with tempfile.TemporaryDirectory() as tmp:
            command = [sys.executable, "-S", str(ROOT / "tests/write_contract_fixtures.py"), tmp]
            result = subprocess.run(command, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            cases = builder.load(Path(tmp) / "manifest.json")["cases"]
            self.assertEqual(len(cases), 8)
            self.assertEqual(sum(case["accepted"] for case in cases), 2)
            for case in cases:
                data = builder.load(Path(tmp) / case["file"])
                if case["accepted"]:
                    self.assertEqual(builder.validate(data)["cards"], 5)
                else:
                    with self.assertRaises(ValueError):
                        builder.validate(data)
            original = (Path(tmp) / "manifest.json").read_bytes()
            self.assertNotEqual(subprocess.run(command, capture_output=True).returncode, 0)
            self.assertEqual((Path(tmp) / "manifest.json").read_bytes(), original)

    def test_distribution_excludes_private_work_from_zip_and_install(self):
        import zipfile
        from unittest import mock
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            source = tmp / ROOT.name
            shutil.copytree(ROOT, source, ignore=shutil.ignore_patterns("__pycache__", "dist"))
            private = ("private-extracted.json", "plan.json", "references/private.md", "scripts/secret.py")
            for name in private:
                (source / name).write_text("PRIVATE", encoding="utf-8")
            archive = tmp / "skill.zip"
            subprocess.run([sys.executable, str(source / "scripts/package.py"), str(archive)], check=True, capture_output=True)
            with zipfile.ZipFile(archive) as bundle:
                self.assertTrue(all(source.name + "/" + name not in bundle.namelist() for name in private))
            target = tmp / "installed" / ROOT.name
            with mock.patch.object(installer, "ROOT", source):
                installer.install(target)
            self.assertTrue(all(not (target / name).exists() for name in private))
            installer.uninstall(target)

    @unittest.skipIf(os.name == "nt", "symlink creation may require privileges")
    def test_missing_or_linked_payload_fails_before_creating_archive(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / ROOT.name
            shutil.copytree(ROOT, source, ignore=shutil.ignore_patterns("__pycache__", "dist"))
            file = source / "examples/notes.md"
            file.unlink()
            archive = Path(tmp) / "skill.zip"
            for linked in (False, True):
                if linked:
                    file.symlink_to(ROOT / "examples/notes.md")
                result = subprocess.run([sys.executable, str(source / "scripts/package.py"), str(archive)], capture_output=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(archive.exists())

    def test_zip_contains_runnable_standalone_skill(self):
        import zipfile
        with tempfile.TemporaryDirectory() as tmp:
            archive = Path(tmp) / "skill.zip"
            result = subprocess.run([sys.executable, str(ROOT / "scripts/package.py"), str(archive)], capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            with zipfile.ZipFile(archive) as bundle:
                names = bundle.namelist()
                self.assertIn(ROOT.name + "/SKILL.md", names)
                self.assertTrue(all(name.startswith(ROOT.name + "/") for name in names))
                self.assertFalse(any("__pycache__" in name or name.endswith(".pyc") for name in names))
                bundle.extractall(Path(tmp) / "unpacked")
            extracted = Path(tmp) / "unpacked" / ROOT.name
            result = subprocess.run([sys.executable, "-S", str(extracted / "scripts/build_deck.py"), "validate", str(extracted / "examples/import.json")], capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_frontmatter_portable_subset(self):
        # This restricted header uses plain YAML scalars and a string-to-string map.
        source = (ROOT / "SKILL.md").read_text(encoding="utf-8")
        self.assertTrue(source.startswith("---\n"))
        header = source.split("---\n", 2)[1]
        self.assertIn("name: " + ROOT.name, header)
        self.assertLess(len(source.splitlines()), 500)
        self.assertNotIn("allowed-tools:", header)

    def test_references_exist(self):
        import re
        for link in re.findall(r"\]\(([^)]+)\)", (ROOT / "SKILL.md").read_text(encoding="utf-8")):
            self.assertTrue((ROOT / link).is_file(), link)
        for name in ("README.md", "LICENSE", "install.sh", "install.ps1"):
            self.assertTrue((ROOT / name).is_file())


if __name__ == "__main__":
    unittest.main()
