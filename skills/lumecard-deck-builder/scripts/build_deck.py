#!/usr/bin/env python3
"""Build/validate LumeCard content shares. Python 3.9+, standard library only."""
import argparse
import datetime as dt
import json
import re
import sys
import uuid
from pathlib import Path

TYPES = {"BASIC", "REVERSED", "CLOZE", "MULTIPLE_CHOICE", "MARKDOWN"}
FONTS = {"default", "sans-serif", "serif", "monospace", "cursive"}
CLOZE = re.compile(r"\{\{c\d+::([^}]+)\}\}")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def text(value, where):
    require(isinstance(value, str) and bool(value.strip()), where + ": expected non-empty text")
    return value


def objects(value, where):
    require(isinstance(value, list) and bool(value), where + ": expected non-empty array")
    require(all(isinstance(x, dict) for x in value), where + ": expected objects")
    return value


def timestamp(value, where):
    text(value, where)
    require(re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z", value), where + ": expected UTC ISO timestamp")
    dt.datetime.fromisoformat(value.replace("Z", "+00:00"))


def load(path):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, "duplicate JSON key: " + key)
            result[key] = value
        return result
    with Path(path).open(encoding="utf-8-sig") as stream:
        return json.load(stream, object_pairs_hook=pairs)


def style(front, back, kind, overrides):
    require(isinstance(overrides, dict), "style: expected object")
    require(set(overrides) <= {"hcenter", "vcenter", "fontSize", "fontFamily"}, "style: unknown key")
    content = front + "\n" + back
    short = max(len(front), len(back)) <= 100 and "\n" not in front + back
    center = short and kind in {"BASIC", "REVERSED"}
    result = {"hcenter": str(center).lower(), "vcenter": str(center).lower(),
              "fontSize": str(24 if short else 16),
              "fontFamily": "monospace" if "```" in content else "sans-serif"}
    for key, value in overrides.items():
        if key in {"hcenter", "vcenter"}:
            require(isinstance(value, bool), "style." + key + ": expected boolean")
            result[key] = str(value).lower()
        elif key == "fontSize":
            require(type(value) is int and 12 <= value <= 120, "style.fontSize: expected integer 12..120")
            result[key] = str(value)
        else:
            require(value in FONTS, "style.fontFamily: use a built-in font ID")
            result[key] = value
    return result


def card_content(card):
    front = text(card.get("front"), "card.front")
    back = card.get("back", "")
    require(isinstance(back, str), "card.back: expected text")
    kind = card.get("type")
    options = card.get("options")
    if kind is None:
        kind = ("MULTIPLE_CHOICE" if options is not None else "CLOZE" if CLOZE.search(front)
                else "REVERSED" if card.get("bidirectional") is True
                else "MARKDOWN" if re.search(r"```|(?m:^#{1,6} |^[-*] |^\|)", front + "\n" + back)
                else "BASIC")
    require(kind in TYPES, "card.type: unsupported type " + str(kind))
    if options is not None:
        require(kind == "MULTIPLE_CHOICE", "options require MULTIPLE_CHOICE")
        options = objects(options, "card.options")
        lines = []
        for option in options:
            value = text(option.get("text"), "option.text").strip()
            require("\n" not in value and "\r" not in value and not value.startswith("+"), "option.text: single line without leading +")
            require(type(option.get("correct")) is bool, "option.correct: expected boolean")
            lines.append(("+" if option["correct"] else "") + value)
        back = "\n".join(lines)
    if kind == "CLOZE":
        require(CLOZE.search(front) is not None, "CLOZE requires {{c1::answer}} in front")
        back = back or "; ".join(m.group(1).split("::")[0] for m in CLOZE.finditer(front))
    text(back, "card.back")
    if kind == "MULTIPLE_CHOICE":
        lines = [x for x in back.splitlines() if x.strip()]
        require(len(lines) >= 2 and any(x.startswith("+") for x in lines), "choice: at least two options and one leading + correct answer required")
        clean = [x.removeprefix("+").strip() for x in lines]
        require(all(clean) and len(set(clean)) == len(clean), "choice: empty or duplicate option")
    return front, back, kind


def build(plan):
    require(isinstance(plan, dict), "plan: expected object")
    now = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    output = {"exportType": "share", "version": "1.0.0", "schemaVersion": 1,
              "exportDate": now, "knowledgeBases": [], "decks": [], "cards": []}
    def base(prefix):
        return {"id": prefix + "_" + str(uuid.uuid4()), "createdAt": now, "updatedAt": now, "version": 1}
    for kb in objects(plan.get("knowledgeBases"), "knowledgeBases"):
        kb_out = dict(base("kb"), name=text(kb.get("name"), "knowledgeBase.name"), icon="📁")
        output["knowledgeBases"].append(kb_out)
        for deck in objects(kb.get("decks"), "decks"):
            deck_out = dict(base("deck"), name=text(deck.get("name"), "deck.name"),
                            knowledgeBaseId=kb_out["id"], description=None, color="#4CAF50", icon="📚")
            output["decks"].append(deck_out)
            for card in objects(deck.get("cards"), "cards"):
                front, back, kind = card_content(card)
                tags = card.get("tags", [])
                require(isinstance(tags, list) and all(isinstance(x, str) for x in tags), "card.tags: expected string array")
                metadata = style(front, back, kind, card.get("style", {}))
                if "source" in card:
                    metadata["source"] = text(card["source"], "card.source")
                output["cards"].append(dict(base("card"), deckId=deck_out["id"], type=kind,
                                            front=front, back=back, tags=tags, media=[], metadata=metadata))
    validate(output)
    return output


def validate(data):
    require(isinstance(data, dict), "export: expected object")
    require(data.get("exportType") == "share", "exportType must be share")
    require(data.get("version") == "1.0.0" and type(data.get("schemaVersion")) is int and data["schemaVersion"] == 1, "unsupported export/schema version")
    require(not (set(data) & {"settings", "reviewLogs", "learningPlans", "algorithmStates", "splashQuotes"}), "private backup fields forbidden")
    timestamp(data.get("exportDate"), "exportDate")
    ids = set()
    for key in ("knowledgeBases", "decks", "cards"):
        for item in objects(data.get(key), key):
            ident = text(item.get("id"), key + ".id")
            require(ident not in ids, "duplicate ID: " + ident)
            ids.add(ident)
            for field in ("createdAt", "updatedAt"):
                timestamp(item.get(field), key + "." + field)
            require(type(item.get("version", 1)) is int and item.get("version", 1) >= 1, "record version: expected positive integer")
            require(all(item.get(k) is None for k in ("deletedAt", "syncedAt", "lastReviewedAt", "nextReviewAt")), "history/tombstones forbidden in new decks")
    kbs = {x["id"] for x in data["knowledgeBases"]}
    decks = {x["id"] for x in data["decks"]}
    for kb in data["knowledgeBases"]:
        text(kb.get("name"), "knowledgeBase.name")
        if "icon" in kb:
            text(kb["icon"], "knowledgeBase.icon")
        require(kb.get("description") is None or isinstance(kb["description"], str), "knowledgeBase.description: expected text or null")
    for deck in data["decks"]:
        text(deck.get("name"), "deck.name")
        require(deck.get("knowledgeBaseId") in kbs, "dangling knowledgeBaseId")
        require(deck.get("parentId") is None, "nested decks not supported by this skill")
        for field in ("color", "icon"):
            text(deck.get(field), "deck." + field)
        require("description" in deck and (deck["description"] is None or isinstance(deck["description"], str)), "deck.description required (text or null)")
    for card in data["cards"]:
        require(card.get("deckId") in decks, "dangling deckId")
        require(card.get("type") in TYPES, "unsupported card type")
        text(card.get("back"), "card.back")
        require(isinstance(card.get("title", ""), str), "card.title: expected text")
        card_content(card)
        require(isinstance(card.get("tags"), list) and all(isinstance(x, str) for x in card["tags"]), "tags: expected string array")
        require(card.get("media", []) == [], "external media not supported")
        meta = card.get("metadata")
        require(isinstance(meta, dict) and all(isinstance(v, str) for v in meta.values()), "metadata values must be strings")
        for key in ("hcenter", "vcenter"):
            require(meta.get(key) in {"true", "false"}, "metadata." + key + ": expected true/false string")
        require(meta.get("fontFamily") in FONTS, "unknown font ID")
        require(re.fullmatch(r"\d+", meta.get("fontSize", "")) is not None and 12 <= int(meta["fontSize"]) <= 120, "fontSize: expected string 12..120")
    return {key: len(data[key]) for key in ("knowledgeBases", "decks", "cards")}


def write_new(path, data):
    with Path(path).open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n")


def main():
    if sys.version_info < (3, 9):
        print("error: Python 3.9+ required; author JSON manually using references/format.md", file=sys.stderr)
        return 2
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    create = sub.add_parser("build")
    create.add_argument("plan")
    create.add_argument("output")
    check = sub.add_parser("validate")
    check.add_argument("input")
    args = parser.parse_args()
    try:
        if args.action == "build":
            data = build(load(args.plan))
            write_new(args.output, data)
        else:
            data = load(args.input)
        print(json.dumps(validate(data), ensure_ascii=False))
        return 0
    except (ValueError, TypeError, KeyError, OSError) as error:
        print("error: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
