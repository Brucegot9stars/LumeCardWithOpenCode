import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("release_skill", ROOT / "scripts/release_skill.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTests(unittest.TestCase):
    def test_metadata_and_tag_agreement(self):
        info = release.metadata()
        self.assertEqual(release.metadata(expected_tag=info["tag"]), info)
        self.assertEqual(info["archive"], "lumecard-deck-builder-" + info["version"] + ".zip")

    def test_wrong_or_application_tag_rejected(self):
        for tag in ("v0.3.0", "lumecard-deck-builder-v999.0.0", "invalid\nextra=value"):
            with self.subTest(tag=tag), self.assertRaisesRegex(ValueError, "mismatch"):
                release.metadata(expected_tag=tag)

    def test_ambiguous_missing_or_unsafe_versions_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for value in ("", "0.3.0\n  version: 0.4.0", "../file", "01.0.0", "1.0.0-rc.1"):
                (root / "SKILL.md").write_text("---\nmetadata:\n  version: " + value + "\n---\n", encoding="utf-8")
                with self.subTest(value=value), self.assertRaises(ValueError):
                    release.metadata(root)

    def test_only_frontmatter_metadata_version_is_used(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "SKILL.md").write_text('---\nname: example\nmetadata:\n  version: "2.4.6"\n---\n  version: "9.9.9"\n', encoding="utf-8")
            self.assertEqual(release.metadata(root)["version"], "2.4.6")

    def test_zip_checksum_and_no_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "dist"
            info = release.build(out)
            archive = out / info["archive"]
            original = archive.read_bytes()
            self.assertEqual((out / info["checksum"]).read_text(), hashlib.sha256(original).hexdigest() + "  " + archive.name + "\n")
            with zipfile.ZipFile(archive) as package:
                self.assertIsNone(package.testzip())
                self.assertIn("lumecard-deck-builder/references/installation-and-usage.md", package.namelist())
            with self.assertRaisesRegex(ValueError, "already exist"):
                release.build(out)
            self.assertEqual(archive.read_bytes(), original)

    def test_tag_error_does_not_create_output_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "new"
            with self.assertRaises(ValueError):
                release.build(out, expected_tag="v1.0.0")
            self.assertFalse(out.exists())

    def test_cli_metadata_and_github_outputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "outputs"
            command = [sys.executable, "-S", str(ROOT / "scripts/release_skill.py"), "metadata", "--github-output", str(output)]
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            info = json.loads(result.stdout)
            fields = dict(line.split("=", 1) for line in output.read_text().splitlines())
            self.assertEqual(fields, info)


if __name__ == "__main__":
    unittest.main()
