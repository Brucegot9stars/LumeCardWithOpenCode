package com.lumecard.shared.settings

data class SettingsIndexEntry(
    val id: String,
    val title: String,
    val description: String = "",
    val keywords: List<String> = emptyList(),
    val page: String,
    val section: String = "",
    val route: String,
    val order: Int = 0,
    val tags: Set<String> = emptySet(),
)

data class SettingsSearchQuery(
    val raw: String,
    val tokens: List<String> = emptyList(),
    val filters: Map<String, String> = emptyMap(),
)

data class SettingsSearchResult(
    val entry: SettingsIndexEntry,
    val score: Float = 0f,
    val matchedOn: Set<String> = emptySet(),
)
