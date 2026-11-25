import copy
import importlib.util
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import time

import unittest
import unittest.mock
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))


def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / (name + ".py"))
    obj = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(obj)
    return obj


def make_png(path, width, height, rgb):
    """Write a real, decodable RGB PNG without any third-party dependency."""
    def chunk(tag, payload):
        body = tag + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + bytes(rgb) * width for _ in range(height))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    Path(path).write_bytes(
        b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    )


builder = module("build_deck")
installer = module("install")


class WorkflowTests(unittest.TestCase):
    def setUp(self):
        self.plan = builder.load(ROOT / "examples/plan.json")
        self.data = builder.build(self.plan)

    def test_end_to_end(self):
        self.assertEqual(builder.validate(self.data), {"knowledgeBases": 1, "decks": 1, "cards": 4})
        self.assertEqual([c["type"] for c in self.data["cards"]], ["BASIC", "CLOZE", "MULTIPLE_CHOICE", "MARKDOWN"])
        self.assertTrue(self.data["cards"][2]["back"].startswith("+"))
        self.assertNotIn("reviewLogs", self.data)

    def test_multilevel_references_and_uuid(self):
        self.plan["knowledgeBases"].append(copy.deepcopy(self.plan["knowledgeBases"][0]))
        data = builder.build(self.plan)
        self.assertEqual(builder.validate(data)["cards"], 8)
        self.assertNotEqual(data["cards"][0]["id"], self.data["cards"][0]["id"])

    def test_explicit_style_and_source(self):
        card = self.plan["knowledgeBases"][0]["decks"][0]["cards"][0]
        card["style"] = {"fontSize": 32, "hcenter": False, "fontFamily": "serif"}
        meta = builder.build(self.plan)["cards"][0]["metadata"]
        self.assertEqual(meta["fontSize"], "32")
        self.assertEqual(meta["hcenter"], "false")
        self.assertEqual(meta["fontFamily"], "serif")
        self.assertIn("source", meta)

    def test_reversed_and_long_plain_text(self):
        card = {"front": "hello", "back": "你好", "bidirectional": True}
        self.assertEqual(builder.card_content(card)[2], "REVERSED")
        self.assertEqual(builder.style("a" * 101, "b", "BASIC", {})["hcenter"], "false")

    def test_invalid_plans(self):
        for value in (None, {}, {"knowledgeBases": []}, {"knowledgeBases": ["bad"]}):
            with self.subTest(value=value), self.assertRaises(ValueError):
                builder.build(value)

    def test_invalid_cards(self):
        cases = [{"front": "", "back": "a"}, {"front": "q"},
                 {"front": "q", "back": "a", "type": "RICH_TEXT"},
                 {"front": "q", "back": "a", "type": "CLOZE"},
                 {"front": "q", "back": "a\nb", "type": "MULTIPLE_CHOICE"},
                 {"front": "q", "options": [{"text": "a\nb", "correct": True}]},
                 {"front": "q", "back": "+a\na", "type": "MULTIPLE_CHOICE"}]
        for card in cases:
            with self.subTest(card=card), self.assertRaises(ValueError):
                builder.card_content(card)

    def test_dangling_and_duplicate_ids(self):
        self.data["cards"][0]["deckId"] = "missing"
        with self.assertRaisesRegex(ValueError, "dangling"):
            builder.validate(self.data)
        self.data = builder.build(self.plan)
        self.data["cards"][1]["id"] = self.data["cards"][0]["id"]
        with self.assertRaisesRegex(ValueError, "duplicate ID"):
            builder.validate(self.data)

    def test_metadata_history_and_dates(self):
        for key, value in (("metadata", {"fontSize": 16}), ("lastReviewedAt", "2026-01-01T00:00:00Z"), ("createdAt", "not a date")):
            data = copy.deepcopy(self.data)
            data["cards"][0][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                builder.validate(data)

    def test_style_errors(self):
        for style in ({"fontSize": True}, {"fontSize": 121}, {"fontFamily": "download-me"}, {"hcenter": "true"}, {"unknown": 1}):
            with self.subTest(style=style), self.assertRaises(ValueError):
                builder.style("q", "a", "BASIC", style)

    def test_export_requires_actual_back_even_for_cloze(self):
        del self.data["cards"][1]["back"]
        # The index is part of the message: a plan with many cards has to say which one failed.
        with self.assertRaisesRegex(ValueError, r"cards\[1\]\.back"):
            builder.validate(self.data)

    def test_typed_optional_fields(self):
        self.data["cards"][0]["title"] = 123
        with self.assertRaisesRegex(ValueError, "title"):
            builder.validate(self.data)

    def test_plan_title_is_carried_into_export(self):
        # The app stores Card.title and shows it in the list / warehouse tree. A plan that
        # supplied a title had it silently dropped by build(), so the generated card was untitled.
        plan = copy.deepcopy(self.plan)
        plan["knowledgeBases"][0]["decks"][0]["cards"][0]["title"] = "我的卡片标题"
        data = builder.build(plan)
        self.assertEqual(data["cards"][0]["title"], "我的卡片标题")
        builder.validate(data)

    def test_cloze_back_may_be_empty_but_must_exist(self):
        # The CLOZE renderer derives both faces from front; the app's own demo cards and exports
        # write back="". Omitting the key entirely still breaks the export model.
        data = copy.deepcopy(self.data)
        cloze = next(card for card in data["cards"] if card["type"] == "CLOZE")
        cloze["back"] = ""
        builder.validate(data)
        del cloze["back"]
        with self.assertRaisesRegex(ValueError, "back"):
            builder.validate(data)

    def test_plan_with_images_builds_and_validates(self):
        data = builder.build(self.plan)
        card = data["cards"][0]
        # The shipped example uses a remote image, because a local one would need a file next to
        # the plan and `build` cannot embed bytes anyway.
        self.assertEqual(card["media"][0]["filePath"], "https://example.com/jvm-architecture.png")
        self.assertEqual(card["metadata"]["mediaTarget"], "front")
        builder.validate(data)

    def test_media_accepts_https_and_local_paths(self):
        data = copy.deepcopy(self.data)
        data["cards"][0]["media"] = [
            {"id": "img_local", "type": "IMAGE", "filePath": "diagram.png"},
            {"id": "img_remote", "type": "IMAGE", "filePath": "https://cdn.example.com/a.png",
             "caption": "Remote diagram", "mimeType": "image/png", "size": 1024},
        ]
        data["cards"][0]["metadata"]["mediaTarget"] = "both"
        builder.validate(data)

    def test_media_filename_with_http_prefix_is_allowed(self):
        plan = copy.deepcopy(self.plan)
        plan["knowledgeBases"][0]["decks"][0]["cards"][0]["media"] = [
            {"id": "img_http", "type": "IMAGE", "filePath": "httpnotes.png"}
        ]
        data = builder.build(plan)
        self.assertEqual(data["cards"][0]["media"][0]["filePath"], "httpnotes.png")

    def test_media_alignment_and_compress_are_normalised(self):
        data = copy.deepcopy(self.data)
        data["cards"][0]["media"] = [
            {"id": "img_left", "type": "IMAGE", "filePath": "a.png",
             "alignment": "start", "compress": False},
            {"id": "img_default", "type": "IMAGE", "filePath": "b.png"},
        ]
        builder.validate(data)
        plan = copy.deepcopy(self.plan)
        plan["knowledgeBases"][0]["decks"][0]["cards"][0]["media"] = [
            {"id": "img_left", "type": "IMAGE", "filePath": "a.png",
             "alignment": "end", "compress": False},
        ]
        built = builder.build(plan)
        media = built["cards"][0]["media"][0]
        self.assertEqual(media["alignment"], "end")
        self.assertIs(media["compress"], False)
        self.assertEqual(media["caption"], "")

    def test_media_alignment_rejects_unknown_value(self):
        data = copy.deepcopy(self.data)
        data["cards"][0]["media"] = [
            {"id": "img_bad", "type": "IMAGE", "filePath": "a.png", "alignment": "middle"}
        ]
        with self.assertRaises(ValueError):
            builder.validate(data)

    def test_media_compress_rejects_non_boolean(self):
        data = copy.deepcopy(self.data)
        data["cards"][0]["media"] = [
            {"id": "img_bad", "type": "IMAGE", "filePath": "a.png", "compress": "yes"}
        ]
        with self.assertRaises(ValueError):
            builder.validate(data)

    def test_media_filename_with_double_dot_is_allowed(self):
        plan = copy.deepcopy(self.plan)
        plan["knowledgeBases"][0]["decks"][0]["cards"][0]["media"] = [
            {"id": "img_dots", "type": "IMAGE", "filePath": "a..b.png"}
        ]
        data = builder.build(plan)
        self.assertEqual(data["cards"][0]["media"][0]["filePath"], "a..b.png")
        builder.validate(data)

    def test_media_rejects_insecure_and_unsafe_sources(self):
        bad = [
            {"id": "i", "type": "IMAGE", "filePath": "http://example.com/a.png"},
            {"id": "i", "type": "IMAGE", "filePath": "  http://example.com/a.png"},
            {"id": "i", "type": "IMAGE", "filePath": "file:///etc/passwd"},
            {"id": "i", "type": "IMAGE", "filePath": "../secret.png"},
            {"id": "i", "type": "IMAGE", "filePath": "\\\\server\\share\\a.png"},
            {"id": "i", "type": "AUDIO", "filePath": "a.mp3"},
            {"id": "", "type": "IMAGE", "filePath": "a.png"},
            {"id": "i", "type": "IMAGE", "filePath": "a.png", "unknown": 1},
            {"id": "i", "type": "IMAGE", "filePath": "a.png", "size": -1},
        ]
        for item in bad:
            data = copy.deepcopy(self.data)
            data["cards"][0]["media"] = [item]
            with self.subTest(item=item), self.assertRaises(ValueError):
                builder.validate(data)

    def test_https_urls_may_contain_dot_dot_segments(self):
        # `..` in a URL path is resolved by the server; only a media-root relative name can
        # traverse this app's media directory. Rejecting the URL used to be a false positive.
        for path in ("https://example.com/a/../b.png", "https://cdn.example.com/../../x.png"):
            self.assertEqual(
                builder.media_items([{"id": "i", "type": "IMAGE", "filePath": path}],
                                    "card.media")[0]["filePath"], path)
        # A relative path with `..` is still refused.
        with self.assertRaisesRegex(ValueError, "path traversal"):
            builder.media_items([{"id": "i", "type": "IMAGE", "filePath": "../a.png"}], "card.media")

    def test_malformed_https_urls_are_rejected(self):
        # Python's `\\S+` used to accept things the app cannot fetch; `URL_HTTPS` is stricter.
        for path in ("https://", "https://a b", "https://x\ty"):
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, "malformed https URL"):
                builder.media_items([{"id": "i", "type": "IMAGE", "filePath": path}], "card.media")

    def test_media_relative_names_mirror_app_path_rules(self):
        # Mirrors shared MediaPathRules: a weaker builder rule would emit a name that imports
        # today but makes the imported deck fail validate() on export or drops the image on
        # archive packaging.
        bad = [
            "CON.png", "COM¹.png", "a.png.", "dir /a.png", "a.png:hidden",
            "a\\b.png", "a//b.png", "a/./b.png", "a\u0001.png",
            "x" * 129 + ".png", "x" * 256 + ".png",
            # The total-length rule is separate from the per-segment rule, so it needs a name
            # whose segments are each legal but whose sum is not. 128 + 1 + 128 = 257.
            "x" * 128 + "/" + "x" * 128,
        ]
        for path in bad:
            data = copy.deepcopy(self.data)
            data["cards"][0]["media"] = [{"id": "i", "type": "IMAGE", "filePath": path}]
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, "filePath"):
                builder.validate(data)
        # Exactly 255 UTF-16 units is still acceptable, so the boundary itself is not off by one.
        edge = "x" * 127 + "/" + "x" * 127
        self.assertEqual(len(edge), 255)
        data = copy.deepcopy(self.data)
        data["cards"][0]["media"] = [{"id": "i", "type": "IMAGE", "filePath": edge}]
        builder.validate(data)
        for path in ("images/a1b2c3.png", "a..b.png", "https://cdn.example.com/a.png"):
            data = copy.deepcopy(self.data)
            data["cards"][0]["media"] = [{"id": "i", "type": "IMAGE", "filePath": path}]
            builder.validate(data)

    def test_all_whitespace_names_are_refused_whatever_the_space_character(self):
        # `putStrLn` of the divergence: Kotlin's `isBlank` follows `Character.isWhitespace`, which
        # reports false for U+00A0 (no-break space) and U+0085 (NEL), while Python's `str.strip`
        # treats both as space. This builder is therefore *stricter* than the app for these two
        # characters — the safe direction, since it can only refuse a name, never emit one the app
        # would rewrite. The other characters here are blank to both sides.
        for blank in (" ", "\t", "\u3000", "\u2028", "\u00a0", "\u0085", "  \t\n "):
            for value in (blank, "media/" + blank):
                data = copy.deepcopy(self.data)
                data["cards"][0]["media"] = [{"id": "i", "type": "IMAGE", "filePath": value}]
                with self.subTest(value=value), self.assertRaises(ValueError):
                    builder.validate(data)

    def test_archive_inspect_ignores_directory_entries_with_and_without_the_slash(self):
        # `zip -r` marks a folder by giving the entry a trailing slash, but some tools write the
        # folder name bare. Both must be treated as furniture rather than as a media file that
        # cannot be decoded.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (3, 3, 3))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
            name = next(iter(sources))
            for label, folder in (("slash", builder.ARCHIVE_MEDIA_DIR),
                                  ("bare", builder.ARCHIVE_MEDIA_DIR.rstrip("/")),
                                  ("nested", builder.ARCHIVE_MEDIA_DIR + "pics/")):
                archive_path = root / (label + ".lumecard")
                with zipfile.ZipFile(archive_path, "w") as archive:
                    archive.writestr(builder.ARCHIVE_MANIFEST, json.dumps(data).encode("utf-8"))
                    archive.writestr(folder, b"")
                    archive.writestr(builder.ARCHIVE_MEDIA_DIR + name, (root / "a.png").read_bytes())
                with self.subTest(folder=folder):
                    self.assertEqual(builder.inspect_archive(archive_path)["media"], 1)

    def test_manifest_budget_is_enforced_on_write_and_on_read(self):
        # Both sides matter: the writer must not produce a manifest the app refuses to read, and
        # inspect must not accept a foreign archive that carries one. Neither path had coverage,
        # so a change to the constant on one side only would have gone unnoticed.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (4, 4, 4))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
            name = next(iter(sources))
            real_manifest = json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8")
            limit = len(real_manifest) - 1

            with unittest.mock.patch.object(builder, "MAX_MANIFEST_BYTES", limit):
                with self.assertRaisesRegex(ValueError, "manifest is .* over the"):
                    builder.write_archive(root / "write.lumecard", data, sources)
                self.assertFalse((root / "write.lumecard").exists())

                # A foreign archive is not built by us, so the reader has its own guard.
                foreign = root / "read.lumecard"
                with zipfile.ZipFile(foreign, "w") as archive:
                    archive.writestr(builder.ARCHIVE_MANIFEST, real_manifest)
                    archive.writestr(builder.ARCHIVE_MEDIA_DIR + name, (root / "a.png").read_bytes())
                with self.assertRaisesRegex(ValueError, "over the .* the app will read"):
                    builder.inspect_archive(foreign)

            # The same content fits once the real limit is back in force.
            builder.write_archive(root / "ok.lumecard", data, sources)
            self.assertEqual(builder.inspect_archive(root / "ok.lumecard")["media"], 1)


    def test_media_target_validation(self):
        for value in ("both", "front", "back"):
            data = copy.deepcopy(self.data)
            data["cards"][0]["metadata"]["mediaTarget"] = value
            builder.validate(data)
        data = copy.deepcopy(self.data)
        data["cards"][0]["metadata"]["mediaTarget"] = "sideways"
        with self.assertRaisesRegex(ValueError, "mediaTarget"):
            builder.validate(data)

    def test_legacy_empty_media_still_valid(self):
        data = copy.deepcopy(self.data)
        data["cards"][0]["media"] = []
        data["cards"][0]["metadata"].pop("mediaTarget", None)
        builder.validate(data)

    def plan_with_source(self, source):
        return {"knowledgeBases": [{"name": "B", "decks": [{"name": "D", "cards": [
            {"front": "Q", "back": "A",
             "media": [{"id": "m", "type": "IMAGE", "sourcePath": source}]},
        ]}]}]}

    def test_package_embeds_images_as_content_addressed_archive(self):
        # The produced file must match the app's LumecardArchive layout: the importer extracts
        # `media/<filePath>` and only rewrites card references on a name collision, so agreement
        # between the archive entry and `filePath` is what makes the image appear.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "mito.png", 4, 4, (200, 60, 60))
            plan = {
                "knowledgeBases": [{"name": "Biology", "decks": [{"name": "Cells", "cards": [
                    {"front": "Which organelle makes ATP?", "back": "Mitochondrion.",
                     "media": [{"id": "m1", "type": "IMAGE", "sourcePath": str(root / "mito.png"),
                                "caption": "Mitochondrion"}], "mediaTarget": "front"},
                    {"front": "Same file twice", "back": "Deduped", "media": [
                        {"id": "m3", "type": "IMAGE", "sourcePath": str(root / "mito.png")},
                        {"id": "m4", "type": "IMAGE", "sourcePath": str(root / "mito.png")}]},
                    {"front": "Plain", "back": "No media."},
                ]}]}],
            }
            sources = {}
            data = builder.build(plan, sources)
            output = root / "deck.lumecard"
            builder.write_archive(output, data, sources)

            # Three references to one file collapse onto a single entry.
            self.assertEqual(len(sources), 1)
            with zipfile.ZipFile(output) as archive:
                names = archive.namelist()
                self.assertEqual(names[0], builder.ARCHIVE_MANIFEST)
                self.assertEqual(len(names), 2)
                self.assertTrue(names[1].startswith(builder.ARCHIVE_MEDIA_DIR))
                stem = names[1].split("/", 1)[1].rsplit(".", 1)[0]
                self.assertRegex(stem, r"^[0-9a-f]{40}$", "media names are content SHA-1, like the app")
                self.assertEqual(json.loads(archive.read(builder.ARCHIVE_MANIFEST)), data)
                self.assertEqual(archive.infolist()[0].date_time, builder.ARCHIVE_ENTRY_TIME)

            for card in data["cards"]:
                for item in card["media"]:
                    self.assertIn(builder.ARCHIVE_MEDIA_DIR + item["filePath"], names)

            mito = data["cards"][0]["media"][0]
            self.assertEqual(mito["mimeType"], "image/png")
            self.assertEqual(mito["size"], (root / "mito.png").stat().st_size)
            self.assertEqual(data["cards"][0]["metadata"]["mediaTarget"], "front")
            self.assertEqual(builder.inspect_archive(output)["media"], 1)

    def test_package_rejects_unrenderable_or_unsafe_sources(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            # SVG decodes on neither back end (Skia Codec, BitmapFactory), so embedding it would
            # produce a permanently broken card image rather than a fast failure.
            (root / "vector.svg").write_text("<svg xmlns='http://www.w3.org/2000/svg'/>", encoding="utf-8")
            (root / "fake.png").write_bytes(b"not really a png")
            make_png(root / "ok.png", 2, 2, (1, 2, 3))
            bad = {
                "missing": str(root / "absent.png"),
                "svg": str(root / "vector.svg"),
                "misnamed": str(root / "fake.png"),
                "directory": str(root),
            }
            for label, source in bad.items():
                with self.subTest(label=label), self.assertRaisesRegex(ValueError, "sourcePath"):
                    builder.build(self.plan_with_source(source), {})
            # filePath and sourcePath together are ambiguous, not "filePath wins".
            plan = self.plan_with_source(str(root / "ok.png"))
            plan["knowledgeBases"][0]["decks"][0]["cards"][0]["media"][0]["filePath"] = "a.png"
            with self.assertRaisesRegex(ValueError, "filePath"):
                builder.build(plan, {})
            # An empty file is not an image.
            (root / "empty.png").write_bytes(b"")
            with self.assertRaisesRegex(ValueError, "sourcePath"):
                builder.build(self.plan_with_source(str(root / "empty.png")), {})

    def test_build_without_sources_cannot_silently_drop_local_images(self):
        # `build` writes JSON only, so the images would never reach the user; refuse instead.
        with self.assertRaisesRegex(ValueError, "package"):
            builder.build(self.plan_with_source("whatever.png"))
        # A built export must not carry `sourcePath` at all: the app would resolve it as a media
        # name, so the message has to point at the field rather than suggest another subcommand.
        data = copy.deepcopy(self.data)
        data["cards"][0]["media"] = [{"id": "i", "type": "IMAGE", "sourcePath": "x.png"}]
        with self.assertRaisesRegex(ValueError, "not allowed in an export"):
            builder.validate(data)

    def test_archive_writer_never_overwrites_and_checks_extension(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (9, 9, 9))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
            with self.assertRaisesRegex(ValueError, "lumecard"):
                builder.write_archive(root / "deck.zip", data, sources)
            target = root / "deck.lumecard"
            target.write_bytes(b"keep me")
            with self.assertRaises(FileExistsError):
                builder.write_archive(target, data, sources)
            self.assertEqual(target.read_bytes(), b"keep me")

    def test_archive_inspect_catches_dangling_and_misordered_entries(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (4, 4, 4))
            # A manifest that references a file the archive does not carry is exactly the
            # "imports fine, image missing" failure this check exists to prevent.
            declared = builder.build(self.plan_with_source(str(root / "a.png")), {})
            broken = root / "broken.lumecard"
            builder.write_archive(broken, declared, {})
            with self.assertRaisesRegex(ValueError, "missing media entry"):
                builder.inspect_archive(broken)

            sources = {}
            good = root / "good.lumecard"
            builder.write_archive(good, builder.build(self.plan_with_source(str(root / "a.png")), sources), sources)
            self.assertEqual(builder.inspect_archive(good)["media"], 1)

            # The app reads entry zero as the manifest; a reordered archive is a real hazard.
            reordered = root / "reordered.lumecard"
            with zipfile.ZipFile(good) as src, zipfile.ZipFile(reordered, "w") as dst:
                for name in src.namelist()[1:] + src.namelist()[:1]:
                    dst.writestr(name, src.read(name))
            with self.assertRaisesRegex(ValueError, "first entry"):
                builder.inspect_archive(reordered)

    def test_archive_rejects_unsafe_entry_names(self):
        for name in ("media/../evil.png", "media/a//b.png", "media/CON.png", "media/a.png:hidden",
                     "media/a\\b.png", "other/a.png", "media/"):
            with self.subTest(name=name):
                self.assertIsNone(builder.archive_media_path(name))
        self.assertEqual(builder.archive_media_path("media/a1b2c3.png"), "a1b2c3.png")
        self.assertEqual(builder.archive_media_path("media/x/y.png"), "x/y.png")

    def test_inspect_rejects_non_image_media_entries(self):
        # A hand-assembled archive can carry any bytes under media/. The app would store the file
        # and then fail to decode it on every view, so the checker must catch it up front.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "ok.png", 2, 2, (8, 8, 8))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "ok.png")), sources)
            supplied = next(iter(sources))
            archive_path = root / "tampered.lumecard"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr(builder.ARCHIVE_MANIFEST, json.dumps(data).encode("utf-8"))
                archive.writestr(builder.ARCHIVE_MEDIA_DIR + supplied, b"plain text")
            with self.assertRaisesRegex(ValueError, "not a decodable image"):
                builder.inspect_archive(archive_path)

    def test_archive_inspect_tolerates_unreferenced_media_entries(self):
        # ExportArchiveServiceTest's `unreferenced archive entry is neither extracted nor counted
        # as missing` proves the app filters to referencedPaths before extracting. Rejecting such
        # an archive here would refuse one the app imports perfectly.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "present.png", 2, 2, (1, 2, 3))
            make_png(root / "junk.png", 2, 2, (4, 5, 6))
            sources = {}
            plan = self.plan_with_source(str(root / "present.png"))
            data = builder.build(plan, sources)
            name = next(iter(sources))
            archive_path = root / "extra.lumecard"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr(builder.ARCHIVE_MANIFEST, json.dumps(data).encode("utf-8"))
                archive.writestr(builder.ARCHIVE_MEDIA_DIR + name, (root / "present.png").read_bytes())
                archive.writestr(builder.ARCHIVE_MEDIA_DIR + "junk.png", (root / "junk.png").read_bytes())
            result = builder.inspect_archive(archive_path)
            self.assertEqual(result["media"], 2)

    def test_archive_rejects_header_only_image_payload(self):
        # A complete-looking 16-byte IHDR prefix still passes sniff_image but cannot be decoded.
        # The importer would write it and show a permanent broken image, so inspect must fail it.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (7, 7, 7))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
            name = next(iter(sources))
            archive_path = root / "header.lumecard"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr(builder.ARCHIVE_MANIFEST, json.dumps(data).encode("utf-8"))
                archive.writestr(builder.ARCHIVE_MEDIA_DIR + name,
                                 b"\x89PNG\r\n\x1a\n\x00\x00\x00\x0dIHDR")
            with self.assertRaisesRegex(ValueError, "not a decodable image"):
                builder.inspect_archive(archive_path)

    def test_archive_budgets_match_what_the_app_will_import(self):
        # The app enforces these limits at import time by skipping the offending image, which
        # leaves the card with a reference to a file that never landed. Producing such an archive
        # has to fail here instead, where the author can still act on it.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (1, 2, 3))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
            name = next(iter(sources))

            too_many = {("f%05d.png" % i): builder.CollectedImage("f%05d.png" % i, "image/png", b"x")
                        for i in range(builder.MAX_MEDIA_ENTRIES + 1)}
            with self.assertRaisesRegex(ValueError, "exceeds the %d the app will import" % builder.MAX_MEDIA_ENTRIES):
                builder.write_archive(root / "many.lumecard", data, too_many)

            oversized = {name: builder.CollectedImage(name, "image/png", b"x")}
            oversized[name].size = builder.MAX_TOTAL_MEDIA_BYTES + 1
            with self.assertRaisesRegex(ValueError, "the app will import"):
                builder.write_archive(root / "big.lumecard", data, oversized)

            # A single image is capped at what the *importer* accepts, not what a reader can hold.
            # 512 MB is the reader's diagnostic ceiling; 64 MB is where the importer gives up and
            # skips the file, so the build-side limit has to be the lower of the two.
            self.assertEqual(builder.MAX_MEDIA_BYTES, 64 * 1024 * 1024)
            self.assertLess(builder.MAX_MEDIA_BYTES, builder.MAX_TOTAL_MEDIA_BYTES)

    def test_archive_without_local_images_still_packages_cleanly(self):
        # `package` also has to work for a plan that references images by https only: nothing is
        # embedded, so the archive is a manifest and nothing else.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plan = copy.deepcopy(self.plan)
            plan["knowledgeBases"][0]["decks"][0]["cards"][0]["media"] = [
                {"id": "i", "type": "IMAGE", "filePath": "https://cdn.example.com/a.png"}]
            sources = {}
            target = root / "remote.lumecard"
            builder.write_archive(target, builder.build(plan, sources), sources)
            self.assertEqual(sources, {})
            self.assertEqual(builder.inspect_archive(target)["media"], 0)

    def test_temporary_archive_name_is_not_derived_from_the_output_alone(self):
        # Two builds aimed at the same path is the case a temporary file actually has to
        # discriminate: both pass the `exists()` guard before either writes, so a name derived
        # only from the output makes them share one temp file — the second open fails with
        # FileExistsError (on "xb") or, without that flag, corrupts the other's archive.
        # `exists()` is forced to False here to model exactly that overlap.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (8, 8, 8))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
            seen = []
            real_open = Path.open

            def spy(self_path, *args, **kwargs):
                if self_path.suffix == ".part":
                    seen.append(self_path.name)
                return real_open(self_path, *args, **kwargs)

            with unittest.mock.patch.object(Path, "open", spy), \
                    unittest.mock.patch.object(Path, "exists", lambda self_path: False):
                builder.write_archive(root / "deck.lumecard", data, dict(sources))
                builder.write_archive(root / "deck.lumecard", data, dict(sources))
            self.assertEqual(len(seen), 2, seen)
            self.assertNotEqual(seen[0], seen[1],
                                "two writes to one target shared a temporary file: " + repr(seen))

    def test_archive_writer_leaves_no_partial_file_behind(self):
        # A failure partway through a write used to leave a truncated archive at the destination,
        # and the retry then failed with "file exists" against its own debris.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (5, 5, 5))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
            name = next(iter(sources))

            class Exploding(object):
                size = 4

                @property
                def data(self):
                    raise RuntimeError("simulated read failure")
            broken = dict(sources)
            broken["zz.png"] = Exploding()
            target = root / "deck.lumecard"
            with self.assertRaises(RuntimeError):
                builder.write_archive(target, data, broken)
            self.assertFalse(target.exists())
            self.assertEqual(sorted(x.name for x in root.iterdir()), ["a.png"])

            # The same destination is usable afterwards.
            builder.write_archive(target, data, sources)
            self.assertEqual(builder.inspect_archive(target)["media"], 1)

    def test_archive_inspect_tolerates_files_the_app_ignores(self):
        # `zip -r` writes a directory entry per folder and macOS adds resource forks; the app
        # skips both. Refusing them would reject archives that import perfectly well.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (6, 6, 6))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
            name = next(iter(sources))
            archive_path = root / "furniture.lumecard"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr(builder.ARCHIVE_MANIFEST, json.dumps(data).encode("utf-8"))
                archive.writestr(zipfile.ZipInfo(builder.ARCHIVE_MEDIA_DIR), b"")
                archive.writestr(builder.ARCHIVE_MEDIA_DIR + name, (root / "a.png").read_bytes())
                archive.writestr("__MACOSX/._a.png", b"resource fork")
            self.assertEqual(builder.inspect_archive(archive_path)["media"], 1)

    def test_archive_inspect_rejects_an_archive_too_long_to_list(self):
        # listZipArchive returns null once it sees MAX_ARCHIVE_LIST_ENTRIES non-directory entries,
        # so the app reports READ_FAILED and imports nothing — even if all media is within budget.
        # Use a patched small limit to keep the test cheap while exercising the exact comparison.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (6, 6, 6))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
            name = next(iter(sources))
            archive_path = root / "many.lumecard"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr(builder.ARCHIVE_MANIFEST, json.dumps(data).encode("utf-8"))
                archive.writestr(builder.ARCHIVE_MEDIA_DIR + name, (root / "a.png").read_bytes())
                for i in range(8):
                    archive.writestr("__MACOSX/._%d" % i, b"x")
            with unittest.mock.patch.object(builder, "MAX_ARCHIVE_LIST_ENTRIES", 5):
                with self.assertRaisesRegex(ValueError, "exceeds the 5 the app will list"):
                    builder.inspect_archive(archive_path)
            # Just under the limit still passes.
            with unittest.mock.patch.object(builder, "MAX_ARCHIVE_LIST_ENTRIES", 10):
                self.assertEqual(builder.inspect_archive(archive_path)["media"], 1)

    def test_archive_inspect_accepts_a_unique_case_only_match(self):
        # ExportArchiveService lands the bytes under the *manifest* spelling: with one entry and
        # one reference differing only by case, `singleOrNull()` resolves cleanly and the image
        # works. Rejecting this would refuse an archive the app imports correctly.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (7, 7, 7))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
            name = next(iter(sources))
            data["cards"][0]["media"][0]["filePath"] = name.upper()
            archive_path = root / "cased.lumecard"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr(builder.ARCHIVE_MANIFEST, json.dumps(data).encode("utf-8"))
                archive.writestr(builder.ARCHIVE_MEDIA_DIR + name, (root / "a.png").read_bytes())
            self.assertEqual(builder.inspect_archive(archive_path)["media"], 1)

    def test_archive_inspect_rejects_two_references_differing_only_by_case(self):
        # Two manifest spellings folding to one key make the app's `singleOrNull()` fallback return
        # null, so it writes under the archive spelling and both cards point at a nonexistent file.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (7, 7, 7))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
            name = next(iter(sources))
            data["cards"][0]["media"][0]["filePath"] = name.upper()
            card = copy.deepcopy(data["cards"][0])
            card["id"] = "card_second"
            card["media"][0]["filePath"] = name.lower()
            data["cards"].append(card)
            archive_path = root / "ambiguous.lumecard"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr(builder.ARCHIVE_MANIFEST, json.dumps(data).encode("utf-8"))
                archive.writestr(builder.ARCHIVE_MEDIA_DIR + name, (root / "a.png").read_bytes())
            with self.assertRaisesRegex(ValueError, "permanently missing image"):
                builder.inspect_archive(archive_path)

    def test_archive_inspect_case_matrix_all_branches(self):
        # The resolution matrix has an exact-match branch, a one-entry fallback, two rejection
        # branches and the "one reference / many entries" branch. Ordinary archives exercise only
        # the first two, so the others could be deleted without any test failing.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (7, 7, 7))
            make_png(root / "b.png", 2, 2, (8, 8, 8))
            blob_a = (root / "a.png").read_bytes()
            blob_b = (root / "b.png").read_bytes()

            def archive_with(refs, names):
                sources = {}
                data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
                original = next(iter(sources))
                data["cards"][0]["media"][0]["filePath"] = refs[0]
                for extra in refs[1:]:
                    card = copy.deepcopy(data["cards"][0])
                    card["id"] = "card_" + extra
                    card["media"][0]["filePath"] = extra
                    data["cards"].append(card)
                path = root / ("m%d.lumecard" % len(names))
                with zipfile.ZipFile(path, "w") as archive:
                    archive.writestr(builder.ARCHIVE_MANIFEST, json.dumps(data).encode("utf-8"))
                    for index, name in enumerate(names):
                        archive.writestr(builder.ARCHIVE_MEDIA_DIR + name,
                                         blob_a if index == 0 else blob_b)
                return path

            # 1. Two references with two exact entries: each card keeps its own file.
            upper, lower = "A1.png", "a1.png"
            self.assertEqual(builder.inspect_archive(archive_with([upper, lower], [upper, lower]))["media"], 2)
            # 2. One reference, two case-only entries: the app writes both in zip order.
            with self.assertRaisesRegex(ValueError, "zip entry order"):
                builder.inspect_archive(archive_with([lower], [upper, lower]))
            # 3. Two references, two entries, neither exact: nothing the app can resolve.
            with self.assertRaisesRegex(ValueError, "differ only by case"):
                builder.inspect_archive(archive_with(["A1.png", "a1.png"], ["A1.PNG", "a1.PNG"]))

    def test_sniff_image_does_not_guess_from_short_prefixes(self):
        # `BM` is only two bytes: classifying on that alone would treat any file starting with
        # those letters as a bitmap and let a broken image through to a card.
        self.assertIsNone(builder.sniff_image(b"BMW is a car brand"))
        self.assertIsNone(builder.sniff_image(b"BM"))
        self.assertEqual(builder.sniff_image(b"BM" + b"\0" * 12)[0], "bmp")
        # Short reads must not be mistaken for WEBP just because RIFF is present.
        self.assertIsNone(builder.sniff_image(b"RIFF"))
        self.assertIsNone(builder.sniff_image(b"RIFF\0\0\0\0WEB"))
        self.assertEqual(builder.sniff_image(b"RIFF\0\0\0\0WEBP")[0], "webp")
        self.assertIsNone(builder.sniff_image(b""))

    def test_sniff_image_rejects_truncated_png_and_jpeg(self):
        # A bare signature matched, so an 8-byte "PNG" or a 4-byte "JPEG" was packaged and the
        # archive passed its own inspect; the app then showed a permanent broken image with no
        # error. The first structural chunk/marker is fixed by the format specs and fits in the
        # 16-byte prefix both callers already read.
        self.assertIsNone(builder.sniff_image(b"\x89PNG\r\n\x1a\n"))
        self.assertIsNone(builder.sniff_image(b"\x89PNG\r\n\x1a\n" + b"\0" * 20))
        self.assertIsNone(builder.sniff_image(b"\xff\xd8\xff\x00" + b"\0" * 12))
        self.assertIsNone(builder.sniff_image(b"\xff\xd8\xff"))
        # Real files (and the fixtures built by make_png) still classify correctly.
        self.assertEqual(builder.sniff_image(
            b"\x89PNG\r\n\x1a\n\x00\x00\x00\x0dIHDR")[0], "png")
        self.assertEqual(builder.sniff_image(b"\xff\xd8\xff\xe0\x00\x10JFIF")[0], "jpg")
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (1, 1, 1))
            self.assertEqual(builder.sniff_image((root / "a.png").read_bytes()[:16])[0], "png")
            # A signature-only sourcePath is refused before it can reach the archive.
            (root / "truncated.png").write_bytes(b"\x89PNG\r\n\x1a\n")
            plan = copy.deepcopy(self.plan)
            plan["knowledgeBases"][0]["decks"][0]["cards"][0]["media"] = [
                {"id": "m", "type": "IMAGE", "sourcePath": str(root / "truncated.png")}]
            with self.assertRaisesRegex(ValueError, "unrecognised or truncated image"):
                builder.build(plan, {})

    def test_local_path_in_file_path_names_source_path_as_the_fix(self):
        # `filePath` is the field the app's own exported JSON uses, so an author who has seen an
        # export writes a real local path there by default. The media-root rule list does not hint
        # at the fix; only this message does, and getting it wrong costs a whole debugging cycle.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for value in (str(root / "a.png"), "/home/me/a.png", "C:/pics/a.png",
                          "C:\\pics\\a.png"):
                plan = copy.deepcopy(self.plan)
                plan["knowledgeBases"][0]["decks"][0]["cards"][0]["media"] = [
                    {"id": "i", "type": "IMAGE", "filePath": value}]
                with self.subTest(value=value), self.assertRaisesRegex(
                        ValueError, "name it with media\\[\\]\\.sourcePath"):
                    builder.build(plan)
            # A name that is merely unsafe, not local, keeps the general rule message.
            plan = copy.deepcopy(self.plan)
            plan["knowledgeBases"][0]["decks"][0]["cards"][0]["media"] = [
                {"id": "i", "type": "IMAGE", "filePath": "CON.png"}]
            with self.assertRaisesRegex(ValueError, "expected a media-root relative path"):
                builder.build(plan)

    def test_media_file_path_is_not_silently_trimmed(self):
        with self.assertRaisesRegex(ValueError, "whitespace"):
            builder.media_items([{"id": "i", "type": "IMAGE", "filePath": " a.png"}], "card.media")
        with self.assertRaisesRegex(ValueError, "whitespace"):
            builder.media_items([{"id": "i", "type": "IMAGE", "filePath": "a.png "}], "card.media")

    def test_package_refuses_a_plan_whose_local_images_carry_no_bytes(self):
        # `filePath` names a file the build cannot see. Packaging such a plan used to succeed and
        # emit an archive whose manifest promised images that were not inside it; the app then
        # imported the cards and left every picture broken without reporting anything.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plan = copy.deepcopy(self.plan)
            plan["knowledgeBases"][0]["decks"][0]["cards"][0]["media"] = [
                {"id": "i", "type": "IMAGE", "filePath": "local.png"}]
            with self.assertRaisesRegex(ValueError, "sourcePath"):
                builder.build(plan, {})
            # A remote reference is fine: it is never archived.
            plan["knowledgeBases"][0]["decks"][0]["cards"][0]["media"] = [
                {"id": "i", "type": "IMAGE", "filePath": "https://cdn.example.com/a.png"}]
            self.assertEqual(builder.build(plan, {})["cards"][0]["media"][0]["filePath"],
                             "https://cdn.example.com/a.png")

    def test_package_output_always_survives_its_own_inspect(self):
        # Whatever `package` writes must pass `inspect`, otherwise the tool can hand the user a
        # file it would itself reject. Exercise the whole CLI path rather than the helpers.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "one.png", 3, 3, (11, 22, 33))
            make_png(root / "two.png", 2, 2, (44, 55, 66))
            plan = {"knowledgeBases": [{"name": "K", "decks": [{"name": "D", "cards": [
                {"front": "a", "back": "b", "media": [
                    {"id": "m1", "type": "IMAGE", "sourcePath": str(root / "one.png")},
                    {"id": "m2", "type": "IMAGE", "sourcePath": str(root / "two.png")}]},
                {"front": "c", "back": "d"},
                {"front": "e", "back": "f", "media": [
                    {"id": "m3", "type": "IMAGE", "filePath": "https://cdn.example.com/x.png"}]}]}]}]}
            plan_path = root / "plan.json"
            plan_path.write_text(json.dumps(plan), encoding="utf-8")
            target = root / "out.lumecard"
            command = [sys.executable, str(ROOT / "scripts/build_deck.py")]
            packaged = subprocess.run(command + ["package", str(plan_path), str(target)], capture_output=True)
            self.assertEqual(packaged.returncode, 0, packaged.stderr)
            self.assertEqual(json.loads(packaged.stdout),
                             {"knowledgeBases": 1, "decks": 1, "cards": 3, "media": 2, "remote": 1})
            checked = subprocess.run(command + ["inspect", str(target)], capture_output=True)
            self.assertEqual(checked.returncode, 0, checked.stderr)
            self.assertEqual(json.loads(checked.stdout), json.loads(packaged.stdout))

    def test_package_removes_its_output_when_self_check_fails(self):
        # A rejected archive must not be left on disk: the user would import it and see the
        # pictures silently vanish.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plan = {"knowledgeBases": [{"name": "K", "decks": [{"name": "D", "cards": [
                {"front": "a", "back": "b", "media": [
                    {"id": "m", "type": "IMAGE", "filePath": "https://cdn.example.com/x.png"}]}]}]}]}
            plan_path = root / "plan.json"
            plan_path.write_text(json.dumps(plan), encoding="utf-8")
            target = root / "out.lumecard"
            # Sabotage the self-check by making inspect reject every manifest.
            script = root / "sabotage.py"
            script.write_text(
                "import sys, runpy\n"
                "sys.path.insert(0, %r)\n" % str(ROOT / "scripts") +
                "import build_deck\n"
                "build_deck.inspect_archive = lambda p: (_ for _ in ()).throw(ValueError('synthetic'))\n"
                "sys.argv = ['build_deck.py', 'package', %r, %r]\n" % (str(plan_path), str(target)) +
                "raise SystemExit(build_deck.main())\n", encoding="utf-8")
            result = subprocess.run([sys.executable, str(script)], capture_output=True)
            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertIn(b"failed its own check", result.stderr)
            self.assertFalse(target.exists())

    def test_package_self_check_cleans_up_on_any_expected_error(self):
        # A disk I/O error during the self-check used to bypass cleanup because the except clause
        # listed only a subset of the errors main() handles. A bad archive left on disk gets
        # imported by the user and silently loses every picture.
        for exception in ("OSError('disk full')", "TypeError('bad shape')", "KeyError('media')"):
            with self.subTest(exception=exception):
                with tempfile.TemporaryDirectory() as tmp:
                    root = Path(tmp)
                    plan = {"knowledgeBases": [{"name": "K", "decks": [{"name": "D", "cards": [
                        {"front": "a", "back": "b", "media": [
                            {"id": "m", "type": "IMAGE", "filePath": "https://cdn.example.com/x.png"}]}]}]}]}
                    plan_path = root / "plan.json"
                    plan_path.write_text(json.dumps(plan), encoding="utf-8")
                    target = root / "out.lumecard"
                    script = root / "sabotage.py"
                    script.write_text(
                        "import sys\n"
                        "sys.path.insert(0, %r)\n" % str(ROOT / "scripts") +
                        "import build_deck\n"
                        "build_deck.inspect_archive = lambda p: (_ for _ in ()).throw(%s)\n" % exception +
                        "sys.argv = ['build_deck.py', 'package', %r, %r]\n" % (str(plan_path), str(target)) +
                        "raise SystemExit(build_deck.main())\n", encoding="utf-8")
                    result = subprocess.run([sys.executable, str(script)], capture_output=True)
                    self.assertEqual(result.returncode, 2, result.stderr)
                    self.assertIn(b"failed its own check", result.stderr)
                    self.assertFalse(target.exists(), "bad archive was left on disk")

    def test_metadata_optional_values(self):
        # The app reads `metadata["hcenter"].toBoolean()`, which is false when the key is absent,
        # and its own writer removes the key when a toggle is cleared. Requiring it would reject
        # shares the app produced — the validator must never be stricter than the reader.
        for metadata in ({}, {"hcenter": "true"}, {"fontSize": "20"},
                         {"hcenter": "true", "fontSize": "20"}):
            data = copy.deepcopy(self.data)
            data["cards"][0]["metadata"] = metadata
            with self.subTest(metadata=metadata):
                builder.validate(data)
        # A present-but-wrong value is still a real error.
        for metadata in ({"hcenter": "yes"}, {"fontFamily": "comic"}, {"fontSize": "8"}):
            data = copy.deepcopy(self.data)
            data["cards"][0]["metadata"] = metadata
            with self.subTest(metadata=metadata), self.assertRaises(ValueError):
                builder.validate(data)

    def test_archive_rejects_forged_entry_size(self):
        # A central directory may claim any size it likes, and Python's reader stops at the
        # declared length, so a bomb cannot be caught by counting bytes. `compress_size` stays
        # pinned to the real layout and deflate never inflates, so that relationship exposes it.
        # The CRC is recomputed over the declared prefix so the *only* thing wrong with this
        # archive is the lie about its size — otherwise the CRC check fires first and the budget
        # logic below is never exercised.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "a.png", 2, 2, (7, 7, 7))
            sources = {}
            data = builder.build(self.plan_with_source(str(root / "a.png")), sources)
            name = next(iter(sources))
            # The payload has to be *incompressible*, otherwise deflate squeezes a solid-colour
            # PNG down to ~130 bytes and the compressed size never exceeds the 1 KiB slack the
            # check allows — the assertion below would fail for the wrong reason and the formula
            # would stay untested. A valid PNG header keeps `sniff_image` happy; the random tail
            # is what makes the compressed size honest.
            payload = b"\x89PNG\r\n\x1a\n" + os.urandom(4096)
            declared = 8
            archive_path = root / "forged.lumecard"
            with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
                archive.writestr(builder.ARCHIVE_MANIFEST, json.dumps(data).encode("utf-8"))
                archive.writestr(builder.ARCHIVE_MEDIA_DIR + name, payload)
            raw = bytearray(archive_path.read_bytes())
            entry = builder.ARCHIVE_MEDIA_DIR + name
            forged_crc = zlib.crc32(payload[:declared]) & 0xFFFFFFFF
            for signature, size_at, crc_at, name_len_at, name_at in (
                    (b"PK\x03\x04", 22, 14, 26, 30), (b"PK\x01\x02", 24, 16, 28, 46)):
                index = raw.find(signature)
                while index >= 0:
                    length = struct.unpack_from("<H", raw, index + name_len_at)[0]
                    found = bytes(raw[index + name_at:index + name_at + length]).decode()
                    if found == entry:
                        struct.pack_into("<I", raw, index + size_at, declared)
                        struct.pack_into("<I", raw, index + crc_at, forged_crc)
                    index = raw.find(signature, index + 4)
            archive_path.write_bytes(raw)
            with zipfile.ZipFile(archive_path) as archive:
                info = archive.getinfo(entry)
                # Precondition: the lie is visible in the size relationship, which is what
                # inspect reads.
                self.assertGreater(info.compress_size, info.file_size + info.file_size // 50 + 1024)
                # And the declared prefix alone is still readable, so nothing before the size
                # check rejects this entry: the forged-size branch is the only thing left to fire.
                with archive.open(entry) as stream:
                    self.assertEqual(stream.read(), payload[:declared])
            with self.assertRaisesRegex(ValueError, "size was forged"):
                builder.inspect_archive(archive_path)

    def test_collect_image_refuses_symbolic_links(self):
        # A link is an indirection the plan did not name; following it would embed bytes from an
        # arbitrary target under a name the author never wrote.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_png(root / "real.png", 2, 2, (1, 1, 1))
            link = root / "link.png"
            try:
                link.symlink_to(root / "real.png")
            except (OSError, NotImplementedError):
                self.skipTest("symlinks unavailable on this platform")
            with self.assertRaisesRegex(ValueError, "symbolic links"):
                builder.collect_image(str(link), "card.media[0]")
            # The real file is still accepted.
            self.assertTrue(builder.collect_image(str(root / "real.png"), "loc").name.endswith(".png"))

    def test_share_contract_guardrails_reject_hand_edited_packages(self):
        # `build` can never produce these, but `validate` is also the entry point for a JSON an
        # author edited by hand. Each rule below protects the app from a file it would either
        # reject late or, worse, import into the wrong place.
        cases = (
            ("exportType", lambda d: d.update(exportType="backup"), "exportType must be share"),
            ("schemaVersion", lambda d: d.update(schemaVersion=2), "unsupported export/schema version"),
            ("version", lambda d: d.update(version="1.1.0"), "unsupported export/schema version"),
            ("reviewLogs", lambda d: d.update(reviewLogs=[]), "private backup fields forbidden"),
            ("learningPlans", lambda d: d.update(learningPlans=[]), "private backup fields forbidden"),
            ("dangling deckId", lambda d: d["cards"][0].update(deckId="nope"), "dangling deckId"),
            ("dangling knowledgeBaseId", lambda d: d["decks"][0].update(knowledgeBaseId="nope"),
             "dangling knowledgeBaseId"),
            ("nested deck", lambda d: d["decks"][0].update(parentId=d["decks"][0]["id"]),
             "nested decks not supported"),
            ("missing description", lambda d: d["decks"][0].pop("description"),
             "deck.description required"),
            ("description type", lambda d: d["decks"][0].update(description=5),
             "deck.description required"),
            ("history leak", lambda d: d["cards"][0].update(nextReviewAt="2026-01-01T00:00:00Z"),
             "history/tombstones forbidden"),
            ("tombstone", lambda d: d["cards"][0].update(deletedAt="2026-01-01T00:00:00Z"),
             "history/tombstones forbidden"),
        )
        for label, mutate, message in cases:
            data = copy.deepcopy(self.data)
            mutate(data)
            with self.subTest(label=label), self.assertRaisesRegex(ValueError, message):
                builder.validate(data)

    def test_share_fields_and_long_version_boundaries(self):
        for field, value in (("deviceId", {}), ("deviceId", 123), ("backupFormatVersion", 99),
                             ("extra", "unexpected")):
            data = copy.deepcopy(self.data)
            data[field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                builder.validate(data)
        for collection in ("knowledgeBases", "decks", "cards"):
            for value in (0, True, 2**63):
                data = copy.deepcopy(self.data)
                data[collection][0]["version"] = value
                with self.subTest(collection=collection, value=value), self.assertRaises(ValueError):
                    builder.validate(data)
            data = copy.deepcopy(self.data)
            data[collection][0]["version"] = 2**63 - 1
            builder.validate(data)
        for value in (None, "device"):
            data = copy.deepcopy(self.data)
            data["deviceId"] = value
            builder.validate(data)

    def test_plan_fields_cannot_override_validation_of_export(self):
        for field, value in (("options", [{"text": "A", "correct": True}, {"text": "B", "correct": False}]),
                             ("style", {}), ("bidirectional", True)):
            data = copy.deepcopy(self.data)
            data["cards"][2][field] = value
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "unsupported fields"):
                builder.validate(data)

    def test_validate_checks_choice_back_independently_of_plan_fields(self):
        # Previously the plan-only-field assertion above fired first even when choice validation
        # was deleted from validate(), so nothing actually guarded that branch. Use a clean export
        # card and anchor the message to the real failure.
        data = copy.deepcopy(self.data)
        data["cards"][2]["type"] = "MULTIPLE_CHOICE"
        data["cards"][2]["back"] = "+only one option"
        with self.assertRaisesRegex(ValueError, "at least two options"):
            builder.validate(data)

    def test_choice_lines_count_kotlin_whitespace_as_a_blank_line(self):
        # Kotlin's Char.isWhitespace() = Character.isWhitespace || Character.isSpaceChar (verified on
        # the JVM): NBSP, FIGURE SPACE and NARROW NO-BREAK SPACE are all blank to CardComponents.kt's
        # trim()/isNotBlank(). Python str.strip() covers a *different* set, so the builder must use the
        # Kotlin set or it would disagree with the app about which lines are real options.
        # U+0085 is in LINE_BREAKS and rejected earlier, so it cannot become a phantom option.
        for blank in ("\u00a0", "\u2007", "\u202f", " "):
            card = {"front": "Q?", "back": "+A\n" + blank + "\nB", "type": "MULTIPLE_CHOICE"}
            with self.subTest(blank=blank):
                self.assertEqual(builder.choice_lines(card["back"]), ["+A", "B"])
                self.assertEqual(builder.card_content(card)[1], "+A\n" + blank + "\nB")
                plan = copy.deepcopy(self.plan)
                plan["knowledgeBases"][0]["decks"][0]["cards"] = [card]
                builder.build(plan)

    def test_font_size_rejects_non_ascii_digits(self):
        # The app parses metadata with toIntOrNull(), which only understands ASCII. Python's
        # `int()` would also accept full-width and Arabic-Indic digits, so validate must not.
        for value in ("１２", "١٦", "０"):
            data = copy.deepcopy(self.data)
            data["cards"][0]["metadata"]["fontSize"] = value
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "fontSize"):
                builder.validate(data)

    def test_record_errors_carry_collection_index(self):
        # A 20-card plan needs to say *which* card/timestamp/version is broken.
        data = copy.deepcopy(self.data)
        data["cards"][1]["createdAt"] = "not-a-date"
        with self.assertRaisesRegex(ValueError, r"cards\[1\]\.createdAt"):
            builder.validate(data)
        data = copy.deepcopy(self.data)
        data["decks"][0]["version"] = 0
        with self.assertRaisesRegex(ValueError, r"decks\[0\]\.version"):
            builder.validate(data)
        data = copy.deepcopy(self.data)
        data["cards"][2]["id"] = data["cards"][0]["id"]
        with self.assertRaisesRegex(ValueError, r"cards\[2\].*duplicate ID"):
            builder.validate(data)

    def test_choice_newlines_match_app_renderer(self):
        for separator in ("\r", "\r\n"):
            card = {"front": "Q?", "back": "+A" + separator + "B", "type": "MULTIPLE_CHOICE"}
            self.assertEqual(builder.card_content(card)[1], "+A\nB")
            plan = copy.deepcopy(self.plan)
            plan["knowledgeBases"][0]["decks"][0]["cards"] = [card]
            data = builder.build(plan)
            self.assertEqual(data["cards"][0]["back"].split("\n"), ["+A", "B"])
            builder.validate(data)
        for separator in ("\r", "\r\n", "\u2028", "\u2029", "\x85", "\v"):
            data = copy.deepcopy(self.data)
            data["cards"][2]["back"] = "+A" + separator + "B"
            with self.subTest(separator=repr(separator)), self.assertRaises(ValueError):
                builder.validate(data)
        for separator in ("\n", "\r", "\u2028", "\u2029", "\x85", "\v"):
            card = {"front": "Q?", "options": [{"text": "A" + separator, "correct": True},
                                               {"text": "B", "correct": False}]}
            with self.subTest(separator=repr(separator)), self.assertRaises(ValueError):
                builder.card_content(card)

    def test_choice_correct_marker_survives_indentation(self):
        # CardComponents.kt trims each line before testing for `+`, so an indented marker is a
        # correct answer to the app. Judging the raw line would report "no correct answer" for a
        # card the app renders fine.
        for back in ("  +A\nB", "\t+A\nB", "+A\n  +B\nC"):
            with self.subTest(back=back):
                builder.choice_lines(back)
        with self.assertRaisesRegex(ValueError, "leading \\+ correct answer"):
            builder.choice_lines("A\nB")
        # Duplicates are judged on the trimmed text, so a marker does not hide a repeat.
        with self.assertRaisesRegex(ValueError, "duplicate option"):
            builder.choice_lines("+A\n  A")

    def test_cli_diagnostics_name_the_problem_and_the_fix(self):
        # These messages are the tool's whole user interface for an agent: a runbook that pipes
        # stderr into a model gets one line and has to act on it. Each case below used to leak an
        # errno, a codec offset with no hint, or a field name with no card index.
        command = [sys.executable, str(ROOT / "scripts/build_deck.py")]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            plan = builder.load(ROOT / "examples/plan.json")

            def run(*args):
                return subprocess.run(command + list(args), capture_output=True, text=True)

            def stderr_of(*args):
                return run(*args).stderr.strip()

            # 1. Empty file: "Expecting value: line 1 column 1" told nobody anything.
            (root / "empty.json").write_text("", encoding="utf-8")
            self.assertIn("is empty", stderr_of("build", str(root / "empty.json"), str(root / "o1.json")))

            # 2. Not UTF-8: a codec error with no remedy named.
            (root / "gbk.json").write_bytes('{"a": "中文"}'.encode("gbk"))
            message = stderr_of("build", str(root / "gbk.json"), str(root / "o2.json"))
            self.assertIn("is not UTF-8", message)
            self.assertIn("UTF-8", message)

            # 3. Malformed JSON: must say which line, and which file.
            (root / "broken.json").write_text('{\n  "a": 1,\n  "b": }', encoding="utf-8")
            message = stderr_of("build", str(root / "broken.json"), str(root / "o3.json"))
            self.assertIn("broken.json", message)
            self.assertIn("line 3", message)

            # 4. A card field error has to name the card, not just the field.
            bad = copy.deepcopy(plan)
            del bad["knowledgeBases"][0]["decks"][0]["cards"][2]["front"]
            (root / "badcard.json").write_text(json.dumps(bad), encoding="utf-8")
            self.assertIn("cards[2].front", stderr_of("build", str(root / "badcard.json"), str(root / "o4.json")))

            # 5. Existing output: the errno string is not actionable.
            first = run("build", str(ROOT / "examples/plan.json"), str(root / "dup.json"))
            self.assertEqual(first.returncode, 0, first.stderr)
            message = stderr_of("build", str(ROOT / "examples/plan.json"), str(root / "dup.json"))
            self.assertIn("already exists", message)
            self.assertIn("delete it", message)

            # 6. A directory passed as a file: "Permission denied" was actively misleading.
            (root / "adir").mkdir()
            self.assertIn("is a directory", stderr_of("build", str(root / "adir"), str(root / "o6.json")))
            self.assertIn("is a directory", stderr_of("inspect", str(root / "adir")))

            # 7. Cross-command misuse points at the right command instead of the encoding.
            archive = root / "real.lumecard"
            packaged = run("package", str(self.write_plan(root, "p.json")), str(archive))
            self.assertEqual(packaged.returncode, 0, packaged.stderr)
            self.assertIn("use `inspect`", stderr_of("build", str(archive), str(root / "o7.json")))
            self.assertIn("use `validate`", stderr_of("inspect", str(root / "dup.json")))

            # 8. A .lumecard output name for `build` means the user wanted `package`.
            message = stderr_of("build", str(ROOT / "examples/plan.json"), str(root / "o8.lumecard"))
            self.assertIn("package", message)
            self.assertFalse((root / "o8.lumecard").exists())

            # 9. inspect on a non-zip that is not JSON either.
            (root / "notazip.lumecard").write_bytes(b"plain text, not a zip")
            self.assertIn("is not a zip archive", stderr_of("inspect", str(root / "notazip.lumecard")))

            # 10. A missing input names the path rather than failing on a later step.
            self.assertIn("no such file", stderr_of("build", str(root / "ghost.json"), str(root / "o10.json")))

    def write_plan(self, root, name):
        """Write a minimal plan whose one card carries a local image."""
        if not (root / "a.png").exists():
            make_png(root / "a.png", 2, 2, (9, 9, 9))
        plan = {"knowledgeBases": [{"name": "B", "decks": [{"name": "D", "cards": [
            {"front": "Q", "back": "A",
             "media": [{"id": "m", "type": "IMAGE", "sourcePath": str(root / "a.png")}]},
        ]}]}]}
        path = root / name
        path.write_text(json.dumps(plan), encoding="utf-8")
        return path

    def test_cli_roundtrip_overwrite_and_errors(self):
        with tempfile.TemporaryDirectory(prefix="lumecard 中文 ") as tmp:
            out = Path(tmp) / "卡牌.json"
            command = [sys.executable, str(ROOT / "scripts/build_deck.py")]
            first = subprocess.run(command + ["build", str(ROOT / "examples/plan.json"), str(out)], capture_output=True)
            self.assertEqual(first.returncode, 0, first.stderr)
            self.assertEqual(subprocess.run(command + ["validate", str(out)], capture_output=True).returncode, 0)
            original = out.read_bytes()
            self.assertEqual(subprocess.run(command + ["build", str(ROOT / "examples/plan.json"), str(out)], capture_output=True).returncode, 2)
            self.assertEqual(out.read_bytes(), original)
            self.assertEqual(subprocess.run(command + ["validate", str(out) + ".missing"], capture_output=True).returncode, 2)

    def test_duplicate_keys(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "bad.json"
            file.write_text('{"a":1,"a":2}', encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "duplicate JSON"):
                builder.load(file)

    def test_write_new_leaves_no_partial_file_on_encode_failure(self):
        # A lone surrogate passes validate() but fails at UTF-8 encode time. Writing straight to
        # the destination left truncated JSON there, so the next run was blocked by its own
        # never-overwrite guard with no way to recover except deleting the file by hand.
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "out.json"
            data = copy.deepcopy(self.data)
            data["cards"][0]["front"] = "Q \ud800?"
            with self.assertRaises(UnicodeEncodeError):
                builder.write_new(out, data)
            self.assertFalse(out.exists(), "a failed build must not leave a truncated file behind")
            self.assertEqual(list(Path(tmp).glob("*.part")), [], "no temp file may survive the failure")
            # A clean retry succeeds into the same path.
            builder.write_new(out, self.data)
            self.assertTrue(out.exists())

    def test_inspect_rejects_duplicate_keys_in_manifest(self):
        # inspect used a bare json.loads, so a manifest with a duplicate key was accepted while the
        # very same bytes failed `validate` as a plan. Package's self-check then blessed an archive
        # whose manifest no other command would accept.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            data = builder.build(self.plan)
            payload = json.dumps(data).replace('"exportType"', '"exportType": "x", "exportType"', 1)
            archive_path = root / "dup.lumecard"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr(builder.ARCHIVE_MANIFEST, payload)
            with self.assertRaisesRegex(ValueError, "duplicate JSON key"):
                builder.inspect_archive(archive_path)

    def test_cli_missing_parent_directory_names_the_fix(self):
        # A raw errno with a random `.part` filename gave the author nothing actionable.
        command = [sys.executable, str(ROOT / "scripts/build_deck.py")]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            result = subprocess.run(command + ["package", str(self.write_plan(root, "p.json")),
                                               str(root / "no-such-dir" / "a.lumecard")],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertIn("parent directory does not exist", result.stderr)
            self.assertNotIn(".part", result.stderr)

    def test_installer_expands_tilde_in_dest_and_project(self):
        # build_deck.py expands `~`, but install.py used the literal path, creating a directory
        # named `~` next to the repo for a runbook that shares one path convention.
        command = [sys.executable, str(ROOT / "scripts/install.py")]
        result = subprocess.run(command + ["--agent", "custom", "--dest", "~/skills", "--dry-run"],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(str(Path.home() / "skills"), result.stdout)
        self.assertNotIn(str(ROOT / "~"), result.stdout)

    def test_load_rejects_oversized_json(self):
        # A 2 GB manifest was read wholly into memory and decoded to a second copy, unlike the
        # 64 MiB cap already applied to an archive's manifest. An untrusted plan could OOM the
        # agent. Use a patched small cap so the test stays cheap while exercising the comparison.
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "huge.json"
            path.write_text('{"x":"' + "A" * 5000 + '"}', encoding="utf-8")
            with unittest.mock.patch.object(builder, "MAX_MANIFEST_BYTES", 100):
                with self.assertRaisesRegex(ValueError, "over the"):
                    builder.load(path)

    def test_html_extraction_is_linear_in_tag_count(self):
        # implied_end() scanned the entire open-tag stack for every start tag, so 16k nested divs
        # took ~49s and a 25 MB document could hang the agent for hours below the byte cap.
        spec = importlib.util.spec_from_file_location(
            "extract_document_for_test", ROOT / "scripts/extract_document.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        for count in (2000, 8000):
            parser = module.HTMLText()
            started = time.monotonic()
            parser.feed("<div>" * count)
            parser.close()
            self.assertLess(time.monotonic() - started, 2.0,
                            "HTML extraction must stay linear for %d tags" % count)
        # Output semantics unchanged for implicit closes.
        parser = module.HTMLText()
        parser.feed("<ul><li>a<li>b</ul><dl><dt>t<dd>d</dl>")
        parser.close()
        self.assertEqual("".join(parser.parts), "\n\na\n\nb\n\n\nt\n\nd\n")

    def test_install_and_package_print_non_gbk_paths_without_failing(self):
        # A Chinese Windows console uses strict GBK stdout; printing an emoji path raised after
        # install() had already written to disk, reporting failure for a successful operation.
        env = dict(os.environ, PYTHONIOENCODING="gbk", PYTHONUTF8="0")
        # Anchor under the temp dir so the path is absolute on every platform. A Windows-style
        # "D:/..." literal is relative on POSIX, resolved inside the source tree, and rejected by
        # the overlap guard long before the encoding behaviour under test is exercised.
        dest = Path(tempfile.gettempdir()) / "测试😀" / "skills"
        command = [sys.executable, str(ROOT / "scripts/install.py"),
                   "--agent", "custom", "--dest", str(dest), "--dry-run"]
        # Capture bytes: the test harness itself decodes text=True with the parent locale,
        # which is UTF-8 here and cannot read GBK output.
        result = subprocess.run(command, capture_output=True, env=env)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(b"Would install", result.stdout)

    def test_timestamp_rejects_impossible_dates_and_overprecision(self):
        # Instant.parse in the importer accepts at most nine fractional digits and real calendar
        # dates. Python's fromisoformat truncates longer fractions, so an over-precise timestamp
        # passed here and then rolled back the entire import in the app.
        for value in ("2026-13-45T99:99:99Z", "2026-01-01T00:00:00+02:00",
                      "2026-11-25T12:00:00.123456789012Z"):
            data = copy.deepcopy(self.data)
            data["cards"][0]["createdAt"] = value
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "createdAt"):
                builder.validate(data)
        data = copy.deepcopy(self.data)
        data["exportDate"] = "2026-11-25T12:00:00.123456789012Z"
        with self.assertRaisesRegex(ValueError, "exportDate"):
            builder.validate(data)

    def test_https_url_matches_java_uri_acceptance(self):
        # `\S` matched NUL and Java rejected it (broken image), but an ASCII-only rule would also
        # reject the IDN hosts and Unicode paths the app's URI constructor accepts. Mirror the JDK.
        for url in ("https://example.com/画像.png", "https://例え.テスト/画像.png",
                    "https://x/a.png#f?g=h"):
            with self.subTest(url=url):
                data = copy.deepcopy(self.data)
                data["cards"][0]["media"] = [{"id": "i", "type": "IMAGE", "filePath": url}]
                builder.validate(data)
        for url in ("https://x/a.png\x00", "https://x/a.png<bad>", "https://x/a.png|bad",
                    "https://x/a.png\"q", "https://x/a.png{bad}", "https://x/a.png\\bad",
                    "https://x/a.png^bad", "https://x/a.png`bad", "https://x/a b.png"):
            with self.subTest(url=url), self.assertRaisesRegex(ValueError, "malformed https URL"):
                data = copy.deepcopy(self.data)
                data["cards"][0]["media"] = [{"id": "i", "type": "IMAGE", "filePath": url}]
                builder.validate(data)

    def test_utf16_path_length_and_all_reserved_superscripts(self):
        # The app counts UTF-16 code units, so an emoji costs two. ASCII-only fixtures never
        # exercise that; nor do they cover every superscript reserved name variant.
        self.assertEqual(builder.utf16_len("😀"), 2)
        for name in ("😀" * 130, "😀" * 128 + "/x.png",
                     "COM².png", "COM³.png", "LPT¹.png", "LPT².png", "LPT³.png"):
            with self.subTest(name=name[:20]):
                self.assertFalse(builder.safe_relative_path(name))
        # 254 UTF-16 units split across two segments, each under the 128-unit segment cap.
        self.assertTrue(builder.safe_relative_path("😀" * 63 + "/" + "😀" * 63))

    def test_complete_image_check_covers_every_supported_format(self):
        # A byte-count floor is too weak: a signature + header + filler can reach any floor and
        # still be undecodable. The structural check needs each format's real terminator / chunk,
        # and each branch must be exercised or weakening it survives the suite.
        prefixes = {
            "png": b"\x89PNG\r\n\x1a\n\x00\x00\x00\x0dIHDR",
            "jpg": b"\xff\xd8\xff\xe0\x00\x10JFIF",
            "gif": b"GIF89a" + b"\0" * 29,
            "webp": b"RIFF" + (24).to_bytes(4, "little") + b"WEBP" + b"\0" * 16,
            "bmp": b"BM" + (40).to_bytes(4, "little") + b"\0" * 36,
        }
        for fmt, prefix in prefixes.items():
            with self.subTest(fmt=fmt):
                self.assertIsNotNone(builder.sniff_image(prefix))
                self.assertFalse(builder.image_is_complete(prefix, fmt))
                with tempfile.TemporaryDirectory() as tmp:
                    path = Path(tmp) / ("x." + fmt)
                    path.write_bytes(prefix)
                    with self.assertRaisesRegex(ValueError, "truncated image"):
                        builder.collect_image(str(path), "card.media[0]")
        # A real PNG passes the same check.
        with tempfile.TemporaryDirectory() as tmp:
            make_png(Path(tmp) / "real.png", 2, 2, (1, 2, 3))
            data = (Path(tmp) / "real.png").read_bytes()
            self.assertTrue(builder.image_is_complete(data, "png"))
            builder.collect_image(str(Path(tmp) / "real.png"), "card.media[0]")

    def test_jpeg_fill_bytes_and_java_uri_special_characters(self):
        # JPEG allows 0xFF fill bytes before a marker; the old single-byte check rejected a valid
        # decodable file. Java's URI also rejects [] and malformed % escapes.
        self.assertIsNotNone(builder.sniff_image(b"\xff\xd8\xff\xff\xe0\x00\x10JFIF"))
        for url in ("https://x/a[1].png", "https://x/a%zz.png", "https://x/a%2.png", "https://x/%"):
            with self.subTest(url=url), self.assertRaisesRegex(ValueError, "malformed https URL"):
                data = copy.deepcopy(self.data)
                data["cards"][0]["media"] = [{"id": "i", "type": "IMAGE", "filePath": url}]
                builder.validate(data)

    def test_non_finite_json_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "invalid.json"
            for value in ("NaN", "Infinity", "-Infinity"):
                file.write_text('{"value":' + value + '}', encoding="utf-8")
                with self.subTest(value=value), self.assertRaisesRegex(ValueError, "non-finite"):
                    builder.load(file)

    def test_stdlib_only(self):
        result = subprocess.run([sys.executable, "-S", str(ROOT / "scripts/build_deck.py"), "validate", str(ROOT / "examples/import.json")], capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)


class InstallationTests(unittest.TestCase):
    def test_installed_adapter_can_uninstall_itself(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / installer.NAME
            installer.install(target)
            result = subprocess.run([sys.executable, str(target / "scripts/install.py"),
                                     "--agent", "custom", "--dest", tmp, "--uninstall"], capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertFalse(target.exists())

    def test_install_refuses_overwrite_and_preserves_modified(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / installer.NAME
            installer.install(target)
            with self.assertRaises(ValueError):
                installer.install(target)
            file = target / "README.md"
            file.write_text("user edit", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "modified"):
                installer.uninstall(target)
            self.assertTrue(file.exists())

    def test_install_uninstall_and_unrelated_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            sibling = Path(tmp) / "unrelated.txt"
            sibling.write_text("keep", encoding="utf-8")
            target = Path(tmp) / installer.NAME
            installer.install(target)
            installer.uninstall(target)
            self.assertFalse(target.exists())
            self.assertEqual(sibling.read_text(), "keep")

    def test_unmanaged_directory_not_removed(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                installer.uninstall(Path(tmp))

    def test_native_project_destinations(self):
        with tempfile.TemporaryDirectory() as tmp:
            for agent, (_, local) in installer.PATHS.items():
                if local:
                    self.assertEqual(installer.destination(agent, "project", tmp), Path(tmp) / local / installer.NAME)
                else:
                    with self.assertRaises(ValueError):
                        installer.destination(agent, "project", tmp)

    @unittest.skipIf(os.name == "nt", "symlink creation requires privileges on some Windows runners")
    def test_symlink_destination_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            link = Path(tmp) / "alias"
            link.symlink_to(Path(tmp), target_is_directory=True)
            with self.assertRaises(ValueError):
                installer.destination("custom", "user", dest=str(link))

    @unittest.skipUnless(shutil.which("sh"), "POSIX shell unavailable")
    def test_shell_wrapper_and_missing_python(self):
        with tempfile.TemporaryDirectory(prefix="skill install ") as tmp:
            args = ["sh", str(ROOT / "install.sh"), "--agent", "claude", "--scope", "project", "--project", tmp]
            result = subprocess.run(args, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(subprocess.run(args + ["--uninstall"], capture_output=True).returncode, 0)
            if os.name == "nt":
                return  # Missing-Python simulation below uses POSIX symlinks.
            # Empty PATH retains only tools needed to locate the wrapper; no Python.
            tools = Path(tmp) / "bin"
            tools.mkdir()
            for name in ("dirname",):
                os.symlink(shutil.which(name), tools / name)
            result = subprocess.run([shutil.which("sh"), str(ROOT / "install.sh"), "--agent", "codex"], env={**os.environ, "PATH": str(tools)}, capture_output=True)
            self.assertEqual(result.returncode, 127, result.stderr)
            self.assertIn(b"manually", result.stderr)

    @unittest.skipUnless(shutil.which("pwsh"), "PowerShell unavailable; NOT verified")
    def test_powershell_wrapper(self):
        with tempfile.TemporaryDirectory() as tmp:
            args = ["pwsh", "-NoProfile", "-File", str(ROOT / "install.ps1"), "-Agent", "codex", "-Scope", "project", "-Project", tmp]
            result = subprocess.run(args, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(subprocess.run(args + ["-Uninstall"], capture_output=True).returncode, 0)


class StructureTests(unittest.TestCase):
    def test_release_build_stdout_is_a_single_json_object(self):
        # package.py prints the archive path on stdout. Letting that through made `release_skill
        # build` exit 0 while its stdout started with a path, so an agent parsing stdout got a
        # JSONDecodeError and could not read the checksum it needed for the release workflow.
        with tempfile.TemporaryDirectory() as tmp:
            result = subprocess.run([sys.executable, str(ROOT / "scripts/release_skill.py"),
                                     "build", "--output-dir", str(Path(tmp) / "dist")],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            info = json.loads(result.stdout)
            # Derive from SKILL.md instead of hardcoding: a literal here has to be edited on
            # every version bump and fails the CI matrix for an unrelated reason.
            expected = module("release_skill").metadata(ROOT)["version"]
            self.assertEqual(info["version"], expected)
            self.assertTrue((Path(tmp) / "dist" / info["checksum"]).exists())

    def test_release_build_reports_packaging_failure_as_one_error_line(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / ROOT.name
            shutil.copytree(ROOT, source, ignore=shutil.ignore_patterns("__pycache__", "dist"))
            (source / "examples" / "plan.json").unlink()  # a payload file is now missing
            result = subprocess.run([sys.executable, str(source / "scripts/release_skill.py"),
                                     "build", "--output-dir", str(Path(tmp) / "out")],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertNotIn("Traceback", result.stderr)
            self.assertEqual(len(result.stderr.strip().splitlines()), 1)
            self.assertIn("distribution file missing", result.stderr)

    def test_app_contract_fixtures_are_generated_and_classified(self):
        with tempfile.TemporaryDirectory() as tmp:
            command = [sys.executable, "-S", str(ROOT / "tests/write_contract_fixtures.py"), tmp]
            result = subprocess.run(command, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            cases = builder.load(Path(tmp) / "manifest.json")["cases"]
            self.assertEqual(len(cases), 11)
            self.assertEqual(sum(case["accepted"] for case in cases), 4)
            for case in cases:
                data = builder.load(Path(tmp) / case["file"])
                if case["accepted"]:
                    self.assertEqual(builder.validate(data)["cards"], 5)
                else:
                    with self.assertRaises(ValueError):
                        builder.validate(data)
            original = (Path(tmp) / "manifest.json").read_bytes()
            self.assertNotEqual(subprocess.run(command, capture_output=True).returncode, 0)
            self.assertEqual((Path(tmp) / "manifest.json").read_bytes(), original)

    def test_distribution_excludes_private_work_from_zip_and_install(self):
        import zipfile
        from unittest import mock
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            source = tmp / ROOT.name
            shutil.copytree(ROOT, source, ignore=shutil.ignore_patterns("__pycache__", "dist"))
            private = ("private-extracted.json", "plan.json", "references/private.md", "scripts/secret.py")
            for name in private:
                (source / name).write_text("PRIVATE", encoding="utf-8")
            archive = tmp / "skill.zip"
            subprocess.run([sys.executable, str(source / "scripts/package.py"), str(archive)], check=True, capture_output=True)
            with zipfile.ZipFile(archive) as bundle:
                self.assertTrue(all(source.name + "/" + name not in bundle.namelist() for name in private))
            target = tmp / "installed" / ROOT.name
            with mock.patch.object(installer, "ROOT", source):
                installer.install(target)
            self.assertTrue(all(not (target / name).exists() for name in private))
            installer.uninstall(target)

    @unittest.skipIf(os.name == "nt", "symlink creation may require privileges")
    def test_missing_or_linked_payload_fails_before_creating_archive(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / ROOT.name
            shutil.copytree(ROOT, source, ignore=shutil.ignore_patterns("__pycache__", "dist"))
            file = source / "examples/notes.md"
            file.unlink()
            archive = Path(tmp) / "skill.zip"
            for linked in (False, True):
                if linked:
                    file.symlink_to(ROOT / "examples/notes.md")
                result = subprocess.run([sys.executable, str(source / "scripts/package.py"), str(archive)], capture_output=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(archive.exists())

    def test_zip_contains_runnable_standalone_skill(self):
        import zipfile
        with tempfile.TemporaryDirectory() as tmp:
            archive = Path(tmp) / "skill.zip"
            result = subprocess.run([sys.executable, str(ROOT / "scripts/package.py"), str(archive)], capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            with zipfile.ZipFile(archive) as bundle:
                names = bundle.namelist()
                self.assertIn(ROOT.name + "/SKILL.md", names)
                self.assertTrue(all(name.startswith(ROOT.name + "/") for name in names))
                self.assertFalse(any("__pycache__" in name or name.endswith(".pyc") for name in names))
                bundle.extractall(Path(tmp) / "unpacked")
            extracted = Path(tmp) / "unpacked" / ROOT.name
            result = subprocess.run([sys.executable, "-S", str(extracted / "scripts/build_deck.py"), "validate", str(extracted / "examples/import.json")], capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_frontmatter_portable_subset(self):
        # This restricted header uses plain YAML scalars and a string-to-string map.
        source = (ROOT / "SKILL.md").read_text(encoding="utf-8")
        self.assertTrue(source.startswith("---\n"))
        header = source.split("---\n", 2)[1]
        self.assertIn("name: " + ROOT.name, header)
        self.assertLess(len(source.splitlines()), 500)
        self.assertNotIn("allowed-tools:", header)

    def test_references_exist(self):
        import re
        for link in re.findall(r"\]\(([^)]+)\)", (ROOT / "SKILL.md").read_text(encoding="utf-8")):
            self.assertTrue((ROOT / link).is_file(), link)
        for name in ("README.md", "LICENSE", "install.sh", "install.ps1"):
            self.assertTrue((ROOT / name).is_file())


if __name__ == "__main__":
    unittest.main()
