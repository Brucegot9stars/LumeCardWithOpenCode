# DESIGN.md — 闪卡图片支持（阶段一设计稿，已评审确认）

> 状态：**已评审确认（2026-11-19，§9 六项人工确认全部闭环）**，阶段二已启动。
> 结论先行：✅ 现状已查清（数据层就绪、4 处缺口）；✅ 技术路线已定（复用 media、零 DB 迁移、零新依赖）；✅ §9 待确认项已逐项交互确认（6/6 采纳推荐）；⚠️ 桌面端 `:composeApp:run` 的 exit 1 未根治，按确认结论采用「编译 + 自动化测试 + 静态自审」降级验收，真机视觉验证延后。

---

## 1. 需求确认（已与用户澄清，4/4 采纳推荐路线）

| 决策项 | 结论 | 取舍理由 |
|---|---|---|
| 图片引用存放 | **复用 `Card.media: List<Media>`，零 DB 迁移** | `media TEXT` 列、`ExportCard.media`、备份/同步/`toDomain()` 已全部打通；不动 schema、`CardType`、三处 `version.properties` |
| 图床链接加载 | **自研，零新依赖** | 复用现有 ktor-client 2.3.12 + expect/actual 解码 + 内存/磁盘缓存；规避 `material3:1.9.0` / `lifecycle-runtime:2.8.7` 的 force pin 冲突 |
| 本期范围（M-S-C） | **全量闭环（M）** | 渲染 + 查看器 + 编辑 UI 录入 + 修复导入丢字段 + skills/AI 提示词/文档同步 |
| 图片呈现形式 | **独立图块，不碰 front/back 文本** | CLOZE 正则、MC 的 `split("\n")`、Markdown/RichText 管线完全不受影响，纯文本卡行为零变化 |

**原始需求逐条映射**：
- 兼容本地资源路径 + 图床链接 → §3.2 `ImageRef` 三态解析
- 按屏幕尺寸自适应缩放、保持比例、不溢出/变形 → §4.1
- 点击图片进入独立查看模式、支持手动缩放 → §4.2
- 流畅无闪烁的进入/退出过渡 → §4.3
- 不影响现有纯文本卡 → §6 回归策略
- 同步 skills / AI 制卡提示词 / 文档 → §7 文档与提示词同步

**优先级**：M = 上述全部；S = 编辑 UI 的拖拽/剪贴板贴图（已有平台能力但 UI 零调用，本期以「选择文件 + 填 URL」最小可用交付）；C = 图片标注/遮挡（Image Occlusion）、`<img>` 内联、CSV 带图（明确不做）。

### 1.1 人工确认项结论（2026-11-19 交互式确认，6/6 采纳推荐）

| # | 确认项 | 结论 | 对方案的影响 |
|---|---|---|---|
| 1 | 图片归属粒度 | **整卡级** `metadata["mediaTarget"]`（`front`/`back`/`both`，缺省 `both`） | 不做逐图 target；skill / AI 输入只多一个可选 metadata key，校验器放行该 key |
| 2 | `http` 图床 | **强制 https**，不放开 Android 明文 | `network_security_config.xml` 保持 `cleartextTrafficPermitted=false`；校验器与文档拒绝 http，运行时显示失败占位 |
| 3 | 桌面端视觉验收 | **接受降级验收** | 验收 = 编译 + 自动化测试 + 静态自审 + `SKIKO_RENDER_API=SOFTWARE` 兜底尝试；如实标注「未做真机 GUI 验收」，视觉验证延后至 run 问题修复后 |
| 4 | skill 版本号 | **升到 0.4.0** | 更新 `SKILL.md` version、`format.md` 审计基线；按 `uninstall → install` 刷新安装副本与哈希清单 |
| 5 | 设计文档归属 | **移入 `docs/` 并纳入 git** | `DESIGN.md` / `TASK.md` 归档至 `docs/`，根目录保持干净 |
| 6 | 阶段二启动 | **确认，开始开发** | 开 `feat/card-image-support` 分支，按 TASK.md 逐块实现、每块自审与构建验证 |

---

## 2. 现状基线（调研结论，均经代码核实）

✅ **已就绪**：`shared/.../model/Models.kt:48-91` 的 `Card.media` / `Media` / `MediaType.IMAGE`；`LumeCardDatabase.sq:38` 的 `media TEXT DEFAULT '[]'`；`ExportManager.kt:111`、`BackupRestoreService.kt`、`SyncManager.kt` 的 `toCard/toExport`、`SqlDelightRepositories.kt` 两份 `toDomain()` 均保留 media/metadata。

⚠️ **四个真实缺口**：
1. `CardComponents.kt:161-302` `CardFace` 只渲染 front/back，全文件无 `card.media`；`FlipCard:126-158` 背面 `scaleX=-1f` 会镜像图片，`key(card.id)`（`CardContent:50`）换卡重建子树 → 闪烁主因。
2. `CardViewModel.createCard/updateCard`（`:92-160`）不接收也不写入 media；`Platform.kt:38,41` 的 `pasteClipboardMedia`/`saveMediaFile`、`FilePicker.kt:18` 的 `pickMediaFile` 已实现但 UI 零调用。
3. **`SettingsScreen.kt:883-895` UI 导入 share JSON 时构造 `Card` 漏传 `media`/`metadata`/`title`** → 导出→导入会静默丢图（最隐蔽）。
4. `PlatformImageLoader` expect/actual 只支持本地同步解码，URL 一律返回 null、无缓存；项目无任何图片加载库。

⚠️ **skill/提示词阻塞点**：`skills/lumecard-deck-builder/scripts/build_deck.py:194` `require(card.get("media", []) == [], "external media not supported")` 硬禁 media；`:144-145` 强制 `media=[]`；`references/format.md` / `authoring.md` / `installation-and-usage.md:169-171` 均声明不支持图片。**`skills/` 源目录与 `.workbuddy/skills/` 安装副本是两份，必须同步**。AI 侧 `prompt_ai_card.md:27-42`、`AiCardItemJson`（`AiCardModels.kt:72-86`）、`AiCardGenerator.createCards:223-243` 三处只认 5 个文本字段。

---

## 3. 技术方案

### 3.1 备选方案与取舍（每个关键决策至少 2 选）

| 决策 | 方案 A（采纳） | 方案 B（未采纳） | 取舍 |
|---|---|---|---|
| 数据承载 | 复用 `Card.media` + `metadata` 记 `target` | 新增 `CardType.IMAGE` / 新 DB 列 | B 需 `.sqm` 迁移、放宽 `DatabaseDriverFactory.desktop.kt:169` 的 `check(to <= 3L)`、改 3 处 SQL、备份 `CardType.valueOf` 严格校验有兼容风险；A 零迁移且导出/备份/同步现成 |
| 网络加载 | 自研：ktor `HttpClient` + expect/actual 解码 + 缓存 | 引入 Coil3 / Kamel | B 有成熟缓存与生命周期，但需新增依赖，与 force pin 冲突风险高（`docs/p2-verification-handoff.md:9` 明确警告勿动）；A 复用现有 `SharedModule.kt:33-42` 的 HttpClient，零依赖 |
| 查看器容器 | 全屏 `Dialog`/`Popup` + `AnimatedVisibility` 淡入淡出/缩放，共享已解码 bitmap | Voyager 新 Screen 导航 | B 换页会销毁/重建卡片树、跨页面共享元素复杂、返回易闪；A 原位覆盖、不重建卡片、共享 bitmap 天然无二次解码 |
| 正反面归属 | `metadata["mediaTarget"]` = `front`/`back`/`both`（默认 `both`） | 给 `Media` 加 `target` 字段 | B 需改 `@Serializable` 数据类；A 用现成 `Map<String,String>`，零模型改动（但校验器需放行新 key） |
| 图片语法 | 独立图块（`CardFace` 的 Column 尾部） | `![](path)` 内联到 front/back | B 会污染 CLOZE 的 `[^}]+` 与 MC 的 `split("\n")`，且要改 mikepenz Markdown 与自定义 `RichTextCardFace`；A 隔离彻底 |

### 3.2 数据模型与接口设计

**`Media` 模型**（`shared/.../model/Models.kt:79-86`）**不删字段、只加带默认值的可选字段**，这是向后兼容的硬要求：`SqlDelightRepositories.kt:453,472` 用 kotlinx **默认 Json（严格模式）** 反序列化，新字段若无默认值，旧 JSON 会抛异常被 `catch` 吞成 `emptyList()` → 静默丢图。

```kotlin
@Serializable
data class Media(
    val id: String,
    val type: MediaType,
    val filePath: String,          // 语义扩展见下
    val mimeType: String = "",     // 图床可空，新增默认值
    val size: Long = 0,            // 图床可 0，新增默认值
    val caption: String = "",      // 可选图注
)

enum class ImageSourceKind { LOCAL_RELATIVE, LOCAL_URI, NETWORK }

data class ImageRef(
    val mediaId: String,
    val source: String,            // 原始 filePath
    val kind: ImageSourceKind,
    val target: MediaTarget,       // FRONT / BACK / BOTH
    val caption: String,
)
enum class MediaTarget { FRONT, BACK, BOTH }
```

**`filePath` 三态约定**（与现有 `Platform.desktop.kt:158-163` 的 `![image]($hash.png)` 及 WebDAV SHA-1 manifest 对齐）：
- `LOCAL_RELATIVE`：相对 media 根目录的文件名，如 `a1b2...png`；经 `getMediaBasePath()` + `resolveMediaFilePath()`（`Platform.kt:27,35`，已含防符号链接逃逸）解析。跨设备可同步。
- `LOCAL_URI`：`file://` / `content://` / 绝对路径；Android 走现有 `contentResolver.openInputStream`，Desktop 走 `java.io.File`。不可跨设备。
- `NETWORK`：**仅 `https://`**（✅ 已确认强制 https，不放开明文）。⚠️ Android `network_security_config.xml:3` `cleartextTrafficPermitted=false` 会拦截 http；`http://` 由 skill 校验器与导入校验**直接拒绝**，运行时若出现则显示稳定失败占位，不改网络安全配置。

**正反面归属**（✅ 已确认，整卡级）：`card.metadata["mediaTarget"]`，取值 `front`/`back`/`both`，缺省 `both`。本期**不支持逐图指定**，`mediaTarget:<mediaId>` 形式留给后续版本；校验器只放行 `mediaTarget` 这一个 key。

**图片加载器**（`composeApp/src/commonMain/.../platform/CardImageLoader.kt`）：
```
expect suspend fun platformDecodeImage(bytes: ByteArray): ImageBitmap?   // Android BitmapFactory / Desktop Skia
suspend fun loadImage(ref: ImageRef): ImageLoadState                     // Loading / Success(bitmap) / Failure
  ├─ LOCAL_* → platformReadFileBytes(resolve(...))
  ├─ NETWORK → injected ktor HttpClient.get(source).readBytes()  (expectSuccess=false → 手动校验 status)
  └─ decode → 内存 LRU(≤32 张 / 按像素预算) + 磁盘缓存(url 的 sha1，落 media/cache)
```
- 全程 `suspend` + `withContext(Dispatchers.Default)`，解码不阻塞 UI。
- 结果用 `remember(source) { ... }` / `produceState` 缓存，**不随翻转重组重建**（§4.3）。
- 失败返回稳定占位（固定尺寸 Box），不抛异常、不闪烁。

### 3.3 平台与依赖改动

- `composeApp/build.gradle.kts` commonMain 无需新增依赖（ktor-client-core 已在 `shared` commonMain，composeApp 通过 `project(":shared")` 传递可获得；若传递不可用，则按 AGENTS.md:46 在 `buildSrc/Dependencies.kt` 加常量后显式声明 `ktor-client-core`）。
- 新 expect/actual：`platformDecodeImage`（2 端）、`platformReadImageBytes`（2 端，或复用 `PlatformPath.kt` 的 `platformReadFileBytes`）。
- Android：`INTERNET` 权限已具备（`AndroidManifest.xml:4`），**无需改动**；`file_paths.xml` 本期不动（不做图片外分享）。
- **不改** SQLDelight schema、`CardType` 枚举、三处 `version.properties`、`EXPORT_VERSION`/`SCHEMA_VERSION`。

---

## 4. UI 与交互设计

### 4.1 卡片内自适应缩放（保持比例、不溢出、不变形）

`CardFace` 的 `Column` 尾部追加 `CardImageBlock`，仅渲染 `card.media.filter { it.type == IMAGE && target 匹配当前面 }`：
```
BoxWithConstraints(Modifier.fillMaxWidth()) {
    val maxH = maxHeight * 0.45f              // 上限：卡片可用高度的 45%
    Image(
        painter = BitmapPainter(bitmap),
        contentDescription = caption,
        contentScale = ContentScale.Fit,       // 保持宽高比，不裁剪不拉伸
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxH)              // 防溢出
            .aspectRatio(bitmap.width / bitmap.height.toFloat(), matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpenViewer(ref) }   // 独立点击，不冒泡到翻卡手势
    )
}
```
- `ContentScale.Fit` + 显式 `aspectRatio` 双保险；高度封顶避免 `StudyScreen.kt:452` vCenter 模式下不挂 `verticalScroll` 时溢出。
- 多图：`LazyColumn`/`Column` 顺序排列，单图上限生效；加载中与失败态固定占位尺寸，避免布局跳动。
- 纯文本卡 `media` 为空 → 不进入该分支，渲染路径与现状完全一致。

### 4.2 独立图片查看模式（手动放大/缩小）

全屏 `Dialog`（`usePlatformDefaultWidth = false`）+ 独立 `ImageViewerState`：
- 缩放：`pointerInput` + `detectTransformGestures`（双指捏合）+ 双击切换 1x/2.5x + 「+ / − / 适应屏幕 / 1:1」按钮；scale 钳制 `[1f, 5f]`，`graphicsLayer { scaleX/scaleY; translationX/Y }` 平移，带边界约束防止拖出屏幕。
- 关闭：右上角关闭按钮、点击背景、返回键（BackHandler / `onKeyEvent`）。
- 图注：底部显示 `caption`（可选）。
- 状态用 `remember` 保存在 Dialog 内，打开时复用**已解码的同一 bitmap**（不重新下载/解码）。

### 4.3 无闪烁进入/退出过渡（关键）

问题根源：`FlipCard` 用 `if (rotation < 90f) front() else back()` 切换子树 + `key(card.id)` 重建 + 背面 `scaleX=-1f` 镜像 + 3D `graphicsLayer`。若把大图叠在翻转卡片上，会出现镜像、重组、二次解码三重闪烁。

对策（按优先级）：
1. **查看器隔离**：Dialog 是独立窗口/层，**不参与卡片的 3D 旋转与 `scaleX` 镜像**，也不在 `key(card.id)` 子树内。
2. **共享 bitmap**：缩略图与查看器读同一份内存缓存的 `ImageBitmap`，进入时不重新加载 → 无白屏。
3. **过渡动画**：`AnimatedVisibility` 的 `fadeIn/fadeOut + scaleIn(0.92f)/scaleOut`，`tween(180ms, FastOutSlowIn)`；退出动画播完再移除节点（`visible` 驱动，不用 `if` 硬切）。
4. **解码缓存**：`remember(source)` + 全局 LRU；翻转重组时命中缓存，同步返回，无重解码。
5. **手势隔离**：图片 `clickable` 消费点击，避免 `StudyScreen.kt:454-462` 的 `detectTapGestures { flipCard() }` 误触发；查看器打开期间通过状态位屏蔽滑动手势（`:379-433`）。

---

## 5. 改动清单（文件级，最小增量）

**A. 加载器与平台（新增 + 扩展）**
- 新增 `composeApp/.../platform/CardImageLoader.kt`（commonMain）：`ImageRef` 解析、缓存、`loadImage`。
- 扩展 `PlatformImageLoader.kt` 两端 actual：新增 `platformDecodeImage(bytes)`；保留原 `platformLoadImage(path)` 供启动语景背景使用（不破坏现有调用）。
- `SharedModule.kt` 复用已注册 `HttpClient`；必要时 `Dependencies.kt` 加 ktor 常量。

**B. UI（改现有文件）**
- `CardComponents.kt`：`CardFace` 追加 `CardImageBlock`；新增 `ImageViewerDialog`；`CardContent` 传递 `onOpenImage`。
- `StudyScreen.kt`：接线查看器状态、手势隔离、vCenter 溢出保护。
- `CreateCardScreen.kt` + `CardViewModel.kt`：新增「本地选图 / 图床 URL」录入区；`createCard`/`updateCard` 增 `media: List<Media>` 参数，**update 必须保留既有 media**（`card.copy(media = ...)` 不能丢）。
- `SettingsScreen.kt:883-895`：**补 `media = ec.media, metadata = ec.metadata, title = ec.title`**。
- `I18nStrings.kt` + 5 份 `Strings*.kt`：新增图片相关文案（按 AGENTS.md:64 先接口后实现）。

**C. skills（两份副本同步；✅ 确认升版 0.4.0）**
- `skills/lumecard-deck-builder/scripts/build_deck.py`：`TYPES` 不变；`build()` 接受 plan 的 `media`；**解除 `:194` 禁令**并改为结构校验（id/type/filePath/可选 caption、URL 必须 https、相对名不含 `..`）；`validate()` 放行 `mediaTarget` 等 metadata key。
- `references/format.md`：补图片字段、输入格式、展示规则、查看交互说明。
- `references/authoring.md`：图片卡著录规则（何时配图、图注、不臆造 URL）。
- `references/installation-and-usage.md:169-171`、`references/testing.md`、`references/validators.md`：同步。
- `examples/plan.json` / `examples/import.json`：补带图示例；`tests/test_skill.py` 加校验用例。
- ⚠️ **`.workbuddy/skills/lumecard-deck-builder/` 是安装副本，含 `.lumecard-install.json` 哈希清单，禁止手工改**：按「先改 `skills/` 源 → `install.py --uninstall` → 重新安装」流程同步，否则托管卸载会失效。

**D. AI 制卡（三处同步）**
- `shared/.../resources/config/prompt_ai_card.md`：输出 JSON 增可选 `media` 字段与格式约束（只接受用户提供/https URL，禁止编造）。
- `AiCardModels.kt`：`AiCardItemJson` 增 `media: List<AiMediaJson> = emptyList()`。
- `AiCardGenerator.kt:223-243`：`createCards` 把 media 映射进 `Card`；`extractCardsFallback` 的 brace-depth 扫描天然兼容新字段（整对象反序列化）。
- `AI_CARD_DELIVERY.md` / `AI_CARD_DESIGN.md`：同步字段说明，并修正文档里 `prompt_ai_card.txt` → `.md` 的过期描述。

**E. 文档**
- `README.md:29,47-48,311-319`：移除两条 ❌ TODO，补图片能力、限制（https / 相对路径 / CSV 不支持图片）。
- `AGENTS.md`：在 Cloze Cards 之后新增「Card Images」约定段（media 语义、三态路径、mediaTarget、查看器交互、校验器约束）。
- 5 语言帮助中心 `shared/.../resources/help/{zh-CN,en,ja,es,zh-TW}/articles.json`：卡片类型段补图片说明 + 更新日志。

**F. 测试**
- 新增 `shared/src/desktopTest/.../CardMediaSerializationTest.kt`：旧 JSON（无新字段）→ 新模型反序列化不丢；新字段往返。
- 新增 `shared/src/desktopTest/.../CardImageImportTest.kt`（或扩展 `SkillImportContractTest.kt`）：share JSON 带 media 导入后保留。
- `SkillImportContractTest.kt:16-48`：增加带图卡用例。
- skill 侧 `tests/test_skill.py`：media 校验正/反例（含 http 拒绝、`..` 拒绝）。

---

## 6. 回归与兼容策略

- **纯文本卡零影响**：`media` 为空时不进入任何新分支；`CardFace` 现有 7 类型分支代码不动，仅在 Column 尾部追加。
- **序列化兼容**：`Media` 新字段全部带默认值；`ExportManager.kt:183-186` 的 `ignoreUnknownKeys=true` 保证旧客户端读新 JSON 不炸；`SqlDelightRepositories` 严格 Json 读旧 JSON 靠默认值通过。
- **不改 schema / 枚举 / 版本号**：`BackupRestoreService.kt:189` 的 `CardType.valueOf` 严格校验不受影响；无 `.sqm` 迁移，无 `SCHEMA_VERSION` 变更。
- **导入链路补齐**：修复 `SettingsScreen` 后，导出→导入、WebDAV 备份、skill 生成三条链路 media 均完整。
- **全量回归**：`:shared:desktopTest`（含新增用例）+ `:shared:compileKotlinDesktop` + `:composeApp:compileKotlinDesktop` + `:composeApp:compileDebugKotlinAndroid`；Python 侧 `python -m unittest discover skills/.../tests`。

---

## 7. 验收标准（可度量）

1. ✅ 本地相对路径、本地 URI、`https` URL 三类图片均能在卡片内显示；`http` 在 Android 被拒且有明确失败占位。
2. ✅ 图片按可用宽度自适应、≤45% 高度、保持原始宽高比，无裁剪/拉伸/溢出；纯文本卡渲染与交互与改动前一致。
3. ✅ 点击图片进入全屏查看器；捏合、双击、按钮均可缩放（1x–5x），可平移、可关闭；进入/退出 180ms 淡入淡出 + 缩放，无白屏、无镜像、无闪烁。
4. ✅ 编辑界面可添加本地图片与图床 URL、可删除、可设 `mediaTarget`；编辑已有卡片不丢失原有 media。
5. ✅ 带图卡片经导出→导入、备份还原、WebDAV 同步后 media 完整保留。
6. ✅ skill 生成的带图 share JSON 通过 `build_deck.py validate`，且通过 `SkillImportContractTest`。
7. ✅ AI 提示词含图片字段与格式说明，AI 返回带 media 时能正确入库并渲染。
8. ✅ README / AGENTS.md / 5 语言帮助中心 / i18n / skill references 与实现一致，无「不支持图片」的残留表述。
9. ✅ 全量编译与测试通过（见 §6）。

> ⚠️ **验收方式（已确认）**：由于 `:composeApp:run` 的 exit 1 未根治，第 3 条「无白屏 / 无镜像 / 无闪烁」等视觉类断言本期以**静态自审 + 代码级保证（共享 bitmap、Dialog 隔离、AnimatedVisibility）+ 编译 / 单测**为验收依据，并附 `SKIKO_RENDER_API=SOFTWARE` 兜底启动尝试结果；**如实标注未做真机 GUI 验收**，不虚报。真机视觉验证延后到 run 问题修复后补做。

### 验收执行结果（T12 自审，2026-11-19）

| # | 标准 | 状态 | 依据 |
|---|---|---|---|
| 1 | 三类图片显示 / http 拒绝 | ✅ 已验证（代码级）· 🟡 未做真机目视 | `CardImageLoader` 三态解析、`http://` 直接返回 `Failure`；Desktop/Android actual 双端编译通过；`image_https` 契约用例通过 |
| 2 | 自适应缩放 / 纯文本卡零变化 | ✅ 已验证（代码级）· 🟡 未做真机目视 | `ContentScale.Fit` + `aspectRatio(matchHeightConstraintsFirst)` + `heightIn(max=320dp/45%)` + 固定占位；`media` 为空不进入新分支；72 tests 全绿 |
| 3 | 查看器交互 / 无闪烁 | 🟡 代码级保证 · ⚠️ **未做真机 GUI 验收** | 独立 `Dialog` + 共享 `ImageBitmap` + `AnimatedVisibility` 180ms + LRU；`:composeApp:run` exit 1 未根治，无法目视确认「无白屏/无镜像/无闪烁」 |
| 4 | 编辑录入 / media 保留 | ✅ 已验证（代码级 + 编译） | `CardImageEditor` 本地选图 / https URL / caption / `mediaTarget`；`updateCard` 默认 `media = card.media`；双端编译通过；UI 层无自动化测试框架，靠静态自审 |
| 5 | 导出→导入 / 备份 / 同步 media 完整 | ✅ 部分验证 | `SettingsScreen` 补齐 `media`/`metadata`/`title`；`CardImageImportTest` 3 例 + `CardMediaSerializationTest` 4 例 + 契约 10 fixtures 全通过；WebDAV/备份链路复用既有代码，靠静态自审 + `MediaSyncServiceTest` 7 例 |
| 6 | skill 带图 JSON 通过 validate + 契约测试 | ✅ 已验证 | Python 79 tests OK；`build_deck.py` 带图示例 build/validate 通过；fixtures 10 例 / 3 accepted；`SkillImportContractTest` PASSED（未跳过） |
| 7 | AI 提示词 / 带 media 入库 | ✅ 已验证（代码级 + 编译）· 🟡 未接真实 LLM | `AiMediaJson` + `mediaTarget`、过滤 `http://`/`..`/空路径、`prompt_ai_card.md` Image Rules；双端编译通过 |
| 8 | 文档 / i18n / 无残留 | ✅ 已验证 | README / AGENTS.md / 5 语言 articles.json（JSON 合法）/ 5 份 `Strings*.kt` / skill references 全量同步；残留检索为空；安装副本 `diff -rq` 一致（32 托管文件） |
| 9 | 全量编译与测试 | ✅ 已验证 | Gradle `BUILD SUCCESSFUL in 3m 3s`；`:shared:desktopTest` 72 tests / 0 fail / 0 skipped；Python 79 tests OK（skipped=3） |

**实现与设计的偏差（如实记录，不影响验收）**：
- T3 / §3.1 计划「复用 `SharedModule` 已注册的 Ktor `HttpClient`」，实际采用平台原生 `HttpURLConnection`（Desktop `URI(url).toURL()`、Android `HttpURLConnection`）：仍是**零新依赖**、手动校验 2xx、15s/30s 超时 + 20MB 上限、仅 https，且规避了在 commonMain 引入 Ktor 客户端的编译风险。`expectSuccess=false` 的 planned 处理不适用。
- 视觉类断言（#1/#2/#3 的目视部分）按 §9 第 3 项确认结论降级验收，**不虚报**；`SKIKO_RENDER_API=SOFTWARE` 兜底仅作为诊断手段，未据此声称 GUI 已启动。

---

## 8. 风险预演（上线后最可能挂在哪）

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| UI 导入漏传 media（现有 bug） | 高 | 用户导出再导入后图片全丢 | 列为 M 级修复项 + 专门回归用例 |
| 严格 Json 反序列化新字段无默认值 → `catch` 吞成空 | 高 | 静默丢图且无报错 | 所有新字段带默认值 + 旧 JSON 反序列化用例 |
| 翻转卡片 `scaleX=-1f` 镜像 / `key(card.id)` 重建 | 高 | 背面图片镜像、切换闪烁 | 查看器独立 Dialog + 共享 bitmap + LRU + remember |
| Android 拦截 http 图床 | 中 | 图片不显示 | 校验器/文档强制 https；失败态明确提示 |
| 大图 OOM / 布局溢出 | 中 | 卡顿或界面变形 | ContentScale.Fit + 高度封顶 + 解码降采样（`BitmapFactory.Options.inSampleSize` / Skia 缩放） |
| skill 两份副本不同步、安装副本哈希被破坏 | 中 | Agent 生成的带图牌组被 `validate` 拒绝，或无法卸载 | 走 uninstall→install 流程；CI/测试加一致性检查 |
| 编辑 UI update 覆盖丢失 media | 中 | 编辑一次图片消失 | `card.copy(media = newMedia)` 显式保留 + 用例 |
| 图片点击与翻卡/滑动手势冲突 | 中 | 点图误翻卡 | clickable 消费 + 查看器期状态位屏蔽 |
| 桌面端 `:composeApp:run` exit 1 未根治（✅ 已确认接受） | 中 | **无法做真机视觉验收** | 编译 + 单测 + 静态自审 + `SKIKO_RENDER_API=SOFTWARE` 兜底验证；如实标注未做 GUI 验收，视觉验证延后 |
| 引入 ktor 到 composeApp 触发 force pin 冲突 | 低 | 编译失败 | 优先复用传递依赖；确需显式声明时先小步验证编译 |

---

## 9. 人工确认项（已闭环）

2026-11-19 通过交互式问答逐项确认，**6/6 采纳推荐路线**，结论已并入 §1.1 与正文相应章节：

| # | 原待确认项 | 确认结论 |
|---|---|---|
| 1 | 图片归属粒度（整卡级 vs 逐图） | ✅ **整卡级** `metadata["mediaTarget"]`，默认 `both`；不做逐图 target |
| 2 | `http` 图床是否放宽明文 | ✅ **强制 https**，不放宽 `network_security_config.xml`；http 直接拒绝 + 失败占位 |
| 3 | 桌面端视觉验收安排 | ✅ **接受降级验收**（编译 + 自动化测试 + 静态自审 + SOFTWARE 兜底），真机视觉验证延后 |
| 4 | skill 是否升版 | ✅ **升到 0.4.0**，同步 `format.md` 审计基线与安装副本哈希清单 |
| 5 | 设计文档归属 | ✅ **移入 `docs/` 并纳入 git** |
| 6 | 是否进入阶段二 | ✅ **确认，开始开发** |

> 阶段二已启动：开 `feat/card-image-support` 分支，按 `docs/TASK.md` 逐项实现并勾选，每完成一块做自审与构建验证；门禁规则见 TASK.md「门禁规则」。
