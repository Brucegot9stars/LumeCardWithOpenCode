# LumeCard 制卡 Skill 使用指南

`lumecard-deck-builder` 把知识文档、笔记、教材、文章或大纲转换成 **可直接导入 LumeCard 的 JSON**（知识库 → 牌组 → 卡牌三层结构），并自动选择卡型、居中方式、字号与字体。

- 主入口：`SKILL.md`
- 详细规则：`references/`
- 完整示例：`assets/example.share.json`
- 校验脚本：`scripts/validate.mjs`（需要 Node.js）

---

## 1. 支持哪些 agent

本 Skill 使用通用 `SKILL.md` 格式，同一份内容放在不同 agent 的目录即可使用。

| Agent | 项目级目录 | 用户级目录 | 调用方式 |
|---|---|---|---|
| OpenCode | `.opencode/skills/<name>/` | `~/.config/opencode/skills/<name>/` | 自动加载；直接描述任务，或明确说“使用 lumecard-deck-builder skill” |
| Claude Code | `.claude/skills/<name>/` | `~/.claude/skills/<name>/` | 自动触发，或输入 `/lumecard-deck-builder` |
| Codex | `.agents/skills/<name>/` | `~/.agents/skills/<name>/` | 自动触发，或用 `$lumecard-deck-builder`；`/skills` 可查看列表 |
| CodeBuddy | `.codebuddy/skills/<name>/` | `~/.codebuddy/skills/<name>/` | 自动触发，或输入 `/lumecard-deck-builder` |

> 目录名必须是 `lumecard-deck-builder`（小写字母、数字、连字符），否则 Claude Code 等 agent 可能拒绝加载。

> **Codex 没有 `.codex/skills` 目录**：Codex 已采用 Agent Skills 标准，仓库级扫描 `.agents/skills/`（从当前目录逐级向上直到仓库根），用户级是 `~/.agents/skills/`。`.codex/` 只存放 `config.toml`、`agents/`、`rules/` 等配置；旧的 `~/.codex/skills` 仅作兼容保留，新技能不要放那里。

仓库中已经预置了四份内容一致的副本：

```
.opencode/skills/lumecard-deck-builder/   # 规范源（canonical）
.claude/skills/lumecard-deck-builder/
.agents/skills/lumecard-deck-builder/
.codebuddy/skills/lumecard-deck-builder/
```

## 2. 在其他项目中使用

### 方式 A：复制到项目

```bash
# 以 Claude Code 为例；其他 agent 换成对应目录
mkdir -p .claude/skills
cp -r /path/to/LumeCard/.claude/skills/lumecard-deck-builder .claude/skills/
```

Windows PowerShell：

```powershell
New-Item -ItemType Directory -Force .claude/skills | Out-Null
Copy-Item -Recurse "D:\path\to\LumeCard\.claude\skills\lumecard-deck-builder" ".claude\skills\"
```

### 方式 B：安装到用户级目录（所有项目可用）

```bash
cp -r .claude/skills/lumecard-deck-builder ~/.claude/skills/
```

把 `.claude` 换成 `~/.config/opencode`、`~/.agents` 或 `~/.codebuddy` 即可。

### 方式 C：只带一个目录

如果只想给别人一份最小文件，直接发送 `.opencode/skills/lumecard-deck-builder/` 整个目录。它包含 `SKILL.md`、`references/`、`assets/`、`scripts/`，放到任意 agent 的 skills 目录下都能工作。

## 3. 怎么用

安装后不需要记命令，直接把材料交给 agent 即可。推荐提示词：

```text
使用 lumecard-deck-builder，把这些内容做成可导入 LumeCard 的知识库。

学习目标：<例如：一周内掌握 A1 日常用语>
材料语言：<中文 / 英文 / ...>
期望规模：<例如：3 个牌组、60 张卡；不确定就写"你决定">
输出路径：<可选，默认 lumecard-exports/主题-slug.lumecard.json>

<粘贴文档内容，或 @文件名>
```

最短版本：

```text
把 @lecture-notes.md 做成 LumeCard 闪卡，输出可导入的 JSON。
```

Agent 会按 `SKILL.md` 的工作流执行：读材料 → 划分知识库/牌组 → 抽取原子知识 → 选卡型 → 设置居中与字号 → 生成 JSON → 运行校验 → 汇报路径与结构摘要。

## 4. 产出与导入

默认输出：

```
lumecard-exports/<主题-slug>.lumecard.json
```

导入步骤：

1. 打开 LumeCard。
2. 进入 **设置 → 数据管理 → 导入**。
3. 选择生成的 `.lumecard.json` 文件。
4. 导入完成后到知识库列表查看新增的知识库与牌组。

> 导入使用 `share` 格式，不包含学习进度。相同 ID 再次导入会覆盖同名实体，适合反复迭代同一套卡。

## 5. 手动校验（推荐）

生成后可以自己再跑一次校验：

```bash
node "<skill 目录>/scripts/validate.mjs" "lumecard-exports/my-topic.lumecard.json"
```

- 必须 **0 个 ERROR**。
- WARNING 逐条判断，能改就改。
- 校验内容：结构完整性、ID 引用、重复 ID、卡型格式、Cloze 标记、选择题选项、HTML/Markdown 语法、居中是否对卡型生效、长内容误用垂直居中等。

示例：

```bash
node .opencode/skills/lumecard-deck-builder/scripts/validate.mjs \
  .opencode/skills/lumecard-deck-builder/assets/example.share.json
```

没有 Node 时，按 `SKILL.md` 末尾的「人工自检清单」检查。

## 6. 维护多份副本

仓库把 `.opencode/skills/lumecard-deck-builder/` 作为规范源，其余三份是生成副本。修改 Skill 后运行：

```bash
node scripts/sync-skill.mjs
```

检查副本是否与源一致（适合 CI 或提交前）：

```bash
node scripts/sync-skill.mjs --check
```

> 不要直接编辑 `.claude/`、`.agents/`、`.codebuddy/` 下的副本，下次同步会被覆盖。

## 7. 常见问题

**导入了卡片，但居中/字号没生效？**
检查 LumeCard 版本是否包含导入字段透传修复（`title` / `media` / `metadata`）。旧版本导入会丢失 `metadata`。另外居中只对 `BASIC`、`REVERSED`、`RICH_TEXT` 生效，字号只对 `BASIC`、`REVERSED`、`CLOZE` 生效。

**长卡片显示不全？**
`vcenter=true` 会关闭学习页滚动。长内容不要开垂直居中。

**填空卡背面不显示？**
LumeCard 的 `CLOZE` 学习时只渲染 `front`，`back` 不要承载额外解释。

**`REVERSED` 没有生成反向卡？**
当前版本不会自动生成反面，需要两张卡，或一张 `BASIC` + 一张 `REVERSED`。

**Agent 没有自动触发 Skill？**
确认目录名和 frontmatter 的 `name` 都是 `lumecard-deck-builder`，并重启会话；也可以直接输入 `/lumecard-deck-builder`（Claude Code / CodeBuddy）或 `$lumecard-deck-builder`（Codex）。
