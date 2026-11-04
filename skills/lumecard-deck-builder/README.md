# lumecard-deck-builder

将文档、笔记、教材、文章或大纲转换为 LumeCard 可导入的闪卡 JSON。
知识库 → 牌组 → 卡牌通过 ID 关联；自动选择卡型、居中、字号和内置字体。
版本 0.3.0。核心定义只有标准 `SKILL.md`，没有平台专用工具依赖。

首次使用请阅读 [安装与使用指南](references/installation-and-usage.md)：包含下载、依赖准备、
自动检测、各 Agent 调用、多格式资料、图片限制、LumeCard 导入、升级卸载和常见问题。
维护者远程出包见 [GitHub Actions 发布指南](references/releasing.md)：支持手动生成 Artifact，
或推送独立 Skill 标签生成 GitHub Release，`dist/` 压缩包无需提交。

Agent 负责理解资料、提炼知识和形成内容计划；Python 标准库脚本负责生成唯一 ID、
选择默认样式、编码与校验。没有模型 API、网络请求、自动安装依赖或外传文档。
脚本需要 Python 3.9+；没有 Python 时，Agent 可用宿主文档读取能力并按格式参考输出 JSON，
说明未机器校验。MD/TXT、HTML、DOCX 提取使用标准库；PDF 文本提取可选使用 pypdf。
扫描 PDF 和图片需要宿主 OCR/视觉能力，本项目不捆绑 OCR。详见 [文档格式支持](references/documents.md)。

## 安装

先下载或 clone，再进入本目录。默认拒绝覆盖已存在的安装。

```sh
sh install.sh
sh install.sh --list-agents
sh install.sh --yes
sh install.sh --agent codex --scope user
sh install.sh --agent claude --scope project --project /path/to/project
sh install.sh --agent opencode --scope user
sh install.sh --agent hermes --scope user
sh install.sh --agent codebuddy --scope project --project /path/to/project
```

Windows PowerShell：

```powershell
.\install.ps1
.\install.ps1 -ListAgents
.\install.ps1 -Yes
.\install.ps1 -Agent codex -Scope user
.\install.ps1 -Agent claude -Scope project -Project 'D:\Workspace\My Project'
```

目标 Agent：`codex`、`claude`、`opencode`、`hermes`、`codebuddy`、`custom`。
省略 `--agent` / `-Agent` 或指定 `auto` 会检测本机 CLI、配置目录及本安装器管理的已有安装，
展示检测依据与目标目录。交互终端可输入逗号分隔的 Agent 名称；回车选全部，`none` 取消。
非交互环境使用 `--yes` / `-Yes` 安装全部检测到且支持当前作用域的 Agent，或显式指定 Agent。
也可用 `--agent codex,claude` / `-Agent 'codex,claude'` 指定多个目标。
`--list-agents` / `-ListAgents` 仅检测；`--dry-run` / `-DryRun` 预览检测后的全部目标，无需确认。
没有检测到时会报错并提示显式指定，不会默认选择某个平台。配置残留也可能被检测到，
检测不代表 Agent 可正常运行或一定具有写入权限。已有完整受管安装会保留；更新仍需先卸载。
自动模式检测到 OpenCode 与 Codex/Claude 时，优先复用同作用域兼容目录，避免再创建
OpenCode 原生目录副本；显式指定的目标则按指定目录处理。目标冲突会在整批安装开始前检查。
其他 Agent 用 `--agent custom --dest /verified/skills/parent`；PowerShell 用 `-Dest`。
`--dest` 必须配显式单个 Agent。自动卸载只选择当前作用域已有的受管安装：
`sh install.sh --uninstall --yes` 或 `.\install.ps1 -Uninstall -Yes`。
`--dry-run` / `-DryRun` 只显示目标。Hermes 项目级发现机制未确认，默认拒绝项目级安装。
安装器检查目录、拒绝符号链接、只复制文件、不修改 Agent 配置。
详细安装目录、调用方式和来源见 [兼容性矩阵](references/platforms.md)。

没有 Python：复制整个 `lumecard-deck-builder` 文件夹到矩阵列出的父目录中，保留目录名，
重新启动 Agent 或刷新其技能列表；这种手动安装不由脚本管理卸载。
安装脚本只更改指定作用域。不要同时安装到同一 Agent 会搜索的多个兼容目录。

卸载：用相同参数加 `--uninstall` / `-Uninstall`。仅删除带安装清单且内容未改的技能目录；
修改过的安装会拒绝删除，请先备份再手动处理。升级为卸载旧版后安装新版；默认不原地覆盖。
许可证建议并采用 MIT，仅适用于本目录原创代码和说明；输入教材、生成内容与字体各自的
版权不因本许可证改变。向独立 GitHub 仓库发布时可直接以此目录内容作为仓库根目录。

## 使用

Codex：`$lumecard-deck-builder 将 notes.md 转成中文 LumeCard 闪卡，保存到 kotlin.json`。
Claude Code / Hermes：`/lumecard-deck-builder 将 notes.md 转成 LumeCard 闪卡`。
OpenCode / CodeBuddy：请求使用命名技能，按各自技能列表确认已发现。
首次安装后开新会话；权限策略可能要求允许读取或执行脚本。

不通过 Agent 运行时，先手工编写 [内容计划](examples/plan.json)，然后：

```sh
python3 scripts/build_deck.py build examples/plan.json my-deck.json
python3 scripts/build_deck.py validate my-deck.json
```

文档输入先提取为有来源定位信息的中间结果，再由 Agent 提炼内容计划：

```sh
python3 scripts/extract_document.py textbook.docx extracted.json
python3 scripts/extract_document.py textbook.pdf extracted.json
```

PDF 命令需要已安装的可选 pypdf。提取结果包含覆盖范围与警告，不能直接导入 LumeCard。
支持 `.md/.markdown/.txt/.html/.htm/.docx/.pdf`；其他格式优先用宿主已有读取或转换能力。
提取器不会执行 HTML/文档代码，不会自动联网或安装依赖；编码失败、扫描件缺 OCR、
加密/损坏文件会明确报告，不能宣称完整读取。

Windows 可将 `python3` 换成 `py -3`。成品示例为 `examples/import.json`，在应用的
**内容导入**入口使用；不要用完整备份恢复。重复生成会创建新 ID，再次导入可能产生重复卡。
保留原成品可保持 ID。字号/字体对 Markdown、选择题等专用渲染器的作用有限，详见格式参考。

## 目录与验证

```text
lumecard-deck-builder/
  SKILL.md                 标准入口
  README.md, LICENSE       安装、分发、MIT 许可
  install.sh, install.ps1  平台启动脚本
  references/             格式、设计、平台证据、测试记录
  scripts/                生成器、校验器、安装器、ZIP 打包器
  examples/               原始笔记、内容计划、导入成品
  tests/                  标准库测试与触发评估案例
  .github/workflows/      独立发布仓库的跨 OS 测试工作流
```

```sh
python3 -m unittest discover -s tests -v
python3 scripts/package.py /path/to/lumecard-deck-builder-0.3.0.zip
```

打包拒绝覆盖，排除缓存和安装清单。独立发布仓库的 CI 覆盖 Linux、Windows 和 macOS；
当前实际执行结果及未验证项见 [测试记录](references/testing.md)。文件结构/示例验证不等于
各 Agent 自动触发成功，也不等于应用 UI 导入验收。发布前对目标 Agent 开新会话，运行
`tests/scenarios.json` 的案例，保存实际发现/触发和成品结果。

本仓库可执行的 Skill 出包工作流位于仓库根目录 `.github/workflows/skill-release.yml`；
Skill 文件夹内的同名目录是独立仓库模板。远程产物统一从 SKILL.md 读取版本并附带 SHA256，
手动运行不发布 Release，`lumecard-deck-builder-v*` 标签才会公开发布。

维护优先级：P1 跟踪 LumeCard 序列化和渲染格式变更；P1 完成各 Agent、各 OS 的真实加载及
应用导入验收；P2 用用户资料评估提取质量、卡牌和样式；P3 按需加 OCR/自定义字体适配。
每次更新先重跑生成/校验/安装/卸载测试，再复核官方路径文档；兼容性注明日期和证据。

本机校验器过旧时，参见 [独立校验器更新说明](references/validators.md)，使用官方参考实现，
无需手改 Codex 系统技能。
