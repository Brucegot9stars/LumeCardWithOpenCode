# GitHub Actions 远程出包与发布

当前 LumeCardWithOpenCode 仓库使用根目录 `.github/workflows/skill-release.yml`，
在 Actions 中显示为 **Skill Build and Release**。Skill 子目录中的
`.github/workflows/test.yml` 仍是独立仓库的测试模板，不会在本仓库自动执行。

`dist/` 是本地生成产物，保持 Git 忽略，不需要提交压缩包。工作流从对应提交的源码重新
打包。版本唯一来源是 SKILL.md 的 `metadata.version`，当前只接受稳定版 `X.Y.Z`。

## 手动出包：下载 Actions Artifact

1. 将根目录工作流提交并推送到远程；手动触发入口需先在仓库默认分支存在。
2. 在 GitHub 打开 Actions → Skill Build and Release → Run workflow。
3. 选择要打包的分支或标签并运行。
4. 等待全部测试、构建步骤成功，在该运行页面的 Artifacts 下载
   `lumecard-deck-builder-<version>`。

手动运行仅生成 Artifact，不创建公开 Release，即使选择的 ref 是标签也是如此。
Artifact 保留 30 天，仍受仓库/组织的保留策略限制。Artifact 下载后通常还有一层外部 ZIP，
先解开它，里面才是可安装的 Skill ZIP 和 SHA256 文件。

## 标签发布：提供公开下载资产

Skill 标签格式：`lumecard-deck-builder-v<metadata.version>`。例如当前版本 0.3.0：

```sh
# 先确认 HEAD 是要发布且测试通过的提交，再推送该分支。
git push origin HEAD
git tag -a lumecard-deck-builder-v0.3.0 -m 'lumecard-deck-builder 0.3.0'
git push origin refs/tags/lumecard-deck-builder-v0.3.0
```

这些命令由发布者按需执行，新增工作流并不等于已经远程出包或发布。
标签必须指向含根目录发布工作流的提交，且标签版本须与该提交的 SKILL.md 一致，否则
prepare 阶段直接失败。若该标签已存在，不要强制覆盖：检查已有 Release，后续版本更新
SKILL.md 后使用新标签。预发布版本目前不支持，需要另行扩展版本校验与 Release 标识。

标签触发的四组测试全部通过后，工作流会生成：

- `lumecard-deck-builder-0.3.0.zip`
- `lumecard-deck-builder-0.3.0.zip.sha256`

两份文件先上传到 Artifact，再作为资产发布到同名 GitHub Release。
Skill Release 显式设置 `--latest=false`，不会替换应用 Release 的 Latest 标记。
既有应用工作流监听 `v*` 标签，Skill 标签不匹配该规则，不会触发 Android/Windows 应用出包。
已有 Release 不会被覆盖；发布步骤失败时先检查是否已创建了草稿或 Release，再决定后续操作。

## 测试、权限和构建规则

- 矩阵包含 Ubuntu/Windows/macOS 上的 Python 3.11，以及 Ubuntu 上的 Python 3.9 最低版本检查。
- 安装可选 pypdf 后运行整个 Skill 测试集，包括文档、安装器和发布辅助脚本；本工作流不运行 Gradle。
- 全部矩阵成功后才打包，构建和公开发布前都会校验 SHA256。
- prepare/test/build 只有 `contents: read`；仅标签触发的 publish job 使用 `contents: write`。
- 使用 GitHub 自动提供的 GITHUB_TOKEN，无需额外配置发布 Token；仓库/组织策略仍须允许该权限。
- SHA256 校验用于发现文件损坏/变化，不等同于作者数字签名。

## 校验下载文件

在包含 ZIP 和 checksum 的目录中执行，文件名按实际版本替换。

Linux：

```sh
sha256sum --check lumecard-deck-builder-0.3.0.zip.sha256
```

macOS：

```sh
shasum -a 256 -c lumecard-deck-builder-0.3.0.zip.sha256
```

PowerShell：

```powershell
$expectedHash = (Get-Content 'lumecard-deck-builder-0.3.0.zip.sha256').Split(' ')[0]
$actualHash = (Get-FileHash 'lumecard-deck-builder-0.3.0.zip' -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne $expectedHash) { throw 'SHA256 mismatch' }
```

校验通过后解压 Skill ZIP，并按 [安装与使用指南](installation-and-usage.md) 安装。

## 本地复现远程打包

从 Skill 根目录运行，输出目录须没有同名产物：

```sh
python3 scripts/release_skill.py metadata
python3 scripts/release_skill.py build --output-dir dist/release-preview
```

可加 `--expected-tag lumecard-deck-builder-v0.3.0` 验证标签匹配。
该辅助脚本只读版本、生成 ZIP/校验文件，不调用 GitHub API，不创建标签或上传文件。

依据：[GitHub workflow_dispatch 规则](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_dispatch)、
[Artifact 下载](https://docs.github.com/en/actions/tutorials/store-and-share-data)、
[GitHub CLI Release 参数](https://cli.github.com/manual/gh_release_create)。
