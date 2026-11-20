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

    def image_card(data):
        for card in data["cards"]:
            if card.get("media"):
                return card
        raise AssertionError("generated plan must contain an image card")

    # https image-host URL is a valid source and must survive the Kotlin importer.
    image_https = copy.deepcopy(valid)
    image_card(image_https)["media"][0]["filePath"] = "https://example.com/diagram.png"
    cases.append(("image_https", image_https, True))

    # Per-image alignment and the compression flag must survive the round trip unchanged.
    image_alignment = copy.deepcopy(valid)
    entry = image_card(image_alignment)["media"][0]
    entry["alignment"] = "end"
    entry["compress"] = False
    cases.append(("image_alignment", image_alignment, True))

    # An unknown alignment is a skill-validator error only: the app stays lenient at read time so a
    # hand-edited or future value degrades to centered instead of dropping the image. This is not a
    # shared contract case, so it is asserted in the Python suite rather than here.

    # A Media element without its required id must be rejected by both sides.
    image_missing_id = copy.deepcopy(valid)
    del image_card(image_missing_id)["media"][0]["id"]
    cases.append(("image_missing_id", image_missing_id, False))
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
