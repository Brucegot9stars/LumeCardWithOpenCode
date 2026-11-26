#!/usr/bin/env python3
"""Build/validate LumeCard content shares and media archives. Python 3.9+, standard library only."""
import argparse
import datetime as dt
import hashlib
import json
import os
import re
import stat
import sys
import uuid
import zipfile
import zlib
from pathlib import Path

TYPES = {"BASIC", "REVERSED", "CLOZE", "MULTIPLE_CHOICE", "MARKDOWN"}
FONTS = {"default", "sans-serif", "serif", "monospace", "cursive"}
MEDIA_TARGETS = {"both", "front", "back"}
MEDIA_ALIGNMENTS = {"start", "center", "end"}
MEDIA_FIELDS = {"id", "type", "filePath", "mimeType", "size", "caption", "alignment", "compress"}
# `sourcePath` is a plan-only field: it names the local image to embed and never reaches the export,
# because the app resolves `filePath` against its own media root and would treat a build-machine path
# as a media-relative name.
PLAN_MEDIA_FIELDS = MEDIA_FIELDS | {"sourcePath"}
CLOZE = re.compile(r"\{\{c\d+::([^}]+)\}\}")
# `\S` matched NUL/control characters, which Java's URI constructor (used by the app's loader)
# rejects; an all-ASCII rule would in turn reject the Unicode paths and IDN hosts the app accepts.
# Measured against JDK's URI: ASCII controls, space and `<>"{}[]|\^`` are invalid, everything else
# (including non-ASCII) is accepted. Percent escapes must be two hex digits; Java rejects `%zz`
# and even a trailing `%`. Mirror those rules instead of approximating RFC 3986.
URL_HTTPS = re.compile(r"https://[^\x00-\x20<>\"{}\[\]\\|^`]+", re.IGNORECASE)
BAD_PERCENT_ESCAPE = re.compile(r"%(?![0-9A-Fa-f]{2})")
# Same rule as the app's MediaPathRules, including the superscript device spellings (COM¹ …).
WINDOWS_RESERVED = re.compile(r"^(con|prn|aux|nul|com[1-9¹²³]|lpt[1-9¹²³])(\..*)?$", re.IGNORECASE)
LONG_MAX = 2**63 - 1
LINE_BREAKS = re.compile(r"[\r\n\v\f\x1c-\x1e\x85\u2028\u2029]")
# Kotlin's Char.isWhitespace() is `Character.isWhitespace(c) || Character.isSpaceChar(c)`, which
# was measured on the JVM (U+00A0, U+2007 and U+202F report false for isWhitespace alone but true
# for isSpaceChar). String.trim() and isBlank() both use it, so this set must match exactly:
# CardComponents.kt trims each choice line before testing for `+`.
KOTLIN_WHITESPACE = (
    "\t\n\v\f\r\x1c\x1d\x1e\x1f \u00a0\u1680"
    "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a"
    "\u2028\u2029\u202f\u205f\u3000")
RECORD_FIELDS = {"id", "createdAt", "updatedAt", "version", "deletedAt", "syncedAt"}
# Every failure mode that must become a clean `error:` line instead of a traceback. Used both by
# main()'s outer handler and by package's self-check, so a failed self-check cannot skip cleanup
# just because it raised a different member of the same family (e.g. OSError on a full disk).
INPUT_ERRORS = (ValueError, TypeError, KeyError, OSError, zipfile.BadZipFile,
                zipfile.LargeZipFile, NotImplementedError, RuntimeError, zlib.error)

# Archive contract, mirrored from the app's LumecardArchive object.
ARCHIVE_EXTENSION = "lumecard"
ARCHIVE_MANIFEST = "lumecard.json"
ARCHIVE_MEDIA_DIR = "media/"
# Budgets the app enforces when *importing* an archive. Exceeding any of them does not fail the
# import outright: the affected image is silently left off disk and the card keeps a dangling
# reference, which reads as a broken image forever. Checking here turns that into a build error.
# Mirrors ExportArchiveService (MAX_IMPORT_MEDIA_BYTES / MAX_IMPORT_MEDIA_TOTAL_BYTES) and
# LumecardArchive (MAX_MANIFEST_BYTES / MAX_ARCHIVE_ENTRIES).
MAX_MEDIA_BYTES = 64 * 1024 * 1024
MAX_TOTAL_MEDIA_BYTES = 1024 * 1024 * 1024
MAX_MEDIA_ENTRIES = 10_000
# The app's listZipArchive gives up at MAX_ARCHIVE_ENTRIES * 2 non-directory entries, counting
# the manifest and any stray files — not just media. A zip full of resource forks can therefore
# fail to import even when every media entry is within budget.
MAX_ARCHIVE_LIST_ENTRIES = 20_000
MAX_MANIFEST_BYTES = 64 * 1024 * 1024
# Fixed timestamp so re-running a build over unchanged inputs yields identical entry metadata.
ARCHIVE_ENTRY_TIME = (1980, 1, 1, 0, 0, 0)

# Magic numbers the app can actually decode (Skia Codec on desktop, BitmapFactory on Android).
# SVG is deliberately absent: both back ends fail to decode it, so embedding it would produce a
# permanently broken card image.
IMAGE_SIGNATURES = (
    (b"\x89PNG\r\n\x1a\n", "png", "image/png"),
    (b"\xff\xd8\xff", "jpg", "image/jpeg"),
    (b"GIF87a", "gif", "image/gif"),
    (b"GIF89a", "gif", "image/gif"),
)
# A JPEG's first marker after SOI is one of these. `\xff\xd8\xff\x00` is a four-byte prefix that
# matches the 3-byte signature but is not a JPEG at all, and the app would show a broken image.
JPEG_FIRST_MARKERS = frozenset(
    list(range(0xC0, 0xD0)) + [0xD8, 0xDD, 0xDB, 0xC4, 0xE0, 0xE1, 0xE2, 0xE3,
                               0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9, 0xEA, 0xEB,
                               0xEC, 0xED, 0xEE, 0xEF, 0xFE])
# Structural terminators a complete file of each format must contain. A byte-count floor alone is
# not enough: a signature + IHDR + filler can reach any floor and still be undecodable. These are
# de facto format requirements present in every file the app's decoders accept, so checking them
# on the full payload rejects header-only files without rejecting legitimately small images.
PNG_IEND = b"\x00\x00\x00\x00IEND\xaeB`\x82"
JPEG_EOI = b"\xff\xd9"
JPEG_SOS = b"\xff\xda"
GIF_TRAILER = b"\x3b"


def sniff_image(head):
    """Return (extension, mime) for bytes the app can render, or None.

    Signatures are checked with as much structure as available, not just the shortest prefix that
    a format name implies: a two-byte match would classify ordinary files as images and let a
    broken picture reach a card. A signature-only PNG/JPEG would still pass that check and be
    packaged into an archive whose image never decodes, so require the format's first structural
    chunk/marker as well. These are fixed by the PNG/JPEG specs and present in every valid file;
    the same 16-byte prefix is available both to `collect_image` and to `inspect_archive`.
    """
    for signature, extension, mime in IMAGE_SIGNATURES:
        if head.startswith(signature):
            if extension == "png" and head[12:16] != b"IHDR":
                # PNG: 8-byte signature, 4-byte chunk length, then the IHDR chunk type.
                return None
            if extension == "jpg":
                # JPEG permits any number of 0xFF fill bytes before a marker. SOI is FF D8; skip
                # filler starting at byte 2 and require a real marker code after it.
                index = 2
                while index < len(head) and head[index] == 0xFF:
                    index += 1
                if index >= len(head) or head[index] not in JPEG_FIRST_MARKERS:
                    return None
            return extension, mime
    # WEBP needs a two-part check: RIFF....WEBP.
    if head[:4] == b"RIFF" and head[8:12] == b"WEBP":
        return "webp", "image/webp"
    # BMP is only `BM` + a 32-bit size, so any text beginning with those letters would match. A
    # real BMP header keeps bytes 6..9 reserved and zero.
    if head[:2] == b"BM" and head[6:10] == b"\x00\x00\x00\x00":
        return "bmp", "image/bmp"
    return None


def image_is_complete(data, image_type):
    """True when the full payload has the terminators a decodable file of this type needs.

    `sniff_image` works on a 16-byte prefix and cannot tell a complete image from a truncated one.
    These checks use the whole payload (available in both collect_image and inspect_archive) and
    look only for markers every valid file carries, so small real images still pass.
    """
    if image_type == "png":
        return data.endswith(PNG_IEND) and b"IDAT" in data
    if image_type == "jpg":
        return data.endswith(JPEG_EOI) and JPEG_SOS in data
    if image_type == "gif":
        return data.endswith(GIF_TRAILER) and len(data) >= 35
    if image_type == "webp":
        # RIFF's 32-bit little-endian size counts everything after the 8-byte header.
        return len(data) >= 20 and int.from_bytes(data[4:8], "little") == len(data) - 8
    if image_type == "bmp":
        # BMP's own header declares the total file size; a header-only file lies about it.
        return len(data) >= 26 and int.from_bytes(data[2:6], "little") == len(data)
    return True


def utf16_len(value):
    """Length in UTF-16 code units, which is what Kotlin's `String.length` counts.

    The app's MediaPathRules bounds names with `path.length`, so one emoji costs two characters
    there. Measuring code points here would accept a 65-emoji name that the app rejects, and the
    archive would then import with the image silently dropped.
    """
    return len(value.encode("utf-16-le", "surrogatepass")) // 2


def safe_relative_path(path):
    """True when `path` is a media-root-relative name the app is willing to store.

    Mirrors `MediaPathRules.isSafeRelativePath` rule for rule, in the same order, so a name that
    passes here cannot be one the app refuses to write or read back.
    """
    if not path.strip() or utf16_len(path) > 255:
        return False
    if any(ord(ch) < 32 for ch in path):
        return False
    if "\\" in path:
        return False
    if path.startswith("/"):
        return False
    # A drive prefix is absolute to the platform, never media-root relative.
    if len(path) >= 2 and path[1] == ":" and path[0].isalpha():
        return False
    lower = path.lower()
    if lower.startswith(("http://", "https://", "file://", "content://", "data:")):
        return False
    segments = path.split("/")
    if any(not seg or seg in (".", "..") for seg in segments):
        return False
    if any(utf16_len(seg) > 128 for seg in segments):
        return False
    # Win32 strips these, so `a.png.` and `a.png` address the same file on Windows.
    if any(seg.endswith(".") or seg.endswith(" ") for seg in segments):
        return False
    # `:` also enables NTFS alternate data streams (`a.png:hidden`).
    if any(":" in seg for seg in segments):
        return False
    if any(WINDOWS_RESERVED.fullmatch(seg) for seg in segments):
        return False
    return True


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
    # The app parses these with Instant.parse, whose ISO_INSTANT form accepts at most nine
    # fractional digits. Python's fromisoformat silently truncates longer fractions, so a value
    # that passes validation here throws DateTimeFormatException in the importer and rolls back
    # the entire share. Bound the digits to the app's grammar before handing it to fromisoformat.
    require(re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?Z", value),
            where + ": expected UTC ISO timestamp (at most 9 fractional digits)")
    try:
        dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        # A bare "month must be in 1..12" gives an author no idea which of the many records it
        # came from; validate() is the only place that checks hand-edited files.
        raise ValueError(where + ": invalid UTC ISO timestamp: " + str(error))


def fields(value, allowed, where):
    require(not (set(value) - allowed), where + ": unsupported fields " + ", ".join(sorted(set(value) - allowed)))


def media_target(value, where):
    require(value in MEDIA_TARGETS, where + ": expected both/front/back")
    return value


def media_items(value, where, sources=None, plan=False):
    """Validate card.media and normalise it into the app's Media JSON shape.

    A plan item may name a local image through `sourcePath` instead of `filePath`. In that case
    the file is read here, validated as a decodable image, and `filePath` is derived as
    `<sha1>.<ext>` — exactly the name the app itself gives a copied media file, so the archive
    entry and the card reference agree and importing is an identity mapping.

    `plan` distinguishes the two callers, because `sourcePath` means opposite things to them: in a
    plan it is the supported way to attach an image, in an already-built export it is a leftover
    that the app would try to resolve as a media name.
    """
    require(isinstance(value, list), where + ": expected array")
    result = []
    for index, item in enumerate(value):
        loc = "%s[%d]" % (where, index)
        require(isinstance(item, dict), loc + ": expected object")
        if not plan and "sourcePath" in item:
            raise ValueError(loc + ".sourcePath: not allowed in an export; the app resolves filePath "
                                   "against its own media root")
        if plan and sources is None and "sourcePath" in item:
            # `build` writes bare JSON, which cannot carry the image bytes. Say so instead of
            # failing with a generic "unsupported field", because the fix is a different subcommand.
            raise ValueError(loc + ".sourcePath: embedding local images requires the 'package' action")
        allowed = PLAN_MEDIA_FIELDS if plan else MEDIA_FIELDS
        fields(item, allowed, loc)
        require(item.get("type") == "IMAGE", loc + ".type: only IMAGE is supported")
        ident = text(item.get("id"), loc + ".id")
        collected = None
        if "sourcePath" in item:
            require("filePath" not in item, loc + ": use either filePath or sourcePath, not both")
            collected = collect_image(item["sourcePath"], loc)
            path = collected.name
        else:
            # Deliberately not stripped: the importer compares this string byte for byte against
            # archive entry names, so silent trimming here would validate a name that the archive
            # can never satisfy.
            path = item.get("filePath")
            text(path, loc + ".filePath")
            require(path == path.strip(), loc + ".filePath: no leading or trailing whitespace")
        lower = path.lower()
        require(not lower.startswith("http://"), loc + ".filePath: only https URLs are allowed")
        if lower.startswith("https://"):
            # A remote image is referenced, never archived, so the media-root rules below do not
            # apply: the app resolves it over the network instead of on disk. A `..` inside a URL
            # path is the server's concern, not a local traversal, so it is allowed here.
            require(URL_HTTPS.fullmatch(path) is not None
                    and BAD_PERCENT_ESCAPE.search(path) is None,
                    loc + ".filePath: malformed https URL")
        else:
            # Everything else has to be a name the app is willing to store. The importer, exporter
            # and archive writer all use the same rule, so a weaker check here would build a file
            # whose deck then drops the image on import or export.
            require(not any(seg == ".." for seg in re.split(r"[\\/]", path)),
                    loc + ".filePath: path traversal '..' is forbidden")
            if not safe_relative_path(path):
                # The overwhelmingly likely cause is a local path written as `filePath`, which is
                # the field the app's own JSON uses. The generic rule list below does not hint at
                # the fix, so a path that looks like a real local file gets its own message.
                looks_local = (path.startswith("/") or "\\" in path
                               or (len(path) >= 2 and path[1] == ":" and path[0].isalpha())
                               or path.startswith("~"))
                if looks_local:
                    raise ValueError(
                        loc + ".filePath: %r is a local path, but filePath must be a media-root "
                        "relative name (like a1b2c3.png) or an https URL. To embed a local image, "
                        "name it with media[].sourcePath and run the 'package' action instead" % path)
                raise ValueError(
                    loc + ".filePath: expected a media-root relative path using '/' (no '..', '.', "
                    "'' segments, no backslash, drive letter, ':', control characters, Windows "
                    "reserved names, or trailing dot/space; segments <=128 and total <=255 "
                    "characters) or an https URL")
        caption = item.get("caption", "")
        require(isinstance(caption, str), loc + ".caption: expected text")
        if collected is not None:
            # Fill the descriptive fields from the real file; an author-visible size/mime mismatch
            # is harmless at import, but reporting the truth costs nothing here.
            mime, size = collected.mime, collected.size
            sources[collected.name] = collected
        else:
            mime = item.get("mimeType", "")
            require(isinstance(mime, str), loc + ".mimeType: expected text")
            size = item.get("size", 0)
            require(type(size) is int and 0 <= size <= LONG_MAX, loc + ".size: expected non-negative integer")
            if plan and sources is not None and not lower.startswith("https://"):
                # A plan that promises a local image but supplies no bytes produces an archive
                # whose manifest references a file that is not inside it. The app imports the
                # card, finds nothing to extract, and leaves the image broken forever — with no
                # error shown to anyone. Requiring `sourcePath` here is the only place the
                # mistake can still be caught cheaply.
                raise ValueError(
                    loc + ".filePath: a plan must attach local images with sourcePath so their "
                    "bytes reach the archive; filePath alone names a file this build cannot see. "
                    "Use https URLs for remote images")
        alignment = item.get("alignment", "center")
        require(alignment in MEDIA_ALIGNMENTS, loc + ".alignment: expected start, center or end")
        compress = item.get("compress", True)
        require(isinstance(compress, bool), loc + ".compress: expected boolean")
        result.append({"id": ident, "type": "IMAGE", "filePath": path,
                       "mimeType": mime, "size": size, "caption": caption,
                       "alignment": alignment, "compress": compress})
    return result


class CollectedImage(object):
    """A local image read from disk, addressed by the media name the app would give it."""

    def __init__(self, name, mime, data):
        self.name = name
        self.mime = mime
        self.size = len(data)
        self.data = data


def collect_image(source, where):
    """Read and identify a local image, returning its content-addressed media name."""
    require(isinstance(source, str) and source.strip(), where + ".sourcePath: expected a file path")
    path = Path(source).expanduser()
    try:
        info = path.lstat()
    except OSError as error:
        raise ValueError("%s.sourcePath: cannot read %s (%s)" % (where, source, error.strerror))
    # `is_file()` follows symlinks and accepts anything the OS reports as a regular file; a FIFO
    # or device node would block or stream forever in `read_bytes()`. A symlink is refused too:
    # the plan names the image it wants, so a link adds an indirection that can point anywhere and
    # the archive would then carry the target's bytes under a name the author never wrote.
    require(not stat.S_ISLNK(info.st_mode), where + ".sourcePath: symbolic links are not followed: " + source)
    require(stat.S_ISREG(info.st_mode), where + ".sourcePath: not a regular file: " + source)
    require(0 < info.st_size <= MAX_MEDIA_BYTES,
            "%s.sourcePath: expected a file of 1 byte to %d bytes (%.0f MB is the largest image the "
            "app will import); got %d bytes: %s"
            % (where, MAX_MEDIA_BYTES, MAX_MEDIA_BYTES / (1024.0 * 1024.0), info.st_size, source))
    data = path.read_bytes()
    # Re-check the length actually read: the file can grow between stat and read.
    require(0 < len(data) <= MAX_MEDIA_BYTES,
            "%s.sourcePath: file changed while reading (now %d bytes): %s" % (where, len(data), source))
    sniffed = sniff_image(data[:16])
    require(sniffed is not None and image_is_complete(data, sniffed[0]),
            where + ".sourcePath: unsupported, unrecognised or truncated image (PNG, JPEG, GIF, "
            "WebP and BMP are rendered by the app; the file must carry more than a header): " + source)
    extension, mime = sniffed
    # Content addressing mirrors `copyFileToMedia` in the app: the name is the SHA-1 of the bytes,
    # so two references to identical images collapse onto one archive entry.
    name = hashlib.sha1(data).hexdigest() + "." + extension
    require(len(name) <= 255, where + ".sourcePath: derived media name too long")
    return CollectedImage(name, mime, data)


def choice_lines(back, where="card.back"):
    """Validate a MULTIPLE_CHOICE back, matching how the app parses it.

    CardComponents.kt trims each line *before* testing for a leading `+`, so `"  +A"` is a correct
    answer to the app. Testing the raw line here would call that same string a plain option, and a
    card with a single indented marker would silently have no correct answer at all. The trim set
    is Kotlin's, not Python's: NBSP & co. look blank to us but remain a real option to the app.
    """
    require(LINE_BREAKS.search(back.replace("\n", "")) is None, where + ": use LF (\\n) option separators")
    lines = [x for x in back.split("\n") if x.strip(KOTLIN_WHITESPACE)]
    trimmed = [x.strip(KOTLIN_WHITESPACE) for x in lines]
    require(len(lines) >= 2 and any(x.startswith("+") for x in trimmed),
            where + ": at least two options and one leading + correct answer required")
    clean = [x.removeprefix("+").strip(KOTLIN_WHITESPACE) for x in trimmed]
    require(all(clean) and len(set(clean)) == len(clean), where + ": empty or duplicate option")
    return lines


def strict_json_loads(decoded, name):
    """Parse JSON with the same strictness for plan files AND archives.

    Duplicate keys and non-finite numbers are rejected here rather than in `validate`, because
    JSON's last-key-wins decoding happens before validation ever sees the object. Sharing the
    parser keeps `inspect` from passing a manifest that `validate` would refuse as a plan.
    """
    def constant(value):
        raise ValueError(name + ": non-finite JSON number: " + value)
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, name + ": duplicate JSON key: " + key)
            result[key] = value
        return result
    try:
        return json.loads(decoded, object_pairs_hook=pairs, parse_constant=constant)
    except json.JSONDecodeError as error:
        raise ValueError("%s is not valid JSON at line %d column %d: %s"
                         % (name, error.lineno, error.colno, error.msg))


def load(path):
    """Read a plan or share JSON, turning every parse failure into an actionable message.

    A bare `JSONDecodeError` string ("Expecting value: line 1 column 1") says nothing about which
    file or what to do, and a UnicodeDecodeError surfaces as codec internals. Both are the most
    common ways a hand-edited file fails, so they get named explicitly.
    """
    path = Path(path)
    require(not path.is_dir(), "input: %s is a directory; pass the plan .json file inside it" % path)
    require(path.is_file(), "input: no such file: " + str(path))
    try:
        size = path.stat().st_size
        require(size <= MAX_MANIFEST_BYTES,
                "input: %s is %.1f MB, over the %.0f MB this tool reads; split the deck"
                % (path.name, size / (1024.0 * 1024.0), MAX_MANIFEST_BYTES / (1024.0 * 1024.0)))
        # Read one byte past the cap so a file that grows between stat and read is caught too.
        with path.open("rb") as stream:
            raw = stream.read(MAX_MANIFEST_BYTES + 1)
    except OSError as error:
        raise ValueError("input: cannot read %s (%s)" % (path, error.strerror or error))
    require(len(raw) <= MAX_MANIFEST_BYTES,
            "input: %s grew past the %.0f MB this tool reads"
            % (path.name, MAX_MANIFEST_BYTES / (1024.0 * 1024.0)))
    require(raw.strip(), "input: %s is empty" % path.name)
    # A `.lumecard` reaches here when someone reaches for `build` on an archive; the "not UTF-8"
    # message below would then blame the encoding instead of the wrong command.
    if raw[:4] == b"PK\x03\x04":
        raise ValueError("input: %s is a .lumecard archive, not a plan; use `inspect` for it" % path.name)
    try:
        decoded = raw.decode("utf-8-sig")
    except UnicodeDecodeError as error:
        byte = raw[error.start] if error.start < len(raw) else 0
        raise ValueError(
            "input: %s is not UTF-8 (byte 0x%02X at offset %d); save it as UTF-8 and retry"
            % (path.name, byte, error.start))
    return strict_json_loads(decoded, "input: " + path.name)


def style(front, back, kind, overrides, where="style"):
    require(isinstance(overrides, dict), where + ": expected object")
    require(set(overrides) <= {"hcenter", "vcenter", "fontSize", "fontFamily"}, where + ": unknown key")
    content = front + "\n" + back
    short = max(len(front), len(back)) <= 100 and "\n" not in front + back
    center = short and kind in {"BASIC", "REVERSED"}
    result = {"hcenter": str(center).lower(), "vcenter": str(center).lower(),
              "fontSize": str(24 if short else 16),
              "fontFamily": "monospace" if "```" in content else "sans-serif"}
    for key, value in overrides.items():
        if key in {"hcenter", "vcenter"}:
            require(isinstance(value, bool), where + "." + key + ": expected boolean")
            result[key] = str(value).lower()
        elif key == "fontSize":
            require(type(value) is int and 12 <= value <= 120, where + ".fontSize: expected integer 12..120")
            result[key] = str(value)
        else:
            require(value in FONTS, where + ".fontFamily: use a built-in font ID")
            result[key] = value
    return result


def card_content(card, where="card"):
    """Normalize one card, reporting problems against [where].

    [where] exists so the plan author gets an index (`knowledgeBases[0].decks[1].cards[3].front`)
    instead of a bare field name. A plan with twenty cards is unreadable without one, and the same
    function is reused by `validate`, where the index counts cards of the whole file.
    """
    front = text(card.get("front"), where + ".front")
    back = card.get("back", "")
    require(isinstance(back, str), where + ".back: expected text")
    kind = card.get("type")
    options = card.get("options")
    if kind is None:
        kind = ("MULTIPLE_CHOICE" if options is not None else "CLOZE" if CLOZE.search(front)
                else "REVERSED" if card.get("bidirectional") is True
                else "MARKDOWN" if re.search(r"```|(?m:^#{1,6} |^[-*] |^\|)", front + "\n" + back)
                else "BASIC")
    require(kind in TYPES, where + ".type: unsupported type " + str(kind))
    if options is not None:
        require(kind == "MULTIPLE_CHOICE", where + ".options require MULTIPLE_CHOICE")
        options = objects(options, where + ".options")
        lines = []
        for option in options:
            raw = text(option.get("text"), where + ".options[].text")
            value = raw.strip()
            require(LINE_BREAKS.search(raw) is None and not value.startswith("+"),
                    where + ".options[].text: single line without leading +")
            require(type(option.get("correct")) is bool, where + ".options[].correct: expected boolean")
            lines.append(("+" if option["correct"] else "") + value)
        back = "\n".join(lines)
    if kind == "CLOZE":
        require(CLOZE.search(front) is not None, where + ": CLOZE requires {{c1::answer}} in front")
        back = back or "; ".join(m.group(1).split("::")[0] for m in CLOZE.finditer(front))
    text(back, where + ".back")
    if kind == "MULTIPLE_CHOICE":
        back = back.replace("\r\n", "\n").replace("\r", "\n")
        choice_lines(back, where + ".back")
    return front, back, kind


def build(plan, sources=None):
    """Turn a content plan into the share/export JSON.

    [sources] collects local images named by `media[].sourcePath`, keyed by their derived media
    name. Pass a dict to enable embedding (the `package` action does); pass None to build the JSON
    alone, in which case a plan that names local files is rejected rather than silently dropped.
    """
    require(isinstance(plan, dict), "plan: expected object")
    now = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
    output = {"exportType": "share", "version": "1.0.0", "schemaVersion": 1,
              "exportDate": now, "knowledgeBases": [], "decks": [], "cards": []}

    def base(prefix):
        return {"id": prefix + "_" + str(uuid.uuid4()), "createdAt": now, "updatedAt": now, "version": 1}
    for kb_index, kb in enumerate(objects(plan.get("knowledgeBases"), "knowledgeBases")):
        kb_where = "knowledgeBases[%d]" % kb_index
        kb_out = dict(base("kb"), name=text(kb.get("name"), kb_where + ".name"), icon="📁")
        output["knowledgeBases"].append(kb_out)
        for deck_index, deck in enumerate(objects(kb.get("decks"), kb_where + ".decks")):
            deck_where = "%s.decks[%d]" % (kb_where, deck_index)
            deck_out = dict(base("deck"), name=text(deck.get("name"), deck_where + ".name"),
                            knowledgeBaseId=kb_out["id"], description=None, color="#4CAF50", icon="📚")
            output["decks"].append(deck_out)
            for card_index, card in enumerate(objects(deck.get("cards"), deck_where + ".cards")):
                card_where = "%s.cards[%d]" % (deck_where, card_index)
                front, back, kind = card_content(card, card_where)
                explanation = card.get("explanation", "")
                require(isinstance(explanation, str), card_where + ".explanation: expected text")
                tags = card.get("tags", [])
                require(isinstance(tags, list) and all(isinstance(x, str) for x in tags),
                        card_where + ".tags: expected string array")
                metadata = style(front, back, kind, card.get("style", {}), card_where + ".style")
                if "source" in card:
                    metadata["source"] = text(card["source"], card_where + ".source")
                media = media_items(card.get("media", []), card_where + ".media", sources, plan=True)
                if media:
                    metadata["mediaTarget"] = media_target(card.get("mediaTarget", "both"),
                                                           card_where + ".mediaTarget")
                # The app stores a card title and uses it in the card list / warehouse tree. A
                # plan that supplies one must carry it through; dropping it here was silent and
                # left the generated card untitled even though validate() accepts the field.
                title = card.get("title", "")
                require(isinstance(title, str) and len(title) <= 200,
                        card_where + ".title: expected text of at most 200 characters")
                output["cards"].append(dict(base("card"), deckId=deck_out["id"], type=kind,
                                            front=front, back=back, explanation=explanation,
                                            title=title, tags=tags, media=media, metadata=metadata))
    validate(output)
    return output


def validate(data):
    require(isinstance(data, dict), "export: expected object")
    require(data.get("exportType") == "share", "exportType must be share")
    require(data.get("version") == "1.0.0" and type(data.get("schemaVersion")) is int and data["schemaVersion"] == 1, "unsupported export/schema version")
    require(not (set(data) & {"settings", "reviewLogs", "learningPlans", "algorithmStates", "splashQuotes", "backupFormatVersion"}), "private backup fields forbidden")
    fields(data, {"exportType", "version", "schemaVersion", "exportDate", "deviceId", "knowledgeBases", "decks", "cards"}, "export")
    require(data.get("deviceId") is None or isinstance(data["deviceId"], str), "deviceId: expected text or null")
    timestamp(data.get("exportDate"), "exportDate")
    ids = set()
    for key in ("knowledgeBases", "decks", "cards"):
        for index, item in enumerate(objects(data.get(key), key)):
            where = "%s[%d]" % (key, index)
            ident = text(item.get("id"), where + ".id")
            require(ident not in ids, where + ": duplicate ID: " + ident)
            ids.add(ident)
            for field in ("createdAt", "updatedAt"):
                timestamp(item.get(field), where + "." + field)
            require(type(item.get("version", 1)) is int and 1 <= item.get("version", 1) <= LONG_MAX,
                    where + ".version: expected integer 1.." + str(LONG_MAX))
            require(all(item.get(k) is None for k in ("deletedAt", "syncedAt", "lastReviewedAt", "nextReviewAt")),
                    where + ": history/tombstones forbidden in new decks")
    kbs = {x["id"] for x in data["knowledgeBases"]}
    decks = {x["id"] for x in data["decks"]}
    for kb in data["knowledgeBases"]:
        fields(kb, RECORD_FIELDS | {"name", "icon", "description"}, "knowledgeBase")
        text(kb.get("name"), "knowledgeBase.name")
        if "icon" in kb:
            text(kb["icon"], "knowledgeBase.icon")
        require(kb.get("description") is None or isinstance(kb["description"], str), "knowledgeBase.description: expected text or null")
    for deck in data["decks"]:
        fields(deck, RECORD_FIELDS | {"name", "knowledgeBaseId", "description", "color", "icon", "parentId"}, "deck")
        text(deck.get("name"), "deck.name")
        require(deck.get("knowledgeBaseId") in kbs, "dangling knowledgeBaseId")
        require(deck.get("parentId") is None, "nested decks not supported by this skill")
        for field in ("color", "icon"):
            text(deck.get(field), "deck." + field)
        require("description" in deck and (deck["description"] is None or isinstance(deck["description"], str)), "deck.description required (text or null)")
    for index, card in enumerate(data["cards"]):
        where = "cards[%d]" % index
        fields(card, RECORD_FIELDS | {"deckId", "type", "front", "back", "explanation", "title", "tags", "media", "metadata", "lastReviewedAt", "nextReviewAt"}, where)
        require(card.get("deckId") in decks, "dangling deckId")
        require(card.get("type") in TYPES, "unsupported card type")
        # The app's CLOZE renderer derives both faces from `front` and never reads `back`; its own
        # demo CLOZE cards and ExportManager write `back=""`. Keep the key mandatory (omitting it
        # breaks the JSON model) but allow an empty value for CLOZE only.
        require("back" in card and isinstance(card["back"], str),
                where + ".back: expected text" if "back" in card else where + ".back: required")
        if card["type"] != "CLOZE":
            text(card.get("back"), where + ".back")
        require(isinstance(card.get("title", ""), str), where + ".title: expected text")
        require(isinstance(card.get("explanation", ""), str), where + ".explanation: expected text")
        card_content(card, where)
        if card["type"] == "MULTIPLE_CHOICE":
            choice_lines(card["back"], where + ".back")
        require(isinstance(card.get("tags"), list) and all(isinstance(x, str) for x in card["tags"]),
                where + ".tags: expected string array")
        media_items(card.get("media", []), where + ".media")
        meta = card.get("metadata")
        require(isinstance(meta, dict) and all(isinstance(v, str) for v in meta.values()), "metadata values must be strings")
        if "mediaTarget" in meta:
            media_target(meta["mediaTarget"], "metadata.mediaTarget")
        # Every key here is optional: the app reads `metadata["hcenter"].toBoolean()`, which yields
        # false when absent, and its own writer *removes* the key when a toggle is cleared
        # (CardViewModel). Demanding a value would reject shares the app itself produced.
        for key in ("hcenter", "vcenter"):
            require(meta.get(key, "false") in {"true", "false"}, "metadata." + key + ": expected true/false string")
        require(meta.get("fontFamily", "sans-serif") in FONTS, "unknown font ID")
        # ASCII only: the app uses toIntOrNull(), which does not parse full-width or Arabic digits.
        require(re.fullmatch(r"[0-9]+", meta.get("fontSize", "16")) is not None
                and 12 <= int(meta.get("fontSize", "16")) <= 120, "fontSize: expected string 12..120")
    return {key: len(data[key]) for key in ("knowledgeBases", "decks", "cards")}


def write_new(path, data):
    """Write the share JSON atomically, refusing to clobber a file the user already has.

    The bare `FileExistsError` from `open("x")` reads as `[Errno 17] File exists` with a path, and
    someone re-running a build over the same output has no idea whether that is expected. Naming
    the remedy is the whole difference between a wall and a step.

    Serialize before opening anything: a lone surrogate in a card field fails at UTF-8 encoding
    time. Writing straight to the destination left a truncated JSON file there, and the next run
    then refused its own half-written output. A sibling temp file plus a single rename makes a
    failed build leave no file at all, the same contract `package` already honours.
    """
    path = Path(path)
    require(path.suffix.lower() == ".json",
            "output: expected a .json path, got %s. A .lumecard name means the media-bearing "
            "archive, which `package` writes; `build` cannot embed images" % path.name)
    if path.exists():
        raise FileExistsError("output: %s already exists; delete it or choose another path" % path.name)
    require(path.parent.is_dir(),
            "output: parent directory does not exist: %s; create it and retry" % path.parent)
    encoded = (json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")
    partial = path.with_name("%s.%s.part" % (path.name, uuid.uuid4().hex[:8]))
    try:
        with partial.open("xb") as stream:
            stream.write(encoded)
        partial.replace(path)
    finally:
        try:
            partial.unlink()
        except OSError:
            pass


def write_archive(path, data, sources):
    """Write a `.lumecard` archive: `lumecard.json` first, then one entry per embedded image.

    The layout is the app's own (`LumecardArchive`), so the produced file imports through both the
    Settings full-import button. Scoped object-import is deliberately not supported: the app
    rejects files without its own `objectScope` marker there.
    Entries carry a fixed timestamp, so re-running a build over unchanged inputs yields the same
    entry metadata.

    The bytes are written to a sibling temporary file and moved into place only once the zip is
    complete. Writing directly to the destination would leave a truncated archive behind if an
    entry failed mid-write, and the next run would then refuse the path it had just corrupted.
    """
    path = Path(path)
    require(path.suffix.lower() == "." + ARCHIVE_EXTENSION,
            "output: expected a .%s path, got %s" % (ARCHIVE_EXTENSION, path.name))
    require(len(sources) <= MAX_MEDIA_ENTRIES,
            "output: %d images exceeds the %d the app will import; split the deck"
            % (len(sources), MAX_MEDIA_ENTRIES))
    total = sum(source.size for source in sources.values())
    require(total <= MAX_TOTAL_MEDIA_BYTES,
            "output: %.1f MB of images exceeds the %.0f MB the app will import; split the deck"
            % (total / (1024.0 * 1024.0), MAX_TOTAL_MEDIA_BYTES / (1024.0 * 1024.0)))
    payload = json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    encoded = payload.encode("utf-8")
    require(len(encoded) <= MAX_MANIFEST_BYTES,
            "output: manifest is %.1f MB, over the %.0f MB the app will read; split the deck"
            % (len(encoded) / (1024.0 * 1024.0), MAX_MANIFEST_BYTES / (1024.0 * 1024.0)))
    # `x` keeps the never-overwrite promise; checking up front makes it fail fast and keeps the
    # conventional FileExistsError. The temporary sibling means a failure mid-write cannot leave a
    # truncated archive at the destination. The name carries a random suffix because a fixed
    # `<output>.part` makes two builds of different decks into the same directory collide on the
    # file both are writing; with one, a stale temp from a crashed run would also block the next.
    if path.exists():
        raise FileExistsError("output: refusing to overwrite existing " + path.name)
    # Without this, a missing parent leaks a random `.part` name and a bare errno instead of
    # telling the author what to fix — the same mistake the CLI diagnostics test guards against.
    require(path.parent.is_dir(),
            "output: parent directory does not exist: %s; create it and retry" % path.parent)
    partial = path.with_name("%s.%s.part" % (path.name, uuid.uuid4().hex[:8]))
    try:
        with partial.open("xb") as stream:
            with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                manifest = zipfile.ZipInfo(ARCHIVE_MANIFEST, date_time=ARCHIVE_ENTRY_TIME)
                manifest.compress_type = zipfile.ZIP_DEFLATED
                manifest.external_attr = 0o644 << 16
                archive.writestr(manifest, encoded)
                for name in sorted(sources):
                    entry = zipfile.ZipInfo(ARCHIVE_MEDIA_DIR + name, date_time=ARCHIVE_ENTRY_TIME)
                    entry.compress_type = zipfile.ZIP_DEFLATED
                    entry.external_attr = 0o644 << 16
                    archive.writestr(entry, sources[name].data)
        # Only reveal the result once the zip is complete.
        partial.replace(path)
    finally:
        # Best effort: if cleanup itself fails (a reader still holding the file on Windows), the
        # original exception is what the caller needs to see, not a secondary OSError about it.
        try:
            partial.unlink()
        except OSError:
            pass
    return path


def inspect_archive(path):
    """Validate a produced archive the way the app's importer will read it.

    Media is inspected by header and measured as it streams rather than fully buffered: a
    legitimate archive may hold hundreds of megabytes of images, and a checker that held them all
    would be the most memory-hungry thing in the pipeline. The manifest is the one entry read
    whole, so it is capped first — an over-long manifest is a hard import failure in the app, and
    reading it unbounded would be the one place a hostile archive could exhaust memory here.
    """
    path = Path(path)
    require(not path.is_dir(), "archive: %s is a directory, not a .lumecard file" % path)
    require(path.is_file(), "archive: no such file: " + str(path))
    try:
        archive_handle = zipfile.ZipFile(path)
    except zipfile.BadZipFile:
        # A share JSON handed to `inspect` is the likely mistake, and "not a zip" alone does not
        # point at the right command. Look at the first byte instead of guessing.
        try:
            head = path.open("rb").read(64)
        except OSError:
            head = b""
        if head.lstrip()[:1] in (b"{", b"["):
            raise ValueError("archive: %s is JSON, not a .lumecard archive; use `validate` on it"
                             % path.name)
        raise ValueError("archive: %s is not a zip archive (a .lumecard is a zip containing "
                         "lumecard.json)" % path.name)
    with archive_handle as archive:
        names = archive.namelist()
        require(ARCHIVE_MANIFEST in names, "archive: missing " + ARCHIVE_MANIFEST)
        # The app looks the manifest up by name, not by position, but both its writer and this
        # skill always put it first. Keeping the stricter check catches malformed archives early
        # without rejecting anything either tool can produce.
        require(names[0] == ARCHIVE_MANIFEST, "archive: " + ARCHIVE_MANIFEST + " must be the first entry")
        require(len(names) == len(set(names)), "archive: duplicate entry names")
        # The app's listZipArchive refuses the whole archive once it sees this many non-directory
        # entries (MAX_ARCHIVE_LIST_ENTRIES). Extra Finder/AV entries are harmless individually,
        # but enough of them turn into READ_FAILED and no card is imported at all.
        listed = sum(1 for name in names if not name.endswith("/"))
        require(listed <= MAX_ARCHIVE_LIST_ENTRIES,
                "archive: %d entries exceeds the %d the app will list" % (listed, MAX_ARCHIVE_LIST_ENTRIES))
        manifest_size = archive.getinfo(ARCHIVE_MANIFEST).file_size
        require(0 < manifest_size <= MAX_MANIFEST_BYTES,
                "archive: manifest is %d bytes, over the %d the app will read" % (manifest_size, MAX_MANIFEST_BYTES))
        media = {}
        total = 0
        for name in names[1:]:
            info = archive.getinfo(name)
            # Directory entries are ordinary zip furniture (`zip -r` emits one per folder) and carry
            # no bytes. The app skips them too, so rejecting them would refuse harmless archives.
            if info.is_dir():
                continue
            relative = archive_media_path(name)
            # The app tolerates extra files that no card references (resource forks, Finder
            # metadata). Ignoring them keeps inspect aligned with what import actually does,
            # instead of failing an archive the app would accept.
            if relative is None:
                continue
            # Count only real media: directory entries and stray files are not work the app has
            # to do, so charging them against the limit would refuse ordinary `zip -r` output.
            # This check runs *before* the entry is added, so `<` permits exactly MAX_MEDIA_ENTRIES
            # entries — the same count the importer's `take(MAX_ARCHIVE_ENTRIES)` accepts.
            require(len(media) < MAX_MEDIA_ENTRIES,
                    "archive: more than %d media entries, which is what the app will import"
                    % MAX_MEDIA_ENTRIES)
            # The declared `file_size` comes from the archive's own central directory and is
            # attacker-controlled. It cannot be checked by reading: Python's ZipExtFile stops at
            # the declared length, so a 60 KB entry claiming 100 bytes yields exactly 100 bytes
            # and a matching CRC. `compress_size`, by contrast, is pinned by the real byte layout
            # of the archive. Deflate output is never meaningfully larger than its input, so a
            # compressed size far above the declared size means the size was forged to hide a
            # bomb. Check that relationship first, then count what actually streams out.
            require(info.compress_size <= info.file_size + info.file_size // 50 + 1024,
                    "archive: media entry declares %d bytes but carries %d compressed (impossible "
                    "for deflate, so the size was forged): %s"
                    % (info.file_size, info.compress_size, name))
            actual = 0
            head = b""
            tail = b""
            carry = b""
            has_idat = False
            has_sos = False
            # Keep only bounded state while streaming: a 64 MiB image must not be buffered just
            # to check that its IEND/EOI terminator and pixel chunk are actually present. `carry`
            # keeps the last few bytes so a marker straddling two reads is still seen.
            tail_keep = max(len(PNG_IEND), len(JPEG_EOI), len(GIF_TRAILER)) + 1
            with archive.open(name) as stream:
                while True:
                    chunk = stream.read(64 * 1024)
                    if not chunk:
                        break
                    actual += len(chunk)
                    require(actual <= MAX_MEDIA_BYTES,
                            "archive: media entry expands past %d bytes (declared %d): %s"
                            % (MAX_MEDIA_BYTES, info.file_size, name))
                    if len(head) < 16:
                        head += chunk[:16 - len(head)]
                    tail = (tail + chunk)[-tail_keep:]
                    scanned = carry + chunk
                    has_idat = has_idat or b"IDAT" in scanned
                    has_sos = has_sos or JPEG_SOS in scanned
                    carry = scanned[-3:]
            require(actual > 0, "archive: media entry is empty: " + name)
            total += actual
            require(total <= MAX_TOTAL_MEDIA_BYTES,
                    "archive: media expands past the %d bytes the app will import" % MAX_TOTAL_MEDIA_BYTES)
            sniffed = sniff_image(head)
            complete = {
                "png": tail.endswith(PNG_IEND) and has_idat,
                "jpg": tail.endswith(JPEG_EOI) and has_sos,
                "gif": tail.endswith(GIF_TRAILER) and actual >= 35,
                "webp": actual >= 20 and int.from_bytes(head[4:8], "little") == actual - 8,
                "bmp": actual >= 26 and int.from_bytes(head[2:6], "little") == actual,
            }
            require(sniffed is not None and complete.get(sniffed[0], False),
                    "archive: media entry is not a decodable image: " + name)
            media[relative] = actual
        with archive.open(ARCHIVE_MANIFEST) as stream:
            raw = stream.read(MAX_MANIFEST_BYTES + 1)
        require(len(raw) <= MAX_MANIFEST_BYTES, "archive: manifest grew while reading")
    try:
        decoded = raw.decode("utf-8-sig")
    except UnicodeDecodeError as error:
        raise ValueError("archive: " + ARCHIVE_MANIFEST + " is not UTF-8 (byte 0x%02X at offset %d)"
                         % (raw[error.start] if error.start < len(raw) else 0, error.start))
    manifest = strict_json_loads(decoded, "archive: " + ARCHIVE_MANIFEST)
    counts = validate(manifest)
    references = []
    remote = 0
    for card in manifest["cards"]:
        for item in card.get("media", []):
            path_value = item["filePath"]
            if path_value.lower().startswith("https://"):
                remote += 1
            else:
                references.append(path_value)
    # Mirror how ExportArchiveService resolves one card reference to a file on disk:
    #   referencedByExact[name] ?: referencedByFolded[lower(name)]?.singleOrNull() ?: name
    # Decisions are made per case-folded group, because that is the unit the app collapses:
    #   * one spelling, one entry            -> the bytes land under the spelling (safe)
    #   * N spellings, N exact entries       -> each card keeps its own file (app test covers this)
    #   * N spellings, one entry             -> N-1 cards point at a file never written (bad)
    #   * one spelling, N entries            -> app processes both in namelist order and renames the
    #                                           loser, so the image depends on zip order (bad)
    folded = {}
    for name in media:
        folded.setdefault(name.lower(), []).append(name)
    by_fold = {}
    for path_value in references:
        by_fold.setdefault(path_value.lower(), set()).add(path_value)
    resolved = set()
    for key, spellings in by_fold.items():
        entries = folded.get(key, [])
        if not entries:
            raise ValueError("archive: card references a missing media entry: " + sorted(spellings)[0])
        targets = {}
        for path_value in sorted(spellings):
            if path_value in entries:
                targets[path_value] = path_value
            elif len(entries) == 1:
                targets[path_value] = entries[0]
            else:
                raise ValueError(
                    "archive: card references %s, which differ only by case, and the archive holds "
                    "%s; the app cannot decide which entry belongs to which card" %
                    (", ".join(sorted(spellings)), ", ".join(sorted(entries))))
        if len(entries) > len(spellings):
            raise ValueError(
                "archive: entries %s all fold to the same reference as %s; the app would write them "
                "in archive order and the image would depend on zip entry order" %
                (", ".join(sorted(entries)), ", ".join(sorted(spellings))))
        if len(set(targets.values())) < len(spellings):
            raise ValueError(
                "archive: card references %s but the archive has only %s for them; %d card(s) would "
                "show a permanently missing image" %
                (", ".join(sorted(spellings)), ", ".join(sorted(entries)),
                 len(spellings) - len(set(targets.values()))))
        resolved.update(targets.values())
    # The app deliberately ignores `media/` entries no card references: its importer filters to
    # referencedPaths before extracting, and ExportArchiveServiceTest asserts such an entry is
    # neither written nor counted as missing. Refusing one here would reject an archive the app
    # imports perfectly (e.g. one re-used from another deck). Only a reference without an entry is
    # broken; an entry without a reference is inert.
    return dict(counts, media=len(media), remote=remote)


def archive_media_path(entry_name):
    """Media-root-relative path for an archive entry, or None when it is unsafe or unrelated."""
    if not entry_name.startswith(ARCHIVE_MEDIA_DIR):
        return None
    relative = entry_name[len(ARCHIVE_MEDIA_DIR):]
    # The app peels the same prefix and then applies MediaPathRules to what is left, so reuse the
    # identical rule here: a name this function accepts must be one `mediaPathFromEntry` accepts.
    return relative if safe_relative_path(relative) else None


def expand(user_path):
    """Apply shell conventions to a CLI path: `~` and environment variables.

    argparse hands over the string exactly as typed, and this tool is run from agent runbooks that
    write `~/decks/a.lumecard`. Without expansion that means a literal `~` directory under the
    working directory, which is a different file than the one intended.
    """
    return Path(os.path.expandvars(os.path.expanduser(user_path)))


def main():
    if sys.version_info < (3, 9):
        print("error: Python 3.9+ required; author JSON manually using references/format.md", file=sys.stderr)
        return 2
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    create = sub.add_parser("build", help="plan -> plain share JSON (no media files)")
    create.add_argument("plan")
    create.add_argument("output")
    archive = sub.add_parser("package", help="plan with local images -> .lumecard archive")
    archive.add_argument("plan")
    archive.add_argument("output")
    check = sub.add_parser("validate", help="re-check a share JSON file")
    check.add_argument("input")
    verify = sub.add_parser("inspect", help="re-check a .lumecard archive")
    verify.add_argument("input")
    args = parser.parse_args()
    try:
        if args.action == "build":
            data = build(load(expand(args.plan)))
            write_new(expand(args.output), data)
            print(json.dumps(validate(data), ensure_ascii=False))
        elif args.action == "package":
            output = expand(args.output)
            sources = {}
            data = build(load(expand(args.plan)), sources)
            write_archive(output, data, sources)
            # Verify what was just written. Without this, a plan that names local files through
            # `filePath` instead of `sourcePath` produces an archive whose manifest promises
            # images that are not inside it: the app imports the cards and leaves every picture
            # broken, with no error anywhere. Checking our own output turns that into a build
            # failure the author can still fix.
            try:
                counts = inspect_archive(output)
            except INPUT_ERRORS as error:
                # Do not leave a broken archive behind for the user to import by mistake.
                try:
                    output.unlink()
                except OSError:
                    pass
                raise ValueError(
                    "output: the archive failed its own check and was removed — " + str(error)
                    + " (name every local image with media[].sourcePath so it gets embedded)")
            print(json.dumps(counts, ensure_ascii=False))
        elif args.action == "inspect":
            print(json.dumps(inspect_archive(expand(args.input)), ensure_ascii=False))
        else:
            print(json.dumps(validate(load(expand(args.input))), ensure_ascii=False))
        return 0
    except INPUT_ERRORS as error:
        # A zip can fail in ways that are not BadZipFile: encrypted entries raise RuntimeError,
        # unknown compression methods raise NotImplementedError, and a corrupted deflate stream
        # raises zlib.error. `inspect` exists to give a verdict on untrusted input, so every
        # parse failure has to come back as a message rather than a traceback.
        print("error: " + (str(error) or type(error).__name__), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
