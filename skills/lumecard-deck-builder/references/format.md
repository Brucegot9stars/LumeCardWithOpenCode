# LumeCard content contract (skill 0.4.3; serialization unchanged since 0.1.0)

Audited against LumeCardWithOpenCode commit `1cc6e56` and re-verified against the archive
export/import rework (app 0.27.0, branch `feat/card-image-support`, 2026-11-23); verify with
Git history if the app changes. Export version `1.0.0`, schema `1`.
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

Optional card fields: `type`, `tags` (string array), `source` (locator),
`bidirectional` (true only for safe two-way recall), `style` with boolean `hcenter`/
`vcenter`, integer `fontSize` 12–120, built-in `fontFamily` ID, plus the optional
image fields `media` (array) and `mediaTarget` (`both`/`front`/`back`, default `both`).
This plan is NOT an import file. Feed it to the builder. Unknown optional plan fields
are not copied.

### Image input format (`media`)

Each `media` item is an object:

| field | required | rule |
|---|---|---|
| `id` | yes | non-empty unique string within the card |
| `type` | yes | must be `"IMAGE"` |
| `filePath` | yes | an **https** URL, or a media-relative file name passing the strict rules below (also accepts `file://`, `content://` and absolute paths the app itself writes). `http://` is rejected |
| `caption` | no | short optional caption (default `""`) |
| `mimeType` | no | informational string (default `""`) |
| `size` | no | non-negative integer bytes (default `0`) |
| `alignment` | no | `start` / `center` / `end` (default `center`); horizontal placement of this one image |
| `compress` | no | boolean (default `true`); whether an oversized import is downscaled |

Rules:
- **Never invent image URLs or file names.** Only use paths/URLs actually supplied in
  the source material or by the user. If no image is provided, omit `media` entirely.
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

## Export archive (`.lumecard`)

This skill emits plain JSON, and the app accepts it directly. When the **app** exports a knowledge
base that references local media files, it produces a `.lumecard` archive instead of a bare `.json`.
Understanding this helps explain to users what they are importing:

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
- **Absent media is reported, not fatal**: images referenced but already deleted from disk are
  counted and omitted; a knowledge base with no local media still exports as plain `.json`.
- **Collisions are resolved, never overwritten**: if the importing device already has a different
  file under the same name, the incoming file is stored as `<stem>_<shorthash>.<ext>` and the card
  reference is rewritten to match.

This skill does not create archives — emit the JSON and let the app package it.

## Import entry points (app 0.27.0)

JSON produced by this skill carries no `objectScope` marker, so the app classifies it as a
**full share export**. It imports through the full-import button in Settings, which accepts
any share file. The separate scoped object-import screen only accepts files the app itself
exported (they carry `objectScope`) and reports full files as rejected — tell users to use
the Settings import, not that screen. Both entries accept the same media fields.

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

Use new UUIDs for real generated IDs. Dates are UTC ISO 8601 strings. `description`
on a deck is required by serialization even when null; `tags` is required on cards.
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
  `+` at column zero for each correct option. Multiple correct answers are supported.
  Never append explanation paragraphs to back: they would become extra options.
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
