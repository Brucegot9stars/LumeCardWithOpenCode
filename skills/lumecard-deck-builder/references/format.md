# LumeCard content contract (skill 0.2.0; unchanged since 0.1.0)

Audited against LumeCardWithOpenCode commit `1cc6e56` (repository HEAD when work began;
verify with Git history if the app changes), export version `1.0.0`, schema `1`.
Evidence: `shared/src/commonMain/kotlin/com/lumecard/shared/data/ExportManager.kt`
(`ShareExport`, `ExportKnowledgeBase`, `ExportDeck`, `ExportCard`, `importData`);
`composeApp/src/commonMain/kotlin/com/lumecard/app/ui/screens/study/CardComponents.kt`
and `StudyScreen.kt`; `font/FontInitializer.kt` under the same app Kotlin package.
This helper validates a strict **new content subset**, not every historical app backup.

## Agent-authored input plan

```json
{"knowledgeBases":[{"name":"Biology","decks":[{"name":"Cells","cards":[
  {"front":"What contains a cell's genetic information?","back":"DNA.","source":"Notes §1"},
  {"front":"DNA stores {{c1::genetic information}}."},
  {"front":"Which is a nucleic acid?","options":[{"text":"DNA","correct":true},{"text":"Glucose","correct":false}]}
]}]}]}
```

Optional card fields: `type`, `tags` (string array), `source` (locator),
`bidirectional` (true only for safe two-way recall), `style` with boolean `hcenter`/
`vcenter`, integer `fontSize` 12–120, built-in `fontFamily` ID. This plan is NOT an
import file. Feed it to the builder. Unknown optional plan fields are not copied.

## Output JSON

The app's hierarchy is represented as flat arrays, not nested entities:

```json
{
  "exportType":"share","version":"1.0.0","schemaVersion":1,
  "exportDate":"2026-11-04T00:00:00Z",
  "knowledgeBases":[{"id":"kb_unique","name":"Biology","icon":"📁","createdAt":"2026-11-04T00:00:00Z","updatedAt":"2026-11-04T00:00:00Z"}],
  "decks":[{"id":"deck_unique","knowledgeBaseId":"kb_unique","name":"Cells","description":null,"color":"#4CAF50","icon":"📚","createdAt":"2026-11-04T00:00:00Z","updatedAt":"2026-11-04T00:00:00Z"}],
  "cards":[{"id":"card_unique","deckId":"deck_unique","type":"BASIC","front":"What stores genetic information?","back":"DNA.","tags":[],"media":[],"metadata":{"hcenter":"true","vcenter":"true","fontSize":"24","fontFamily":"sans-serif"},"createdAt":"2026-11-04T00:00:00Z","updatedAt":"2026-11-04T00:00:00Z"}]
}
```

Use new UUIDs for real generated IDs. Dates are UTC ISO 8601 strings. `description`
on a deck is required by serialization even when null; `tags` is required on cards.
Metadata is `Map<String,String>`: booleans and font size MUST be strings in output.
Never include private learning plans, settings, review logs, scheduling or tombstones.

## Card syntax and renderer limits

- `BASIC`: plain question and answer. `REVERSED`: safe bidirectional pairs; app flips faces.
- `CLOZE`: front contains `{{c1::answer}}` or `{{c1::answer::hint}}`; all blanks are
  hidden/revealed together in the current renderer, not separate Anki-style cards.
  Back contains extracted answers but the study renderer reveals the front's blanks.
- `MULTIPLE_CHOICE`: front is the question; back contains one option per line with
  `+` at column zero for each correct option. Multiple correct answers are supported.
  Never append explanation paragraphs to back: they would become extra options.
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
