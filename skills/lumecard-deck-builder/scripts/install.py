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


def detection_paths(agent, project=None):
    home = Path.home()
    paths = {
        "codex": [Path(os.environ.get("CODEX_HOME") or home / ".codex")],
        "claude": [home / ".claude", home / ".claude.json"],
        "opencode": [Path(os.environ.get("XDG_CONFIG_HOME") or home / ".config") / "opencode"],
        "hermes": [Path(os.environ.get("HERMES_HOME") or home / ".hermes")],
        "codebuddy": [home / ".codebuddy"],
    }[agent]
    if project and agent != "hermes":
        paths.append(Path(project) / ("." + agent))
    return paths


def detect_agents(scope="user", project=None):
    """Evidence only: do not run Agent binaries, read credentials or create directories."""
    rows = []
    for agent in PATHS:
        reasons = []
        executable = shutil.which(agent)
        if executable:
            reasons.append("PATH: " + executable)
        reasons.extend("config: " + str(path) for path in detection_paths(agent, project) if path.exists())
        supported = scope == "user" or PATHS[agent][1] is not None
        blocked = None
        try:
            target = destination(agent, scope, project) if supported else None
        except ValueError as error:
            target, supported, blocked = None, False, str(error)
        managed = target is not None and (target / MARKER).is_file()
        if managed:
            reasons.append("managed skill: " + str(target))
        rows.append({"agent": agent, "detected": bool(reasons), "supported": supported,
                     "target": str(target) if target else None, "managed": managed, "reasons": reasons,
                     "blocked": blocked})
    return rows


def choose_agents(rows, yes=False, dry_run=False, uninstalling=False):
    eligible = [row["agent"] for row in rows if row["detected"] and row["supported"]
                and (not uninstalling or row["managed"])]
    if not eligible:
        raise ValueError("no eligible Agents detected; specify --agent explicitly (or --agent custom --dest PARENT)")
    if yes or dry_run:
        return eligible
    if not sys.stdin.isatty():
        raise ValueError("automatic selection requires a terminal; use --yes for detected Agents, --agent for explicit selection, or --dry-run")
    answer = input("Select Agents (comma-separated names, Enter=all, none=cancel): ").strip().lower()
    if answer == "none":
        return []
    selected = list(dict.fromkeys(part.strip() for part in answer.split(","))) if answer else eligible
    if any(agent not in eligible for agent in selected):
        raise ValueError("select only detected eligible Agents: " + ", ".join(eligible))
    return selected


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


def check_install(target):
    safe_parents(target)
    if target.exists():
        raise ValueError("destination exists; inspect and uninstall before updating: " + str(target))
    if ROOT == target or ROOT in target.parents or target in ROOT.parents:
        raise ValueError("installation destination overlaps source")
    if any(linked(p) for p in ROOT.rglob("*")):
        raise ValueError("source symlinks not supported")


def install(target):
    check_install(target)
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


def check_uninstall(target):
    safe_parents(target)
    marker = target / MARKER
    if not marker.is_file() or marker.is_symlink():
        raise ValueError("unmanaged installation; refusing removal: " + str(target))
    if any(linked(p) for p in target.rglob("*")):
        raise ValueError("installation contains symlinks; inspect manually")
    manifest = json.loads(marker.read_text(encoding="utf-8"))
    if not isinstance(manifest, dict) or manifest.get("skill") != NAME or manifest.get("files") != files(target):
        raise ValueError("installation was modified; back it up and remove manually")


def uninstall(target):
    check_uninstall(target)
    shutil.rmtree(target)


def main():
    if sys.version_info < (3, 9):
        print("error: Python 3.9+ required; copy the skill folder manually", file=sys.stderr)
        return 2
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--agent", help="Agent name(s), comma-separated; omitted or auto detects installed Agents")
    parser.add_argument("--scope", choices=["user", "project"], default="user")
    parser.add_argument("--project")
    parser.add_argument("--dest", help="explicit parent directory for skill, required for custom Agent")
    parser.add_argument("--uninstall", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--yes", action="store_true", help="use all detected eligible Agents without prompting")
    parser.add_argument("--list-agents", action="store_true", help="show detection evidence and destinations without changes")
    args = parser.parse_args()
    try:
        if args.scope == "project" and not args.dest and (not args.project or not Path(args.project).is_dir()):
            raise ValueError("project scope requires --project pointing to an existing directory")
        automatic = args.agent in (None, "auto")
        if automatic and args.dest:
            raise ValueError("--dest requires an explicit single --agent to avoid ambiguous destinations")
        if automatic or args.list_agents:
            rows = detect_agents(args.scope, args.project)
            for row in rows:
                state = "detected" if row["detected"] else "not found"
                if not row["supported"]:
                    state += "; destination blocked: " + row["blocked"] if row["blocked"] else "; scope unsupported"
                details = "; ".join(row["reasons"])
                print(row["agent"] + ": " + state + ("; " + details if details else ""))
                if row["target"]:
                    print("  target: " + row["target"])
            if args.list_agents:
                return 0
            selected = choose_agents(rows, args.yes, args.dry_run, args.uninstall)
        else:
            selected = list(dict.fromkeys(part.strip() for part in args.agent.split(",")))
        if any(agent not in list(PATHS) + ["custom"] for agent in selected):
            raise ValueError("unknown Agent; choose " + ", ".join(PATHS) + ", custom, or auto")
        if args.dest and len(selected) != 1:
            raise ValueError("--dest requires a single Agent")
        if "custom" in selected and not args.dest:
            raise ValueError("custom Agent requires --dest; consult its official discovery documentation")
        targets = [(agent, destination(agent, args.scope, args.project, args.dest)) for agent in selected]
        if automatic and not args.uninstall:
            compatible = next((target for agent, target in targets if agent in {"codex", "claude"}), None)
            if compatible is not None:
                # OpenCode also discovers these standard compatibility roots.
                kept = []
                for agent, target in targets:
                    if agent == "opencode" and not target.exists():
                        print("opencode: uses compatible skill at " + str(compatible))
                    else:
                        kept.append((agent, target))
                targets = kept
        # Validate every target before beginning a multi-Agent mutation.
        for agent, target in targets:
            if automatic and not args.uninstall and (target / MARKER).is_file():
                check_uninstall(target)
                print(agent + ": already installed; kept unchanged (uninstall before upgrading)")
                continue
            (check_uninstall if args.uninstall else check_install)(target)
        for agent, target in targets:
            if automatic and not args.uninstall and (target / MARKER).is_file():
                continue
            if not args.dry_run:
                (uninstall if args.uninstall else install)(target)
            verb = "Would remove " if args.uninstall else "Would install "
            print(agent + ": " + (verb if args.dry_run else "Removed " if args.uninstall else "Installed ") + str(target))
        if not selected:
            print("Cancelled; no changes")
        return 0
    except (OSError, ValueError, KeyError, EOFError) as error:
        print("error: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
