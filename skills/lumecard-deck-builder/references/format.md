# LumeCard content contract (skill 0.4.0; serialization unchanged since 0.1.0)

Audited against LumeCardWithOpenCode commit `1cc6e56` (repository HEAD when work began;
verify with Git history if the app changes), export version `1.0.0`, schema `1`.
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
| `filePath` | yes | media-relative file name (no `..`), `file://`, `content://`, absolute path, or an **https** URL. `http://` is rejected |
| `caption` | no | short optional caption (default `""`) |
| `mimeType` | no | informational string (default `""`) |
| `size` | no | non-negative integer bytes (default `0`) |

Rules:
- **Never invent image URLs or file names.** Only use paths/URLs actually supplied in
  the source material or by the user. If no image is provided, omit `media` entirely.
- Images are independent blocks rendered below the card text. Do **not** put
  `![alt](...)` or `<img>` inside `front`/`back`: the CLOZE regex, choice option
  splitting and Markdown/RichText renderers would be polluted.
- `mediaTarget` is **card-level** (one value for all images on the card), stored in
  `metadata["mediaTarget"]`. `both` (default) shows on both faces, `front` only on
  the front, `back` only on the back. Per-image targets are not supported.
- Plain-text cards with `media: []` behave exactly as before.

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
`caption` (all with defaults in the app model, so older JSON without them still loads).
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

## Image display and viewer behaviour (0.4.0)

How images appear in the app — keep documentation and generated content aligned with this:

- **Placement**: images are separate blocks below the front/back text of a card, never
  inline in the text. Filtering by `metadata["mediaTarget"]` decides which face shows
  them; cards without images take exactly the old rendering path.
- **Adaptive scaling**: each image scales to the available card width with
  `ContentScale.Fit`, keeps its original aspect ratio (explicit `aspectRatio`), is
  height-capped (≤45% of the available height, max 320dp) and never crops, stretches
  or overflows. Loading and failure states use fixed-size placeholders to avoid layout
  jumps. Multiple images stack vertically.
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
