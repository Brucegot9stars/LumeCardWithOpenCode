#!/usr/bin/env python3
"""Create a portable zip without caches, installations or build artifacts."""
import argparse
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", help="new ZIP file path")
    args = parser.parse_args()
    with zipfile.ZipFile(args.output, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(ROOT.rglob("*")):
            rel = file.relative_to(ROOT)
            if file.is_symlink():
                raise ValueError("source symlinks cannot be packaged")
            if file.is_file() and not any(x in {"__pycache__", "dist", ".git"} for x in rel.parts) and file.suffix not in {".pyc", ".zip"} and file.name != ".lumecard-install.json":
                archive.write(file, ROOT.name + "/" + rel.as_posix())
    print(args.output)


if __name__ == "__main__":
    main()
