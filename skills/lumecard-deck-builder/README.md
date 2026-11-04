# lumecard-deck-builder

将文档、笔记、教材、文章或大纲转换为 LumeCard 可导入的闪卡 JSON。
知识库 → 牌组 → 卡牌通过 ID 关联；自动选择卡型、居中、字号和内置字体。
版本 0.1.0。核心定义只有标准 `SKILL.md`，没有平台专用工具依赖。

Agent 负责理解资料、提炼知识和形成内容计划；Python 标准库脚本负责生成唯一 ID、
选择默认样式、编码与校验。没有模型 API、网络请求、自动安装依赖或外传文档。
脚本需要 Python 3.9+；没有 Python 时，Agent 可按格式参考直接输出 JSON 并说明未机器校验。
任意 PDF、扫描教材、图片、Word 的提取需要宿主已有能力；本项目不捆绑 OCR/转换器。

## 安装

先下载或 clone，再进入本目录。默认拒绝覆盖已存在的安装。

```sh
sh install.sh --agent codex --scope user
sh install.sh --agent claude --scope project --project /path/to/project
sh install.sh --agent opencode --scope user
sh install.sh --agent hermes --scope user
sh install.sh --agent codebuddy --scope project --project /path/to/project
```

Windows PowerShell：

```powershell
.\install.ps1 -Agent codex -Scope user
.\install.ps1 -Agent claude -Scope project -Project 'D:\Workspace\My Project'
```

目标 Agent：`codex`、`claude`、`opencode`、`hermes`、`codebuddy`、`custom`。
其他 Agent 用 `--agent custom --dest /verified/skills/parent`；PowerShell 用 `-Dest`。
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
python3 scripts/package.py /path/to/lumecard-deck-builder-0.1.0.zip
```

打包拒绝覆盖，排除缓存和安装清单。独立发布仓库的 CI 覆盖 Linux、Windows 和 macOS；
当前实际执行结果及未验证项见 [测试记录](references/testing.md)。文件结构/示例验证不等于
各 Agent 自动触发成功，也不等于应用 UI 导入验收。发布前对目标 Agent 开新会话，运行
`tests/scenarios.json` 的案例，保存实际发现/触发和成品结果。

维护优先级：P1 跟踪 LumeCard 序列化和渲染格式变更；P1 完成各 Agent、各 OS 的真实加载及
应用导入验收；P2 用用户资料评估卡牌质量和样式；P3 按需加 OCR/自定义字体适配。
每次更新先重跑生成/校验/安装/卸载测试，再复核官方路径文档；兼容性注明日期和证据。

本机校验器过旧时，参见 [独立校验器更新说明](references/validators.md)，使用官方参考实现，
无需手改 Codex 系统技能。
