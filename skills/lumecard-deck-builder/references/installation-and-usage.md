# lumecard-deck-builder 安装与使用指南

适用版本：0.3.0。支持 Codex、Claude Code、OpenCode、Hermes Agent、CodeBuddy，
其他支持标准 SKILL.md 的 Agent 可指定安装目录。安装器和核心脚本不依赖 CodeGraph。

## 1. 获取 Skill

任选一种方式：

- 解压分发包 `lumecard-deck-builder-0.3.0.zip`，进入其中的 `lumecard-deck-builder` 目录。
- 在 LumeCardWithOpenCode 仓库中，进入 `skills/lumecard-deck-builder`。

发布后可从仓库的同名 Skill Release 下载 ZIP 和 SHA256；手动构建则从对应 Actions 运行的
Artifacts 下载。远程出包及文件校验步骤见 [GitHub Actions 发布指南](releasing.md)。

下面的命令均从这个目录运行。确认目录内存在 `SKILL.md`、`install.sh`、`install.ps1`、
`scripts/` 和 `references/`。不要只复制 SKILL.md，否则会缺少格式参考与辅助脚本。

## 2. 准备运行环境

自动安装和辅助脚本需要 Python 3.9+，并需要目标 Agent 已安装或已创建配置目录。
Linux/macOS 检查：

```sh
python3 --version
```

Windows PowerShell 检查：

```powershell
py -3 --version
```

Windows 也可使用能正确运行的 `python3` 或 `python`；安装入口会寻找可用解释器。
Windows 可使用系统 Windows PowerShell 或已安装的 PowerShell；Linux/macOS 的
PowerShell 入口需要自行安装 `pwsh`，也可直接使用系统 `sh`。
本项目目前实测的是 Linux 上的 Shell 和 PowerShell 7.6.9，Windows/macOS 原生执行仍待验证。

一般生成、校验、MD/TXT/HTML/DOCX 提取无需 pip 依赖。PDF 脚本提取需要可选的 pypdf，
见第 6 节。没有 Python 时，可手动复制整个 Skill 到目标目录，让 Agent 使用自身文件读取能力；
辅助脚本和机器校验将不可用，应在结果中说明。

## 3. 自动检测并安装

Linux/macOS：

```sh
sh install.sh
```

Windows PowerShell：

```powershell
.\install.ps1
```

安装器会显示检测结果、依据和安装路径。交互终端内输入逗号分隔的 Agent 名称，例如
`codex,claude`；直接回车选全部，输入 `none` 取消。默认安装到用户级目录。

检测依据包括 PATH 中的 CLI、已知配置目录和本安装器管理的已有 Skill。它不会启动 Agent、
读取凭据或修改 Agent 配置。配置目录可能是残留，检测到不等于 Agent 已能正常运行。
没有检测到时不会默认安装到某个平台，可使用显式安装。

仅查看检测结果或安装预览：

```sh
sh install.sh --list-agents
sh install.sh --dry-run
```

```powershell
.\install.ps1 -ListAgents
.\install.ps1 -DryRun
```

非交互环境中，安装全部已检测且支持当前作用域的 Agent：

```sh
sh install.sh --yes
```

```powershell
.\install.ps1 -Yes
```

已有完整受管安装会保持不变，不会自动升级。检测到 OpenCode 与 Codex/Claude 同时被选择时，
若 OpenCode 尚无原生副本，会复用同作用域兼容目录。手动安装时也应避免多处重复副本。

## 4. 显式选择 Agent 和安装范围

用户级安装用于本机该用户的多个项目；项目级安装只影响指定项目。
Agent 参数使用 `codex`、`claude`、`opencode`、`hermes`、`codebuddy`。

```sh
sh install.sh --agent codex --scope user
sh install.sh --agent claude --scope project --project /path/to/project
sh install.sh --agent codex,claude --scope user
```

```powershell
.\install.ps1 -Agent codex -Scope user
.\install.ps1 -Agent claude -Scope project -Project 'D:\Workspace\My Project'
.\install.ps1 -Agent 'codex,claude' -Scope user
```

以上是独立示例，按需要选择，不必逐条执行。项目目录须已存在；Hermes 的项目级发现机制
未确认，因此自动项目级安装会排除 Hermes，显式项目级安装会提示不支持。

| Agent | 用户级 Skill 父目录 | 项目级 Skill 父目录 |
|---|---|---|
| Codex | `~/.agents/skills` | `.agents/skills` |
| Claude Code | `~/.claude/skills` | `.claude/skills` |
| OpenCode | `~/.config/opencode/skills` | `.opencode/skills` |
| Hermes | `~/.hermes/skills` | 未确认 |
| CodeBuddy | `~/.codebuddy/skills` | `.codebuddy/skills` |

父目录下再追加 `lumecard-deck-builder/SKILL.md`。`~` 指当前用户主目录，包括 Windows。
OpenCode 的用户目录会遵循 `XDG_CONFIG_HOME`，Hermes 会遵循 `HERMES_HOME`。
目录证据、运行时验收状态与官方来源见 [平台兼容说明](platforms.md)。

对其他 Agent，先查其真实技能目录，再显式指定父目录；脚本不猜目录：

```sh
sh install.sh --agent custom --dest /verified/skills/parent
```

```powershell
.\install.ps1 -Agent custom -Dest 'D:\Verified\skills'
```

## 5. 让 Agent 使用 Skill

安装后打开 Agent 新会话，或使用该 Agent 支持的刷新方式，然后确认技能已出现。
Codex 的显式请求示例：

```text
$lumecard-deck-builder
读取 ./资料/Kotlin笔记.docx，为 Kotlin 初学者制作中文闪卡。
按主题组织知识库和牌组，保留“val 不保证引用对象不可变”等限定条件。
保留来源定位，未能读取的图片或页码单独列出，输出到 ./output/kotlin.json。
```

Claude Code / Hermes 可使用 `/lumecard-deck-builder`，后接同类需求。
OpenCode / CodeBuddy 可请求“请使用 lumecard-deck-builder 将这些资料转换为 LumeCard JSON”。
CodeBuddy 可用 `/skills` 检查，Hermes 可用 `hermes skills list` 检查。
是否自动触发还取决于 Agent 的模型和权限；各平台真实加载验收状态见平台说明。

推荐提供资料路径、语言、学习对象、主题组织、目标卡量和输出路径。只给章节标题或大纲时，
没有资料支撑的事实应由 Agent 询问或标记，不能自行补造。输出目录应已存在；已有输出文件
不会被脚本覆盖，请使用新文件名。

## 6. 文档格式与图片限制

| 输入 | 当前处理方式 |
|---|---|
| MD/TXT | 读取文本，保留行号、Markdown 和代码缩进；旧编码可显式指定 |
| HTML | 提取静态文本，不运行 JavaScript/CSS，不抓取外部资源 |
| DOCX | 提取正文及简单表格；图片、公式和复杂版式需另外核对 |
| 有文本层的 PDF | 使用宿主 PDF 能力，或可选 pypdf；保留页码和提取警告 |
| 扫描 PDF、照片、图表 | 需要宿主 OCR/视觉能力；未提供该能力时不能声称已读取 |
| 其他格式 | 使用宿主已有读取/转换工具，或请求提供可读文本 |

0.3.0 没有完整的自动识图流程。脚本不会识别 DOCX/PDF 内嵌图片，也不会读取 HTML/Markdown
引用的图片。宿主 Agent 若支持视觉或 OCR，可补充理解，但必须记录未读内容和识别不确定性。
当前生成的卡牌为文字内容，不会把原图片打包进卡牌，`media` 保持为空。

若要使用脚本提取 PDF，可在独立环境中安装可选依赖。Linux/macOS：

```sh
python3 -m venv ../lumecard-pdf-reader-venv
../lumecard-pdf-reader-venv/bin/python -m pip install 'pypdf>=6,<7'
../lumecard-pdf-reader-venv/bin/python scripts/extract_document.py textbook.pdf extracted.json
```

Windows PowerShell：

```powershell
py -3 -m venv ..\lumecard-pdf-reader-venv
..\lumecard-pdf-reader-venv\Scripts\python.exe -m pip install 'pypdf>=6,<7'
..\lumecard-pdf-reader-venv\Scripts\python.exe scripts/extract_document.py textbook.pdf extracted.json
```

示例将虚拟环境放在 Skill 目录外，避免将依赖一起复制或打包分发。
依赖安装由用户决定，Skill 不会自动安装。pypdf 本身不提供 OCR；复杂表格、公式、图表
必须与原文核对。编码、大小限制与逐格式覆盖范围见 [文档提取说明](documents.md)。

## 7. 从资料生成并导入 LumeCard

推荐让 Agent 完成整个流程。手动运行辅助脚本时，分清三个产物：

1. `extracted.json`：带来源定位与覆盖警告的资料提取结果。
2. `plan.json`：Agent 理解资料后整理的知识库、牌组、卡牌内容计划。
3. `deck.json`：生成器输出的 LumeCard 内容分享 JSON，只有这个成品用于导入。

例如先提取 DOCX，再由 Agent 参考 [计划示例](../examples/plan.json) 和
[格式规范](format.md) 编写 plan.json，最后生成并校验：

```sh
python3 scripts/extract_document.py notes.docx extracted.json
python3 scripts/build_deck.py build plan.json deck.json
python3 scripts/build_deck.py validate deck.json
```

Windows 将 `python3` 换成 `py -3`。生成器不会自动将 extracted.json 转为内容计划：
知识提炼、去重和事实核对仍由 Agent 或人完成。

在 LumeCard 的内容导入入口选择 deck.json，检查知识库、牌组和卡牌数量，再抽查
中文、代码、挖空、选择题、居中、字体和字号。它的 `exportType` 是 `share`，
不要当作完整备份恢复。专用卡型的渲染器可能不完全采用样式元数据，详见格式规范。
重复生成会产生新 ID，重复导入可能产生重复卡；需要稳定 ID 时保留原成品。

## 8. 更新与卸载

先确认原安装目录和作用域，备份自己修改过的文件，再卸载旧版、安装新版。
比如卸载用户级 Codex 安装：

```sh
sh install.sh --agent codex --scope user --uninstall
```

```powershell
.\install.ps1 -Agent codex -Scope user -Uninstall
```

自动选择当前作用域的受管安装进行卸载：

```sh
sh install.sh --uninstall --yes
```

```powershell
.\install.ps1 -Uninstall -Yes
```

项目级操作保留原 `--scope project --project ...` / `-Scope project -Project ...` 参数。
卸载只删除清单完整且未被修改的 Skill 目录，不删除 Agent 本身或其他配置。
修改过或手动复制的安装会拒绝自动删除，应先备份并人工处理。
更新完成后重新打开 Agent 会话。安装了多个副本时确认实际被发现的是新版目录。

## 9. 常见问题

| 提示或现象 | 处理方法 |
|---|---|
| 没有检测到 Agent | 查看 --list-agents 的依据；将 CLI 加入 PATH，或显式指定 --agent |
| 非交互环境要求终端 | 显式指定 Agent，或用 --yes 选择检测到的目标 |
| destination exists / already installed | 已有安装受保护；确认目录后先卸载再更新 |
| installation was modified | 先备份本地改动，再手动处理；不要为卸载去伪造清单 |
| Python 不存在/版本过旧 | 安装 Python 3.9+ 并确认命令可用，或按平台路径手动复制 Skill |
| PDF_DEPENDENCY_MISSING | 使用宿主 PDF 读取能力，或在独立环境安装可选 pypdf |
| NO_TEXT / 只有部分页可读 | 核对原文；扫描页需 OCR，不能将空提取结果当作完整处理 |
| 乱码或解码失败 | 核对原始编码；文本/HTML 提取可传 --encoding gb18030 等已知编码 |
| Windows 阻止运行 ps1 | 先审查脚本来源并按所在环境政策处理；也可直接使用 Python 安装入口 |
| Agent 未显示技能 | 检查真实路径、完整文件夹、权限与重复副本，再开新会话 |

Windows 上不运行 ps1 也能调用相同安装器：`py -3 scripts/install.py --list-agents`，
或 `py -3 scripts/install.py --agent codex --scope user`。
测试执行结果和未验证项见 [测试记录](testing.md)；维护校验器见 [校验器更新说明](validators.md)。
