package com.lumecard.shared.settings

class SettingsSearchEngine {
    private var index: List<SettingsIndexEntry> = emptyList()

    fun rebuild(entries: List<SettingsIndexEntry>) {
        index = entries.sortedBy { it.order }
    }

    fun search(query: String): List<SettingsSearchResult> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase().trim()
        val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }

        val results = index.mapNotNull { entry ->
            var score = 0f
            val matched = mutableSetOf<String>()

            val titleLower = entry.title.lowercase()
            val descLower = entry.description.lowercase()
            val pageLower = entry.page.lowercase()
            val sectionLower = entry.section.lowercase()
            val allKeywords = entry.keywords.map { it.lowercase() }

            tokens.forEach { token ->
                // Title match (highest priority)
                if (titleLower.contains(token)) {
                    score += if (titleLower == token) 100f else if (titleLower.startsWith(token)) 80f else 50f
                    matched.add("title")
                }
                // Description match
                if (descLower.contains(token)) {
                    score += 20f
                    matched.add("description")
                }
                // Keyword match
                allKeywords.forEach { kw ->
                    if (kw.contains(token)) {
                        score += 30f
                        matched.add("keyword")
                    }
                }
                // Page name match
                if (pageLower.contains(token)) {
                    score += 10f
                    matched.add("page")
                }
                // Section match
                if (sectionLower.contains(token)) {
                    score += 15f
                    matched.add("section")
                }
                // Tag match
                entry.tags.forEach { tag ->
                    if (tag.lowercase().contains(token)) {
                        score += 5f
                        matched.add("tag")
                    }
                }
            }

            if (matched.isNotEmpty()) {
                // Boost exact token matches against the raw query
                val rawLower = query.lowercase()
                if (entry.title.lowercase() == rawLower) score += 200f
                if (entry.id.lowercase() == rawLower) score += 150f

                SettingsSearchResult(
                    entry = entry,
                    score = score,
                    matchedOn = matched,
                )
            } else null
        }

        return results.sortedByDescending { it.score }
    }

    fun findById(id: String): SettingsIndexEntry? = index.find { it.id == id }
}
