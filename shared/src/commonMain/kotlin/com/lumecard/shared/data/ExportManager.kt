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

/** Full backup: includes private data (review logs, learning plans). For sync/restore only. */
@Serializable
data class DataExport(
    val exportType: String = "backup",
    val version: String = AppVersion.EXPORT_VERSION,
    val schemaVersion: Int = AppVersion.SCHEMA_VERSION,
    val exportDate: String,
    val deviceId: String? = null,
    val knowledgeBases: List<ExportKnowledgeBase> = emptyList(),
    val decks: List<ExportDeck>,
    val cards: List<ExportCard>,
    val reviewLogs: List<ExportReviewLog> = emptyList(),
    val learningPlans: List<ExportLearningPlan> = emptyList()
)

/** Share-friendly: only knowledge-base content. No private learning plans or progress. */
@Serializable
data class ShareExport(
    val exportType: String = "share",
    val version: String = AppVersion.EXPORT_VERSION,
    val schemaVersion: Int = AppVersion.SCHEMA_VERSION,
    val exportDate: String,
    val deviceId: String? = null,
    val knowledgeBases: List<ExportKnowledgeBase> = emptyList(),
    val decks: List<ExportDeck>,
    val cards: List<ExportCard>
)

/** Incremental sync payload — only records changed since a given timestamp. */
@Serializable
data class IncrementalExport(
    val exportType: String = "incremental",
    val version: String = AppVersion.EXPORT_VERSION,
    val schemaVersion: Int = AppVersion.SCHEMA_VERSION,
    val exportDate: String,
    val deviceId: String? = null,
    val since: String? = null,
    val knowledgeBases: List<ExportKnowledgeBase> = emptyList(),
    val decks: List<ExportDeck> = emptyList(),
    val cards: List<ExportCard> = emptyList(),
    val reviewLogs: List<ExportReviewLog> = emptyList(),
    val learningPlans: List<ExportLearningPlan> = emptyList()
)

@Serializable
data class ConfigExport(
    val version: String = AppVersion.EXPORT_VERSION,
    val schemaVersion: Int = AppVersion.SCHEMA_VERSION,
    val exportDate: String,
    val deviceId: String? = null,
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

/** A snapshot entry in the sync history archive. */
@Serializable
data class SyncHistoryEntry(
    val timestamp: String,
    val deviceId: String,
    val filename: String
)

/** Index of all sync snapshots on the remote. */
@Serializable
data class SyncHistoryIndex(
    val entries: List<SyncHistoryEntry> = emptyList()
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

class ExportManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun exportData(
        knowledgeBases: List<KnowledgeBase>,
        decks: List<Deck>,
        cards: List<Card>,
        reviewLogs: List<ReviewLog> = emptyList(),
        learningPlans: List<LearningPlan> = emptyList(),
        deviceId: String? = null
    ): String {
        val export = DataExport(
            exportDate = Clock.System.now().toString(),
            deviceId = deviceId,
            knowledgeBases = knowledgeBases.map { kb ->
                ExportKnowledgeBase(
                    id = kb.id, name = kb.name, description = kb.description,
                    createdAt = kb.createdAt.toString(), updatedAt = kb.updatedAt.toString(),
                    version = kb.version, deletedAt = kb.deletedAt?.toString()
                )
            },
            decks = decks.map { d ->
                ExportDeck(
                    id = d.id, knowledgeBaseId = d.knowledgeBaseId, name = d.name,
                    description = d.description, color = d.color, icon = d.icon,
                    parentId = d.parentId, createdAt = d.createdAt.toString(),
                    updatedAt = d.updatedAt.toString(), version = d.version,
                    deletedAt = d.deletedAt?.toString()
                )
            },
            cards = cards.map { c ->
                ExportCard(
                    id = c.id, deckId = c.deckId, type = c.type.name,
                    front = c.front, back = c.back, tags = c.tags,
                    createdAt = c.createdAt.toString(), updatedAt = c.updatedAt.toString(),
                    lastReviewedAt = c.lastReviewedAt?.toString(),
                    nextReviewAt = c.nextReviewAt?.toString(),
                    version = c.version, deletedAt = c.deletedAt?.toString()
                )
            },
            reviewLogs = reviewLogs.map { l ->
                ExportReviewLog(
                    id = l.id, cardId = l.cardId, rating = l.rating,
                    reviewTime = l.reviewTime, interval = l.interval,
                    easeFactor = l.easeFactor, repetitions = l.repetitions,
                    lapseCount = l.lapseCount, reviewedAt = l.reviewedAt.toString(),
                    version = l.version, deletedAt = l.deletedAt?.toString()
                )
            },
            learningPlans = learningPlans.map { p ->
                ExportLearningPlan(
                    id = p.id, name = p.name, description = p.description,
                    status = p.status.name, isDefault = p.isDefault,
                    knowledgeBaseIds = p.knowledgeBaseIds, deckIds = p.deckIds,
                    cardIds = p.cardIds, totalCards = p.totalCards,
                    completedCards = p.completedCards,
                    createdAt = p.createdAt.toString(), updatedAt = p.updatedAt.toString(),
                    version = p.version, deletedAt = p.deletedAt?.toString()
                )
            }
        )
        return json.encodeToString(DataExport.serializer(), export)
    }

    /** Export knowledge base content only — no private learning plans or review logs. */
    fun exportShareData(
        knowledgeBases: List<KnowledgeBase>,
        decks: List<Deck>,
        cards: List<Card>,
        deviceId: String? = null
    ): String {
        val export = ShareExport(
            exportDate = Clock.System.now().toString(),
            deviceId = deviceId,
            knowledgeBases = knowledgeBases.map { kb ->
                ExportKnowledgeBase(
                    id = kb.id, name = kb.name, description = kb.description,
                    createdAt = kb.createdAt.toString(), updatedAt = kb.updatedAt.toString(),
                    version = kb.version, deletedAt = kb.deletedAt?.toString()
                )
            },
            decks = decks.map { d ->
                ExportDeck(
                    id = d.id, knowledgeBaseId = d.knowledgeBaseId, name = d.name,
                    description = d.description, color = d.color, icon = d.icon,
                    parentId = d.parentId, createdAt = d.createdAt.toString(),
                    updatedAt = d.updatedAt.toString(), version = d.version,
                    deletedAt = d.deletedAt?.toString()
                )
            },
            cards = cards.map { c ->
                ExportCard(
                    id = c.id, deckId = c.deckId, type = c.type.name,
                    front = c.front, back = c.back, tags = c.tags,
                    createdAt = c.createdAt.toString(), updatedAt = c.updatedAt.toString(),
                    lastReviewedAt = c.lastReviewedAt?.toString(),
                    nextReviewAt = c.nextReviewAt?.toString(),
                    version = c.version, deletedAt = c.deletedAt?.toString()
                )
            }
        )
        return json.encodeToString(ShareExport.serializer(), export)
    }

    /** Import knowledge base content from a share file. */
    fun importShareData(jsonString: String): ShareExport? {
        return try {
            json.decodeFromString(ShareExport.serializer(), jsonString)
        } catch (_: Exception) {
            null
        }
    }

    /** Export only records changed since [since]. Returns full snapshot if [since] is null. */
    fun exportIncrementalData(
        knowledgeBases: List<KnowledgeBase>,
        decks: List<Deck>,
        cards: List<Card>,
        reviewLogs: List<ReviewLog> = emptyList(),
        learningPlans: List<LearningPlan> = emptyList(),
        since: String? = null,
        deviceId: String? = null
    ): String {
        val export = IncrementalExport(
            exportDate = Clock.System.now().toString(),
            since = since,
            deviceId = deviceId,
            knowledgeBases = knowledgeBases.map { kb ->
                ExportKnowledgeBase(
                    id = kb.id, name = kb.name, description = kb.description,
                    createdAt = kb.createdAt.toString(), updatedAt = kb.updatedAt.toString(),
                    version = kb.version, deletedAt = kb.deletedAt?.toString()
                )
            },
            decks = decks.map { d ->
                ExportDeck(
                    id = d.id, knowledgeBaseId = d.knowledgeBaseId, name = d.name,
                    description = d.description, color = d.color, icon = d.icon,
                    parentId = d.parentId, createdAt = d.createdAt.toString(),
                    updatedAt = d.updatedAt.toString(), version = d.version,
                    deletedAt = d.deletedAt?.toString()
                )
            },
            cards = cards.map { c ->
                ExportCard(
                    id = c.id, deckId = c.deckId, type = c.type.name,
                    front = c.front, back = c.back, tags = c.tags,
                    createdAt = c.createdAt.toString(), updatedAt = c.updatedAt.toString(),
                    lastReviewedAt = c.lastReviewedAt?.toString(),
                    nextReviewAt = c.nextReviewAt?.toString(),
                    version = c.version, deletedAt = c.deletedAt?.toString()
                )
            },
            reviewLogs = reviewLogs.map { l ->
                ExportReviewLog(
                    id = l.id, cardId = l.cardId, rating = l.rating,
                    reviewTime = l.reviewTime, interval = l.interval,
                    easeFactor = l.easeFactor, repetitions = l.repetitions,
                    lapseCount = l.lapseCount, reviewedAt = l.reviewedAt.toString(),
                    version = l.version, deletedAt = l.deletedAt?.toString()
                )
            },
            learningPlans = learningPlans.map { p ->
                ExportLearningPlan(
                    id = p.id, name = p.name, description = p.description,
                    status = p.status.name, isDefault = p.isDefault,
                    knowledgeBaseIds = p.knowledgeBaseIds, deckIds = p.deckIds,
                    cardIds = p.cardIds, totalCards = p.totalCards,
                    completedCards = p.completedCards,
                    createdAt = p.createdAt.toString(), updatedAt = p.updatedAt.toString(),
                    version = p.version, deletedAt = p.deletedAt?.toString()
                )
            }
        )
        return json.encodeToString(IncrementalExport.serializer(), export)
    }

    fun exportConfig(
        settings: Map<String, String>,
        deviceId: String? = null
    ): String {
        val export = ConfigExport(
            exportDate = Clock.System.now().toString(),
            deviceId = deviceId,
            settings = settings
        )
        return json.encodeToString(ConfigExport.serializer(), export)
    }

    /** Full backup import. Also accepts share-format files (converts to DataExport). */
    fun importData(jsonString: String): DataExport? {
        val direct = try {
            val export = json.decodeFromString(DataExport.serializer(), jsonString)
            when (export.schemaVersion) {
                AppVersion.SCHEMA_VERSION -> export
                1 -> migrateV1DataToV2(export)
                else -> export
            }
        } catch (_: Exception) { null }
        if (direct != null) return direct

        return try {
            val share = json.decodeFromString(ShareExport.serializer(), jsonString)
            DataExport(
                exportType = "backup",
                exportDate = share.exportDate,
                deviceId = share.deviceId,
                knowledgeBases = share.knowledgeBases,
                decks = share.decks,
                cards = share.cards
            )
        } catch (_: Exception) { null }

        return try {
            val inc = json.decodeFromString(IncrementalExport.serializer(), jsonString)
            DataExport(
                exportType = "backup",
                exportDate = inc.exportDate,
                deviceId = inc.deviceId,
                knowledgeBases = inc.knowledgeBases,
                decks = inc.decks,
                cards = inc.cards,
                reviewLogs = inc.reviewLogs,
                learningPlans = inc.learningPlans
            )
        } catch (_: Exception) { null }
    }

    fun importConfig(jsonString: String): ConfigExport? {
        return try {
            json.decodeFromString(ConfigExport.serializer(), jsonString)
        } catch (e: Exception) {
            null
        }
    }

    fun importLegacy(jsonString: String): Pair<DataExport?, ConfigExport?> {
        return try {
            val export = json.decodeFromString(DataExport.serializer(), jsonString)
            val data = export.copy(schemaVersion = AppVersion.SCHEMA_VERSION)
            val config = ConfigExport(
                exportDate = data.exportDate,
                deviceId = data.deviceId,
                settings = emptyMap()
            )
            Pair(data, config)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    private fun migrateV1DataToV2(v1Export: DataExport): DataExport {
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
            id = "default", name = "Default", description = null,
            createdAt = v1Export.exportDate, updatedAt = v1Export.exportDate, version = 1
        )
        val kbs = v1Export.knowledgeBases.ifEmpty { listOf(defaultKb) }
        return v1Export.copy(
            schemaVersion = AppVersion.SCHEMA_VERSION,
            version = AppVersion.EXPORT_VERSION,
            knowledgeBases = kbs, decks = migratedDecks, cards = migratedCards
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
            if (parts.size >= 2) Pair(parts[0].trim(), parts[1].trim()) else null
        }
    }
}
