#!/usr/bin/env python3
"""Read Skill release metadata or build a ZIP plus SHA256. No GitHub API calls."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
PREFIX = "lumecard-deck-builder-v"
VERSION = re.compile(r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)")


def metadata(root=ROOT, expected_tag=None):
    # Parse only this project's deliberately small metadata.version scalar format.
    # Full Skill/YAML validation is a separate check, not this version reader's job.
    lines = (root / "SKILL.md").read_text(encoding="utf-8").splitlines()
    if not lines or lines[0] != "---" or "---" not in lines[1:]:
        raise ValueError("SKILL.md must start with closed YAML frontmatter")
    header = lines[1:lines.index("---", 1)]
    in_metadata = False
    versions = []
    for line in header:
        if line == "metadata:":
            in_metadata = True
        elif line and not line[0].isspace():
            in_metadata = False
        elif in_metadata and line.startswith("  version:"):
            value = line.split(":", 1)[1].strip()
            if value.startswith(('"', "'")):
                if len(value) < 2 or value[-1] != value[0]:
                    raise ValueError("metadata.version has mismatched quotes")
                value = value[1:-1]
            versions.append(value)
    if len(versions) != 1 or not VERSION.fullmatch(versions[0]):
        raise ValueError("exactly one metadata.version in stable X.Y.Z format is required")
    version = versions[0]
    tag = PREFIX + version
    if expected_tag and expected_tag != tag:
        raise ValueError("tag/version mismatch: expected " + tag + ", got " + expected_tag)
    archive = "lumecard-deck-builder-" + version + ".zip"
    return {"version": version, "tag": tag, "archive": archive,
            "checksum": archive + ".sha256", "artifact": "lumecard-deck-builder-" + version}


def build(output_dir, expected_tag=None, root=ROOT):
    info = metadata(root, expected_tag)
    output_dir = Path(output_dir).absolute()
    archive = output_dir / info["archive"]
    checksum = output_dir / info["checksum"]
    if archive.exists() or checksum.exists():
        raise ValueError("release outputs already exist; use a new output directory")
    output_dir.mkdir(parents=True, exist_ok=True)
    subprocess.run([sys.executable, str(root / "scripts/package.py"), str(archive)], check=True)
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    with checksum.open("x", encoding="ascii", newline="\n") as stream:
        stream.write(digest + "  " + archive.name + "\n")
    return info


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["metadata", "build"])
    parser.add_argument("--expected-tag", help="fail unless this matches metadata.version")
    parser.add_argument("--output-dir", default="dist")
    parser.add_argument("--github-output", help="append validated metadata to an explicit GitHub step output file")
    args = parser.parse_args()
    try:
        info = build(args.output_dir, args.expected_tag) if args.action == "build" else metadata(expected_tag=args.expected_tag)
        if args.github_output:
            with Path(args.github_output).open("a", encoding="utf-8", newline="\n") as stream:
                for key, value in info.items():
                    stream.write(key + "=" + value + "\n")
        print(json.dumps(info))
        return 0
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print("error: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
