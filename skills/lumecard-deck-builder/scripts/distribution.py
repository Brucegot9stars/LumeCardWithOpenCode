"""Reviewed file allowlist shared by packaging and installation."""
from pathlib import Path
import stat

# Add distributable resources explicitly; never scan user working documents.
PAYLOAD = (
    ".gitignore", ".github/workflows/test.yml", "LICENSE", "README.md", "SKILL.md",
    "install.sh", "install.ps1",
    "examples/notes.md", "examples/plan.json", "examples/import.json",
    "references/authoring.md", "references/documents.md", "references/format.md",
    "references/installation-and-usage.md", "references/platforms.md",
    "references/releasing.md", "references/testing.md", "references/validators.md",
    "scripts/build_deck.py", "scripts/distribution.py", "scripts/extract_document.py",
    "scripts/install.py", "scripts/package.py", "scripts/release_skill.py",
    "tests/probe_codex.py", "tests/scenarios.json", "tests/scenario-review.md",
    "tests/test_detection.py", "tests/test_documents.py", "tests/test_release.py",
    "tests/test_skill.py", "tests/write_contract_fixtures.py",
)


def payload_files(root):
    root = Path(root).absolute()
    files = []
    for name in PAYLOAD:
        path = root / name
        for candidate in (path, *path.parents):
            if candidate == root.parent:
                break
            reparse = getattr(candidate.lstat(), "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0) if candidate.exists() else 0
            if candidate.is_symlink() or reparse:
                raise ValueError("distribution symlink not allowed: " + name)
        if not path.is_file():
            raise ValueError("distribution file missing: " + name)
        files.append(path)
    return files
