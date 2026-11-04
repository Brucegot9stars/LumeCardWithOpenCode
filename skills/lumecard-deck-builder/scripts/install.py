#!/usr/bin/env python3
"""Copy-only platform adapter; never edits Agent configuration."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import sys

NAME = "lumecard-deck-builder"
ROOT = Path(__file__).resolve().parent.parent
PATHS = {
    "codex": (".agents/skills", ".agents/skills"),
    "claude": (".claude/skills", ".claude/skills"),
    "opencode": (".config/opencode/skills", ".opencode/skills"),
    "codebuddy": (".codebuddy/skills", ".codebuddy/skills"),
    "hermes": (".hermes/skills", None),
}
MARKER = ".lumecard-install.json"


def linked(path):
    if path.is_symlink():
        return True
    try:
        # Windows junctions, including Python versions before is_junction().
        return bool(getattr(path.lstat(), "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0))
    except FileNotFoundError:
        return False


def files(root):
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in root.rglob("*") if p.is_file() and p != root / MARKER}


def safe_parents(path):
    for item in (path, *path.parents):
        if linked(item):
            raise ValueError("symlink destination not allowed: " + str(item))


def destination(agent, scope, project=None, dest=None):
    if dest:
        # Custom --dest is a parent directory, never an arbitrary deletion target.
        parent = Path(dest).absolute()
    else:
        user, local = PATHS[agent]
        if scope == "project":
            if local is None:
                raise ValueError(agent + ": project discovery not verified; use --dest for an explicit manual adapter")
            if not project or not Path(project).is_dir():
                raise ValueError("project installation requires --project pointing to an existing directory")
            parent = Path(project).absolute() / local
        else:
            if agent == "opencode" and os.environ.get("XDG_CONFIG_HOME"):
                parent = Path(os.environ["XDG_CONFIG_HOME"]) / "opencode/skills"
            elif agent == "hermes" and os.environ.get("HERMES_HOME"):
                parent = Path(os.environ["HERMES_HOME"]) / "skills"
            else:
                parent = Path.home() / user
    target = parent / NAME
    safe_parents(target)
    return target


def install(target):
    if target.exists():
        raise ValueError("destination exists; inspect and uninstall before updating: " + str(target))
    if ROOT == target or ROOT in target.parents or target in ROOT.parents:
        raise ValueError("installation destination overlaps source")
    if any(linked(p) for p in ROOT.rglob("*")):
        raise ValueError("source symlinks not supported")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.mkdir()  # Exclusive creation prevents overwriting an existing skill.
    try:
        for source in ROOT.iterdir():
            if source.name in {"__pycache__", "dist", ".git", MARKER}:
                continue
            if source.is_dir():
                shutil.copytree(source, target / source.name, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
            else:
                shutil.copy2(source, target / source.name)
        (target / MARKER).write_text(json.dumps({"skill": NAME, "files": files(target)}, indent=2), encoding="utf-8")
    except BaseException:
        shutil.rmtree(target)
        raise


def uninstall(target):
    marker = target / MARKER
    if not marker.is_file() or marker.is_symlink():
        raise ValueError("unmanaged installation; refusing removal: " + str(target))
    if any(linked(p) for p in target.rglob("*")):
        raise ValueError("installation contains symlinks; inspect manually")
    manifest = json.loads(marker.read_text(encoding="utf-8"))
    if manifest.get("skill") != NAME or manifest.get("files") != files(target):
        raise ValueError("installation was modified; back it up and remove manually")
    shutil.rmtree(target)


def main():
    if sys.version_info < (3, 9):
        print("error: Python 3.9+ required; copy the skill folder manually", file=sys.stderr)
        return 2
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--agent", required=True, choices=list(PATHS) + ["custom"])
    parser.add_argument("--scope", choices=["user", "project"], default="user")
    parser.add_argument("--project")
    parser.add_argument("--dest", help="explicit parent directory for skill, required for custom Agent")
    parser.add_argument("--uninstall", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    try:
        if args.agent == "custom" and not args.dest:
            raise ValueError("custom Agent requires --dest; consult its official discovery documentation")
        target = destination(args.agent, args.scope, args.project, args.dest)
        if not args.dry_run:
            (uninstall if args.uninstall else install)(target)
        print(("Would remove " if args.uninstall else "Would install ") + str(target) if args.dry_run else str(target))
        return 0
    except (OSError, ValueError, KeyError) as error:
        print("error: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
