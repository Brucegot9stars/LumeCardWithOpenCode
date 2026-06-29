# AI 制卡功能 — 设计文档

## 模块划分

```
shared/
├── src/commonMain/
│   ├── resources/config/
│   │   └── prompt_ai_card.txt       # 默认 Prompt
│   └── kotlin/com/lumecard/shared/
│       ├── data/
│       │   ├── AiCardModels.kt      # 请求/响应/结果模型
│       │   ├── AiCardPromptManager.kt
│       │   └── AiCardGenerator.kt   # 核心编排器
│       └── di/
│           └── SharedModule.kt      # + AiCardPromptManager, AiCardGenerator

composeApp/
├── src/commonMain/kotlin/com/lumecard/app/
│   ├── i18n/ (6 files)
│   ├── ui/screens/aicard/
│   │   └── AiCardScreen.kt          # 页面 + ViewModel
│   ├── ui/screens/settings/
│   │   └── SettingsScreen.kt        # + 入口
│   └── di/
│       └── AppModule.kt             # + AiCardViewModel
```

## 数据流

```
Settings → AiCardScreen
  User: mode / materials / count
    → AiCardViewModel
      → AiCardPromptManager (prompt)
      → AiConfigManager (config)
      → AiCardGenerator
        → AiClient.sendChatCompletion()
        → Repositories (KB/Deck/Card)
      → AiCardResult
```

## 三种模式

| Mode | KB | Deck | Cards |
|------|----|------|-------|
| AUTO | AI 新建 | AI 新建 | 新建 |
| SPECIFY_KB | 用户选 | AI 在 KB 内新建 | 新建 |
| SPECIFY_BOTH | 用户选 | 用户选 | 仅新增卡片 |

## Prompt 外置策略

- 默认 Prompt: `shared/src/commonMain/resources/config/prompt_ai_card.txt`
- 加载方式: `getResourceAsStream()` (与 AppVersion 相同)
- 用户修改: 缓存于 SettingsRepository (key: `ai_card_prompt`)
- 恢复默认: 从资源文件重新加载
