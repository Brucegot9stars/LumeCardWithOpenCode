# Updating validators

The local Codex skill-creator quick_validate.py rejected the specification's optional
compatibility field on 2026-11-04. Its allowed-field list was narrower than the standard.
The delivered Skill keeps environment requirements in its body for older-client support.

Do not patch a managed system skill in place. Bundled scripts may change with Codex
updates, but updating Codex does not guarantee a particular validator revision.
For an independent check use the reference implementation linked by the
[specification](https://agentskills.io/specification), in an isolated environment.
It is a demonstration/reference library, not a production security certification.

With uv available (optional maintenance dependency):

```sh
uvx --refresh --from 'git+https://github.com/agentskills/agentskills.git#subdirectory=skills-ref' skills-ref validate /path/to/lumecard-deck-builder
```

Alternatively, use the official source in a virtual environment. Linux/macOS:

```sh
git clone https://github.com/agentskills/agentskills.git
python3 -m venv .skill-validator-venv
.skill-validator-venv/bin/python -m pip install -e ./agentskills/skills-ref
.skill-validator-venv/bin/skills-ref validate /path/to/lumecard-deck-builder
```

Windows PowerShell:

```powershell
git clone https://github.com/agentskills/agentskills.git
py -3 -m venv .skill-validator-venv
.\.skill-validator-venv\Scripts\python.exe -m pip install -e .\agentskills\skills-ref
.\.skill-validator-venv\Scripts\skills-ref.exe validate C:\path\to\lumecard-deck-builder
```

To update the source, run `git -C agentskills pull --ff-only` and repeat the environment's
pip install command. For reproducible CI pin a reviewed commit instead of HEAD.
The core scripts do not need uv, pip, PyYAML or skills-ref. See the
[upstream README](https://github.com/agentskills/agentskills/tree/main/skills-ref).

## Content validator (build_deck.py), 0.4.1

This is separate from the Skill-spec validator above. As of 0.4.1, `validate()` no
longer rejects `card.media`. It checks every media item with `media_items()`:
only `IMAGE`, required non-empty `id`/`filePath`, no `..` path traversal, no `http://`
(https URLs may contain Unicode paths and IDN hosts, matching the app's `java.net.URI`; ASCII
control characters, spaces and `<>"{}|\^`` are rejected because `URI` refuses them), optional
string `caption`/`mimeType` and a
non-negative integer `size`. As of 0.4.3 relative names must also pass the app's
strict `MediaPathRules` mirror (no backslash, no drive letter or `:`, no control
characters, no trailing dot/space, no Windows-reserved segment, 128/255 length caps)
— the app's importer, exporter and archive writer enforce the same rule.
`metadata["mediaTarget"]` is accepted only when it is
`both` / `front` / `back`. Legacy exports with `media: []` still pass unchanged.
`build()` writes `mediaTarget` into metadata only for cards that actually have images.

Two per-image presentation fields are validated as well:

- `alignment` must be one of `start` / `center` / `end`; anything else is rejected, and
  omitting it normalises to `center`. The app is deliberately more lenient at read time
  (unknown values fall back to `center` instead of dropping the image), so an invalid
  alignment is a skill-side authoring error only and is **not** part of the shared
  contract fixtures.
- `compress` must be a real boolean (not `"true"`, `1` or `null`); omitting it
  normalises to `true`.

Unknown keys inside a media item are still rejected through the media field allow-list,
so a typo such as `aligment` fails loudly instead of being silently ignored.

## Archive packaging (0.4.4)

`build_deck.py` gained two actions next to `build`/`validate`:

- `package plan.json out.lumecard` — accepts `media[].sourcePath` in the plan, reads each
  local file, and derives `filePath` as `<sha1>.<ext>`. The digest is taken over the file
  bytes, matching the app's `copyFileToMedia`, so an identical image referenced from several
  cards collapses onto one archive entry. Only PNG/JPEG/GIF/WebP/BMP are accepted; SVG is
  rejected because neither Skia Codec nor `BitmapFactory` decodes it and the card would show
  a permanent failure placeholder. `filePath` and `sourcePath` are mutually exclusive.
- `inspect out.lumecard` — re-reads a produced archive and fails when the manifest is not the
  first entry or a card's `filePath` has no matching entry (the "imports fine, image missing"
  case). Extra `media/` entries no card references are not an error — the importer filters to
  referenced paths before extracting — though every entry under `media/` must still be a
  decodable, in-budget image: a format signature alone is not enough, the file must also carry
  the real image data and terminator (PNG IEND + IDAT, JPEG SOS + EOI, GIF trailer, WebP/BMP
  declared size). It also enforces the
  importer's other budgets — 64 MiB per image, 1 GiB total, 10,000 media entries, 64 MiB
  manifest, 20,000 total entries (the app gives up listing beyond that) — and catches an entry
  whose declared size cannot fit its compressed payload, which is the shape of a zip bomb. A
  reference that matches an entry only by case is accepted when it is the only such spelling:
  the app writes the bytes under the manifest name. Two references differing only by case are
  rejected, because the app cannot choose between them and both images would break.
- `inspect` **skips** directory entries (every `zip -r` archive has one per folder) and entries
  outside `media/` (resource forks, `__MACOSX`), matching what the importer ignores. The manifest
  itself is parsed with the same strict parser as `validate`: duplicate JSON keys and non-finite
  numbers (`NaN`, `Infinity`) are rejected rather than silently accepting the last duplicate.

`package` runs `inspect` on its own output before reporting success and deletes the archive if
that check fails. A plan that names a local image by `filePath` instead of `sourcePath` is
rejected up front, because nothing would carry the bytes into the archive and the imported cards
would show permanently broken images without any error.

Both write paths refuse to overwrite an existing file, and the archive checks reuse
`validate()` on the embedded manifest, so an archive can never hold a payload that would be
rejected as a bare JSON. `build` (JSON only) explicitly rejects a plan that uses
`sourcePath`, because a bare JSON copies no media into the app and the images would silently
fail to load.

### Error reporting (0.4.4)

Every failure is a single `error: ...` line on stderr with exit code 2. When a plan misbehaves,
the message names the exact position with indices —
`knowledgeBases[0].decks[1].cards[3].front: expected non-empty text` — so a large plan can be
fixed without searching. Other cases that carry their own remedy:

- A local path written into `filePath` (the field the app's own export uses) says to switch to
  `media[].sourcePath` and run `package`. This is the most common authoring mistake, so it is
  called out instead of listing the media-root rules.
- Input that is not UTF-8, is empty, or is invalid JSON reports the file, the offending byte or
  the line/column, and what to do about it.
- A `.lumecard` passed to `build`/`validate`, or a `.json` passed to `inspect`, names the action
  that was probably meant.
- A directory where a file was expected says so, rather than surfacing `Permission denied`.
- `~` and environment variables in CLI paths are expanded.

Temporary files during an archive write carry a random suffix, so two builds do not share one.
