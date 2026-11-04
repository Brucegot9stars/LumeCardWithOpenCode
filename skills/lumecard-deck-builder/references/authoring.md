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

Only readable text can be transformed without extractors. PDF scans may need OCR,
images may need vision, and office files may need local conversion. If unavailable,
ask for extracted text and retain a clear unprocessed-source list. Do not silently skip
unreadable pages or report complete coverage of partially extracted material.
