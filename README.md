# LumeCard

基于 Compose Multiplatform 与 Kotlin 开发的闪卡学习应用，保留闪卡核心功能的同时大幅简化了操作。让学习者能专注于知识本身，免去繁琐的配置摸索。

---

## 功能特性

- ✅ **复习算法选择** — FSRS-6（21 参数，可训练遗忘曲线）/ SM-2（经典 Anki 算法）/ 莱特纳盒子 / 简单固定间隔，四种算法可随时切换，算法状态持久化存储
- ✅ **多人算法** — StudyViewModel 通过 `ReviewAlgorithm` 接口解耦，可扩展自定义算法
- ✅ **每日学习目标** — 设置每日复习卡片数与每日新卡片数，支持进度追踪
- ✅ **深色/浅色模式** — Material Design 3 主题，设置页面切换即生效
- ✅ **卡片翻转模式** — 支持 Flip（3D 翻转动画，点击卡片也可翻转）与 Split（上下分区）两种复习查看模式
- ✅ **知识库管理** — 树状结构管理知识库 → 牌组 → 卡牌层级，支持批量操作
- ✅ **卡片管理** — 创建/编辑/删除卡片，支持 7 种卡片类型（Basic、Reversed、Cloze、Multiple Choice、Markdown、AI、Rich Text），Basic 卡支持独立水平/垂直居中与字体大小（12–120sp）
- ✅ **示例知识库** — 首次安装自动创建示例知识库，包含 4 个牌组、18 张卡片（覆盖全部 7 种卡片类型），用户可自由删除
- ✅ **填空卡（Cloze）** — Anki 兼容格式 `{{c1::答案}}` / `{{c1::答案::提示}}`，正面自动挖空显示下划线，背面高亮答案
- ✅ **学习流程** — 翻卡 → 评分（Again/Hard/Good/Easy），间隔重复调度，支持左滑/右滑手势评分
- ✅ **学习计划** — 创建/编辑/删除学习计划，支持关联知识库/牌组/卡牌，进度追踪，状态管理
- ✅ **多牌组学习模式** — 支持混合模式（所有牌组）、单牌组模式、多牌组选择模式
- ✅ **上一张功能** — 学习过程中可返回上一张卡片重新评分
- ✅ **复习日志** — 每条评分记录持久化，支持学习统计
- ✅ **统计分析** — 总卡片数、总牌组数、总复习数、今日/本周/本月复习数、记忆保持率、学习时长、连续学习天数、新卡片/待复习/未到期卡片分布
- ✅ **数据导入导出** — JSON 格式完整导出/导入，支持分享导出（不含学习进度）与完整备份导出分离；含本地图片时自动打包为 `.lumecard` 归档（媒体目录与媒体根恒等映射）；另支持知识库 / 牌组 / 卡牌**单独导出**与**按对象类型导入**（独立入口，不影响全量导入导出）
- ✅ **全文搜索** — SQL LIKE 搜索 + FTS5 虚拟表全文索引（FTS5 不可用时自动降级 LIKE）
- ✅ **排序功能** — 牌组与卡片列表支持按名称、创建时间、更新时间排序（升序/降序）
- ✅ **WebDAV 云同步** — 手动强制覆盖（学习数据 / 配置分开独立同步）+ 覆盖前云端快照自动归档（可回滚）+ Anki 风格媒体同步（SHA-1 + mtime 缓存，仅哈希变更文件）+ 多配置管理
- ✅ **级联软删除** — 删除知识库级联软删除下属牌组与卡片，删除牌组级联软删除下属卡片
- ✅ **媒体贴入** — 粘贴图片到卡片编辑器字段（Desktop: AWT / Android: ClipboardManager），自动 SHA-1 去重保存，插入 Markdown 图片引用；Card 模型含 `media: List<Media>` 字段（IMAGE/AUDIO/VIDEO/FILE）
- ✅ **卡片图片** — 本地图片（media 相对路径 / `file://` / `content://`）与 https 图床链接均可附加到卡片，整卡级指定正/反/双面显示；图片**不铺满整行**：按屏幕物理像素 1:1 换算，小于半宽保持原尺寸、超过半宽封顶 50%，保持比例不拉伸；每张图可独立设置居中/靠左/靠右；超大图片导入时默认压缩（最长边 2048px、保留格式与透明度），逐图可选是否压缩；点击图片进入独立全屏查看器，支持手势与按钮缩放、平移、180ms 淡入淡出过渡；AI 制卡与 `lumecard-deck-builder` skill 同步支持可选图片字段
- ✅ **AI 制卡助手** — SSE 流式生成 + 增量 JSON 解析 + 批量自动分类到牌组/知识库 + 实时进度气泡 + 交互日志面板 + 停止生成（保留已完成卡片）
- ✅ **AI 事件流框架** — 事件驱动架构：`AiEventBus`（SharedFlow）+ `AiEvent` 14 种事件类型 + `AiBatchGenerator` 批处理循环 + `AiTaskStateMachine` 状态验证
- ✅ **字体系统** — 跨平台自定义字体加载（TTF/OTF），平台字体检测（Android: `Typeface.create` / Desktop: `GraphicsEnvironment`），用户导入字体存储
- ✅ **设置持久化** — 所有设置存入 SQLite，dirty-state 追踪按需保存
- ✅ **跨平台** — Android + Desktop (Windows/Linux/macOS)，支持 Windows EXE/MSI 打包 & macOS DMG
- ✅ **Markdown 渲染** — 基于 CommonMark 的 GFM 标准渲染，支持标题/表格/代码高亮/任务列表/删除线/自动链接/数学公式
- ✅ **国际化** — 简体中文、繁體中文、English、日本語、Español 五国语言，支持跟随系统语言
- ✅ **CI/CD** — GitHub Actions 自动构建 Android APK + Windows 打包 + Tag 触发 Release
- ✅ **版本管理** — 统一版本管理（version.properties），支持 V0.x.y 开发阶段版本规范
- ✅ **平台文件对话框** — Android SAF 与 Desktop JFileChooser 完整实现，用于导出/导入/媒体操作
- ✅ **加密模块** — PBKDF2 + AES-256-GCM 敏感数据加密，密钥管理

### 待实现

- ❌ 知识图谱
- ❌ 学习热力图 / 日历贡献图
- ❌ iOS 平台
- ❌ 音频/视频媒体渲染 — `Media` 模型支持 AUDIO / VIDEO / FILE，但学习界面当前仅渲染 IMAGE

## 技术栈

| 类别 | 技术 |
|------|------|
| UI | Compose Multiplatform 1.11.1 + Material 3 |
| 语言 | Kotlin 2.4.0 + Compose Multiplatform Plugin |
| 数据库 | SQLDelight 2.3.2 (SQLite) |
| 网络 | Ktor 2.3.12 |
| DI | Koin 3.5.6 + koin-compose 1.1.5 |
| 导航 | Voyager 1.0.1 |
| 序列化 | kotlinx-serialization 1.11.0 |
| 日期 | kotlinx-datetime 0.7.1 |
| Markdown | mikepenz Markdown Renderer 0.41.0（GFM Tables、Strikethrough、Autolink、TaskListItems）|

## 项目结构

```
LumeCard/
├── composeApp/                         # Compose 应用
│   └── src/
│       ├── commonMain/kotlin/
│       │   ├── App.kt                  # 入口 + 底部导航
│       │   ├── CrashLogHolder.kt       # 崩溃日志
│       │   ├── data/
│       │   │   └── AiCardGenerationManager.kt
│       │   ├── di/
│       │   │   ├── AppModule.kt        # Koin 应用模块入口
│       │   │   └── PlatformModule.kt   # 平台 Koin 模块（expect）
│       │   ├── font/                   # 字体系统
│       │   │   ├── FontSystem.kt
│       │   │   ├── FontInitializer.kt
│       │   │   └── FontPlatform.kt     # expect/actual
│       │   ├── feature/quote/          # 每日一句
│       │   │   ├── screen/ScreenSaverOverlay.kt
│       │   │   └── viewer/             # 引言动画/渲染
│       │   ├── i18n/                   # 国际化（5 语言）
│       │   │   ├── I18nManager.kt
│       │   │   ├── I18nStrings.kt      # 字符串接口（350+ 条）
│       │   │   └── Strings*.kt         # 各语言实现
│       │   ├── platform/               # 平台抽象（expect/actual）
│       │   │   ├── Platform.kt         # 媒体/剪贴板/路径
│       │   │   ├── FilePicker.kt       # 文件选择器
│       │   │   ├── MediaDropTarget.kt  # 拖拽放入
│       │   │   ├── CardImageLoader.kt  # 卡片图片加载（三态路径 + LRU + 磁盘缓存）
│       │   │   └── ...                 # 其他平台适配
│       │   ├── ui/
│       │   │   ├── components/         # 可复用组件
│       │   │   │   ├── AnimatedDonutChart.kt
│       │   │   │   ├── CardTypeSelector.kt
│       │   │   │   ├── LumeCardDialog.kt
│       │   │   │   ├── RichTextCardEditor.kt  # 富文本编辑器
│       │   │   │   ├── MarkdownEditor.kt
│       │   │   │   ├── MarkdownRenderer.kt
│       │   │   │   ├── SearchBar.kt
│       │   │   │   └── UpdateCheckDialog.kt
│       │   │   ├── screens/
│       │   │   │   ├── dashboard/      # 首页看板
│       │   │   │   ├── stats/          # 统计分析
│       │   │   │   ├── warehouse/      # 仓管（树状数据管理）
│       │   │   │   ├── study/          # 学习（模式/界面/评分/卡片组件）
│       │   │   │   ├── learningplan/   # 学习计划
│       │   │   │   ├── knowledgebase/  # 知识库管理
│       │   │   │   ├── deck/           # 牌组管理 + 卡片列表
│       │   │   │   ├── card/           # 卡片创建/编辑
│       │   │   │   ├── aicard/         # AI 制卡助手
│       │   │   │   ├── ai/             # AI 配置
│       │   │   │   ├── splash/         # 每日一句管理
│       │   │   │   ├── help/           # 帮助中心 + 欢迎导览
│       │   │   │   └── settings/       # 设置 + WebDAV + 引言设置
│       │   │   └── theme/Theme.kt      # Material 3 绿色调主题
│       │   └── util/FormatUtils.kt
│       ├── androidMain/                # Android 入口 + 平台实现
│       ├── desktopMain/                # Desktop 入口 (main.kt)
│       └── iosMain/                    # iOS 入口（骨架）
│
├── shared/                             # 共享业务逻辑
│   └── src/commonMain/
│       ├── kotlin/
│       │   ├── AppVersion.kt           # 统一版本管理
│       │   ├── model/Models.kt         # 数据模型
│       │   ├── domain/scheduler/       # 复习算法（FSRS-6/SM-2/Leitner/Simple）
│       │   ├── repository/             # 仓库接口 + SqlDelight/InMemory 实现
│       │   ├── crypto/                 # 加密模块（PBKDF2 + AES-256-GCM）
│       │   ├── data/
│       │   │   ├── ai/                 # AI 事件流框架
│       │   │   │   ├── AiClientAdapter.kt
│       │   │   │   ├── AiFallbackManager.kt
│       │   │   │   ├── AiModelListFetcher.kt
│       │   │   │   ├── ProviderAdapter.kt
│       │   │   │   └── stream/AiStreamParser.kt
│       │   │   ├── AiCardGenerator.kt  # AI 卡片生成
│       │   │   ├── AiClient.kt         # AI API 客户端
│       │   │   ├── ExportManager.kt    # 导入导出（v2 格式）
│       │   │   ├── SyncManager.kt      # WebDAV 同步（含媒体同步）
│       │   │   ├── MediaManager.kt     # 媒体 Manifest & 缓存
│       │   │   ├── DemoDataManager.kt  # 示例知识库
│       │   │   ├── WebDavConfigManager.kt
│       │   │   └── SplashQuoteManager.kt
│       │   ├── feature/quote/          # 引言功能
│       │   ├── help/                   # 帮助中心
│       │   ├── settings/               # 设置索引/搜索
│       │   └── di/SharedModule.kt      # Koin 共享模块
│       └── sqldelight/
│           └── com/lumecard/shared/database/
│               ├── LumeCardDatabase.sq # 10 张表（schema v2）
│               └── migrations/1.sqm    # v1→v2 迁移
│
├── buildSrc/src/main/kotlin/
│   └── Dependencies.kt                 # 统一版本管理
│
├── .github/workflows/
│   ├── android.yml                     # Android APK 自动构建
│   ├── pr-validation.yml               # PR 校验
│   ├── release.yml                     # Tag 触发 Release
│   └── windows.yml                     # Tag 触发 Windows 打包
│
├── version.properties                  # 应用版本（唯一来源）
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew / gradlew.bat / gradle/
```

## 快速开始

### 环境要求

- JDK 17+（推荐使用 Android Studio 自带 JBR；打包安装包需 JDK 21+，含 `jpackage`）
- Android Studio（最新版，含 Android SDK 35）
- Gradle 9.6.0 (已内嵌，通过 `gradlew` 使用)
- Android SDK 编译目标 36

> **Windows 用户名含非 ASCII 字符（如中文）时**，必须事先设置 `JAVA_HOME` 与 `GRADLE_USER_HOME`，否则 Gradle 会因缓存/临时目录路径含中文而失败。

**PowerShell**（推荐）：
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:GRADLE_USER_HOME="C:\gradle-home"
```

**cmd.exe**：
```cmd
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set GRADLE_USER_HOME=C:\gradle-home
```

> 以下命令均假设已按上述方式设置环境变量。打包安装包需改用 JDK 21+（见「构建 Desktop 安装包」）。

### 构建 Android APK

```bash
.\gradlew.bat :composeApp:assembleDebug
```

APK 输出: `composeApp/build/outputs/apk/debug/LumeCard-v{version}-debug.apk`

### 调试启动 Desktop 客户端

#### 方式一：开发模式直接启动（最常用）

编译并启动桌面窗口，适合边改代码边调试：

```bash
.\gradlew.bat :composeApp:run
```

入口为 `composeApp/src/desktopMain/kotlin/com/lumecard/app/main.kt` 的 `main()`（`mainClass = "com.lumecard.app.MainKt"`），无需额外参数。

#### 方式二：免安装「绿色版」直接运行

生成免安装、可直接运行的程序目录：

```bash
.\gradlew.bat :composeApp:createDistributable
```

产物目录 `composeApp/build/compose/binaries/main/app/LumeCard/`，其中的 `LumeCard.exe` 可直接启动：

```powershell
.\composeApp\build\compose\binaries\main\app\LumeCard\LumeCard.exe
```

#### 方式三：构建并运行安装包

`packageExe` / `packageMsi` 生成的是 **jpackage 安装器**（`.exe` / `.msi`），运行后会先安装到系统、而非免安装启动，见下文「构建 Desktop 安装包」。

### 常用调试命令

```bash
# 快速 Desktop 编译（跳过 Android 与测试，仅校验编译）
.\gradlew.bat :shared:compileKotlinDesktop :composeApp:compileKotlinDesktop

# 快速 Android 编译检查（跳过 APK 打包）
.\gradlew.bat :shared:compileDebugKotlinAndroid :composeApp:compileDebugKotlinAndroid

# 运行共享模块测试（CI 同款）
.\gradlew.bat :shared:desktopTest

# 构建/依赖异常时强制刷新依赖
.\gradlew.bat --refresh-dependencies
```

### 构建 Desktop 安装包

需要 JDK 21+（含 `jpackage`；Android Studio 自带 JBR 不含 `jpackage.exe`）：

```bash
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
set GRADLE_USER_HOME=C:\gradle-home
.\gradlew.bat :composeApp:packageExe :composeApp:packageMsi
```

- EXE 安装器输出: `composeApp/build/compose/binaries/main/exe/LumeCard-{version}.exe`
- MSI 安装包输出: `composeApp/build/compose/binaries/main/msi/LumeCard-{version}.msi`
- 安装时提供「创建桌面快捷方式」选项

### 常见构建问题排查

**`AccessDeniedException` / `Could not create ZIP`（Windows）**

表现为：`desktopJar` 或 `run` 报 `Could not create ZIP '...-desktop.jar'`、`AccessDeniedException: build/reports/problems/problems-report.html`、末尾 `Could not update ...last-build.bin`。

原因是残留的 Gradle daemon 进程锁住了 `build/` 下的产物文件（常见于多次后台编译之后）。先停掉所有 daemon 再重试：

```powershell
.\gradlew.bat --stop
```

若仍被占用，可改用单次运行模式（不常驻 daemon）：

```powershell
.\gradlew.bat :composeApp:run --no-daemon
```

## 数据库

SQLDelight 管理 10 张表（schema v2），全部查询在 `LumeCardDatabase.sq` 中定义：

| 表 | 用途 |
|------|---------|
| `KnowledgeBase` | 知识库 |
| `Deck` | 牌组 |
| `Card` | 卡片（含调度字段） |
| `ReviewLog` | 复习日志 |
| `FSRSCard` | FSRS 调度状态（兼容保留） |
| `AlgorithmState` | 通用算法调度状态 |
| `AppSettings` | 键值设置存储 |
| `LearningPlan` | 学习计划（含进度、状态、关联关系） |
| `CardFTS` | FTS5 全文搜索虚拟表（运行时升级，不可用时降级为普通表） |
| `MediaCache` | 媒体同步缓存（path、mtime、sha1、synced_at）— 仅哈希变更文件 |

## 数据模型

### 知识组织结构

```
Knowledge Base → Deck → Card
```

- **KnowledgeBase** — 顶层知识库，卡片与牌组的容器
- **Deck** — 牌组，支持层级嵌套（`parentId` 引用），配有颜色与 emoji 图标
- **Card** — 闪卡，7 种类型（Basic、Reversed、Cloze、Multiple Choice、Markdown、AI、Rich Text），支持标题（title）、标签（tags）、媒体附件（media）、元数据（metadata）

### 媒体附件与卡片图片

Card 模型定义了 `media: List<Media>` 字段，支持 IMAGE、AUDIO、VIDEO、FILE 四种类型。后端基础设施：
- `MediaManager` — Manifest 序列化、SHA-1 缓存、同步差异计算
- `MediaCacheRepository` — 媒体缓存持久化
- 平台函数 — `pasteClipboardMedia`、`saveMediaFile`、`scanMediaDirectory`（Desktop/Android 各有实现）
- WebDAV 媒体同步 — `SyncManager.uploadMedia()` / `downloadMedia()`

**图片支持（IMAGE）**：
- **数据**：复用 `media` 字段，不改 DB schema、不加 `CardType`、不改导出版本号；`Media` 新增 `caption`、`alignment`、`compress` 并给 `mimeType` / `size` / 新增字段都加默认值，保证旧 JSON 严格反序列化不丢图。
- **来源三态**：media 根目录下的相对文件名、`file://` / `content://` / 绝对路径、https 图床 URL；`http://` 与含 `..` 的路径被拒绝。相对路径经 `resolveMediaFilePath()` 防目录穿越。
- **显示归属**：整卡级 `metadata["mediaTarget"]`（`front` / `back` / `both`，默认 `both`），不做逐图设置。
- **对齐**：逐图 `alignment`（`start` / `center` / `end`，默认 `center`），同一张卡可混排左/中/右；说明文字跟随图片对齐。校验器拒绝未知值，读取时宽松回落到 `center`，手改卡不会丢图。
- **展示尺寸**：`ContentScale.Fit` + 显式 `Modifier.size(DpSize)`，**不再 `fillMaxWidth` 铺满**。源图像素按 `LocalDensity.density` 换算成 dp 与「可用宽度一半」比较：小于半宽按原尺寸显示、超过半宽封顶 50%；高度上限（320dp / 可用高度 45%）仅在超高图时生效，且按比例同时缩小两轴，比例永不变形。加载与失败使用固定占位避免布局跳动；内存 LRU 缓存避免翻卡重复解码。
- **压缩**：`platformCompressImage` 在**导入拷贝瞬间**执行——最长边超过 2048px 时降采样并重编码（PNG 保 PNG 保留透明、JPEG 质量 85），压缩后内容再算 SHA-1 命名，故与原图不碰撞；返回 `null`（未超阈值 / 解码失败 / 编码失败）一律回退原始字节，图片永不丢失。开关必须在导入前设置，已入库文件不会被偷偷重编码。
- **查看交互**：点击图片进入独立全屏 `Dialog` 查看器，与缩略图共享已解码 `ImageBitmap`，180ms 淡入淡出 + 缩放过渡；支持按钮/捏合缩放（1–5x）、平移、适应屏幕、点击背景或返回键关闭，底部显示说明文字。查看器打开期间屏蔽滑动与翻卡手势。
- **录入**：创建/编辑界面提供本地选图（保存到 media 目录）与 https 图床链接录入，可设置说明文字、显示面、逐图对齐，并在选图前切换「导入时压缩」；`updateCard` 显式保留既有 media。
- **导出/导入归档**：含本地图片的知识库导出为 `.lumecard` 压缩包（`lumecard.json` + `media/<filePath>`，与媒体根恒等映射），无本地媒体时仍是单个可读的 `.json`；导入按内容识别（看头部字节，不依赖扩展名），解包时逐个校验路径防穿越，同名不同内容改名写入并同步改写卡片引用，缺失媒体只统计不中断。
- **AI / skill**：`prompt_ai_card.md` 与 `lumecard-deck-builder` 0.4.1 支持可选 `media` / `mediaTarget` / 逐图 `alignment` / `compress` 字段，禁止编造 URL 或内联 Markdown 图片。
- **限制**：仅学习界面渲染 IMAGE；AUDIO / VIDEO / FILE 仅存储不渲染；CSV 导入不含图片字段；网络图片仅 https。

相关实现：`composeApp/.../platform/CardImageLoader.kt`、`composeApp/.../ui/screens/study/CardImageViews.kt`、`composeApp/.../ui/screens/card/CardImageEditor.kt`。

### 指定对象单独导出 / 按对象类型导入

在**保留全量导入 / 全量导出作为独立入口**的前提下，支持对单个知识库、牌组、卡牌做导出与导入。全量导入路径本批也做了加固：归档条目校验前置、失败时补偿回滚，不再是完全原样。

**入口**：知识库页 / 牌组页 / 卡牌页各自的「导出该对象」操作；设置页新增独立的「按对象类型导入」页（「导入数据」按钮仍是独立入口，未被对象导入取代）。新入口**拒绝全量导出文件**（无 `objectScope`），并提示改用原导入按钮。导入流程是「选文件 → 预览 → 执行」：预览**始终**计算，仅当预览显示有删除（即「覆盖」策略命中）时才弹出二次确认，保留两者等非破坏性导入直接执行。确认框列出对象名与「将被删除的知识库/牌组/卡牌数量」，该数量已**展开为实际会被软删除的整棵子树**（一个知识库种子会连带它的全部牌组与卡牌）。导出与导入都带重入保护，取消文件选择按「取消」处理而不是报失败。

**文件格式（对既有读取器安全，但不等于可走旧入口）**：`ObjectExport` 是 `ShareExport` 的**超集**——字段名与顺序一致，仅多一个顶层 `objectScope`（另外给 `decks` / `cards` 补了空列表默认值，便于宽松解码）。`exportType` 显式写为 `"share"`（`Json { encodeDefaults = true }`，避免被旧 `DataExport` 解码器回落到 `"backup"` 而被 `importBackup()` 误判为破坏性备份）。旧读取器用 `ignoreUnknownKeys = true` 忽略 `objectScope`，文件仍能被解析成普通 share ——但这只是**格式安全**：旧入口按原 ID 走 `INSERT OR REPLACE`，主键冲突不会报错，而是**隐式删除同 ID 的旧行**；旧行若有子行则外键约束失败中断，没有子行时本地数据会被**静默覆盖**。因此同设备同样不可用，要保留对象语义请用新入口。

**自包含子图**：为保证任何按顺序插表的读取器外键可解析，导出数组统一**父先子后**排序，并携带必要的祖先「容器」，`objectScope.payload*Ids` 用来区分两者。

| 类型 | payload | 容器 |
| --- | --- | --- |
| 知识库 | 该 KB + 其全部牌组 + 全部卡牌 | 无（跨 KB 父链除外） |
| 牌组 | 该牌组 + 所有后代子牌组 + 其下全部卡牌 | 祖先牌组、所属 KB |
| 卡牌 | 指定卡牌（所属牌组已删除的会被丢弃） | 所属牌组 + 祖先链 + 所属 KB |

**导入决策**（`ObjectImportPlanner`，纯逻辑、`commonTest` 全覆盖）：

- **payload 里的对象一律重新生成 ID**，以规避 `INSERT OR REPLACE` 删父行的风险。因此「覆盖」不是原地改写，而是**软删除旧对象 + 以新 ID 写入新副本**；「保留两者」则是**自动改名 + 新 ID 并存**。文件里的祖先「容器」**不会**被复制：它们只用来把导入对象挂到本地同名祖先（逐层匹配），匹配不到则该对象落到目标知识库根。
- 重名匹配按层级进行：牌组只在**同一父**下匹配同名，不会因为根下同名就连带删除整棵无关子树；名称先 `trim` 并限长 120，同批导出内部的同名也会被去重，不会写出两条同名活记录。
- 重复卡牌判定：**卡牌类型 + 正背面内容**（前后 `trim`、统一换行符），支持 `OVERWRITE` / `KEEP_BOTH` / `SKIP`。`OVERWRITE` 只做 1:1 替换（本地存在多条同内容卡时也只替换其中一条），`SKIP` 直接丢弃不写库；同一文件内内容相同的卡只处理一次。**这套策略只对「卡牌」导入生效**——牌组 / 知识库导入总是新建副本，不做卡牌去重（这两个入口不显示该选项）。
- 牌组导入需指定目标知识库；卡牌导入需指定目标牌组（或新建牌组 + 目标知识库）；本地无知识库 / 无牌组时向导自动进入创建流程。目标默认为空、必须显式选择，不会静默落到「最早创建的那个容器」里。

**事务与一致性**（`ObjectImportService`）：单事务内完成「软删除覆盖子树 → 写新 KB/牌组/卡牌（父先子后）→ 维护 FTS 与 AlgorithmState → 重映射并校正学习计划」。软删除保留 tombstone，避免破坏 `ReviewLog.card_id` 外键并让 WebDAV 同步能传播删除。

**学习计划**：计划里的 `knowledgeBaseIds` / `deckIds` / `cardIds` 是无外键的 JSON 数组，覆盖后旧 ID 会全部失效。因此规划器在生成 id map 时**同时登记「文件里的 ID → 新 ID」和「被覆盖的本地 ID → 新 ID」**（计划存的是本地 ID），服务在同一事务内先重映射、再剔除真正消失的 ID、最后重算进度：

- 覆盖知识库：知识库选择按 id 重映射；其中的牌组按**名称路径**（自身名 + 各级祖先名）对应到副本，卡牌按**类型 + 正背面内容**对应到副本。改名过的牌组、内容被改过的卡牌对应不上，会被剔除（这一层无法凭空推断）。
- 覆盖牌组 / 卡牌：牌组、卡牌都是 1:1 重映射，选择完整保留。

**边界情况**：`validate()` 在写库前拦截——空根 / 空 payload / 非法 ID / 非法时间戳 / 未知卡牌类型 / 重复 ID / 牌组父环 / 超大 payload（KB、牌组、卡牌数量上限）/ 不支持的 schema 或 `exportType` / `rootIds` 必须非空且同时属于数组与 payload / 媒体路径必须通过 `MediaPathRules.isSafeRelativePath` / 各字段长度上限（名称 200、描述 20000、正背面 100000、tags 100 条）。权限与读写失败的处理分三种：读失败返回 `null`、**写文本失败返回 `false`**（不抛异常）、其余异常由 `catch (Throwable)` 兜底，统一映射为本地化提示。归档媒体先落盘，数据库失败时按 `newlyWrittenPaths` 精确回滚（不误删既有文件）。

**媒体生命周期**：**覆盖不会产生孤儿文件**——被删的卡是软删除，tombstone 仍保存着它的 `media` 数组，文件依然被引用，所以一律保留（WebDAV 恢复该行时还需要原文件）。会被清理的只有「本次导入新写入、但最终没有任何活卡引用」的文件（`unusedNewMediaPaths`，例如所有卡都被 SKIP）。路径比较统一大小写不敏感，避免在 Windows 上把 `abc.png` / `ABC.png` 当成两个文件。

**归档读取**：只解压 manifest 里真正被引用到的条目，未引用条目既不解压也不计入缺失统计；单个媒体 64MB、累计 1GB 预算，防归档炸弹。对象归档**只打包 `MediaType.IMAGE`**，音视频/附件仅存库不随包导出，导出结果里会写明被跳过的数量。

**规模与性能**：上限为 5 000 知识库 / 20 000 牌组 / 100 000 卡牌（`ObjectExportBuilder`）。规划与执行的「找同名」与「展开子树」全部走**预建索引**（`DeckTree`：`parentById` + `childrenByParent`；规划器内部 `LocalIndex`：`(kbId, parentId, name)` 长度前缀分桶），不在循环里扫全表。实测 20 000 牌组整库覆盖式规划约 **200–300 ms**（2 500 牌组约 55–110 ms）；6 000 牌组 × 2 卡覆盖提交约 4.3 s，其中 12 000 条卡写入占绝大部分，子树展开 136 ms。

复杂度回归**不能用绝对时限判断**：旧实现跑满上限只需 6–16 s，任何放过 CI 抖动的门槛都会漏掉它。真正的判据是 `plannerGrowthRatioStaysLinear` 比较 2 500 与 20 000 牌组的耗时**倍率**（索引化约 8 倍，嵌套扫描约 64 倍，阈值 24 倍）。此外覆盖：同名后缀探测按 base 续探（5 000 个同名牌组从 ~12.5M 次探测降到线性）、`LearningPlanCardResolver` 每批只建一次索引、无附件的导入不再解码全库 `media`。回归由 `ObjectImportPlannerScaleTest` / `ObjectImportNameClaimTest` / `ObjectImportServiceScaleTest` / `DeckTreeIndexTest` 守护，计时基准为 `ObjectImportPlannerBenchmark`（`-PrunScaleBenchmark=true` 才运行）。

**实现位置**：`shared/.../data/ObjectTransferModels.kt`、`ObjectExportBuilder.kt`、`ObjectImportPlanner.kt`、`ObjectImportService.kt`、`DeckTree.kt`；`composeApp/.../data/ObjectTransferFacade.kt`、`ui/screens/settings/ObjectImportScreen.kt`。

### 复习算法架构

所有算法实现 `ReviewAlgorithm` 接口：
- `initCard()` → 返回初始 `AlgorithmState`
- `schedule(state, rating, daysElapsed)` → 根据评分返回新的 `AlgorithmState`

状态通过 `AlgorithmStateRepository` 序列化为字符串持久化到数据库，支持算法间切换（切换时自动重置算法状态）。

**FSRS-6 算法**（默认）：
- 21 个可训练参数（w[0]..w[20]），支持指数初始难度、可训练遗忘曲线、同日复习稳定性增长
- 遗忘曲线：`R(t,S) = (1 + factor·t/S)^(-w[20])`，`factor = 0.9^(1/w[20]) - 1`
- 同日复习增长：`S' = S · e^{w[17]·(G-3+w[18])·S^(-w[19])}`
- 状态机：NEW → LEARNING → REVIEW → RELEARNING（ lapse 时回退）
- 遗忘稳定性：`S' = w[11]·D^(-w[12})·((S+1)^w[13}-1)·e^{w[14]·(1-R)}`，下限 `sMin = S/e^{w[17]·w[18]}`

**SM-2 算法**：
- 映射 Rating(1-4) → SM-2 quality(2-5)
- 仅 AGAIN 重置（reps=0，EF 不变），HARD/EASY 有区间乘数（0.8x/1.3x）

**Leitner 盒子**：AGAIN → box 0，HARD → 保持，GOOD → +1，EASY → +2

**简单固定间隔**：AGAIN → stage 0，HARD → 保持，GOOD → +1，EASY → +2

## 关键架构决策

- **Repository 模式**：`Repositories.kt` 定义接口，`SqlDelightRepositories` 与 `InMemoryRepositories` 两套实现，便于测试
- **MVVM 架构**：Voyager `ScreenModel` + Koin DI，状态通过 `StateFlow` 驱动 UI
- **算法接口解耦**：`StudyViewModel` 通过 DI 注入 `ReviewAlgorithm`，可配置不同算法
- **WebDAV 多配置管理**：支持多个同步配置保存、默认标记、连接测试、手动强制覆盖（学习数据与配置分开同步）
- **平台抽象**：使用 Kotlin `expect/actual` 机制处理数据库驱动、平台样式等差异

## License

MIT
