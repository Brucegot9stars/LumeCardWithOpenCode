# TASK.md — 闪卡图片支持（阶段二任务拆解，已确认执行）

> 依据：`docs/DESIGN.md`（已评审确认）。执行前提：✅ 2026-11-19 §9 六项人工确认已闭环，全部采纳推荐路线。
> 分支：`feat/card-image-support`（从 `feature/v1_dev` 开出，**不删除任何本地分支**）。
> 原则：迭代最小改动；每块完成即勾选并验证；修复循环 ≤3 轮，超限停机列「待人工确认项」。

**已确认约束（实现时不得偏离）**：
- 图片归属：**整卡级** `metadata["mediaTarget"]`（`front`/`back`/`both`，缺省 `both`），不做逐图 target。
- 图床：**强制 https**，`http` 由校验器 / 导入直接拒绝、运行时失败占位；不改 `network_security_config.xml`。
- 验收：**编译 + 自动化测试 + 静态自审**降级验收，附 `SKIKO_RENDER_API=SOFTWARE` 兜底尝试；如实标注未做真机 GUI 验收。
- skill 版本：升 **0.4.0**，同步 `format.md` 审计基线，按 `uninstall → install` 刷新安装副本。
- 文档：`DESIGN.md` / `TASK.md` 归档至 `docs/`，纳入 git。

## 依赖总览

```
[T1 数据模型] ──→ [T3 加载器] ──→ [T4 卡片渲染+查看器]
      │                                  │
      ├──→ [T2 导入修复]                ├──→ [T5 编辑 UI]
      │                                  │
      └──→ [T7 AI 制卡]                  └──→ [T6 手势隔离接线]
                    │
[T1] ──→ [T8 skills] ──→ [T9 文档+i18n] ──→ [T10 回归与交付]
```

---

## 阶段 A：数据层与基线（先跑通，串行）

- [x] **T1 扩展 `Media` 模型（向后兼容）** · 依赖：无 ✅ 2026-11-19
  - 文件：`shared/src/commonMain/kotlin/com/lumecard/shared/model/Models.kt:79-86`
  - 给 `mimeType`/`size` 加默认值（`""` / `0`），新增 `caption: String = ""`；**不删字段、不改 `CardType`、不动 `.sq`/`.sqm`**
  - 验收：✅ `:shared:compileKotlinDesktop` 通过；旧 JSON（5 字段）反序列化不抛异常（`shared/src/commonTest/.../CardMediaSerializationTest.kt`，4 用例）
- [x] **T2 修复 UI 导入丢失 media** · 依赖：T1 ✅ 2026-11-19
  - 文件：`composeApp/.../ui/screens/settings/SettingsScreen.kt:883-895`
  - 构造 `Card` 时补 `media = ec.media, metadata = ec.metadata, title = ec.title`
  - 验收：✅ `shared/src/commonTest/.../CardImageImportTest.kt`（3 用例：带图保留 media/metadata/title、图床最小字段、纯文本卡 media 为空）；UI 层无单测框架，靠编译 + 静态自审保证
- [x] **T1.5 基线回归** · 依赖：T1、T2 ✅ 2026-11-19
  - 已跑：`:shared:desktopTest` + `:shared:compileKotlinDesktop` + `:composeApp:compileKotlinDesktop` + `:composeApp:compileDebugKotlinAndroid`
  - 验收：✅ 两次 BUILD SUCCESSFUL（2m9s / 2m17s）；既有 9 个测试类 + 7 个新增用例全绿，Desktop 与 Android 双端编译通过；仅存量 deprecation 警告，无新增警告

## 阶段 B：图片加载器（可与阶段 A 并行设计，实现串行）

- [x] **T3 实现 `CardImageLoader`** · 依赖：T1
  - 新增 `composeApp/src/commonMain/.../platform/CardImageLoader.kt`：`ImageRef`/`ImageSourceKind`/`MediaTarget`、三态路径解析（相对 media 根 / `file://`·`content://` / `https`）、`loadImage(): ImageLoadState`
  - 内存 LRU（按像素预算，≤32 张）+ URL 磁盘缓存（sha1 命名，落 media/cache）；`suspend` + `Dispatchers.Default`
  - ~~复用 `SharedModule.kt:33-42` 已注册 `HttpClient`~~ → **实际改为平台 `HttpURLConnection`**（零新依赖、避免 commonMain 引入 Ktor 客户端）：15s connect / 30s read、20MB 上限、仅 https、手动校验 2xx；详见 DESIGN §7「实现与设计的偏差」
  - 扩展 `PlatformImageLoader.kt` 两端 actual 新增 `platformDecodeImage(bytes)`；**保留原 `platformLoadImage(path)` 不破坏启动语景**
  - Android 解码加 `inSampleSize` 降采样；Desktop 用 Skia `Image.makeFromEncoded`
  - 验收：无新增第三方依赖（若需显式声明 ktor，版本常量只能放 `buildSrc/Dependencies.kt`）；三类 source 单测/手动冒烟；失败返回稳定占位不抛异常

## 阶段 C：UI（核心功能）

- [x] **T4 卡片内图片渲染 + 无闪烁** · 依赖：T3
  - 文件：`composeApp/.../ui/screens/study/CardComponents.kt`
  - `CardFace` 的 `Column` 尾部追加 `CardImageBlock`：`ContentScale.Fit` + 显式 `aspectRatio` + `heightIn(max = maxHeight * 0.45f)` + `clip` + 圆角
  - 按 `metadata["mediaTarget"]` 过滤 FRONT/BACK/BOTH；`media` 为空时**完全不进入新分支**
  - `remember(source)` 命中 LRU，翻转重组不重解码；加载/失败固定占位尺寸
  - 验收：7 种卡片类型逐一冒烟；纯文本卡渲染与改动前像素级一致；宽高比正确、无溢出
- [x] **T5 独立图片查看器** · 依赖：T4
  - 同文件新增全屏 `Dialog`（`usePlatformDefaultWidth=false`）+ `ImageViewerState`
  - 捏合 `detectTransformGestures` + 双击 1x/2.5x + 「+/−/适应/1:1」按钮；scale 钳制 `[1f,5f]`；`graphicsLayer` 平移带边界
  - 关闭：按钮 / 点背景 / 返回键；底部 caption
  - `AnimatedVisibility` `fadeIn/out + scaleIn(0.92f)/scaleOut`，`tween(180ms)`，退出动画播完再移除节点
  - **与缩略图共享同一 `ImageBitmap`，进入不重新加载；Dialog 不参与卡片 3D 旋转/`scaleX` 镜像、不在 `key(card.id)` 子树内**
  - 验收：进入/退出无白屏、无镜像、无闪烁；缩放平移手感正常
- [x] **T6 手势隔离与溢出保护** · 依赖：T4、T5
  - 文件：`composeApp/.../ui/screens/study/StudyScreen.kt`
  - 图片 `clickable` 消费点击，避免 `:454-462` 的 `detectTapGestures { flipCard() }` 误翻卡；查看器打开期间状态位屏蔽 `:379-433` 滑动手势
  - vCenter 模式（`:452` 不挂 verticalScroll）下靠高度封顶防溢出
  - 验收：点图不翻卡、翻卡不影响图片状态、查看器期间不误触切换卡片
- [x] **T7 编辑 UI 录入** · 依赖：T3、T4
  - 文件：`CreateCardScreen.kt` + `CardViewModel.kt:92-160`
  - 新增「本地选图」（复用 `pickMediaFile()` / `saveMediaFile()`）与「图床 URL」录入区；可删除、可设 `mediaTarget`、可选 caption
  - `createCard`/`updateCard` 新增 `media: List<Media>` 参数；**`updateCard` 用 `card.copy(media = newMedia)` 显式保留，不丢既有图片**
  - 按 AGENTS.md:64：先改 `I18nStrings.kt` 接口，再改 5 份 `Strings*.kt`
  - 验收：新建/编辑带图卡片持久化成功；编辑纯文本卡不产生 media；重复编辑图片不丢失

## 阶段 D：AI 制卡与 skills（与阶段 C 并行）

- [x] **T8 AI 制卡提示词与解析** · 依赖：T1
  - 文件：`shared/.../resources/config/prompt_ai_card.md:27-42`、`AiCardModels.kt:72-86`、`AiCardGenerator.kt:223-243`
  - 输出格式增可选 `media` 字段（元素含 `filePath`/可选 `caption`）与约束：仅接受用户提供的路径或 https URL，**禁止编造 URL**
  - `AiCardItemJson` 增 `media: List<AiMediaJson> = emptyList()`；`createCards` 映射进 `Card(media=...)`
  - 确认 `extractCardsFallback` 的 brace-depth 扫描对新字段无影响（整对象反序列化）
  - 文档 `AI_CARD_DELIVERY.md` / `AI_CARD_DESIGN.md` 同步，并修正 `prompt_ai_card.txt` → `.md`
  - 验收：AI 返回带 media 的 JSON 能入库并渲染；不带 media 的旧响应行为不变
- [x] **T9 skills 源目录（lumecard-deck-builder）** · 依赖：T1、T8（字段口径一致）
  - 文件：`skills/lumecard-deck-builder/scripts/build_deck.py`
  - `build()` 接受 plan 的 `media`；**解除 `:194` 的 `external media not supported` 禁令**，改为结构校验：`id`/`type=IMAGE`/`filePath`（非空、相对名不含 `..`、URL 必须 https）/可选 `caption`；`validate()` 放行 `mediaTarget` 等 metadata key，`TYPES` 仍为 5 种不变
  - `references/format.md`：补**图片字段、输入格式、展示规则（自适应/保比例/不溢出）、查看交互（点击/缩放/关闭/过渡）**说明
  - `references/authoring.md`：补图片卡著录规则（何时配图、图注、不臆造 URL）
  - `references/installation-and-usage.md:169-171`、`references/testing.md`、`references/validators.md`：清除「不支持图片」残留表述
  - `examples/plan.json` / `examples/import.json` 增加带图示例；`tests/test_skill.py` 加正/反例（http 拒绝、`..` 拒绝、旧空 media 通过）
  - 验收：`python scripts/build_deck.py build` 生成带图 JSON → `validate` 通过；`python -m unittest discover` 全绿
- [x] **T9.5 同步安装副本（禁止手工改）** · 依赖：T9
  - ⚠️ `.workbuddy/skills/lumecard-deck-builder/` 含 `.lumecard-install.json` 哈希清单
  - 流程：先改 `skills/` 源 → `python .workbuddy/skills/lumecard-deck-builder/scripts/install.py --agent custom --dest .workbuddy/skills --uninstall` → 从新源重新 `install`
  - 验收：`diff -rq` 两份除 `.lumecard-install.json` 外完全一致；托管标记有效（可再次 --uninstall）
- [x] **T10 文档 + 帮助中心 + i18n** · 依赖：T4、T9
  - `README.md:29,47-48,311-319`：移除两条 ❌ TODO，补图片能力、限制（https / 相对路径 / CSV 不支持图片 / 查看器交互）
  - `AGENTS.md`：Cloze Cards 之后新增「Card Images」约定段（media 三态语义、mediaTarget、展示规则、查看交互、校验器约束）
  - 5 语言 `shared/.../resources/help/{zh-CN,en,ja,es,zh-TW}/articles.json`：卡片类型段补图片说明 + 更新日志
  - 验收：全文检索无「不支持图片 / external media not supported」残留；文档描述与 `build_deck.py` 校验规则一致

## 阶段 E：回归与交付

- [x] **T11 契约与回归测试** · 依赖：全部
  - ✅ 阶段 A 已提前完成：`shared/src/commonTest/.../CardMediaSerializationTest.kt`（旧 JSON 兼容 + 新字段往返）与 `CardImageImportTest.kt`（导入契约）
  - 扩展 `shared/src/desktopTest/.../SkillImportContractTest.kt` 加带图卡用例（需 `LUMECARD_SKILL_CONTRACT_DIR` 环境变量，否则跳过）
  - 全量：`:shared:desktopTest` + `:shared:compileKotlinDesktop` + `:composeApp:compileKotlinDesktop` + `:composeApp:compileDebugKotlinAndroid`
  - 验收：全绿；记录命令与结果
- [x] **T12 自审与交付** · 依赖：T11
  - 对照 DESIGN.md §7 九条验收标准逐项核对；代码自审（无越界改动、无密钥、无破坏性 git 操作）
  - 变更摘要（改了哪些文件 / 为什么 / 影响范围）+ Conventional Commits 提交（如 `feat(cards): support local and remote images with flicker-free viewer`）
  - ⚠️ **视觉验收风险**：`:composeApp:run` 的 exit 1 未根治，若无法启动 GUI，如实标注「未做真机视觉验收」，并给出 `SKIKO_RENDER_API=SOFTWARE` 兜底尝试结果，**不虚报**
  - 验收：DESIGN.md §7 每条标注 ✅已验证 / 🟡推测 / ⚠️未验证

---

## 门禁规则

1. 每块完成 → 勾选 + 跑对应验收；未通过不进入下一块。
2. 修复循环 ≤3 轮；超限停止并输出「待人工确认项」。
3. **破坏性变更需停下来问**：删字段、改接口契约、删文件、改 `CardType` 枚举、动 DB schema。
4. 不擅自添加方案范围外功能；不重构无关代码；不改 force pin。
5. 阶段 A 基线未绿，不进入阶段 C。

## 当前进度

- [x] 阶段一：现状调研、需求澄清、DESIGN.md、TASK.md
- [x] 人工确认：§9 六项交互式确认闭环（2026-11-19，6/6 采纳推荐）
- [x] 文档归档：DESIGN.md / TASK.md → `docs/`
- [x] 阶段二启动：已开 `feat/card-image-support` 分支（`feature/v1_dev`、`main` 保留完好）
- [x] **阶段 A（T1 / T2 / T1.5）已完成并验证通过**（基线双端编译 + 测试全绿）
- [x] **阶段 B（T3 加载器）**：`CardImageLoader.kt` + 两端 actual，三态路径 / 强制 https / LRU + sha1 磁盘缓存；实际用平台 `HttpURLConnection`（零新依赖，见 DESIGN §7 偏差记录）；双端编译通过
- [x] **阶段 C（T4–T7 UI）**：`CardImageViews.kt`（图片块 + 独立查看器 + 180ms 过渡）、`CardComponents.kt` / `StudyScreen.kt` 手势隔离与溢出保护、`CardImageEditor.kt` 录入 + ViewModel media 保留、i18n 接口 + 5 语言；双端编译通过
- [x] **阶段 D（T8–T10 AI / skills / 文档）**：AI 模型 + 提示词 + 过滤规则；skill 升 0.4.0（解除 media 禁令、references / examples / tests / README 同步）；安装副本 uninstall→install 刷新且 `diff -rq` 一致；README / AGENTS.md / 5 语言帮助中心 / ResourceLoader 注释同步，残留检索为空
- [x] **阶段 E（T11–T12 回归交付）**：Python 79 tests OK（skipped=3）；Gradle 全量 `BUILD SUCCESSFUL in 3m 3s`，`:shared:desktopTest` 72 tests / 0 fail / **0 skipped**，`SkillImportContractTest` 真实执行（10 fixtures / 3 accepted，含 `image_https` 正例与 `image_missing_id` 反例）PASSED；DESIGN §7 九条逐条标注；代码自审通过
- ⚠️ **视觉验收**：`:composeApp:run` 的 exit 1 未根治，本期按确认结论降级验收（编译 + 自动化测试 + 静态自审），**未做真机 GUI 视觉验收**，也未接真实 LLM 端到端生成，均如实标注不虚报
