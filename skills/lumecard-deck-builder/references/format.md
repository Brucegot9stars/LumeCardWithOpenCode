# LumeCard content contract (skill 0.4.5; serialization unchanged since 0.1.0)

Audited against LumeCardWithOpenCode commit `1cc6e56` and re-verified against the archive
export/import rework (app 0.27.0, branch `feat/card-image-support`, 2026-11-23) plus the
skill-side archive writer (0.4.4); verify with Git history if the app changes. Export
version `1.0.0`, schema `1`.
Evidence: `shared/src/commonMain/kotlin/com/lumecard/shared/data/ExportManager.kt`
(`ShareExport`, `ExportKnowledgeBase`, `ExportDeck`, `ExportCard`, `importData`);
`shared/src/commonMain/kotlin/com/lumecard/shared/model/Models.kt` (`Card`, `Media`);
`composeApp/src/commonMain/kotlin/com/lumecard/app/platform/CardImageLoader.kt`;
`composeApp/src/commonMain/kotlin/com/lumecard/app/ui/screens/study/CardComponents.kt`
and `StudyScreen.kt`; `font/FontInitializer.kt` under the same app Kotlin package.
This helper validates a strict **new content subset**, not every historical app backup.

## Agent-authored input plan

```json
{"knowledgeBases":[{"name":"Biology","decks":[{"name":"Cells","cards":[
  {"front":"What contains a cell's genetic information?","back":"DNA.","source":"Notes §1"},
  {"front":"DNA stores {{c1::genetic information}}."},
  {"front":"Which is a nucleic acid?","options":[{"text":"DNA","correct":true},{"text":"Glucose","correct":false}]},
  {"front":"Identify this organelle","back":"Mitochondrion",
   "media":[{"id":"img_mito","filePath":"a1b2c3.png","caption":"Mitochondrion diagram"}],
   "mediaTarget":"both"}
]}]}]}
```

Optional card fields: `title` (string, up to 200 characters, shown in the card list),
`type`, `tags` (string array), `source` (locator),
`bidirectional` (true only for safe two-way recall), `style` with boolean `hcenter`/
`vcenter`, integer `fontSize` 12–120, built-in `fontFamily` ID, `options` (array of
`{text, correct}` for `MULTIPLE_CHOICE`, converted into the `back` option lines), plus
the optional image fields `media` (array) and `mediaTarget` (`both`/`front`/`back`,
default `both`). This plan is NOT an import file. Feed it to the builder. Unknown
optional plan fields are not copied.

### Image input format (`media`)

Each `media` item is an object:

| field | required | rule |
|---|---|---|
| `id` | yes | non-empty string (uniqueness is not enforced) |
| `type` | yes | must be `"IMAGE"` |
| `filePath` | yes* | an **https** URL, or a media-relative file name passing the strict rules below. `file://`, `content://` and absolute paths are rejected even though the app can store them — this skill only produces share files, and only these two forms survive an archive round trip. `http://` is rejected |
| `sourcePath` | yes* | **plan-only**: path to a local image to embed. Use *instead of* `filePath`; the `package` action reads the file, derives `filePath` and stores the bytes. Only `build` → JSON cannot honour it. Required for any local image: a plan that names one by `filePath` alone is rejected, because nothing would carry its bytes into the archive |
| `caption` | no | short optional caption (default `""`) |
| `mimeType` | no | informational string (default `""`); filled from the real file when `sourcePath` is used |
| `size` | no | non-negative integer bytes (default `0`); filled from the real file when `sourcePath` is used |
| `alignment` | no | `start` / `center` / `end` (default `center`); horizontal placement of this one image |
| `compress` | no | boolean (default `true`); whether an oversized import is downscaled |

\* exactly one of `filePath` / `sourcePath` per item.

Rules:
- **Never invent image URLs or file names.** Only use paths/URLs actually supplied in
  the source material or by the user. If no image is provided, omit `media` entirely.
- When a **local** image was supplied, reference it with `sourcePath` and deliver the
  result with the `package` action — see [Delivering images](#delivering-images) below.
  Never guess a `filePath` for a local picture: a name the archive does not contain
  imports as a broken card.
- Relative names must pass the app's shared `MediaPathRules`, which the importer, the
  exporter's validator and the archive writer all enforce: `/`-separated, no empty/`.`/`..`
  segments, no leading `/`, no backslash, no drive letter, no `:` anywhere, no control
  characters, no segment ending in a dot or space, no Windows-reserved segment (`CON`,
  `PRN`, `AUX`, `NUL`, `COM1-9`/`LPT1-9`, including the superscript spellings), at most 128
  characters per segment and 255 per path. A weaker name imports today but fails the deck's
  next export, so the builder rejects it up front.
- Images are independent blocks rendered outside the card text. Do **not** put
  `![alt](...)` or `<img>` inside `front`/`back`: the CLOZE regex, choice option
  splitting and Markdown/RichText renderers would be polluted.
- `mediaTarget` is **card-level** (one value for all images on the card), stored in
  `metadata["mediaTarget"]`. `both` (default) shows on both faces, `front` only on
  the front, `back` only on the back. Per-image targets are not supported.
- `alignment` is **per-image**: one card may mix left-, center- and right-aligned
  images. Unknown values are rejected by the validator; the app falls back to
  `center` at read time so hand-edited cards never lose the image.
- `compress` records the author's import choice. The app applies it only while copying
  a local file in — images already stored are never re-encoded behind the user's back.
- Plain-text cards with `media: []` behave exactly as before.

## Delivering images

Two ways to get pictures onto cards. Pick by where the bytes live:

| Situation | Plan field | Output | Command |
|---|---|---|---|
| Image is a hosted **https** URL | `filePath` | plain `.json` | `build_deck.py build` |
| Image is a **local file** the user provided | `sourcePath` | `.lumecard` archive | `build_deck.py package` |
| No images at all | — | plain `.json` | `build_deck.py build` |

```sh
python3 scripts/build_deck.py package plan.json deck.lumecard
python3 scripts/build_deck.py inspect deck.lumecard     # re-check what will be imported
```

`package` derives `filePath` itself: the media name is the **SHA-1 of the file bytes** plus the
extension. The hash half matches what the app gives a copied media file (`copyFileToMedia` in
`Platform.desktop.kt` / `Platform.android.kt`), which is what makes the archive's
`media/<filePath>` entry and the card reference agree, so importing is an identity mapping and the
image renders immediately. Identical images referenced from several cards collapse onto one entry.

The extension half is derived from the **content**, while the app keeps the source file's own
extension: `photo.jpeg` becomes `<sha1>.jpg` here but `<sha1>.jpeg` when the app copies it. Both
resolve, because every decoder in the app sniffs bytes rather than trusting the name — the only
visible effect is that the same picture can end up stored twice under two extensions if it also
passed through the app.

Accepted inputs are PNG, JPEG, GIF, WebP and BMP, identified by **content**, not by extension
(a `.png` that holds JPEG bytes is stored as `.jpg`). **SVG is rejected on purpose**: neither Skia
Codec (Desktop) nor `BitmapFactory` (Android) decodes it, so an embedded SVG would show the app's
permanent failure placeholder. Pass the picture through a raster converter first, or host it and
use an `https` URL.

Hard rules for `package`:

- Output must end in `.lumecard` and must not already exist — nothing is overwritten.
- Every referenced local file must exist, be a regular file (symbolic links, FIFOs and device
  nodes are refused), be non-empty, be at most **64 MiB**, and sniff as an image. 64 MiB is the
  importer's per-file limit, not a display limit: a larger image would be silently skipped at
  import and its card would show a broken picture forever. Total media must stay under 1 GiB and
  under 10,000 files, matching the importer's other two budgets.
- A plan must name every local image with `sourcePath`. A bare relative `filePath` names a file the
  build cannot see, so `package` rejects it rather than emit an archive whose manifest promises
  images that are not inside it.
- An `https` URL keeps working in the same plan; only `sourcePath` items are embedded.
- `build` refuses a plan that uses `sourcePath` (a bare JSON cannot carry the bytes) and says so.

## Export archive (`.lumecard`)

Both the app and this skill produce this format, and the app accepts it directly. The app emits it
when exporting a knowledge base that references local media; `build_deck.py package` emits it when a
plan embeds local images.

```
lumecard.json      the identical share/backup JSON documented below
media/<filePath>   one entry per referenced local media file
```

- **Flat media directory**: entries sit directly under `media/` with the same relative path the app
  uses internally (`media/a1b2c3.png`), so importing is an identity mapping. File names are content
  SHA-1, so classification subdirectories (`media/images/…`) would add no clarity. Readers accept
  any sub-path under `media/`, which leaves room for a future layout without a format bump.
- **Content, not extension**: import sniffs the leading bytes, so a `.lumecard` renamed to `.zip`
  still works, and a plain `.json` still imports as before.
- **Manifest first**: the app reads entry zero as the payload; `package` writes it first and
  `inspect` refuses an archive where it is not.
- **Reproducible entries, not reproducible files**: entries carry a fixed `1980-01-01` timestamp,
  so the zip structure is stable across runs. The record IDs are fresh UUIDs and `exportDate` is
  the current time, so two builds of the same plan differ in bytes by design — never diff or
  hash-compare two archives.
- **Absent media is reported, not fatal**: images referenced but already deleted from disk are
  counted and omitted; a knowledge base with no local media still exports as plain `.json`.
- **Collisions are resolved, never overwritten**: if the importing device already has a different
  file under the same name, the incoming file is stored as `<stem>_<shorthash>.<ext>` and the card
  reference is rewritten to match. With content SHA-1 names this basically cannot happen unless the
  archive was hand-made.

## Import entry points (app 0.27.0)

Files produced by this skill carry no `objectScope` marker, so the app classifies them as **full
share exports**. Import them through the full-import button in Settings, which accepts any share
file. The separate scoped object-import screen only accepts files the app itself exported (they
carry `objectScope`) and reports full files as rejected — tell users to use the Settings import,
not that screen.

Both entries accept the same media fields. Media is only ever copied to disk on the **archive**
path: importing a plain `.json` inserts the cards but copies no files, so a `filePath` that only
exists on the authoring machine will show the failure placeholder. That is exactly why local
pictures must go through `package`.

## Output JSON

The app's hierarchy is represented as flat arrays, not nested entities:
```json
{
  "exportType":"share","version":"1.0.0","schemaVersion":1,
  "exportDate":"2026-11-04T00:00:00Z",
  "knowledgeBases":[{"id":"kb_unique","name":"Biology","icon":"📁","createdAt":"2026-11-04T00:00:00Z","updatedAt":"2026-11-04T00:00:00Z"}],
  "decks":[{"id":"deck_unique","knowledgeBaseId":"kb_unique","name":"Cells","description":null,"color":"#4CAF50","icon":"📚","createdAt":"2026-11-04T00:00:00Z","updatedAt":"2026-11-04T00:00:00Z"}],
  "cards":[{"id":"card_unique","deckId":"deck_unique","type":"BASIC","front":"What stores genetic information?","back":"DNA.","tags":[],"media":[{"id":"img_unique","type":"IMAGE","filePath":"a1b2c3.png","mimeType":"","size":0,"caption":"Cell diagram"}],"metadata":{"hcenter":"true","vcenter":"true","fontSize":"24","fontFamily":"sans-serif","mediaTarget":"both"},"createdAt":"2026-11-04T00:00:00Z","updatedAt":"2026-11-04T00:00:00Z"}]
}
```

Use new UUIDs for real generated IDs. Dates are UTC ISO 8601 strings with a `Z` suffix
and at most nine fractional-second digits — the app parses them with `Instant.parse`, which
rejects more. `description` on a deck is required by serialization even when null; `tags`
is required on cards. `title` is an optional card string (up to 200 characters) shown in the
card list; omit it or use `""` when absent. CLOZE cards may carry `back: ""` because the app
derives both faces from `front`.
Metadata is `Map<String,String>`: booleans and font size MUST be strings in output.
`media` items always carry `id`/`type`/`filePath` plus optional `mimeType`/`size`/
`caption`/`alignment`/`compress` (all with defaults in the app model, so older JSON
without them still loads).
`mediaTarget` appears in `metadata` only when the card has images.
Never include private learning plans, settings, review logs, scheduling or tombstones.
The strict validator accepts only documented share/record fields; plan-only fields such
as `options`, `style` and `bidirectional` do not belong in an export. `deviceId`, if
present, must be a string or null. Record versions must be integers 1..9223372036854775807
(Kotlin Long). `backupFormatVersion` is forbidden in this new-content subset.

## Card syntax and renderer limits

- `BASIC`: plain question and answer. `REVERSED`: safe bidirectional pairs; app flips faces.
- `CLOZE`: front contains `{{c1::answer}}` or `{{c1::answer::hint}}`; all blanks are
  hidden/revealed together in the current renderer, not separate Anki-style cards.
  Back contains extracted answers but the study renderer reveals the front's blanks.
- `MULTIPLE_CHOICE`: front is the question; back contains one option per line with
  `+` at the start of each correct option (leading spaces are trimmed first, matching the
  study renderer). Multiple correct answers are supported. Kotlin's `Char.isWhitespace()` also
  treats NBSP, figure space and narrow no-break space as blank, so such a line is ignored just
  like an ordinary empty line and the `back` text is left unchanged. Use plain ASCII spaces for
  indentation. Never append explanation paragraphs to back: they would become extra options.
  Export option separators must be LF (`\n`), matching the app renderer. The builder
  normalizes CRLF/CR in a plan's choice back; validation never rewrites an existing file.
  Individual option text cannot contain line breaks, including Unicode separators.
- `MARKDOWN`: fenced code, lists, headings or tables; preserve valid Markdown syntax.
  Renderer currently applies its own font/alignment settings; stored metadata does
  not guarantee a visible change for this card type.
- `RICH_TEXT`/`AI_GENERATED` exist in the app, but this MVP does not generate them.
  Rich HTML requires separate sanitizer/renderer coverage and is not needed here.

Horizontal/vertical centering is honored for BASIC/REVERSED/RICH_TEXT; cloze and choice
use dedicated layouts. BASIC/REVERSED honor font IDs; other renderers may use defaults.
Built-in IDs: `default`, `serif`, `sans-serif`, `monospace`, `cursive`. They are portable
generic families, not bundled font files; exact glyph appearance varies by OS.
Do not promise custom font availability. Request installed font IDs before using them
in manually authored exports; the helper intentionally accepts built-in IDs only.

## Image display and viewer behaviour (0.4.1, per-image flags in 0.4.2)

How images appear in the app — keep documentation and generated content aligned with this:

- **Placement**: images are separate blocks below the front/back text of a card, never
  inline in the text. Filtering by `metadata["mediaTarget"]` decides which face shows
  them; cards without images take exactly the old rendering path.
- **Sizing**: source pixels map to physical screen pixels 1:1. An image narrower than
  half the available width keeps its natural size — it is **not** stretched to fill the
  row. A wider image is capped at half the width. A height cap (≤45% of available height,
  max 320dp) only applies to very tall images and shrinks both axes together, so the
  aspect ratio is never distorted. Loading and failure states use fixed-size placeholders
  to avoid layout jumps. Multiple images stack vertically.
- **Alignment**: each image honours its own `alignment` (`start` / `center` / `end`),
  so one card can place images left, centred and right. The caption follows the same
  alignment.
- **Sources**: media-relative names resolve under the app media root (path escape is
  rejected); `file://`/`content://`/absolute paths load through the platform resolver;
  `https` URLs are downloaded with a size cap and cached (memory LRU + disk). `http`
  URLs are rejected and show a stable failure placeholder; Android also blocks cleartext.
  Decodable formats are PNG, JPEG, GIF, WebP and BMP; SVG is **not** decoded and shows the
  failure placeholder, which is why the builder refuses to embed it.
- **Viewer**: tapping an image opens an independent fullscreen dialog. It reuses the
  already-decoded bitmap (no second download/decode) and supports pinch zoom, `+`/`−`
  buttons, fit-to-screen reset, panning (scale clamped to 1–5x), optional caption and
  close via button / back / background click.
- **Flicker-free transition**: 180ms fade + scale in/out; the dialog is a separate layer
  outside the card's 3D flip and `scaleX=-1f` mirror, so no mirroring, no white flash and
  no subtree rebuild on card flip. Study gestures (flip / swipe) are suppressed while
  the viewer is open, and tapping an image never flips the card.
- **Limits**: no image occlusion, no inline `<img>`/Markdown images, no CSV with images,
  no per-image face targets. These are out of scope for this version.
