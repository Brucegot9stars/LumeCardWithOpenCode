# AI 制卡功能 — 最终交付

## 新增模块说明

### 1. `shared` 模块（数据 + 业务层）

| 文件 | 说明 |
|------|------|
| `shared/src/commonMain/resources/config/prompt_ai_card.txt` | 默认 Prompt 外置资源文件（不在 Kotlin 代码中硬编码） |
| `.../data/AiCardModels.kt` | AI 制卡数据模型：`AiCardMode`（3种模式）、`AiCardRequest`、`AiCardResult`、`AiCardError`、`AiCardException`、`AiCardResponseJson`、`AiCardItemJson` |
| `.../data/AiCardPromptManager.kt` | Prompt 管理器：从资源文件加载默认、保存用户编辑到 SettingsRepository、恢复默认 |
| `.../data/AiCardGenerator.kt` | 核心编排器：调用 AI API → 解析 JSON → 创建知识库/牌组/卡牌（含名称冲突处理） |

### 2. `composeApp` 模块（UI 层）

| 文件 | 说明 |
|------|------|
| `.../ui/screens/aicard/AiCardScreen.kt` | 完整制卡页面 + 4个私有 Composable（ModeSelector、KbSelector、DeckSelector） |
| `.../ui/screens/aicard/AiCardViewModel.kt` | ViewModel（ScreenModel）：状态管理、异步生成、错误分类 |

### 3. 修改文件

| 文件 | 修改内容 |
|------|----------|
| `AiProtocol.kt` | 新增 `buildChatRequest()` + `parseChatResponse()` 接口方法，OpenAI Chat 和 Anthropic Messages 各实现一套（支持系统提示词+用户消息的聊天补全） |
| `AiClient.kt` | 新增 `sendChatCompletion()` 方法 |
| `shared/.../di/SharedModule.kt` | 注册 `AiCardPromptManager`、`AiCardGenerator` |
| `composeApp/.../di/AppModule.kt` | 注册 `AiCardViewModel` |
| `composeApp/.../i18n/I18nStrings.kt` | 新增 32 条 AI 制卡相关字符串 |
| `StringsZhCn.kt` / `StringsEn.kt` / `StringsJa.kt` / `StringsEs.kt` / `StringsZhTw.kt` | 各新增 32 条翻译 |
| `composeApp/.../SettingsScreen.kt` | 新增「AI 制卡」导航入口 |
| `Repositories.kt` | `KnowledgeBaseRepository` 新增 `getByName()`、`DeckRepository` 新增 `getByNameInKnowledgeBase()` |
| `SqlDelightRepositories.kt` / `InMemoryRepositories.kt` | 实现上述新增方法 |
| `LumeCardDatabase.sq` | 新增 `selectKnowledgeBaseByName`、`selectDeckByNameAndKnowledgeBase` 查询 |

### 4. 未改动的模块

- 普通制卡流程（CreateCardScreen / CardViewModel / CardRepository）—— **不受影响**
- 学习/复习流程（StudyViewModel / AlgorithmState）—— **不受影响**
- 同步/导出机制（SyncManager / ExportManager）—— **不受影响**
- 数据库迁移（已有表结构）—— **不受影响**

---

## 核心架构变化

### AiProtocol 接口扩展（向后兼容）

```kotlin
interface AiProtocol {
    // 原有方法（testConnection）不变
    fun endpoint(): String
    fun headers(config: AiConfig): Map<String, String>
    fun buildTestRequestBody(config: AiConfig): String
    fun parseTestResponse(responseBody: String): Result<String>
    fun extractError(responseBody: String, statusCode: Int): String

    // 新增方法（有默认实现，不破坏现有协议类）
    fun buildChatRequest(config: AiConfig, systemPrompt: String, userMessage: String): String
    fun parseChatResponse(responseBody: String): Result<String>
}
```

- `OpenAiChatProtocol` — 完整实现，使用 `/chat/completions` + system/user 消息格式
- `AnthropicMessagesProtocol` — 完整实现，使用 `/messages` + system 参数 + user 消息格式
- `OpenAiResponsesProtocol` — 使用默认实现（不支持），待后续扩展

### 实体唯一性保障

新增名称查询方法到 Repository 层：
- `KnowledgeBaseRepository.getByName(name: String)` — 全局唯一检查
- `DeckRepository.getByNameInKnowledgeBase(kbId, name)` — 知识库内唯一检查

冲突自动增加后缀 `(1)(2)(3)...`，在 `AiCardGenerator.makeUniqueKbName()` 和 `makeUniqueDeckName()` 中实现。

---

## AI 流程说明

```
1. 用户填写：选题/资料/数量/模式
2. 系统构建 Prompt：
   - 从资源文件加载默认 Prompt → 替换 {card_count} → 拼接参考资料
3. 发送 AI 请求（AiClient.sendChatCompletion）：
   - 根据协议构建消息（system = Prompt + 资料，user = "生成 N 张卡片"）
4. 接收 AI 响应：
   - 期望 JSON：{ knowledge_base_name, deck_name, cards: [{ front, back, type, tags }] }
5. 解析 JSON → AiCardResponseJson
6. 创建实体（按模式）：
   - 完全自动：创建 KB → 创建 Deck → 创建 Cards
   - 指定 KB：在 KB 内创建 Deck → 创建 Cards
   - 指定 KB+Deck：在指定 Deck 中创建 Cards
7. 名称冲突自动添加后缀
8. 返回 AiCardResult（含创建统计）
```

### 三种模式对比

| 模式 | KB | Deck | Cards |
|------|-----|------|-------|
| `AUTO` | AI 生成名 → 新建（唯一） | AI 生成名 → 在 KB 内新建（唯一） | → 新建 |
| `SPECIFY_KB` | 用户选定 | AI 生成名 → 在 KB 内新建（唯一） | → 新建 |
| `SPECIFY_BOTH` | 用户选定 | 用户选定 | → 仅新增（不覆盖已有） |

---

## 数据流

```
SettingsScreen → [AI 制卡] → AiCardScreen
                                │
    ┌────────────────────────────┴────────────────────────────┐
    │                                                         │
    │  AiCardScreen (Composable)                              │
    │   ├─ Mode Selector (Radio)                              │
    │   ├─ KB Picker (Radio list)          [模式2/3]          │
    │   ├─ Deck Picker (Radio list)        [模式3]            │
    │   ├─ Reference Materials (TextField + File Import)      │
    │   ├─ Card Count Slider + Input                          │
    │   ├─ Prompt Editor (expandable copy/restore/save)       │
    │   ├─ [Generate] Button → 确认对话框（>100张时）          │
    │   └─ Result/Error Card                                   │
    │                                                         │
    │  AiCardViewModel (ScreenModel)                          │
    │   ├─ state: StateFlow<AiCardUiState>                    │
    │   ├─ loadInitialData() → KB列表 + Prompt + Config        │
    │   ├─ generate() → AiCardGenerator.generate(request)      │
    │   └─ savePrompt/restoreDefaultPrompt/appendMaterials...   │
    │                                                         │
    └────────────────────────────┬────────────────────────────┘
                                 │
                    AiCardGenerator (shared)
                    ┌────────────┴────────────┐
                    │                         │
            AiClient.sendChatCompletion   Repositories
            (AiProtocol.buildChatRequest)  (getByName/insert)
                    │                         │
                    └────────────┬────────────┘
                                 │
                         AiCardResult
                       (kbId, deckId, cardsCreated)
```

---

## 验证结果

| 环境 | 编译 | 状态 |
|------|------|------|
| Desktop (compileKotlinDesktop) | `BUILD SUCCESSFUL` | ✅ |
| Android (compileDebugKotlinAndroid) | `BUILD SUCCESSFUL` | ✅ |
| 单元测试 (desktopTest) | `BUILD SUCCESSFUL` | ✅ |

---

## 后续扩展建议

1. **更多 AI Provider 支持**：为 `OpenAiResponsesProtocol` 实现 `buildChatRequest`/`parseChatResponse`；新增 Gemini Protocol 实现（需适配其 API 格式）
2. **Prompt 国际化**：在 `resources/config/` 下按语言放置 `prompt_ai_card_{locale}.txt`，`AiCardPromptManager` 根据当前语言加载
3. **PDF/Word 文件解析**：在 `AiCardViewModel.appendReferenceMaterials()` 中扩展，解析 PDF/Word 文件后提取文本
4. **卡牌预览**：生成前展示 AI 即将创建的卡牌列表，供用户确认
5. **批量生成进度**：单次生成超过 500 张时，显示流式进度（逐步返回已创建的卡牌）
6. **自定义 Prompt 模板**：允许多套 Prompt 模板切换（如：考试模式、基础概念模式等）
7. **Prompt 导出/导入**：复用现有 ConfigExport 机制，将 Prompt 模板纳入数据导出
8. **增量生成**：在已有牌组追加卡牌时，AI 自动避免重复内容
