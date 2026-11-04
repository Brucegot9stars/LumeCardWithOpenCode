# Platform evidence and adapters

Checked 2026-11-04. Documentation support and actual runtime testing are different.
Paths below are **parent directories**; append `lumecard-deck-builder/SKILL.md`.
`~` means the OS user home, including Windows. Use PowerShell's installer on Windows.

| Agent | Support evidence | User parent | Project parent | Invocation | Actual loading |
|---|---|---|---|---|---|
| Codex | Official standard support | `~/.agents/skills` | `.agents/skills` | `$lumecard-deck-builder` or relevant request | Discovery passed on 0.166.0; generation unverified |
| Claude Code | Official standard support plus extensions | `~/.claude/skills` | `.claude/skills` | `/lumecard-deck-builder` or relevant request | Unverified |
| OpenCode | Official support; also compatible with `.agents`/`.claude` skills | `~/.config/opencode/skills` | `.opencode/skills` | Ask to use the named skill; native skill loading tool | Unverified |
| Hermes Agent (user-confirmed correction of “Herness”) | Official skills documentation | `~/.hermes/skills` | Not confirmed; installer refuses implicit project scope | `/lumecard-deck-builder`; inspect with `hermes skills list` | Blocked by local installation lock; unverified |
| CodeBuddy Code CLI | Official skills documentation | `~/.codebuddy/skills` | `.codebuddy/skills` | `/skills` discovery; request named skill | Unverified |
| CodeBuddy IDE | Official project skills/import UI | Use IDE import UI for user scope | `.codebuddy/skills` | Skills settings; relevant task | Unverified |
| Other standard-compatible Agent | Depends on its discovery implementation | Explicit `--dest` only | Explicit `--dest` only | Consult its docs; manually read SKILL.md if necessary | Unverified |

OpenCode respects `XDG_CONFIG_HOME`; Hermes's optional `HERMES_HOME` path is honored by
the adapter. No native manifest, hooks, settings changes or Agent-specific tools are
required in the core. Claude extension frontmatter, Codex `openai.yaml`, and proprietary
slash-command wrappers are deliberately omitted. Discovery remains an Agent feature,
not something the open format itself guarantees. Permission policies may disable skills
or shell access. Duplicate copies in native and compatibility directories can cause
duplicate discovery; install once per scope.

## Installer auto-detection (0.3.0)

Omitting --agent is equivalent to --agent auto. Detection checks PATH via Python's
shutil.which (including Windows executable suffix handling), known config locations,
project config directories when a project is specified, and this installer's marker.
No Agent process is launched and no credentials or config contents are read. The shared
`.agents` directory alone does not identify Codex; its CLI, `.codex`/CODEX_HOME or an
existing managed skill is needed. XDG_CONFIG_HOME and HERMES_HOME are honored.

Inspiration: locally inspected CodeGraph 1.0.1 installer `targets/*`, registry detection
and interactive preselection. This implementation independently uses configuration
presence plus PATH and does not depend on CodeGraph or copy its source. A detected
config directory is evidence, not proof of an installed/running Agent. There is no
default Agent fallback when detection finds nothing.

TTY sessions allow selecting detected eligible names (Enter=all, none=cancel).
Noninteractive sessions need --yes for automatic mutations. --list-agents and --dry-run
perform no writes. Explicit --agent bypasses detection selection and retains the original
single-Agent behavior; comma-separated names select multiple targets. Unsupported scopes
(Hermes project scope) and blocked destinations are reported and excluded from automatic
selection. --dest needs an explicit single Agent. Automatic uninstall only considers
managed installations, even when the Agent CLI was removed.

Automatic installation reuses a selected Codex/Claude compatibility root for OpenCode
when its native target does not already exist. No additional OpenCode copy is created.
Explicit selections keep native destinations. Existing managed installs are left in
place after integrity checks; incompatible/unmanaged conflicts fail preflight. Multi-Agent
preflight checks all targets before changes, but an unforeseen runtime I/O failure can
still leave earlier successful installations in place; output identifies those successes.

Since 0.3.1, compatibility reuse honors `OPENCODE_DISABLE_EXTERNAL_SKILLS`,
`OPENCODE_DISABLE_CLAUDE_CODE` and `OPENCODE_DISABLE_CLAUDE_CODE_SKILLS`
(`1`/`true`, case-insensitive). When no selected compatible root is enabled, the
installer uses OpenCode's native directory. It does not change those settings.

## Primary sources

- [Agent Skills specification](https://agentskills.io/specification): portable directory/frontmatter and progressive disclosure.
- [OpenAI official skills documentation](https://learn.chatgpt.com/docs/build-skills): Codex discovery and explicit invocation.
- [Claude Code](https://code.claude.com/docs/en/skills): local paths, slash invocation, standard versus extensions.
- [OpenCode](https://opencode.ai/docs/skills/): native and compatibility search paths; on-demand tool.
- [Hermes official repository guide](https://github.com/NousResearch/hermes-agent/blob/main/website/docs/guides/work-with-skills.md): user skills, list and slash invocation. Project scope not established by this source.
- [CodeBuddy CLI](https://www.codebuddy.cn/docs/cli/skills): user/project discovery and `/skills`.
- [CodeBuddy IDE](https://www.codebuddy.ai/docs/ide/Features/Skills): project directory and import UI.

Recheck these sources before changing defaults. Never guess a new Agent's install path.
