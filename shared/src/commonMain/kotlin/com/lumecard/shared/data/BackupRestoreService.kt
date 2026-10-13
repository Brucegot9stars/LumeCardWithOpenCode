package com.lumecard.shared.data

import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.AppVersion
import com.lumecard.shared.domain.scheduler.AlgorithmStateCodec
import com.lumecard.shared.domain.scheduler.ReviewMode
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.PlanStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

/** Synchronous database work; callers run it on their platform's IO dispatcher. */
class BackupRestoreService(private val database: LumeCardDatabase) {
    private val queries get() = database.lumeCardDatabaseQueries
    private val json = Json { ignoreUnknownKeys = true }

    fun replaceSettings(settings: Map<String, String>) {
        database.transaction(noEnclosing = true) {
            queries.selectAllSettings().executeAsList()
                .filterNot { PlanProgressKeys.isPlanMetadata(it.key) }
                .forEach { queries.deleteSetting(it.key) }
            settings.filterKeys { !PlanProgressKeys.isPlanMetadata(it) }
                .forEach { (key, value) -> queries.insertSetting(key, value) }
        }
    }

    /** One consistent snapshot, including tombstones and scheduling state. */
    fun snapshot(): DataExport = database.transactionWithResult {
        val settings = queries.selectAllSettings().executeAsList().associate { it.key to it.setting_value }
        DataExport(
            exportDate = Clock.System.now().toString(),
            backupFormatVersion = 2,
            knowledgeBases = queries.selectBackupKnowledgeBases().executeAsList().map {
                ExportKnowledgeBase(it.id, it.name, it.description, it.icon ?: "📁", it.created_at, it.updated_at,
                    it.version, it.deleted_at, it.synced_at)
            },
            decks = queries.selectBackupDecks().executeAsList().map {
                ExportDeck(it.id, it.knowledge_base_id, it.name, it.description, it.color ?: "#4CAF50",
                    it.icon ?: "📚", it.parent_id, it.created_at, it.updated_at, it.version, it.deleted_at, it.synced_at)
            },
            cards = queries.selectBackupCards().executeAsList().map {
                ExportCard(
                    id = it.id, deckId = it.deck_id, type = it.type, title = it.title.orEmpty(),
                    front = it.front, back = it.back, tags = json.decodeFromString(it.tags ?: "[]"),
                    media = json.decodeFromString(it.media ?: "[]"), metadata = json.decodeFromString(it.metadata ?: "{}"),
                    createdAt = it.created_at, updatedAt = it.updated_at, lastReviewedAt = it.last_reviewed_at,
                    nextReviewAt = it.next_review_at, version = it.version, deletedAt = it.deleted_at, syncedAt = it.synced_at,
                )
            },
            reviewLogs = queries.selectBackupReviewLogs().executeAsList().map {
                ExportReviewLog(it.id, it.card_id, it.rating.exportInt(), it.review_time.exportInt(), it.interval.exportInt(),
                    it.ease_factor.toFloat(), it.repetitions.exportInt(), it.lapse_count.exportInt(), it.reviewed_at,
                    it.version, it.deleted_at, it.synced_at)
            },
            learningPlans = queries.selectBackupLearningPlans().executeAsList().map {
                ExportLearningPlan(
                    id = it.id, name = it.name, description = it.description, status = it.status,
                    isDefault = it.is_default == 1L, knowledgeBaseIds = json.decodeFromString(it.knowledge_base_ids ?: "[]"),
                    deckIds = json.decodeFromString(it.deck_ids ?: "[]"), cardIds = json.decodeFromString(it.card_ids ?: "[]"),
                    totalCards = it.total_cards.exportInt(), completedCards = it.completed_cards.exportInt(),
                    createdAt = it.created_at, updatedAt = it.updated_at, version = it.version,
                    deletedAt = it.deleted_at, syncedAt = it.synced_at,
                    progressSince = settings[PlanProgressKeys.forPlan(it.id)],
                    manuallyCompleted = settings[PlanProgressKeys.manualComplete(it.id)] == "true",
                )
            },
            algorithmStates = queries.selectAllAlgorithmStates().executeAsList().map {
                ExportAlgorithmState(it.card_id, it.mode, it.state_json)
            },
            splashQuotes = ExportSplashQuotes(
                version = settings[SplashQuoteManager.KEY_QUOTES_VERSION]?.toLongOrNull() ?: 0L,
                userQuotes = json.decodeFromString(settings[SplashQuoteManager.KEY_USER_QUOTES] ?: "[]"),
                hiddenDefaultIndices = settings[SplashQuoteManager.KEY_HIDDEN_DEFAULTS].orEmpty(),
            ),
        )
    }

    /** Validate before deleting, then replace all business data in one transaction. */
    fun replace(data: DataExport, syncedAt: Instant? = null) {
        require(data.exportType == "backup") { "Only a full backup can replace local data" }
        require(data.backupFormatVersion in 1..2) { "Unsupported backup format" }
        require(data.backupFormatVersion == 1 || data.algorithmStates != null) { "Missing scheduling state" }
        require(data.schemaVersion == AppVersion.SCHEMA_VERSION) { "Unsupported data schema" }
        val decks = validateAndOrderDecks(data)
        val syncTime = syncedAt?.toString()
        database.transaction(noEnclosing = true) {
            queries.selectAllSettings().executeAsList().filter { PlanProgressKeys.isPlanMetadata(it.key) }
                .forEach { queries.deleteSetting(it.key) }
            queries.deleteAllCardFts()
            queries.deleteAllAlgorithmStates()
            queries.deleteAllReviewLogs()
            queries.hardDeleteAllCards()
            queries.hardDeleteAllDecks()
            queries.hardDeleteAllKnowledgeBases()
            queries.hardDeleteAllLearningPlans()
            writeRecords(data, decks, syncTime, merge = false)
        }
    }

    /** Preserve local-only records while restoring content, logs and state atomically. */
    fun merge(data: DataExport) {
        require(data.exportType == "backup") { "Only a full backup can restore learning state" }
        require(data.backupFormatVersion in 1..2 && (data.backupFormatVersion == 1 || data.algorithmStates != null))
        require(data.schemaVersion == AppVersion.SCHEMA_VERSION) { "Unsupported data schema" }
        val decks = validateAndOrderDecks(data)
        database.transaction(noEnclosing = true) { writeRecords(data, decks, null, merge = true) }
    }

    private fun writeRecords(data: DataExport, decks: List<ExportDeck>, syncTime: String?, merge: Boolean) {
        val saveKb = if (merge) queries::upsertBackupKnowledgeBase else queries::insertKnowledgeBase
        val saveDeck = if (merge) queries::upsertBackupDeck else queries::insertDeck
        val saveCard = if (merge) queries::upsertBackupCard else queries::insertCard
        val saveLog = if (merge) queries::insertBackupReviewLogIfAbsent else queries::insertReviewLog
        val savePlan = if (merge) queries::upsertBackupLearningPlan else queries::insertLearningPlan
        data.knowledgeBases.forEach {
            saveKb(it.id, it.name, it.description, it.icon, it.createdAt, it.updatedAt,
                it.version, it.deletedAt, syncTime ?: it.syncedAt)
        }
        decks.forEach {
            saveDeck(it.id, it.knowledgeBaseId, it.name, it.description, it.color, it.icon, it.parentId,
                it.createdAt, it.updatedAt, it.version, it.deletedAt, syncTime ?: it.syncedAt)
        }
        data.cards.forEach {
            saveCard(it.id, it.deckId, it.type, it.title, it.front, it.back, json.encodeToString(it.tags),
                json.encodeToString(it.media), json.encodeToString(it.metadata), it.createdAt, it.updatedAt,
                it.lastReviewedAt, it.nextReviewAt, it.version, it.deletedAt, syncTime ?: it.syncedAt)
            queries.deleteCardFts(it.id)
            if (it.deletedAt == null) queries.insertCardFts(it.id, it.front, it.back, it.tags.joinToString(" "))
        }
        data.reviewLogs.forEach {
            saveLog(it.id, it.cardId, it.rating.toLong(), it.reviewTime.toLong(), it.interval.toLong(),
                it.easeFactor.toDouble(), it.repetitions.toLong(), it.lapseCount.toLong(), it.reviewedAt,
                it.version, it.deletedAt, syncTime ?: it.syncedAt)
        }
        data.learningPlans.forEach {
            savePlan(it.id, it.name, it.description, it.status, if (it.isDefault) 1L else 0L,
                json.encodeToString(it.knowledgeBaseIds), json.encodeToString(it.deckIds), json.encodeToString(it.cardIds),
                it.totalCards.toLong(), it.completedCards.toLong(), it.createdAt, it.updatedAt, it.version,
                it.deletedAt, syncTime ?: it.syncedAt)
            it.progressSince?.let { boundary ->
                val key = PlanProgressKeys.forPlan(it.id)
                val local = if (merge) queries.selectSetting(key).executeAsOneOrNull()?.setting_value else null
                val effective = if (local != null && Instant.parse(local) > Instant.parse(boundary)) local else boundary
                queries.insertSetting(key, effective)
            }
            if (it.manuallyCompleted) queries.insertSetting(PlanProgressKeys.manualComplete(it.id), "true")
        }
        data.algorithmStates.orEmpty().forEach { queries.insertAlgorithmState(it.cardId, it.mode, it.stateJson) }
        // Preserve the existing whole-collection "higher version wins" quote policy.
        data.splashQuotes?.let { quotes ->
            val currentVersion = queries.selectSetting(SplashQuoteManager.KEY_QUOTES_VERSION)
                .executeAsOneOrNull()?.setting_value?.toLongOrNull() ?: 0L
            if (quotes.version > currentVersion) {
                queries.insertSetting(SplashQuoteManager.KEY_USER_QUOTES, json.encodeToString(quotes.userQuotes))
                queries.insertSetting(SplashQuoteManager.KEY_HIDDEN_DEFAULTS, quotes.hiddenDefaultIndices)
                queries.insertSetting(SplashQuoteManager.KEY_QUOTES_VERSION, quotes.version.toString())
            }
        }
    }

    private fun Long.exportInt(): Int {
        require(this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Backup integer is out of range" }
        return toInt()
    }

    private fun validateAndOrderDecks(data: DataExport): List<ExportDeck> {
        fun uniqueIds(ids: List<String>): Set<String> {
            require(ids.all { it.isNotBlank() } && ids.toSet().size == ids.size) { "Blank or duplicate backup IDs" }
            return ids.toSet()
        }
        fun timestamps(vararg values: String?) = values.filterNotNull().forEach { Instant.parse(it) }
        timestamps(data.exportDate)
        val kbIds = uniqueIds(data.knowledgeBases.map { it.id })
        val deckIds = uniqueIds(data.decks.map { it.id })
        val cardIds = uniqueIds(data.cards.map { it.id })
        uniqueIds(data.reviewLogs.map { it.id })
        uniqueIds(data.learningPlans.map { it.id })
        uniqueIds(data.algorithmStates.orEmpty().map { it.cardId })
        data.knowledgeBases.forEach { timestamps(it.createdAt, it.updatedAt, it.deletedAt, it.syncedAt) }
        data.decks.forEach {
            require(it.knowledgeBaseId in kbIds) { "Deck ${it.id} references a missing knowledge base" }
            require(it.parentId == null || it.parentId in deckIds) { "Deck ${it.id} references a missing parent" }
            timestamps(it.createdAt, it.updatedAt, it.deletedAt, it.syncedAt)
        }
        data.cards.forEach {
            require(it.deckId in deckIds) { "Card ${it.id} references a missing deck" }
            CardType.valueOf(it.type)
            timestamps(it.createdAt, it.updatedAt, it.lastReviewedAt, it.nextReviewAt, it.deletedAt, it.syncedAt)
        }
        data.reviewLogs.forEach {
            require(it.cardId in cardIds) { "Review ${it.id} references a missing card" }
            require(it.rating in 1..4 && it.reviewTime >= 0 && it.interval >= 0 && it.repetitions >= 0 && it.lapseCount >= 0)
            require(it.easeFactor.isFinite() && it.easeFactor > 0f)
            timestamps(it.reviewedAt, it.deletedAt, it.syncedAt)
        }
        data.learningPlans.forEach {
            PlanStatus.valueOf(it.status)
            require(it.totalCards >= 0 && it.completedCards >= 0)
            timestamps(it.createdAt, it.updatedAt, it.deletedAt, it.syncedAt, it.progressSince)
        }
        data.algorithmStates.orEmpty().forEach {
            require(it.cardId in cardIds) { "Scheduling state references a missing card" }
            ReviewMode.valueOf(it.mode)
            val state = AlgorithmStateCodec.decode(it.stateJson)
            require(state.stability.isFinite() && state.difficulty.isFinite() && state.easeFactor.isFinite())
            require(state.intervalDays >= 0 && state.repetitions >= 0 && state.lapses >= 0 && state.stage >= 0)
        }
        // Iterative topological ordering also rejects cycles without recursive stack growth.
        val children = data.decks.filter { it.parentId != null }.groupBy { it.parentId }
        val pending = ArrayDeque(data.decks.filter { it.parentId == null })
        val ordered = mutableListOf<ExportDeck>()
        while (pending.isNotEmpty()) {
            val deck = pending.removeFirst()
            ordered.add(deck)
            children[deck.id].orEmpty().forEach { pending.addLast(it) }
        }
        require(ordered.size == data.decks.size) { "Backup contains a deck parent cycle" }
        return ordered
    }
}
