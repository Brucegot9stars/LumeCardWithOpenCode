# LumeCard 导入 JSON 规范

## 1. 用哪种格式

LumeCard 的「设置 → 数据管理 → 导入」接受三种顶层结构：完整备份（`backup`）、分享（`share`）、增量同步（`incremental`）。

制卡交付 **一律使用 `share` 格式**：

- 只包含知识库、牌组、卡牌，不含复习日志、学习计划、私人进度。
- 结构最小，最不容易因多余字段出错。
- 文件本身不携带学习进度；但同 ID 导入会覆盖已有实体，见第 5 节。

## 2. 最小完整示例

```json
{
  "exportType": "share",
  "version": "1.0.0",
  "schemaVersion": 1,
  "exportDate": "2026-11-03T08:00:00.000Z",
  "knowledgeBases": [
    {
      "id": "lcg_spanish_basics_kb",
      "name": "西班牙语入门",
      "description": "A1 核心词汇与日常表达",
      "icon": "📚",
      "createdAt": "2026-11-03T08:00:00.000Z",
      "updatedAt": "2026-11-03T08:00:00.000Z",
      "version": 1
    }
  ],
  "decks": [
    {
      "id": "lcg_spanish_basics_d01",
      "knowledgeBaseId": "lcg_spanish_basics_kb",
      "name": "日常问候",
      "description": "打招呼、告别与礼貌用语",
      "color": "#4CAF50",
      "icon": "🎯",
      "parentId": null,
      "createdAt": "2026-11-03T08:00:00.000Z",
      "updatedAt": "2026-11-03T08:00:00.000Z",
      "version": 1
    }
  ],
  "cards": [
    {
      "id": "lcg_spanish_basics_d01_c001",
      "deckId": "lcg_spanish_basics_d01",
      "type": "BASIC",
      "front": "Hola",
      "back": "你好",
      "title": "西班牙语问候 Hola",
      "tags": ["问候", "A1"],
      "metadata": { "hcenter": "true", "vcenter": "true", "fontSize": "22" },
      "createdAt": "2026-11-03T08:00:00.000Z",
      "updatedAt": "2026-11-03T08:00:00.000Z",
      "version": 1
    }
  ]
}
```

## 3. 字段说明

### 3.1 顶层

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `exportType` | string | 是 | 固定 `"share"` |
| `version` | string | 是 | 导出格式版本，当前 `"1.0.0"` |
| `schemaVersion` | number | 是 | 数据库结构版本，当前 `1`（以 `version.properties` 为准） |
| `exportDate` | string | 是 | 生成时间，ISO-8601 UTC |
| `deviceId` | string \| null | 否 | 制卡文件不需要 |
| `knowledgeBases` | array | 是 | 知识库列表 |
| `decks` | array | 是 | 牌组列表 |
| `cards` | array | 是 | 卡牌列表 |

### 3.2 KnowledgeBase

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 全局唯一；建议 `lcg_<主题>_kb` |
| `name` | string | 是 | 知识库名称 |
| `description` | string \| null | 否 | 一句话说明学习范围 |
| `icon` | string | 否 | emoji，默认 `📁` |
| `createdAt` / `updatedAt` | string | 是 | ISO-8601 |
| `version` | number | 否 | 默认 1 |
| `deletedAt` / `syncedAt` | string \| null | 否 | 制卡时不要填 |

推荐图标（与内置示例一致）：`📁 🗂️ 📚 🧠 🌍 🔬 🎨 💡`。

### 3.3 Deck

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 全局唯一；建议 `lcg_<主题>_d01` |
| `knowledgeBaseId` | string | 是 | 必须指向存在且未删除的知识库 |
| `name` | string | 是 | 牌组名，建议 2–12 字 |
| `description` | string \| null | 是（可为 null） | 一句话说明 |
| `color` | string | 是 | 十六进制色，默认 `#4CAF50` |
| `icon` | string | 是 | emoji，默认 `📚` |
| `parentId` | string \| null | 否 | 父牌组 ID；用于两层结构 |
| `createdAt` / `updatedAt` | string | 是 | ISO-8601 |
| `version` | number | 否 | 默认 1 |

可用颜色：`#4CAF50` `#2196F3` `#FF9800` `#E91E63` `#9C27B0` `#00BCD4` `#FF5722` `#607D8B`。

可用图标：`📚 🎓 💡 🌟 🎯 📝 🔬 🎨`（也可用其他 emoji，但同库保持风格统一）。

如果有 `parentId`，父牌组必须出现在 `decks` 数组中，且不能形成循环。建议父牌组排在子牌组之前。

### 3.4 Card

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 全局唯一；建议 `lcg_<主题>_d01_c001` |
| `deckId` | string | 是 | 必须指向存在的牌组 |
| `type` | string | 是 | 见下方卡型列表 |
| `front` | string | 是 | 正面内容 |
| `back` | string | 是 | 背面内容（CLOZE 可为空字符串，见卡型文档） |
| `title` | string | 强烈建议 | 卡片标题，4–16 字，用于列表识别 |
| `tags` | string[] | 是（可为空数组） | 建议 1–4 个，风格统一 |
| `media` | array | 否 | **制卡时留空**；学习界面暂不渲染媒体 |
| `metadata` | object | 否 | 字符串键值对；见第 4 节 |
| `createdAt` / `updatedAt` | string | 是 | ISO-8601 |
| `lastReviewedAt` / `nextReviewAt` | string \| null | 否 | 制卡时不要填 |
| `version` | number | 否 | 默认 1 |

卡型只允许：

```text
BASIC
REVERSED
CLOZE
MULTIPLE_CHOICE
MARKDOWN
RICH_TEXT
```

不要使用 `AI_GENERATED`。未知卡型在导入时会被静默降级为 `BASIC`，导致格式失效。

## 4. metadata 样式键

`metadata` 必须是 `Map<string, string>`，值全部是字符串：

| 键 | 合法值 | 作用 | 生效卡型 |
|---|---|---|---|
| `hcenter` | `"true"` / `"false"` | 水平居中 | BASIC、REVERSED、RICH_TEXT |
| `vcenter` | `"true"` / `"false"` | 垂直居中 | BASIC、REVERSED、RICH_TEXT |
| `fontSize` | 整数字符串，12–120 | 字号（sp） | BASIC、REVERSED、CLOZE |
| `fontFamily` | 内置字体 ID | 字体 | BASIC、REVERSED |

- 只在需要时写入；`false` 可以省略。
- 统一写字符串，例如 `"fontSize": "20"`，不要写数字 `20`。
- 内置字体 ID：`default`、`serif`、`sans-serif`、`monospace`、`cursive`。不要写 `user_*`。
- **不要把样式元数据写到不生效的卡型上**：写错不会报错，但完全没效果。

## 5. ID、时间与覆盖行为

### 文件编码

- 使用 **UTF-8**，建议无 BOM。
- 中文、日文、emoji 直接写字符，不要转成 `\uXXXX`（可读性更好，解析器也支持）。
- 字符串内的换行用 `\n`，不要写真实换行。

### ID

- ASCII、小写、只用字母/数字/下划线，例如 `lcg_linear_algebra_d02_c017`。
- 知识库 ID、牌组 ID、卡牌 ID 分别全局唯一。
- **稳定 ID**：同一主题重新生成时保持不变，应用按 ID 覆盖，不会产生重复。
- **大改版换命名空间**：例如 `lcg_linear_algebra_v2_*`。如果旧卡已经被学习过，直接用同 ID 覆盖会让「旧的学习状态」对应到「新的卡片内容」，容易错位；大改版应使用新 ID。
- 不要用随机数或时间戳做 ID，否则每次导入都会重复。

### 时间戳

- 必须是 `Instant.parse` 能解析的 ISO-8601，例如 `2026-11-03T08:00:00.000Z`。
- 同一批实体可以共用同一个时间。
- `createdAt` / `updatedAt` 缺失会导致整份文件反序列化失败。

### 导入行为

- 导入按 `id` 执行 `INSERT OR REPLACE`：同 ID 覆盖，不同 ID 即使同名也会并存。
- 同 ID 覆盖是「替换实体行」，不是内容合并；已学习过的卡不要依赖覆盖来更新，优先换新 ID。
- 导入不会按名称去重，也不会自动解决命名冲突；命名空间和稳定 ID 由制卡方负责。
- 牌组通过 `knowledgeBaseId` 归属知识库；卡牌通过 `deckId` 归属牌组。引用错误会产生孤儿数据。

## 6. 常见导入失败原因

| 现象 | 原因 | 修复 |
|---|---|---|
| 提示 JSON 无效 | 尾逗号、注释、单引号、BOM、未转义换行 | 用校验器；字符串内换行写 `\n` |
| 导入后卡片变成 BASIC | `type` 拼写错误或用了 `AI_GENERATED` | 只用允许的 6 种卡型 |
| 牌组/卡片不见了 | `deckId` / `knowledgeBaseId` 指向不存在的 ID | 校验引用完整性 |
| 居中/字号没效果 | 卡型不支持，或值不是字符串 | 对照第 4 节 |
| 导入后出现两份同名内容 | 用了新 ID 重复导入 | 保持稳定 ID，或导入前删除旧内容 |
| 学习页内容被截断 | 长内容开了 `vcenter`（关闭滚动） | 长内容去掉 `vcenter` |

## 7. 导入步骤（交付时告诉用户）

1. 打开 LumeCard。
2. 进入 **设置 → 数据管理 → 导入**。
3. 选择生成的 `.lumecard.json` 文件。
4. 导入完成后回到知识库列表查看。
