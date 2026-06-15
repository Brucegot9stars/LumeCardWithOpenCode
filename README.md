# LumeCard

一款现代化、跨平台的闪卡学习应用（Anki 替代品），使用 Compose Multiplatform + Kotlin 构建。

> 本项目全程使用 **opencode + DeepSeek + MIMO** 等 AI 工具开发，在此致以诚挚感谢！
> 这些 AI 工具在架构设计、代码生成、Bug 修复和持续迭代中提供了巨大帮助。

---

## 功能特性

- ✅ **复习算法选择** — FSRS（先进间隔重复）/ SM-2（Anki 标准算法）/ 莱特纳盒子 / 简单固定间隔，四种算法可随时切换，算法状态持久化存储
- ✅ **多人算法** — StudyViewModel 通过 `ReviewAlgorithm` 接口解耦，可扩展自定义算法
- ✅ **每日学习目标** — 设置每日复习卡片数与每日新卡片数，支持进度追踪
- ✅ **深色/浅色模式** — Material Design 3 主题，设置页面切换即生效
- ✅ **卡片翻转模式** — 支持 Flip（点击翻转）与 Split（上下分区）两种复习查看模式
- ✅ **牌组 CRUD** — 创建/重命名/删除牌组，层级结构
- ✅ **卡片管理** — 创建/编辑/删除卡片，支持 9 种卡片类型（Basic、Reversed、Cloze、Multiple Choice、Image Occlusion、Audio、Video、Markdown、AI Generated）
- ✅ **学习流程** — 翻卡 → 评分（Again/Hard/Good/Easy），间隔重复调度，支持左滑/右滑手势评分
- ✅ **多牌组学习模式** — 支持混合模式（所有牌组）、单牌组模式、多牌组选择模式
- ✅ **上一张功能** — 学习过程中可返回上一张卡片重新评分
- ✅ **复习日志** — 每条评分记录持久化，支持学习统计
- ✅ **统计分析** — 总卡片数、总牌组数、总复习数、今日/本周/本月复习数、记忆保持率、学习时长、连续学习天数、新卡片/待复习/未到期卡片分布
- ✅ **数据导入导出** — JSON 格式完整导出/导入
- ✅ **排序功能** — 牌组与卡片列表支持按名称、创建时间、更新时间排序（升序/降序）
- ✅ **WebDAV 云同步** — 多配置管理、连接测试、上传/下载/双向同步，默认配置标记
- ✅ **设置持久化** — 所有设置（暗色模式、复习算法、每日目标、卡片翻转模式、通知、WebDAV 配置等）存入 SQLite，dirty-state 追踪按需保存
- ✅ **跨平台** — Android + Desktop (Windows/Linux/macOS)
- ✅ **Markdown 渲染** — 基于 CommonMark 的 GFM 标准渲染，支持标题/表格/代码高亮/任务列表/删除线/自动链接/数学公式（`$$` 块级公式）
- ✅ **国际化** — 简体中文、繁體中文、English、日本語、Español 五国语言，支持跟随系统语言
- ✅ **单次保存** — 设置页支持 dirty-state 追踪，仅在修改后显示「保存」按钮
- ✅ **CI/CD** — GitHub Actions 自动构建 Android APK + PR 校验

### 待实现

- ❌ 平台文件对话框（SAF / JFileChooser）— 需要 expect/actual 平台抽象实现
- ❌ AI 制卡助手 — 接口已定义（`AIApi`），后端尚未对接
- ❌ 知识图谱
- ❌ 学习热力图 / 日历贡献图
- ❌ iOS 平台
- ❌ 单元测试

## 技术栈

| 类别 | 技术 |
|------|------|
| UI | Compose Multiplatform 1.7.3 + Material 3 |
| 语言 | Kotlin 2.0.20 + Compose Multiplatform Plugin |
| 数据库 | SQLDelight 2.0.2 (SQLite) |
| 网络 | Ktor 2.3.12 |
| DI | Koin 3.5.6 + koin-compose 1.1.5 |
| 导航 | Voyager 1.0.1 |
| 序列化 | kotlinx-serialization 1.7.2 |
| 日期 | kotlinx-datetime 0.6.1 |
| Markdown | CommonMark 0.21.0（含 GFM Tables、Strikethrough、Autolink、TaskListItems 扩展）|

## 项目结构

```
LumeCard/
├── composeApp/                     # Compose 应用
│   └── src/
│       ├── commonMain/kotlin/
│       │   ├── App.kt              # 入口 + 底部导航（首页/统计/设置）
│       │   ├── di/
│       │   │   ├── AppModule.kt    # Koin 应用模块入口
│       │   │   └── PlatformModule.kt # 平台相关 Koin 模块（expect）
│       │   ├── i18n/               # 国际化
│       │   │   ├── I18nManager.kt  # 多语言管理（SYSTEM/ZH_CN/ZH_TW/EN/JA/ES）
│       │   │   ├── I18nStrings.kt  # 字符串接口（200+ 条）
│       │   │   └── Strings*.kt     # 各语言实现
│       │   ├── platform/           # 平台抽象
│       │   └── ui/
│       │       ├── components/
│       │       │   ├── MarkdownRenderer.kt   # GFM CommonMark 渲染
│       │       │   └── MarkdownEditor.kt     # 编辑/预览切换
│       │       ├── navigation/Navigation.kt
│       │       ├── theme/Theme.kt  # Material 3 绿色调主题
│       │       └── screens/
│       │           ├── dashboard/  # 首页看板（今日概况 + 牌组列表）
│       │           ├── deck/       # 牌组管理（列表/卡片列表/排序）
│       │           ├── study/      # 学习（模式选择/学习界面/评分）
│       │           ├── card/       # 卡片创建/编辑（9种类型）
│       │           ├── settings/   # 设置（完整 UI + dirty-state 持久化）
│       │           └── stats/      # 统计（概览/时间/习惯/分布）
│       ├── androidMain/            # Android 入口 + 平台实现
│       ├── desktopMain/            # Desktop 入口（Window 1024x768）
│       └── iosMain/                # iOS 入口（ComposeUIViewController）
│
├── shared/                         # 共享业务逻辑
│   └── src/commonMain/
│       ├── kotlin/
│       │   ├── model/Models.kt           # 数据模型（KnowledgeBase, Deck, Card 等）
│       │   ├── domain/scheduler/         # 4 种复习算法
│       │   ├── repository/               # 仓库接口 + SQLDelight + InMemory 实现
│       │   ├── data/
│       │   │   ├── ExportManager.kt      # JSON/CSV 导入导出
│       │   │   ├── SyncManager.kt        # WebDAV 上传/下载
│       │   │   ├── WebDavConfig.kt       # WebDAV 配置模型
│       │   │   ├── WebDavConfigManager.kt # 多配置管理+连接测试
│       │   │   └── api/AIApi.kt          # AI 制卡接口+占位实现
│       │   └── di/SharedModule.kt        # Koin 共享模块
│       └── sqldelight/
│           └── LumeCardDatabase.sq       # 7 张表
│
├── buildSrc/src/main/kotlin/
│   └── Dependencies.kt                   # 统一版本管理
│
├── .github/workflows/
│   ├── android.yml                       # Android APK 自动构建
│   └── pr-validation.yml                 # PR 校验（编译+测试）
│
├── build.gradle.kts                      # 根构建脚本
├── settings.gradle.kts                   # 项目设置
├── gradle.properties                     # Gradle 配置
└── gradlew / gradlew.bat / gradle/       # Gradle Wrapper (8.12.1)
```

## 快速开始

### 环境要求

- JDK 17+（推荐使用 Android Studio 自带 JBR）
- Android Studio（最新版，含 Android SDK 35）
- Gradle 8.12.1 (已内嵌)
- Android SDK 编译目标 35

### 构建 Android APK

```bash
./gradlew assembleDebug
```

APK 输出: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

### 运行 Desktop 版本

```bash
./gradlew :composeApp:run
```

### Windows 注意事项

若 Windows 用户名包含非 ASCII 字符（如中文），需要额外设置：

```bash
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set GRADLE_USER_HOME=C:\gradle-home
```

## 数据库

SQLDelight 管理 7 张表，全部查询在 `LumeCardDatabase.sq` 中定义：

| 表 | 用途 |
|------|---------|
| `KnowledgeBase` | 知识库 |
| `Deck` | 牌组 |
| `Card` | 卡片（含调度字段） |
| `ReviewLog` | 复习日志 |
| `FSRSCard` | FSRS 调度状态（兼容保留） |
| `AlgorithmState` | 通用算法调度状态 |
| `AppSettings` | 键值设置存储 |

## 数据模型

### 知识组织结构

```
Knowledge Base → Deck → Card
```

- **KnowledgeBase** — 顶层知识库，卡片与牌组的容器
- **Deck** — 牌组，支持层级嵌套（`parentId` 引用），配有颜色与 emoji 图标
- **Card** — 闪卡，9 种类型，支持标签（tags）、媒体附件（media）、元数据（metadata）

### 复习算法架构

所有算法实现 `ReviewAlgorithm` 接口：
- `initCard()` → 返回初始 `AlgorithmState`
- `schedule(state, rating)` → 根据评分返回新的 `AlgorithmState`

状态通过 `AlgorithmStateRepository` 序列化为字符串持久化到数据库，支持算法间切换。

## 关键架构决策

- **Repository 模式**：`Repositories.kt` 定义接口，`SqlDelightRepositories` 与 `InMemoryRepositories` 两套实现，便于测试
- **MVVM 架构**：Voyager `ScreenModel` + Koin DI，状态通过 `StateFlow` 驱动 UI
- **算法接口解耦**：`StudyViewModel` 通过 DI 注入 `ReviewAlgorithm`，可配置不同算法
- **WebDAV 多配置管理**：支持多个同步配置保存、默认标记、连接测试、双向同步
- **平台抽象**：使用 Kotlin `expect/actual` 机制处理数据库驱动、平台样式等差异

## License

MIT
