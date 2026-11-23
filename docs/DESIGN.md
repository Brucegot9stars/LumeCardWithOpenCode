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
- 图片不铺满、小于半宽按原尺寸、大于半宽封顶 50% → §4.1 `naturalOrHalfSize`
- 过大图片默认压缩（降像素、少损质量）、逐图可选 → §4.1.1
- 图片可居中 / 靠左 / 靠右，逐图设置 → §4.1 对齐
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

### 4.1 卡片内尺寸策略（1:1 物理像素、半宽封顶、不铺满）

`CardFace` 的 `Column` 尾部追加 `CardImageBlock`，仅渲染 `card.media.filter { it.type == IMAGE && target 匹配当前面 }`：

```
Column(Modifier.fillMaxWidth()) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = boxAlignment) {
        val maxH = if (maxHeight.value.isFinite())
            (maxHeight * 0.45f).coerceAtMost(320.dp) else 320.dp
        val size = naturalOrHalfSize(bitmap.width, bitmap.height, maxWidth, maxH)
        Image(
            painter = BitmapPainter(bitmap),
            contentScale = ContentScale.Fit,          // 保持比例，不裁剪不拉伸
            modifier = Modifier
                .size(size.width, size.height)        // 显式尺寸，绝不 fillMaxWidth
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenViewer(ref, bitmap) }
        )
    }
    if (caption.isNotBlank()) { /* 与图片同 alignment 的 Text */ }
}
```

尺寸计算 `naturalOrHalfSize(widthPx, heightPx, containerWidth, heightCap)`：

```
val naturalWidth = (widthPx / LocalDensity.current.density).dp   // 物理像素 → dp，1:1
val halfWidth    = containerWidth / 2
var width  = if (naturalWidth <= halfWidth) naturalWidth else halfWidth
var height = width / (widthPx.toFloat() / heightPx)
if (height > heightCap) { height = heightCap; width = height * ratio }   // 超高图按比例收缩两轴
```

- **核心改动**：原实现用 `fillMaxWidth()` 撑满整行，小图被强行放大。现在小于半宽的图**保持原像素尺寸**，大于半宽的图**封顶 50%**，两张图在视觉上不会再被拉成同宽。
- **对齐**：`MediaAlignment.START/CENTER/END` → `Alignment.CenterStart/Center/CenterEnd`；`BoxWithConstraints` 的 `contentAlignment` 负责水平定位，图注 `Text` 用对应 `TextAlign`。逐图独立，同一张卡可左中右混排。
- 高度封顶（320dp / 可用高度 45%）避免 `StudyScreen` vCenter 模式下不挂 `verticalScroll` 时溢出；仅在超高图时生效，且**两轴同比缩小**，比例永不变形。
- 多图：`Column` 顺序排列，单图上限生效；加载中与失败态固定占位尺寸，避免布局跳动。
- 纯文本卡 `media` 为空 → 不进入该分支，渲染路径与现状完全一致。

### 4.1.1 导入期压缩（降像素、保格式）

`platformCompressImage(bytes, maxEdge = 2048, sourceExtension): ByteArray?`（expect/actual）：

- Desktop：`Codec.makeFromData` 读尺寸 → `Codec.readPixels(Bitmap(新尺寸))` 完成降采样 → `Image.encodeToData(PNG-100 / JPEG-85)`。
- Android：`inJustDecodeBounds` 读尺寸 → `inSampleSize` 2 的幂降采样 → `createScaledBitmap` 精确缩放 → `Bitmap.compress`，并显式 `recycle()` 中间 bitmap。
- 格式策略：png / gif / webp / bmp 保持 PNG（透明不丢）；其余转 JPEG 质量 85。
- **返回 `null` 一律回退原始字节**（未超阈值 / 解码失败 / 编码失败），压缩失败永不丢图。
- 压缩发生在**算哈希之前**，故压缩产物有独立内容哈希，与原图不冲突可共存；已入库文件不会被二次重编码——开关只能影响下一次导入。

### 4.1.2 导出/导入归档（`.lumecard`）

JSON 无法携带媒体文件，因此**含本地图片**的知识库导出为 zip 归档，纯文字仍导出 `.json`。

归档结构（格式版本 1，**不改 `EXPORT_VERSION` / `SCHEMA_VERSION`**）：

```
lumecard.json        与普通 .json 导出字节完全一致
media/<filePath>     每个被引用的本地媒体一个条目，路径与媒体根恒等
```

- **为什么用拉通目录**：媒体文件名本身就是内容 SHA-1，分类子目录（`media/images/…`）只增加观感上的归类，却要付出真实成本——改写所有 `Card.media.filePath`、并让 WebDAV manifest 里每条路径都"变新"而触发一次全量重传。读侧仍接受 `media/` 下任意子路径，将来换布局不必升格式版本。
- **格式规则**集中在 `LumecardArchive.kt` 的纯函数里：`referencedLocalMedia` 只收集 `LOCAL_RELATIVE` 的 IMAGE；`isArchivableMediaPath` 拒绝 `http(s)://`/`file://`/`content://`/`data:`/绝对路径/反斜杠；`isSafeMediaRelativePath` 拒绝 `.`、`..`、空段。**写任何 media 条目都必须先过这三道**。
- **导出**（`packageExport`）：有可用媒体才打包；缺失的被计数（`ExportOutcome.mediaMissing`）并跳过——**单个文件被删不能让整次导出失败**；无任何可用媒体时回退为纯 JSON，避免产出 `media/` 为空的误导性归档。
- **导入**（`importArchiveMedia`）：媒体**先于**卡片写入，杜绝"卡片已存在但图缺失"的中间态。内容相同 → 跳过；同名不同内容 → 存为 `<stem>_<shorthash>.<ext>` 并回传 `renamed`，由调用方改写 `Card.media.filePath`，**绝不覆盖用户本地文件**。
- **按内容识别**：`readFileHeader(path, 4)` + `looksLikeZip`（`PK\x03\x04`），不看扩展名，故重命名过的归档仍可导入。
- **平台层**（`Archive.kt`）：Desktop 用 `ZipFile`（可随机访问）；Android 必须用 `ZipInputStream`，因为 `content://` URI 无法 seek。因此导入走 `readZipArchiveEntries` **一次流式扫描**取全部媒体——逐条 `readZipArchiveEntry` 会让 Android 把同一份流重扫 N 次（O(n²)），100 张图就会卡到不可用。
- **暂存目录**：manifest 先写到 `getApkCacheDir()/export-staging/manifest-<ts>-<rand>.json`，**绝不从 `outputPath` 推导**。Android 的 `pickSaveFile` 返回 `content://`，其"父目录"不是目录，据此写暂存会静默失败 → zip 里缺 `lumecard.json` → 产出不可导入的归档而 UI 仍报成功（本项目曾真实出现过）。
- **成功判定**：`packageExport` 必须校验 `writtenEntries` 里**确实含** `ENTRY_MANIFEST`；缺失即按 `writeFailed` 处理（删半成品 + 回退纯 JSON + 明确告知）。仅靠"媒体数 > 0"判断成功不够。
- **资源上限**：manifest 64 MB（`readManifest` 要 `decodeToString()`，UTF-16 会翻倍）、单个 media 64 MB、单次导入累计 1 GB、最多 10000 条媒体。两个字节预算都**在流式读取过程中**生效（`readZipArchiveEntries` 的 `maxBytes` / `maxTotalBytes`），因为返回的 `Map<String, ByteArray>` 是全量驻留内存的——读完再检查等于先分配 `10000 × 64 MB` 再拒绝。溢出计入 `failed` 而非静默丢弃。（`readMedia()` 里的 512 MB 只服务于测试读取，不在导入路径上。）
- **导入结果四元组**：`written`（新落盘）/ `skipped`（内容相同已存在）/ `missing`（**manifest 引用但归档里没有**）/ `failed`（归档里有但写不进去）。`missing` 必须按 `referencedPaths` 求差，而不是"归档条目总数 - 落盘数"，否则多余条目会反向虚增缺失数。
- **回滚**：`importArchiveMedia` 返回 `newlyWrittenPaths`（仅本次新建）。卡片插入中途抛异常时，调用方据此删除刚写入的媒体，避免孤儿文件被 media scan 反复同步；已存在的同名文件**不在**该列表中，绝不误删用户数据。
- **写入类型对称**：导出只收 `IMAGE`，导入计算 `missing` 时也必须按同一类型过滤，否则带本地音频的卡片每次导入都会误报"图片缺失"。
- ⚠️ `pickSaveFile` 的强制扩展名改为从**建议文件名**推导，否则 `.lumecard` 会被补成 `*.lumecard.json`。
- ⚠️ `jsonSiblingPath` 对 `content://`/`file://` 输入**原样返回**：在 document-id 段后拼 `.json` 会得到任何 provider 都解析不了的伪 URI。

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
| 2 | 尺寸策略 / 对齐 / 纯文本卡零变化 | ✅ 已验证（代码级）· 🟡 未做真机目视 | `naturalOrHalfSize` 按 `LocalDensity.density` 1:1 换算、≤半宽保持原尺寸、>半宽封顶 50%、超高图两轴同比收缩；`BoxWithConstraints(contentAlignment)` + 逐图 `TextAlign` 实现对齐；`media` 为空不进入新分支；`shared` 80 tests + `composeApp` 10 tests 全绿 |
| 3 | 查看器交互 / 无闪烁 | 🟡 代码级保证 · ⚠️ **未做真机 GUI 验收** | 独立 `Dialog` + 共享 `ImageBitmap` + `AnimatedVisibility` 180ms + LRU；`:composeApp:run` exit 1 未根治，无法目视确认「无白屏/无镜像/无闪烁」 |
| 4 | 编辑录入 / media 保留 | ✅ 已验证（代码级 + 编译） | `CardImageEditor` 本地选图 / https URL / caption / `mediaTarget` / 逐图 alignment chips / 选图前 `compressOnImport`；`updateCard` 默认 `media = card.media`；双端编译通过；UI 层无自动化测试框架，靠静态自审 |
| 5 | 导出→导入 / 备份 / 同步 media 完整 | ✅ 部分验证 | `SettingsScreen` 补齐 `media`/`metadata`/`title`；`CardImageImportTest` 3 例 + `CardMediaSerializationTest` 6 例（含 legacy 无 alignment/compress 兼容）+ 契约 11 fixtures 全通过；WebDAV/备份链路复用既有代码，靠静态自审 + `MediaSyncServiceTest` 7 例 |
| 6 | skill 带图 JSON 通过 validate + 契约测试 | ✅ 已验证 | Python 84 tests OK（skipped=3）；`build_deck.py` 校验 `alignment`/`compress`；fixtures 11 例 / 4 accepted；`SkillImportContractTest` PASSED（未跳过，含 `image_alignment` 定向断言） |
| 7 | AI 提示词 / 带 media 入库 | ✅ 已验证（代码级 + 编译）· 🟡 未接真实 LLM | `AiMediaJson.alignment`/`compress` + `mediaTarget`、过滤 `http://`/`..`/空路径、未知 alignment 回落 `center`、`prompt_ai_card.md` Image Rules 含对齐与压缩说明；双端编译通过 |
| 8 | 文档 / i18n / 无残留 | ✅ 已验证 | README / AGENTS.md / 5 语言 articles.json（JSON 合法）/ 5 份 `Strings*.kt` / skill references 全量同步；残留检索为空；安装副本 `diff -rq` 一致（32 托管文件） |
| 9 | 全量编译与测试 | ✅ 已验证 | Gradle `BUILD SUCCESSFUL`；`:shared:desktopTest` 80 tests / 0 fail / 0 skipped；`:composeApp:desktopTest` 10 tests / 0 fail / 0 skipped；Python 84 tests OK（skipped=3） |

**追加需求（2026-11-19，尺寸 / 压缩 / 对齐）验收补充**：

| # | 标准 | 状态 | 依据 |
|---|---|---|---|
| 2a | 图片不铺满整行 | ✅ 已验证（代码级）· 🟡 未做真机目视 | 移除 `fillMaxWidth()`，改 `Modifier.size(DpSize)`；`ContentScale.Fit` 不变形 |
| 2b | 小于半宽按原尺寸、大于半宽封顶 50% | ✅ 已验证（代码级）· 🟡 未做真机目视 | `naturalOrHalfSize` + `LocalDensity.density` 1:1 换算；纯函数，逻辑可静态推演 |
| 2c | 逐图居中 / 靠左 / 靠右 | ✅ 已验证（代码级 + 单测） | `MediaAlignmentParsingTest` 5 例覆盖 `parseMediaAlignment` 宽松回退与 `parseImageRefs` 逐图传递 |
| 2d | 超大图导入默认压缩、逐图可选 | ✅ 编译 + 静态自审 · ⚠️ 未做真机画质/体积验收 | Desktop skiko `Codec`/`encodeToData`、Android `inSampleSize`/`createScaledBitmap` 双端编译通过；`null` 回退原始字节保证不丢图；**实际压缩率与画质未实测** |

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

---

## 10. 发布记录（2026-11-19）

**分支**：`feat/card-image-support`（图片支持开发提交 `d0a0b69`，修复提交另记）。
**第一版 tag**：`lumecard-deck-builder-v0.4.0`（附注标签，指向 `d0a0b69`）。

- 分支与 tag 均成功推送到 `origin`；`Skill Build and Release` 被分支 push 与 tag push 各自动触发一次。
- ❌ 两条 run 均失败：GitHub 已将 `ubuntu-latest` 升级到 **Ubuntu 26.04**，`actions/setup-python@v7` 不再提供 **Python 3.9 x64**，`test (ubuntu-latest, 3.9)` 在 setup-python 阶段报错，导致依赖 `needs: [prepare, test, app-contract]` 的 `build`/`publish` 被跳过 → 未生成 ZIP/SHA256、未创建 Release。`prepare`、`app-contract` 及 Windows/macOS/Ubuntu 的 3.11 测试全部通过。属平台镜像升级导致的**存量 workflow 缺陷**，与图片支持代码无关（2026-11-04 同 workflow 在 Ubuntu 24.04 上成功）。
- 修复：把 Python 3.9 矩阵项 runner 固定为 `ubuntu-24.04`（独立仓库模板 `skills/.../.github/workflows/test.yml` 同步）；`SKILL.md` 升 **0.4.1** 并同步 references / README / 主项目文档；按 `releasing.md`「已存在的 tag 禁止覆盖」，打新 tag `lumecard-deck-builder-v0.4.1` 重发，**不删除、不覆盖 `v0.4.0` tag**。
- 应用 tag `v0.26.17` 已于 2026-10-29 在 `main` 发布为 Latest，本次未重复创建、未覆盖。

---

## 11. 附录：指定对象单独导出 / 按对象类型导入（2026-11-21，增量功能）

详细设计见 `README.md` 同名章节；本节只记录与既有图片支持设计的衔接与验收结论。

### 11.1 关键决策（用户交互确认 3/3）

| 决策项 | 结论 |
| --- | --- |
| 重名 / 重复且「不覆盖」 | **保留两者（自动改名 + 全新 ID）** |
| 重复卡牌判定 | **类型 + 正背面内容**（前后 `trim`，NUL 分隔避免内容含分隔符误判） |
| 导入入口 | **新增独立入口，原全量导入按钮保留**（本批对其归档校验与失败回滚做了加固，不再是完全原样） |

### 11.2 与既有格式的衔接

- `ObjectExport` = `ShareExport` + 顶层 `objectScope`；`exportType` 显式写 `"share"`（`encodeDefaults = true`）。**必须显式写**：kotlinx 默认省略等于默认值的字段，若省略，旧 `DataExport` 解码器会回落到 `"backup"`，`importBackup()` 的条件 `backupFormatVersion == 1` 会误把分享文件当作破坏性备份接受。已由 `scopedExportIsAcceptedByTheUnchangedLegacyImporter` 回归测试守护。
- 单个 Deck / Card 导出仍携带祖先 Deck 与所属 KB 作为「容器」，导出数组**父先子后**排列，使任何按顺序插表的读取器外键可解析。注意这只是格式层面的可解析：旧 `importData()` 按原 ID 直插，同设备仍会撞主键，对象语义只有新入口能保留。
- `.lumecard` 归档复用 `packageExport()` / `importArchiveMedia()`；对象导入用 `ObjectExportBuilder.rewriteMediaPaths()` 把整份 manifest 的重命名映射一次应用（旧入口的 `resolveRenamedPath()` 只处理单条路径，语义相同、作用层不同）。未改动归档格式版本。归档只解压 manifest 引用的条目，写盘时用**manifest 里的路径拼写**（匹配大小写不敏感，但文件名必须落到卡片实际引用的名字上，否则在大小写敏感文件系统上就是坏图），且只打包 `MediaType.IMAGE`。

### 11.3 一致性与回滚

`ObjectImportService.execute()` 在**单事务**内完成：软删除覆盖子树（保留 tombstone，避免破坏 `ReviewLog.card_id` FK）→ 按父先子后写入新副本 → 维护 `CardFTS` / `AlgorithmState` → `LearningPlanMutationService.contentReplaced()` 重映射学习计划。规划器写 id map 时会**同时**登记「文件 ID → 新 ID」与「被覆盖的本地 ID → 新 ID」（计划里存的是本地 ID；只登记前者会让覆盖把计划清空）；覆盖知识库时，其中的牌组按名称路径、卡牌按内容身份对应到副本。之后 `reconcileAllInCurrentTransaction()` 在同一事务内重算进度。数据库失败整体回滚；归档媒体在事务外用 `MediaImportOutcome.newlyWrittenPaths` 精确回滚。

不存在「孤儿媒体」集合：被删的卡是软删除，tombstone 仍保存 `media`，文件依然被引用，因此一律保留；只清理「本次导入新写入且最终无人引用」的文件（`unusedNewMediaPaths`）。路径比较大小写不敏感。

### 11.4 性能：索引化（2026-11-21）

文档化上限是 `MAX_DECKS = 20 000` / `MAX_CARDS = 100 000`（`ObjectExportBuilder`）。在此量级下，规划器与执行路径原本有 **5 处二次复杂度**，全部是同一模式：以「整表扫描」回答点查询。

| 位置 | 原实现 | 代价（20k 牌组） | 现在 |
| --- | --- | --- | --- |
| `ObjectImportPlanner` 知识库/牌组碰撞查找 | 每个 payload 对象 `context.decks.filter { ... }` | payload × library ≈ 4×10⁸ 次 trim 比较 | `LocalIndex`：一次建 `(kbId, parentId, name)` → 桶，点查询 O(1) |
| `ObjectImportPlanner.resolveParent` | 每层祖先各扫一遍 live 列表 | 深 payload × library | 同上，按层 O(1) |
| `ObjectImportPlanner` 覆盖知识库的牌组路径映射 | 每行 `deckPath()` 重新走到根 | O(n × depth)，且两侧各建一次 map | `DeckTree.pathOf()` + 两侧各建一次索引 |
| `ObjectImportPlanner` 同名后缀探测（第 5 轮补） | 每行从 ` (1)` 重新试到 ` (k)` | N 个同名牌组 ≈ N²/2 次探测 | 按 base 记录上次落点，探测从该处续（`kbClaimHint` / `deckClaimHint`），语义不变 |
| `LearningPlanCardResolver`（第 5 轮补） | `decks.associateBy` 在每个计划里重建，`deckIds` 内部再建一次 | 复习写路径 O(计划数 × 牌组数) | `Scoped` 每批建一次索引 |
| `ObjectImportService` 未用媒体路径核算（第 5 轮补） | `newlyWrittenPaths` 为空时仍 JSON 解码全库 `media` | 每次无附件导入 O(100k) 解码 | 空即早退 |
| `ObjectExportBuilder.expandDescendants/Ancestors` | `while (changed) { for (deck in all) }` | O(n²)，层数越深越慢 | `DeckTree` 层级索引 + 单趟 BFS，O(n + e) |
| `ObjectImportService.expandedDeleteCounts` / `execute` 子树展开 | 同上（`execute` 内还在**写锁**下） | O(n²) 两次 | 同上，索引只建一次 |

新增 `DeckTree.kt`（`commonMain`，`internal`）：`parentById` + `childrenByParent` 两个 `HashMap`，提供 `expandDescendants` / `expandAncestors` / `pathOf`。缺失父节点按「根」处理（与旧扫描一致），因此软删除父节点或脏父 id 的既有行为不变；`pathOf` 仍有 64 层上限防环，但触顶时会标记（见下）。

**截断必须可区分（第 6 轮）**：`pathOf` 撞到 64 层上限时，结果首位会加一个「停止处祖先 id」标记（`TRUNCATED_MARKER + cursor`），而不是静默截短。原因是截断后的路径只剩「叶子 + 最近 64 个祖先」，两条只在第 65 层以上分叉的链会产出**完全相同**的字符串，`remapLocalDeckSelections` 于是把两个本地牌组映射到同一个副本——学习计划指向错卡，且无异常。加标记后，截断路径不可能等于完整路径，最坏退化为「不匹配」（即索引化之前对未知祖先的行为），而不是「错匹配」。

**行为不变性**：桶内保留 `context.decks` 原顺序，`delete*Ids` 顺序与旧的 `filter` 完全一致；`cardsInDeckByIdentity` / `DeckTree` 均为惰性构建，CARD 分支不会为 20k 牌组建索引；`ofRows()` 让服务层直接索引 SQLDelight 投影，避免为两列额外物化 20k 个 `Deck`。

**实测**（本机 Desktop，纯规划，`-PrunScaleBenchmark=true`；两次不同负载下取样，给出区间）：

| 库规模 | 覆盖式导入整库耗时 |
| --- | --- |
| 2 500 牌组 | 55 – 111 ms |
| 5 000 牌组 | 71 – 152 ms |
| 10 000 牌组 | 85 – 130 ms |
| 20 000 牌组（上限） | 199 – 302 ms |

8 倍数据约 3 – 4 倍耗时（线性区间；旧实现为约 64 倍）。服务层 6 000 牌组 × 2 卡：`expandedDeleteCounts` 136 ms；`execute()` 4.3 s，其中展开已可忽略，剩余为 12 000 张卡的 SQL 写入本身。绝对毫秒数随机器负载浮动，结论看的是**增长倍率**，因此基准断言用的是倍率而不是绝对时限。

### 11.5 验收执行结果

| 项 | 命令 / 方式 | 结果 |
| --- | --- | --- |
| shared Desktop 编译 | `:shared:compileKotlinDesktop` | ✅ BUILD SUCCESSFUL |
| composeApp Desktop 编译 | `:composeApp:compileKotlinDesktop` | ✅ BUILD SUCCESSFUL |
| shared 单元测试 | `:shared:desktopTest` — 154 个用例（`ObjectTransferTest` 36 / `ObjectImportPlannerScaleTest` 7 / `ObjectImportNameClaimTest` 4 / `DeckTreeIndexTest` 11 / `ObjectImportServiceTest` 9 / `ObjectImportServiceScaleTest` 2 等） | ✅ 0 失败（1 跳过：既有 `SkillImportContractTest`；计时基准默认 no-op，需 `-PrunScaleBenchmark=true`） |
| composeApp 单元测试 | `:composeApp:desktopTest` — 54 个用例（归档 / 路径安全 / 引用集回归） | ✅ 0 失败 |
| Android 编译 | `:shared:compileDebugKotlinAndroid` + `:composeApp:compileDebugKotlinAndroid` | ✅ BUILD SUCCESSFUL |
| 规模基准 | `:shared:desktopTest -PrunScaleBenchmark=true --tests "*ObjectImportPlannerBenchmark"` | ✅ 见 11.4 表 |
| 向后兼容 | `ExportManager().importData()` 解码 scoped 文件 | ✅ 单测覆盖 |
| 事务级回归 | `ObjectImportServiceTest`：目标二次校验、学习计划重映射、子树展开计数 | ✅ 覆盖上述两类回归 |
| 真机 GUI 视觉验收 | — | ❌ 未执行（沿用此前降级结论，不虚报） |

> 说明：验收以「编译 + 自动化测试 + 静态自审」为准，未做 GUI 手工点击验证。`ObjectImportServiceTest` 是第 7 轮终审后补的：此前 `execute()` 零测试，导致「目标校验误伤本次新建目标」「学习计划 id map 只登记文件 id」两个缺陷只在规划层测试中隐形通过。
>
> **关于时限断言（第 4 轮修正）**：规模测试里的绝对秒数门槛只是**卡死检测**，不是复杂度判据。旧实现跑满上限约 6–16 s，任何能容忍 CI 抖动的门槛（20–60 s）都会放它过去。真正的判据是 `ObjectImportPlannerScaleTest.plannerGrowthRatioStaysLinear`：同一夹具下 2 500 与 20 000 牌组的**耗时倍率**，索引化约 8 倍、嵌套扫描约 64 倍，阈值取 24 倍。同轮还修掉了三处「恒真断言」：整库覆盖分支的 `deleteDeckIds` 与 `renamed` 在该形态下必然为空（现在断言它们**必须**为空，并补了 `deckLevelLookupAtTheCapStaysLinear` 去真正命中牌组级分桶）、以及 `pathIsCappedOnACorruptCycle` 的 `size <= 65`（现在断言恰好跑满上限）。
