# Verification record

Date: 2026-11-04. This report separates deterministic helper tests, manual instruction
review, Agent discovery tests and GUI import. No Gradle build/test is run in this environment.

## Executed results

| Check | Observed result |
|---|---|
| Standard-library suite, Linux / Python 3.12 | 24 cases: all passed, no skips; includes PowerShell 7.6.9 install/uninstall |
| ZIP distribution | Extracted standalone archive validates its included import fixture; caches excluded |
| YAML Frontmatter | Parsed successfully with PyYAML in the development environment; not a runtime dependency |
| Bundled skill-creator quick_validate.py | Passed after moving optional compatibility text into body |
| Official skills-ref | Passed against upstream commit `69ef37e9424c0a7ea9dd2293b559e43ec8176379`, downloaded into temporary cache |
| Positive/negative/injection scenarios | Eight cases manually reviewed; see tests/scenario-review.md. Automatic Agent routing NOT tested |
| Source → curated plan → import | 1 knowledge base, 1 deck, 4 cards; references/type/style validation passed |
| Shell wrapper and absent Python | Linux install/uninstall passed; empty-PATH simulation returned clear error with manual fallback |
| Third-party dependencies absent | Builder validates fixture under Python `-S` (site packages disabled) |
| Codex 0.166.0 discovery | Temporary project `.agents/skills` discovered and enabled, scope `repo`; `skills/list` RPC succeeded. No model inference/implicit trigger tested |
| Hermes actual loading attempt | Blocked before discovery by read-only install lock under `~/.hermes`; NOT verified; no repair or user-config mutation performed |
| Claude Code / CodeBuddy | Runtime loading NOT verified; CLI presence is insufficient evidence |
| OpenCode | CLI not found; NOT verified |
| PowerShell 7.6.9 on Linux | Project-scope wrapper installation and uninstallation passed. Initial sandbox run failed because PowerShell needed its user data directory; authorized execution outside the sandbox passed |
| Windows / macOS | Native execution NOT verified; cross-OS CI supplied for standalone repository, not run here |
| LumeCard GUI import / visual fit | NOT verified; contract checked against source only |

Reproduce Codex discovery without inference with `python3 tests/probe_codex.py` when
Codex is installed. Probe state and installation are temporary. App-server is an
optional adapter diagnostic; it is not a dependency of the skill or generation workflow.
Its protocol can change independently of the portable skill.

Run `python3 -m unittest discover -s tests -v` from the skill root. It exercises generated
share content, ID relationships, style overrides, invalid input, duplicate JSON keys,
Unicode/space paths, overwrite refusal, missing third-party packages, install/uninstall,
modified-install preservation and both OS wrappers when their runtimes are available.
The workflow under `.github/workflows` is for the extracted standalone GitHub repository.
In the LumeCard monorepo that nested workflow is not automatically discovered by Actions.

## Trigger and workflow evaluation

`tests/scenarios.json` contains positive, negative, inaccessible-source and injection cases.
Manual review checks intended routing; this is not proof of automatic model selection.
An Agent execution evaluation must record Agent version, prompt, observed invocation,
produced JSON and validator result. Do not change expected outcomes to match a failure.
Example source → curated plan → generated import is an authored integration fixture,
not an automatic document-understanding test.

## External acceptance still required

Windows/macOS native execution, Claude/OpenCode/CodeBuddy loading,
automatic triggering in each model, and LumeCard GUI import/rendering remain unverified
unless explicitly recorded below. CLI existence or --help alone does not prove skill loading.
Check Chinese text, code, cloze and choices on both desktop and Android, then verify
knowledge-base/deck/card counts and absence of learning history changes.
