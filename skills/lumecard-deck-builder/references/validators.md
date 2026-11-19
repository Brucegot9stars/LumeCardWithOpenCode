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

## Content validator (build_deck.py), 0.4.0

This is separate from the Skill-spec validator above. As of 0.4.0, `validate()` no
longer rejects `card.media`. It checks every media item with `media_items()`:
only `IMAGE`, required non-empty `id`/`filePath`, no `..` path traversal, no `http://`
(https URLs must be well-formed), optional string `caption`/`mimeType` and a
non-negative integer `size`. `metadata["mediaTarget"]` is accepted only when it is
`both` / `front` / `back`. Legacy exports with `media: []` still pass unchanged.
`build()` writes `mediaTarget` into metadata only for cards that actually have images.
