package com.lumecard.shared.help

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HelpArticle(
    val id: String,
    val title: String,
    val order: Int = 0,
    val tags: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val relatedArticles: List<String> = emptyList(),
    val hasDangerContent: Boolean = false,
    val sections: List<HelpSection>,
)

@Serializable
sealed interface HelpSection {
    val title: String?
}

@Serializable
@SerialName("paragraph")
data class ParagraphSection(
    override val title: String? = null,
    val content: String,
) : HelpSection

@Serializable
@SerialName("steps")
data class StepsSection(
    override val title: String? = null,
    val items: List<String>,
) : HelpSection

@Serializable
@SerialName("tip")
data class TipSection(
    override val title: String? = null,
    val content: String,
) : HelpSection

@Serializable
@SerialName("warning")
data class WarningSection(
    override val title: String? = null,
    val content: String,
) : HelpSection

@Serializable
@SerialName("danger")
data class DangerSection(
    override val title: String? = null,
    val content: String,
) : HelpSection

@Serializable
@SerialName("ordered")
data class OrderedListSection(
    override val title: String? = null,
    val items: List<String>,
) : HelpSection

@Serializable
@SerialName("bullet")
data class BulletListSection(
    override val title: String? = null,
    val items: List<String>,
) : HelpSection

@Serializable
@SerialName("faq")
data class FAQSection(
    override val title: String? = null,
    val items: List<FAQItem>,
) : HelpSection

@Serializable
data class FAQItem(
    val question: String,
    val answer: String,
)

@Serializable
data class HelpArticleCollection(
    val articles: List<HelpArticle>,
)
