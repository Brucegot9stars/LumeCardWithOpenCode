package com.lumecard.shared.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class KnowledgeBase(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val version: Long = 1,
    val deletedAt: Instant? = null,
    val syncedAt: Instant? = null
)

@Serializable
data class Deck(
    val id: String,
    val knowledgeBaseId: String,
    val name: String,
    val description: String? = null,
    val color: String = "#4CAF50",
    val icon: String = "\uD83D\uDCDA",
    val parentId: String? = null,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val version: Long = 1,
    val deletedAt: Instant? = null,
    val syncedAt: Instant? = null
) {
    companion object {
        val colors = listOf("#4CAF50", "#2196F3", "#FF9800", "#E91E63", "#9C27B0", "#00BCD4", "#FF5722", "#607D8B")
        val icons = listOf("\uD83D\uDCDA", "\uD83C\uDF93", "\uD83D\uDCA1", "\uD83C\uDF1F", "\uD83C\uDFAF", "\uD83D\uDCDD", "\uD83D\uDD2C", "\uD83C\uDFA8")
    }
}

@Serializable
data class Card(
    val id: String,
    val deckId: String,
    val type: CardType,
    val front: String,
    val back: String,
    val tags: List<String> = emptyList(),
    val media: List<Media> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val lastReviewedAt: Instant? = null,
    val nextReviewAt: Instant? = null,
    val version: Long = 1,
    val deletedAt: Instant? = null,
    val syncedAt: Instant? = null
)

@Serializable
enum class CardType {
    BASIC,
    REVERSED,
    CLOZE,
    MULTIPLE_CHOICE,
    MARKDOWN,
    AI_GENERATED,
    RICH_TEXT
}

@Serializable
data class Media(
    val id: String,
    val type: MediaType,
    val filePath: String,
    val mimeType: String,
    val size: Long
)

@Serializable
enum class MediaType {
    IMAGE, AUDIO, VIDEO, FILE
}

@Serializable
data class ReviewLog(
    val id: String,
    val cardId: String,
    val rating: Int,
    val reviewTime: Int,
    val interval: Int,
    val easeFactor: Float,
    val repetitions: Int,
    val lapseCount: Int,
    val reviewedAt: Instant = Clock.System.now(),
    val version: Long = 1,
    val deletedAt: Instant? = null,
    val syncedAt: Instant? = null
)

@Serializable
data class FSRSCard(
    val id: String,
    val due: Instant,
    val stability: Double,
    val difficulty: Double,
    val elapsedDays: Int,
    val scheduledDays: Int,
    val reps: Int,
    val lapses: Int,
    val state: CardState
)

@Serializable
enum class CardState {
    NEW,
    LEARNING,
    REVIEW,
    RELEARNING
}

@Serializable
enum class Rating(val value: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4)
}

@Serializable
enum class PlanStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}

@Serializable
data class LearningPlan(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: PlanStatus = PlanStatus.NOT_STARTED,
    val isDefault: Boolean = false,
    val knowledgeBaseIds: List<String> = emptyList(),
    val deckIds: List<String> = emptyList(),
    val cardIds: List<String> = emptyList(),
    val totalCards: Int = 0,
    val completedCards: Int = 0,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val version: Long = 1,
    val deletedAt: Instant? = null,
    val syncedAt: Instant? = null
)
