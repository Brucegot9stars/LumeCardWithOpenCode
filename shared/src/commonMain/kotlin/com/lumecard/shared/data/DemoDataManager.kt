package com.lumecard.shared.data

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.KnowledgeBaseRepository
import com.lumecard.shared.repository.SettingsRepository
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Manages demo knowledge base data for first-time users.
 * The demo data is inserted on first launch and can be deleted by the user.
 */
class DemoDataManager(
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val DEMO_CREATED_KEY = "demo_data_created"
        private const val DEMO_KB_ID = "demo_kb_001"
    }

    /**
     * Insert demo data if not already created.
     * Safe to call multiple times — idempotent.
     */
    suspend fun ensureDemoData() {
        if (settingsRepository.getBoolean(DEMO_CREATED_KEY, false)) return

        val now = Clock.System.now()
        val kb = KnowledgeBase(
            id = DEMO_KB_ID,
            name = "示例知识库",
            description = "这是一个示例知识库，包含各种卡片类型的演示。你可以自由删除它。",
            createdAt = now,
            updatedAt = now,
        )
        knowledgeBaseRepository.insert(kb)

        val decks = createDecks()
        decks.forEach { deckRepository.insert(it) }

        val cards = createCards()
        cards.forEach { cardRepository.insert(it) }

        cardRepository.rebuildFtsIndex()
        settingsRepository.set(DEMO_CREATED_KEY, "true")
    }

    /**
     * Check if demo data exists.
     */
    suspend fun hasDemoData(): Boolean {
        return knowledgeBaseRepository.getById(DEMO_KB_ID) != null
    }

    /**
     * Delete all demo data (knowledge base + decks + cards).
     */
    suspend fun deleteDemoData() {
        val now = Clock.System.now()
        knowledgeBaseRepository.delete(DEMO_KB_ID)
        settingsRepository.delete(DEMO_CREATED_KEY)
    }

    // ── Decks ────────────────────────────────────────────

    private fun createDecks(): List<Deck> = listOf(
        Deck(
            id = "demo_deck_001",
            knowledgeBaseId = DEMO_KB_ID,
            name = "基础术语",
            description = "常见的学习概念和定义",
            color = "#4CAF50",
            icon = "\uD83D\uDCDA",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        ),
        Deck(
            id = "demo_deck_002",
            knowledgeBaseId = DEMO_KB_ID,
            name = "编程概念",
            description = "编程基础知识",
            color = "#2196F3",
            icon = "\uD83D\uDD2C",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        ),
        Deck(
            id = "demo_deck_003",
            knowledgeBaseId = DEMO_KB_ID,
            name = "数学基础",
            description = "基础数学公式与概念",
            color = "#FF9800",
            icon = "\uD83D\uDCA1",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        ),
        Deck(
            id = "demo_deck_004",
            knowledgeBaseId = DEMO_KB_ID,
            name = "日常用语",
            description = "常用外语短语",
            color = "#E91E63",
            icon = "\uD83C\uDFAF",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        ),
    )

    // ── Cards ────────────────────────────────────────────

    private fun createCards(): List<Card> = buildList {
        val now = Clock.System.now()

        // ── Deck 1: 基础术语 ──

        // BASIC card (centered-style: short question + short answer)
        add(Card(
            id = "demo_card_001",
            deckId = "demo_deck_001",
            type = CardType.BASIC,
            title = "间隔重复",
            front = "什么是间隔重复？",
            back = "一种学习技巧，在逐渐增大的间隔时间点复习信息，以加强长期记忆。",
            tags = listOf("学习方法", "记忆"),
            createdAt = now, updatedAt = now,
        ))

        // BASIC card (left-aligned style: longer content)
        add(Card(
            id = "demo_card_002",
            deckId = "demo_deck_001",
            type = CardType.BASIC,
            title = "主动回忆",
            front = "主动回忆（Active Recall）是什么？",
            back = "主动回忆是一种学习策略，通过主动从记忆中提取信息来加强记忆。" +
                    "与被动复习（如重读笔记）不同，主动回忆要求大脑" +
                    "主动搜索并提取存储的信息。研究表明，主动回忆" +
                    "比被动复习更有效地提高长期记忆保持率。常见的主动回忆方法" +
                    "包括：闪卡自测、闭卷练习、费曼技巧等。",
            tags = listOf("学习方法", "认知科学"),
            createdAt = now, updatedAt = now,
        ))

        // REVERSED card
        add(Card(
            id = "demo_card_003",
            deckId = "demo_deck_001",
            type = CardType.REVERSED,
            title = "艾宾浩斯遗忘曲线",
            front = "艾宾浩斯遗忘曲线描述了什么现象？",
            back = "人类大脑在学习新信息后，遗忘速度先快后慢的规律。" +
                    "德国心理学家赫尔曼·艾宾浩斯通过实验发现，" +
                    "学习后20分钟遗忘42%，1小时后遗忘56%，" +
                    "1天后遗忘74%，1周后遗忘77%。" +
                    "间隔重复正是基于这一规律设计的复习策略。",
            tags = listOf("心理学", "记忆"),
            createdAt = now, updatedAt = now,
        ))

        // CLOZE card
        add(Card(
            id = "demo_card_004",
            deckId = "demo_deck_001",
            type = CardType.CLOZE,
            title = "记忆宫殿",
            front = "记忆宫殿（{{c1::方法}}）是一种{{c2::记忆术}}，通过将信息与熟悉的{{c3::空间位置}}关联来增强记忆。",
            back = "",
            tags = listOf("记忆术", "学习方法"),
            createdAt = now, updatedAt = now,
        ))

        // MULTIPLE_CHOICE card
        add(Card(
            id = "demo_card_005",
            deckId = "demo_deck_001",
            type = CardType.MULTIPLE_CHOICE,
            title = "学习金字塔",
            front = "根据学习金字塔理论，哪种学习方式的知识保持率最高？",
            back = "+教授给他人（90%）\n小组讨论（50%）\n实践练习（75%）\n视听学习（20%）",
            tags = listOf("学习理论"),
            createdAt = now, updatedAt = now,
        ))

        // ── Deck 2: 编程概念 ──

        // BASIC card
        add(Card(
            id = "demo_card_006",
            deckId = "demo_deck_002",
            type = CardType.BASIC,
            title = "递归",
            front = "什么是递归（Recursion）？",
            back = "递归是函数直接或间接调用自身的编程技术。" +
                    "递归函数必须包含两个要素：\n" +
                    "1. 基准情况（Base Case）：终止递归的条件\n" +
                    "2. 递归步骤（Recursive Case）：将问题分解为更小的子问题",
            tags = listOf("编程", "算法"),
            createdAt = now, updatedAt = now,
        ))

        // MARKDOWN card
        add(Card(
            id = "demo_card_007",
            deckId = "demo_deck_002",
            type = CardType.MARKDOWN,
            title = "时间复杂度",
            front = """# 常见时间复杂度对比

| 复杂度 | 名称 | 示例 |
|--------|------|------|
| O(1) | 常数 | 数组下标访问 |
| O(log n) | 对数 | 二分查找 |
| O(n) | 线性 | 遍历数组 |
| O(n log n) | 线性对数 | 归并排序 |
| O(n²) | 平方 | 冒泡排序 |""",
            back = """时间复杂度从低到高排列。

**关键理解：**
- O(1) 和 O(log n) 是高效的
- O(n) 是可接受的
- O(n log n) 是排序算法的最优下界
- O(n²) 在大数据集上不可接受""",
            tags = listOf("算法", "复杂度"),
            createdAt = now, updatedAt = now,
        ))

        // RICH_TEXT card (HTML content)
        add(Card(
            id = "demo_card_008",
            deckId = "demo_deck_002",
            type = CardType.RICH_TEXT,
            title = "设计模式",
            front = "<h3>什么是设计模式？</h3><p>设计模式是软件开发中经过验证的、针对特定问题的<strong>可复用解决方案</strong>。</p>",
            back = "<p>设计模式分为三大类：</p><ul><li><strong>创建型</strong>：单例、工厂、建造者</li><li><strong>结构型</strong>：适配器、装饰器、代理</li><li><strong>行为型</strong>：观察者、策略、命令</li></ul>",
            tags = listOf("设计模式", "软件工程"),
            createdAt = now, updatedAt = now,
        ))

        // CLOZE card
        add(Card(
            id = "demo_card_009",
            deckId = "demo_deck_002",
            type = CardType.CLOZE,
            title = "Kotlin 协程",
            front = "Kotlin 协程是 {{c1::轻量级线程}}，通过 {{c2::suspend}} 关键字定义挂起函数，使用 {{c3::CoroutineScope}} 管理生命周期。",
            back = "",
            tags = listOf("Kotlin", "协程"),
            createdAt = now, updatedAt = now,
        ))

        // ── Deck 3: 数学基础 ──

        // BASIC card (centered-style: formula)
        add(Card(
            id = "demo_card_010",
            deckId = "demo_deck_003",
            type = CardType.BASIC,
            title = "勾股定理",
            front = "a² + b² = ?",
            back = "c²\n\n其中 a、b 是直角三角形的两条直角边，c 是斜边。",
            tags = listOf("几何", "公式"),
            createdAt = now, updatedAt = now,
        ))

        // MARKDOWN card (math formulas)
        add(Card(
            id = "demo_card_011",
            deckId = "demo_deck_003",
            type = CardType.MARKDOWN,
            title = "一元二次方程",
            front = """# 一元二次方程求根公式

对于方程 **ax² + bx + c = 0**，求根公式为：

x = (-b ± √(b² - 4ac)) / 2a

其中判别式 **Δ = b² - 4ac** 决定根的性质。""",
            back = """- **Δ > 0**：两个不相等的实数根
- **Δ = 0**：两个相等的实数根（重根）
- **Δ < 0**：两个共轭复数根

**示例：** x² - 5x + 6 = 0
- a=1, b=-5, c=6
- Δ = 25 - 24 = 1 > 0
- x = (5 ± 1) / 2 → x₁ = 3, x₂ = 2""",
            tags = listOf("代数", "公式"),
            createdAt = now, updatedAt = now,
        ))

        // MULTIPLE_CHOICE card
        add(Card(
            id = "demo_card_012",
            deckId = "demo_deck_003",
            type = CardType.MULTIPLE_CHOICE,
            title = "圆的面积",
            front = "圆的面积公式是什么？",
            back = "+S = πr²\nS = 2πr\nS = πd\nS = ½r²θ",
            tags = listOf("几何", "公式"),
            createdAt = now, updatedAt = now,
        ))

        // ── Deck 4: 日常用语 ──

        // BASIC card (centered-style: short phrase)
        add(Card(
            id = "demo_card_013",
            deckId = "demo_deck_004",
            type = CardType.BASIC,
            title = "Nice to meet you",
            front = "Nice to meet you.",
            back = "很高兴认识你。",
            tags = listOf("英语", "问候"),
            createdAt = now, updatedAt = now,
        ))

        // REVERSED card
        add(Card(
            id = "demo_card_014",
            deckId = "demo_deck_004",
            type = CardType.REVERSED,
            title = "How's it going?",
            front = "How's it going?",
            back = "最近怎么样？\n（非正式问候语，用于熟人之间）",
            tags = listOf("英语", "问候"),
            createdAt = now, updatedAt = now,
        ))

        // BASIC card (longer content)
        add(Card(
            id = "demo_card_015",
            deckId = "demo_deck_004",
            type = CardType.BASIC,
            title = "Break a leg",
            front = "What does \"Break a leg\" mean?",
            back = "祝你好运！\n\n这是一个英语习语，字面意思是「断一条腿」，" +
                    "但实际上用于在演出或重要场合前祝福对方。" +
                    "剧院文化中直接说「Good luck」被认为不吉利，" +
                    "所以用反话来表达祝福。",
            tags = listOf("英语", "习语"),
            createdAt = now, updatedAt = now,
        ))

        // CLOZE card
        add(Card(
            id = "demo_card_016",
            deckId = "demo_deck_004",
            type = CardType.CLOZE,
            title = "日语问候",
            front = "おはよう（{{c1::早上好}}）、こんにちは（{{c2::下午好}}）、こんばんは（{{c3::晚上好}}）",
            back = "",
            tags = listOf("日语", "问候"),
            createdAt = now, updatedAt = now,
        ))

        // AI_GENERATED card (treated like MARKDOWN in display)
        add(Card(
            id = "demo_card_017",
            deckId = "demo_deck_004",
            type = CardType.AI_GENERATED,
            title = "法语基础",
            front = """# 法语基础问候

| 法语 | 发音 | 中文 |
|------|------|------|
| Bonjour | 松如赫 | 你好 |
| Merci | 梅尔西 | 谢谢 |
| S'il vous plaît | 西尔乌普莱 | 请 |
| Au revoir | 欧赫乌瓦尔 | 再见 |""",
            back = """法语问候语使用场景：

- **Bonjour** — 白天通用问候
- **Bonsoir** — 晚上问候
- **Merci beaucoup** — 非常感谢
- **De rien** — 不客气""",
            tags = listOf("法语", "问候"),
            createdAt = now, updatedAt = now,
        ))

        // Additional BASIC card to demonstrate title display
        add(Card(
            id = "demo_card_018",
            deckId = "demo_deck_001",
            type = CardType.BASIC,
            title = "",
            front = "这张卡片没有设置标题（title 字段为空）。",
            back = "在卡片列表中，没有标题的卡片会显示前50个字符作为名称。",
            tags = listOf("演示"),
            createdAt = now, updatedAt = now,
        ))
    }
}
