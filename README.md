# LumeCard

一款现代化、高性能、跨平台的闪卡学习应用，目标是成为 Anki 的优秀替代品。

## 功能特性

- 🎯 **FSRS算法** - 先进的间隔重复算法，比SM-2减少20-30%的复习次数
- 📝 **Markdown编辑器** - 支持LaTeX、代码高亮、表格等
- 🎨 **现代化UI** - Material Design 3，支持深色/浅色模式
- 📱 **跨平台** - Android、Windows、iOS、Linux
- 🤖 **AI集成** - 智能制卡、学习助手
- 📊 **统计分析** - 学习热力图、掌握度分析
- 🔗 **知识图谱** - 双向链接、卡片关系
- 📦 **导入导出** - 支持JSON、CSV格式

## 技术栈

- **UI**: Compose Multiplatform
- **语言**: Kotlin
- **数据库**: SQLDelight
- **网络**: Ktor
- **DI**: Koin
- **导航**: Voyager

## 开发环境

### Windows

1. 安装 JDK 17+
   ```bash
   # 从 https://adoptium.net/ 下载安装
   ```

2. 安装 Android Studio
   ```bash
   # 从 https://developer.android.com/studio 下载
   ```

3. 克隆项目
   ```bash
   git clone <repository-url>
   cd LumeCard
   ```

4. 打开项目
   - 使用 Android Studio 或 IntelliJ IDEA 打开项目根目录

## 项目结构

```
LumeCard/
├── shared/                     # 共享业务逻辑
│   ├── model/                  # 数据模型
│   │   └── Models.kt           # Card, Deck, KnowledgeBase等
│   ├── database/               # 数据库
│   │   └── LumeCardDatabase.sq # SQLDelight Schema
│   ├── repository/             # 数据仓库
│   │   ├── Repositories.kt     # 仓库接口
│   │   └── InMemoryRepositories.kt # 内存实现
│   ├── domain/                 # 领域层
│   │   └── scheduler/          # FSRS算法
│   │       └── FSRSAlgorithm.kt
│   ├── data/                   # 数据层
│   │   ├── api/                # API接口
│   │   │   └── AIApi.kt
│   │   └── ExportManager.kt    # 导入导出
│   └── di/                     # 依赖注入
│       └── SharedModule.kt
│
├── composeApp/                 # Compose应用
│   ├── ui/
│   │   ├── components/         # 通用组件
│   │   │   └── MarkdownEditor.kt
│   │   ├── screens/            # 页面
│   │   │   ├── dashboard/      # 首页
│   │   │   │   ├── DashboardScreen.kt
│   │   │   │   └── DashboardViewModel.kt
│   │   │   ├── deck/           # 牌组管理
│   │   │   │   ├── DeckListScreen.kt
│   │   │   │   ├── DeckManagementScreen.kt
│   │   │   │   └── CardListScreen.kt
│   │   │   ├── study/          # 学习页面
│   │   │   │   ├── StudyScreen.kt
│   │   │   │   └── StudyViewModel.kt
│   │   │   ├── card/           # 卡片编辑
│   │   │   │   ├── CreateCardScreen.kt
│   │   │   │   └── CardViewModel.kt
│   │   │   ├── settings/       # 设置
│   │   │   │   └── SettingsScreen.kt
│   │   │   └── stats/          # 统计
│   │   │       └── StatsScreen.kt
│   │   ├── theme/              # 主题
│   │   │   └── Theme.kt
│   │   └── navigation/         # 导航
│   │       └── Navigation.kt
│   └── di/                     # 依赖注入
│       └── AppModule.kt
│
├── buildSrc/                   # 构建脚本
│   └── Dependencies.kt         # 依赖版本管理
│
├── iosApp/                     # iOS应用壳
└── docs/                       # 文档
```

## 构建

### Android
```bash
./gradlew :composeApp:assembleDebug
```

### Desktop
```bash
./gradlew :composeApp:run
```

### iOS
使用 Xcode 打开 `iosApp/LumeCard.xcodeproj`

## 开发指南

### 添加新页面

1. 在 `composeApp/src/commonMain/kotlin/com/lumecard/app/ui/screens/` 下创建新目录
2. 创建 Screen 类实现 `Screen` 接口
3. 在 `Navigation.kt` 中添加导航支持

### 添加新组件

在 `composeApp/src/commonMain/kotlin/com/lumecard/app/ui/components/` 下创建可组合函数

### 数据模型

所有数据模型定义在 `shared/src/commonMain/kotlin/com/lumecard/shared/model/Models.kt`

### FSRS算法

算法实现在 `shared/src/commonMain/kotlin/com/lumecard/shared/domain/scheduler/FSRSAlgorithm.kt`

### 导入导出

导入导出功能实现在 `shared/src/commonMain/kotlin/com/lumecard/shared/data/ExportManager.kt`

支持格式：
- JSON: 完整数据导出
- CSV: 卡片数据导出

## License

MIT
