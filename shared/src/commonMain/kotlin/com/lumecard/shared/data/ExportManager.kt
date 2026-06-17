package com.lumecard.shared.data

import com.lumecard.shared.AppVersion
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.model.LearningPlan
import com.lumecard.shared.model.ReviewLog
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LumeCardExport(
    val version: String = AppVersion.EXPORT_VERSION,
    val schemaVersion: Int = AppVersion.SCHEMA_VERSION,
    val exportDate: String,
    val deviceId: String? = null,
    val knowledgeBases: List<ExportKnowledgeBase> = emptyList(),
    val decks: List<ExportDeck>,
    val cards: List<ExportCard>,
    val reviewLogs: List<ExportReviewLog> = emptyList(),
    val learningPlans: List<ExportLearningPlan> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

@Serializable
data class ExportKnowledgeBase(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val version: Long = 1,
    val deletedAt: String? = null
)

@Serializable
data class ExportDeck(
    val id: String,
    val knowledgeBaseId: String = "default",
    val name: String,
    val description: String?,
    val color: String,
    val icon: String,
    val parentId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val version: Long = 1,
    val deletedAt: String? = null
)

@Serializable
data class ExportCard(
    val id: String,
    val deckId: String,
    val type: String,
    val front: String,
    val back: String,
    val tags: List<String>,
    val createdAt: String,
    val updatedAt: String,
    val lastReviewedAt: String? = null,
    val nextReviewAt: String? = null,
    val version: Long = 1,
    val deletedAt: String? = null
)

@Serializable
data class ExportReviewLog(
    val id: String,
    val cardId: String,
    val rating: Int,
    val reviewTime: Int,
    val interval: Int,
    val easeFactor: Float,
    val repetitions: Int,
    val lapseCount: Int,
    val reviewedAt: String,
    val version: Long = 1,
    val deletedAt: String? = null
)

@Serializable
data class ExportLearningPlan(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String = "NOT_STARTED",
    val isDefault: Boolean = false,
    val knowledgeBaseIds: List<String> = emptyList(),
    val deckIds: List<String> = emptyList(),
    val cardIds: List<String> = emptyList(),
    val totalCards: Int = 0,
    val completedCards: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    val version: Long = 1,
    val deletedAt: String? = null
)

@Serializable
data class SyncPayload(
    val deviceId: String,
    val syncId: String,
    val timestamp: String,
    val sinceVersion: Map<String, Long> = emptyMap(),
    val knowledgeBases: List<ExportKnowledgeBase> = emptyList(),
    val decks: List<ExportDeck> = emptyList(),
    val cards: List<ExportCard> = emptyList(),
    val reviewLogs: List<ExportReviewLog> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val deletedIds: List<String> = emptyList()
)

class ExportManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun exportToJson(
        knowledgeBases: List<KnowledgeBase>,
        decks: List<Deck>,
        cards: List<Card>,
        reviewLogs: List<ReviewLog> = emptyList(),
        learningPlans: List<LearningPlan> = emptyList(),
        settings: Map<String, String> = emptyMap(),
        deviceId: String? = null
    ): String {
        val export = LumeCardExport(
            exportDate = Clock.System.now().toString(),
            deviceId = deviceId,
            knowledgeBases = knowledgeBases.map { kb ->
                ExportKnowledgeBase(
                    id = kb.id,
                    name = kb.name,
                    description = kb.description,
                    createdAt = kb.createdAt.toString(),
                    updatedAt = kb.updatedAt.toString(),
                    version = kb.version,
                    deletedAt = kb.deletedAt?.toString()
                )
            },
            decks = decks.map { deck ->
                ExportDeck(
                    id = deck.id,
                    knowledgeBaseId = deck.knowledgeBaseId,
                    name = deck.name,
                    description = deck.description,
                    color = deck.color,
                    icon = deck.icon,
                    parentId = deck.parentId,
                    createdAt = deck.createdAt.toString(),
                    updatedAt = deck.updatedAt.toString(),
                    version = deck.version,
                    deletedAt = deck.deletedAt?.toString()
                )
            },
            cards = cards.map { card ->
                ExportCard(
                    id = card.id,
                    deckId = card.deckId,
                    type = card.type.name,
                    front = card.front,
                    back = card.back,
                    tags = card.tags,
                    createdAt = card.createdAt.toString(),
                    updatedAt = card.updatedAt.toString(),
                    lastReviewedAt = card.lastReviewedAt?.toString(),
                    nextReviewAt = card.nextReviewAt?.toString(),
                    version = card.version,
                    deletedAt = card.deletedAt?.toString()
                )
            },
            reviewLogs = reviewLogs.map { log ->
                ExportReviewLog(
                    id = log.id,
                    cardId = log.cardId,
                    rating = log.rating,
                    reviewTime = log.reviewTime,
                    interval = log.interval,
                    easeFactor = log.easeFactor,
                    repetitions = log.repetitions,
                    lapseCount = log.lapseCount,
                    reviewedAt = log.reviewedAt.toString(),
                    version = log.version,
                    deletedAt = log.deletedAt?.toString()
                )
            },
            learningPlans = learningPlans.map { plan ->
                ExportLearningPlan(
                    id = plan.id,
                    name = plan.name,
                    description = plan.description,
                    status = plan.status.name,
                    isDefault = plan.isDefault,
                    knowledgeBaseIds = plan.knowledgeBaseIds,
                    deckIds = plan.deckIds,
                    cardIds = plan.cardIds,
                    totalCards = plan.totalCards,
                    completedCards = plan.completedCards,
                    createdAt = plan.createdAt.toString(),
                    updatedAt = plan.updatedAt.toString(),
                    version = plan.version,
                    deletedAt = plan.deletedAt?.toString()
                )
            },
            settings = settings
        )
        return json.encodeToString(LumeCardExport.serializer(), export)
    }

    fun importFromJson(jsonString: String): LumeCardExport? {
        return try {
            val export = json.decodeFromString(LumeCardExport.serializer(), jsonString)
            when (export.schemaVersion) {
                AppVersion.SCHEMA_VERSION -> export
                1 -> migrateV1ToV2(export)
                2 -> migrateV2ToV3(export)
                else -> export
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun migrateV1ToV2(v1Export: LumeCardExport): LumeCardExport {
        val migratedDecks = v1Export.decks.map { deck ->
            if (deck.knowledgeBaseId.isBlank()) {
                deck.copy(knowledgeBaseId = "default", version = 1)
            } else {
                deck.copy(version = maxOf(deck.version, 1))
            }
        }
        val migratedCards = v1Export.cards.map { card ->
            card.copy(version = maxOf(card.version, 1))
        }
        val defaultKb = ExportKnowledgeBase(
            id = "default",
            name = "Default",
            description = null,
            createdAt = v1Export.exportDate,
            updatedAt = v1Export.exportDate,
            version = 1
        )
        val kbs = v1Export.knowledgeBases.ifEmpty { listOf(defaultKb) }
        return v1Export.copy(
            schemaVersion = AppVersion.SCHEMA_VERSION,
            version = AppVersion.EXPORT_VERSION,
            knowledgeBases = kbs,
            decks = migratedDecks,
            cards = migratedCards
        )
    }

    private fun migrateV2ToV3(v2Export: LumeCardExport): LumeCardExport {
        return v2Export.copy(
            schemaVersion = AppVersion.SCHEMA_VERSION,
            version = AppVersion.EXPORT_VERSION
        )
    }

    fun exportToCsv(cards: List<Card>): String {
        val header = "Front,Back,Tags,Type"
        val rows = cards.map { card ->
            val front = card.front.replace(",", ";").replace("\n", " ")
            val back = card.back.replace(",", ";").replace("\n", " ")
            val tags = card.tags.joinToString(";")
            "$front,$back,$tags,${card.type.name}"
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun importFromCsv(csvString: String): List<Pair<String, String>> {
        val lines = csvString.lines().drop(1)
        return lines.mapNotNull { line ->
            val parts = line.split(",")
            if (parts.size >= 2) {
                Pair(parts[0].trim(), parts[1].trim())
            } else {
                null
            }
        }
    }
}
