# Move & Merge — 设计文档

## 操作映射

| 来源 | 目标 | 操作 | 行为 |
|------|------|------|------|
| 知识库 A | 知识库 B | Merge | A 的牌组 → B（冲突弹窗），A 删除 |
| 牌组 A | 知识库 | Move | A 改变知识库归属 |
| 牌组 A | 牌组 B | Merge | A 的卡牌 → B（自动重命名），A 删除 |
| 卡牌 | 牌组 | Move | 卡牌改变 deckId |

## 模块划分

```
shared/.../data/
├── EntityOperations.kt         # MoveMergeRequest / Result / ConflictType 模型
├── EntityMergeManager.kt       # KB merge / Deck merge / Deck move / Card move

composeApp/.../
├── ui/components/
│   └── OperationConfirmation.kt # 60s 免确认对话框组件
├── ui/screens/knowledgebase/
│   └── KnowledgeBaseScreen.kt   # + 拖拽 + Merge 弹窗
├── ui/screens/deck/
│   ├── DeckListScreen.kt        # + 拖拽 + Move/Merge 弹窗
│   └── DeckViewModel.kt         # + 获取所有 KB（用于移动目标选择）
├── ui/screens/card/
│   ├── CardListScreen.kt        # + Move 弹窗
│   └── CardViewModel.kt         # + 获取所有牌组（用于移动目标选择）
├── i18n/ (6 files)              # + 移动/合并相关字符串
└── di/
    └── AppModule.kt             # + OperationConfirmationManager
```

## 数据流

```
用户触发（拖拽/菜单）
  → MoveMergeRequest
    → EntityMergeManager (shared)
      → Repositories (getCards/getDecks/update/delete)
      → ConflictHandler (名称冲突自动重命名 + (1)(2)(3)...)
    → MoveMergeResult
  → OperationConfirmation (弹窗 → 60s 免确认)
  → 执行结果反馈
```

## 冲突处理

| 场景 | 策略 | 弹窗 |
|------|------|------|
| KB 合并 → 牌组名重复 | 弹窗询问是否继续 | 需确认 |
| Deck 合并 → 卡牌 front 相同 | 自动追加 (1)(2)(3)... | 无弹窗 |
| 无名卡牌 | 使用 `Card_{timestamp}` 兜底 | 无弹窗 |
