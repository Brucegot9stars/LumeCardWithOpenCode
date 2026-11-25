#!/usr/bin/env python3
"""Create a portable zip without caches, installations or build artifacts."""
import argparse
from pathlib import Path
import sys
import zipfile
sys.dont_write_bytecode = True
from distribution import payload_files

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", help="new ZIP file path")
    args = parser.parse_args()
    files = payload_files(ROOT)
    with zipfile.ZipFile(args.output, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for file in files:
            rel = file.relative_to(ROOT)
            archive.write(file, ROOT.name + "/" + rel.as_posix())
    print(args.output)


if __name__ == "__main__":
    # The archive path can contain non-GBK characters on a Chinese Windows console. A strict
    # stdout would raise after the zip was created, leaving a traceback and a "failed" command
    # that actually succeeded. Match stderr's backslashreplace policy.
    try:
        sys.stdout.reconfigure(errors="backslashreplace")
    except (AttributeError, OSError):
        pass
    main()
