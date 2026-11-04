"""Generate builder/validator fixtures consumed by the application's desktop tests."""
import argparse
import copy
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.dont_write_bytecode = True
import build_deck as builder


def write_fixtures(output):
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    valid = builder.build(builder.load(ROOT / "examples/plan.json"))
    valid["cards"].append(dict(valid["cards"][0], id="card_reversed", type="REVERSED"))
    cases = [("generated", valid, True)]
    maximum = copy.deepcopy(valid)
    for collection in ("knowledgeBases", "decks", "cards"):
        maximum[collection][0]["version"] = builder.LONG_MAX
    cases.append(("long_max", maximum, True))
    for name, field, value in (("device_object", "deviceId", {}),
                               ("device_number", "deviceId", 123),
                               ("backup_version", "backupFormatVersion", 99)):
        data = copy.deepcopy(valid)
        data[field] = value
        cases.append((name, data, False))
    for collection in ("knowledgeBases", "decks", "cards"):
        data = copy.deepcopy(valid)
        data[collection][0]["version"] = builder.LONG_MAX + 1
        cases.append((collection + "_overflow", data, False))
    manifest = []
    for name, data, accepted in cases:
        try:
            builder.validate(data)
            validated = True
        except ValueError:
            validated = False
        if validated != accepted:
            raise AssertionError("unexpected validator result: " + name)
        filename = name + ".json"
        builder.write_new(output / filename, data)
        manifest.append({"file": filename, "accepted": accepted})
    builder.write_new(output / "manifest.json", {"cases": manifest})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", help="directory for new fixture files")
    write_fixtures(parser.parse_args().output)
