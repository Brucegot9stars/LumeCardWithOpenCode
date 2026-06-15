# LumeCard

一款现代化、跨平台的闪卡学习应用（Anki 替代品），使用 Compose Multiplatform + Kotlin 构建。

> 本项目全程使用 **opencode + DeepSeek V4 Flash Free + MIMO2.5 Free** 开发，在此致以诚挚感谢！  
> 这些 AI 工具在架构设计、代码生成、Bug 修复和持续迭代中提供了巨大帮助。

---

## 功能特性

- ✅ **复习算法选择** — FSRS 先进间隔重复 / SM-2 标准算法 / 莱特纳盒子 / 简单模式，可随时切换
- ✅ **多人算法** — StudyViewModel 通过 `ReviewAlgorithm` 接口解耦，可扩展自定义算法
- ✅ **每日学习目标** — 设置每日卡片数、新卡片数，进度条实时显示完成情况
- ✅ **深色/浅色模式** — Material Design 3 主题，设置页面切换即生效
- ✅ **牌组 CRUD** — 创建/重命名/删除牌组，层级结构
- ✅ **卡片管理** — 创建/编辑/删除卡片，支持多种卡片类型
- ✅ **学习流程** — 翻卡 → 评分 (Again/Hard/Good/Easy)，间隔重复调度
- ✅ **复习日志** — 每条评分记录持久化，支持学习统计
- ✅ **统计分析** — 总复习数、平均评分、记忆保持率等
- ✅ **数据导入导出** — JSON 格式完整导出/导入
- ✅ **WebDAV 云同步** — 配置 WebDAV 地址/用户名/密码实现云端同步
- ✅ **设置持久化** — 所有设置 (暗色模式、复习算法、每日目标、通知、WebDAV 等) 存入 SQLite
- ✅ **跨平台** — Android + Desktop (Windows/Linux/macOS)
- ✅ **Markdown 渲染** — 基于 CommonMark 的 GFM 标准渲染（标题/表格/代码高亮/任务列表/数学公式/Mermaid 流程图）
- ✅ **单次保存** — 设置页支持 dirty-state 追踪，显示「保存」按钮按需持久化

### 待实现

- ❌ 平台文件对话框 (SAF / JFileChooser) — 需要 expect/actual 实现
- ❌ 算法动态切换 — StudyViewModel 当前固定使用 SM-2，需改为从 SettingsRepository 读取用户选择
- ❌ AI 制卡助手
- ❌ 知识图谱
- ❌ 热力图
- ❌ iOS 平台

## 技术栈

| 类别 | 技术 |
|------|------|
| UI | Compose Multiplatform 1.7.3 + Material Design 3 |
| 语言 | Kotlin 2.0.20 |
| 数据库 | SQLDelight 2.0.2 (SQLite) |
| 网络 | Ktor 2.3.12 |
| DI | Koin 3.5.6 + koin-compose 1.1.5 |
| 导航 | Voyager 1.0.1 |
| 序列化 | kotlinx-serialization 1.7.2 |
| 日期 | kotlinx-datetime 0.6.1 |

## 项目结构

```
LumeCard/
├── shared/                          # 共享业务逻辑
│   ├── src/commonMain/kotlin/
│   │   ├── model/                   # 数据模型
│   │   │   └── Models.kt            # Card, Deck, KnowledgeBase 等
│   │   ├── domain/scheduler/        # 复习算法
│   │   │   ├── ReviewAlgorithm.kt   # 算法接口 + AlgorithmState
│   │   │   ├── FSRSAlgorithm.kt     # FSRS 间隔重复
│   │   │   ├── SM2Algorithm.kt      # SM-2 标准算法
│   │   │   ├── LeitnerAlgorithm.kt  # 莱特纳盒子
│   │   │   ├── SimpleAlgorithm.kt   # 简单固定间隔
│   │   │   └── FSRSAlgorithmAdapter.kt # FSRS → ReviewAlgorithm 适配器
│   │   ├── repository/              # 数据仓库
│   │   │   ├── Repositories.kt      # 接口 (含 SettingsRepository)
│   │   │   ├── SqlDelightRepositories.kt
│   │   │   └── InMemoryRepositories.kt
│   │   ├── data/                    # 数据层
│   │   │   ├── ExportManager.kt     # 导入导出
│   │   │   └── api/AIApi.kt         # AI 接口定义
│   │   └── di/SharedModule.kt       # Koin 共享模块
│   └── src/commonMain/sqldelight/
│       └── LumeCardDatabase.sq      # 7 张表 + 全部查询
│
├── composeApp/                      # Compose 应用
│   └── src/commonMain/kotlin/
│       ├── App.kt                   # 入口 + 底部导航
│       ├── di/AppModule.kt          # Koin 应用模块
│       ├── ui/screens/
│       │   ├── dashboard/           # 首页看板
│       │   ├── deck/                # 牌组管理 (列表/管理/卡片列表)
│       │   ├── study/               # 学习界面 + StudyViewModel
│       │   ├── card/                # 卡片创建/编辑
│       │   ├── settings/            # 设置 (完整 UI + 持久化)
│       │   │   ├── SettingsScreen.kt
│       │   │   ├── SettingsViewModel.kt
│       │   │   └── SettingsStateHolder.kt
│       │   └── stats/               # 学习统计
│       └── ui/theme/Theme.kt        # Material3 主题

├── .github/workflows/               # CI
│   ├── android.yml                  # Android APK 构建
│   └── pr-validation.yml            # PR 校验
```

## 快速开始

### 环境要求

- JDK 17+（推荐 Android Studio JBR）
- Android Studio (最新版)
- Gradle 8.12.1 (已内嵌)

### 构建 Android APK

```bash
./gradlew :composeApp:assembleDebug
```

APK 位于: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

### 运行 Desktop 版本

```bash
./gradlew :composeApp:run
```

### Windows 注意事项

若 Windows 用户名包含非 ASCII 字符（如中文），需要额外设置：

```bash
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set GRADLE_USER_HOME=C:\gradle-home
# 然后使用解压版 Gradle 构建
```

## 数据库

SQLDelight 管理 7 张表：

| 表 | 用途 |
|------|---------|
| `KnowledgeBase` | 知识库 |
| `Deck` | 牌组 |
| `Card` | 卡片（含调度字段） |
| `ReviewLog` | 复习日志 |
| `FSRSCard` | FSRS 调度状态（兼容保留） |
| `AlgorithmState` | 通用算法调度状态 |
| `AppSettings` | 键值设置存储 |

## License

MIT
