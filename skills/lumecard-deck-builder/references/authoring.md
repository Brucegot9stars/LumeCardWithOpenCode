# Card design and automatic presentation

Keep one retrieval target per card. Preserve negation, exceptions, quantities and units.
For a chapter, group by conceptual topic rather than splitting at arbitrary text length.
Maintain the requested audience/language; do not manufacture answers from section titles.
Include source locators in the plan's `source`; the helper stores this in metadata.
If a source contradicts itself, flag it and request clarification rather than guessing.

Prefer BASIC for concepts and Q&A. Choose REVERSED only for genuinely symmetric pairs
(word ↔ translation), never a many-to-one definition. Use CLOZE for facts needing context.
Use MULTIPLE_CHOICE only when the source or user supplies credible alternatives; avoid
invented factual distractors. MARKDOWN suits code and structured comparisons. Basic is
the fallback when evidence for a specialized format is absent.

The helper chooses choice for explicit options, then cloze markers, then bidirectional,
then Markdown structure, otherwise BASIC. Explicit `type` wins and is validated.
For plain BASIC/REVERSED content with no newline and both faces ≤100 characters, choose
both centering flags and 24sp; otherwise choose non-centered and 16sp. Prefer sans-serif
for multilingual text; code requests monospace. For MARKDOWN, renderer control supersedes
these metadata preferences. Respect user overrides; font size stays in the app's 12–120sp
range. These are initial heuristics, not a guarantee of fit on every device. Preview long
cards in the app and split dense cards before increasing font size.

Use the format-specific extraction workflow in documents.md before authoring. Read
coverage and warnings, preserve source locators, and keep a clear unprocessed-source
list. Scans need OCR/vision. Do not silently skip unreadable pages or equate text-only
extraction with complete visual understanding of the document.

## Images (0.4.1)

The app can display images on cards, but this skill has no automatic image extraction.
Author images only when they are genuinely available and useful:

- **Only use explicit sources**: a media file name/path supplied by the user, or an
  https image URL actually present in the material. Never fabricate a URL, guess a
  file name, or copy `![alt](...)` Markdown into a card field.
- **When to add one**: the image carries information the text cannot (diagram, map,
  formula, screenshot) and the card's retrieval target depends on it. Skip decorative
  images. Keep `front`/`back` self-contained: the text must still make sense alone.
- **Caption**: write a short factual caption; do not put the answer into the caption
  if that gives away the card.
- **Face**: set card-level `mediaTarget` to `both` (default), `front` or `back`. For a
  "identify this diagram" card put the image on `front`; for an answer diagram use
  `back`. Per-image targets are not supported — split into two cards if needed.
- **Limits**: no automatic OCR of scanned images, no inline Markdown/HTML images, no
  http URLs, no image occlusion. If a source image cannot be read or referenced safely,
  note it as unprocessed and leave `media` empty rather than inventing a substitute.
