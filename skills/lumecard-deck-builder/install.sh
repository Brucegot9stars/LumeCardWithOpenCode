#!/bin/sh
set -eu
skill_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
if command -v python3 >/dev/null 2>&1; then
    exec python3 "$skill_dir/scripts/install.py" "$@"
elif command -v python >/dev/null 2>&1; then
    exec python "$skill_dir/scripts/install.py" "$@"
fi
printf '%s\n' 'Python 3.9+ is required for automated installation. Copy the skill folder manually; see README.md.' >&2
exit 127
