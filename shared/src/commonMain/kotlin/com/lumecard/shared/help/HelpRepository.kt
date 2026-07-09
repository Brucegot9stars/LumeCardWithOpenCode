package com.lumecard.shared.help

import com.lumecard.shared.util.loadTextResource
import kotlinx.serialization.json.Json

data class HelpSearchResult(
    val article: HelpArticle,
    val score: Float = 0f,
    val matchedOn: Set<String> = emptySet(),
)

class HelpRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, HelpArticleCollection>()

    fun getArticles(locale: String): List<HelpArticle> {
        val collection = loadCollection(locale)
        return collection?.articles?.sortedBy { it.order } ?: emptyList()
    }

    fun getArticle(locale: String, articleId: String): HelpArticle? {
        return getArticles(locale).find { it.id == articleId }
    }

    fun search(locale: String, query: String): List<HelpSearchResult> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase().trim()
        val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        val articles = getArticles(locale)

        return articles.mapNotNull { article ->
            var score = 0f
            val matched = mutableSetOf<String>()
            val titleLower = article.title.lowercase()
            val allKeywords = article.keywords.map { it.lowercase() }
            val allTags = article.tags.map { it.lowercase() }

            tokens.forEach { token ->
                if (titleLower.contains(token)) {
                    score += if (titleLower == token) 100f else if (titleLower.startsWith(token)) 80f else 50f
                    matched.add("title")
                }
                allKeywords.forEach { kw ->
                    if (kw.contains(token)) { score += 30f; matched.add("keyword") }
                }
                allTags.forEach { tag ->
                    if (tag.contains(token)) { score += 10f; matched.add("tag") }
                }
                article.sections.forEach { section ->
                    when (section) {
                        is ParagraphSection -> if (section.content.lowercase().contains(token)) { score += 15f; matched.add("content") }
                        is StepsSection -> section.items.forEach { if (it.lowercase().contains(token)) { score += 15f; matched.add("content") } }
                        is TipSection -> if (section.content.lowercase().contains(token)) { score += 15f; matched.add("content") }
                        is WarningSection -> if (section.content.lowercase().contains(token)) { score += 15f; matched.add("content") }
                        is DangerSection -> if (section.content.lowercase().contains(token)) { score += 20f; matched.add("content") }
                        is OrderedListSection -> section.items.forEach { if (it.lowercase().contains(token)) { score += 15f; matched.add("content") } }
                        is BulletListSection -> section.items.forEach { if (it.lowercase().contains(token)) { score += 15f; matched.add("content") } }
                        is FAQSection -> section.items.forEach { item ->
                            if (item.question.lowercase().contains(token)) { score += 20f; matched.add("faq") }
                            if (item.answer.lowercase().contains(token)) { score += 15f; matched.add("faq") }
                        }
                    }
                }
                if (article.id.lowercase().contains(token)) { score += 50f; matched.add("id") }
            }

            if (matched.isNotEmpty()) {
                val rawLower = query.lowercase()
                if (article.title.lowercase() == rawLower) score += 200f
                if (article.id.lowercase() == rawLower) score += 150f
                HelpSearchResult(article = article, score = score, matchedOn = matched)
            } else null
        }.sortedByDescending { it.score }
    }

    fun preload(locale: String) {
        loadCollection(locale)
    }

    private fun loadCollection(locale: String): HelpArticleCollection? {
        if (cache.containsKey(locale)) return cache[locale]
        val path = "/help/$locale/articles.json"
        val raw = loadTextResource(path)
        if (raw != null) {
            return try {
                json.decodeFromString<HelpArticleCollection>(raw).also { cache[locale] = it }
            } catch (_: Exception) {
                fallbackToEnglish()
            }
        }
        return if (locale != "en") fallbackToEnglish() else null
    }

    private fun fallbackToEnglish(): HelpArticleCollection? {
        if (cache.containsKey("en")) return cache["en"]
        val raw = loadTextResource("/help/en/articles.json") ?: return null
        return try {
            json.decodeFromString<HelpArticleCollection>(raw).also { cache["en"] = it }
        } catch (_: Exception) { null }
    }

    fun clearCache() {
        cache.clear()
    }
}
