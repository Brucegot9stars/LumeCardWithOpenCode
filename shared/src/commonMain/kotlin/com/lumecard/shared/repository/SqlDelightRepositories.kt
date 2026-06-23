package com.lumecard.shared.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.model.ReviewLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private fun safeInstant(value: String): Instant = try { Instant.parse(value) } catch (_: Exception) { Clock.System.now() }
private fun safeInstantOrNull(value: String?): Instant? = value?.let { try { Instant.parse(it) } catch (_: Exception) { null } }
private fun safeCardType(value: String): CardType = try { CardType.valueOf(value) } catch (_: Exception) { CardType.BASIC }

class SqlDelightKnowledgeBaseRepository(
    private val database: LumeCardDatabase
) : KnowledgeBaseRepository {

    private val queries get() = database.lumeCardDatabaseQueries

    override fun getAll(): Flow<List<KnowledgeBase>> {
        return queries.selectAllKnowledgeBases().asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toKnowledgeBase() }
        }
    }

    override suspend fun getById(id: String): KnowledgeBase? {
        return queries.selectKnowledgeBaseById(id).executeAsOneOrNull()?.toKnowledgeBase()
    }

    override suspend fun insert(knowledgeBase: KnowledgeBase) {
        queries.insertKnowledgeBase(
            id = knowledgeBase.id,
            name = knowledgeBase.name,
            description = knowledgeBase.description,
            created_at = knowledgeBase.createdAt.toString(),
            updated_at = knowledgeBase.updatedAt.toString(),
            version = knowledgeBase.version,
            deleted_at = knowledgeBase.deletedAt?.toString(),
            synced_at = knowledgeBase.syncedAt?.toString()
        )
    }

    override suspend fun update(knowledgeBase: KnowledgeBase) {
        queries.updateKnowledgeBase(
            name = knowledgeBase.name,
            description = knowledgeBase.description,
            updated_at = Clock.System.now().toString(),
            deleted_at = knowledgeBase.deletedAt?.toString(),
            synced_at = knowledgeBase.syncedAt?.toString(),
            id = knowledgeBase.id
        )
    }

    override suspend fun delete(id: String) {
        val now = Clock.System.now().toString()
        queries.softDeleteCardsByKnowledgeBase(now, now, id)
        queries.softDeleteDecksByKnowledgeBase(now, now, id)
        queries.softDeleteKnowledgeBase(now, now, id)
    }

    override suspend fun getUpdatedSince(since: Instant): List<KnowledgeBase> {
        return queries.selectKnowledgeBasesUpdatedSince(since.toString()).executeAsList().map { it.toKnowledgeBase() }
    }

    override suspend fun markSynced(ids: List<String>, syncedAt: Instant) {
        val ts = syncedAt.toString()
        ids.forEach { queries.updateKnowledgeBaseSyncedAt(ts, it) }
    }
}

class SqlDelightDeckRepository(
    private val database: LumeCardDatabase
) : DeckRepository {

    private val queries get() = database.lumeCardDatabaseQueries

    override fun getAll(): Flow<List<Deck>> {
        return queries.selectAllDecks().asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toDeck() }
        }
    }

    override fun getByKnowledgeBase(knowledgeBaseId: String): Flow<List<Deck>> {
        return queries.selectDecksByKnowledgeBase(knowledgeBaseId).asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toDeck() }
        }
    }

    override suspend fun getById(id: String): Deck? {
        return queries.selectDeckById(id).executeAsOneOrNull()?.toDeck()
    }

    override suspend fun insert(deck: Deck) {
        queries.insertDeck(
            id = deck.id,
            knowledge_base_id = deck.knowledgeBaseId,
            name = deck.name,
            description = deck.description,
            color = deck.color,
            icon = deck.icon,
            parent_id = deck.parentId,
            created_at = deck.createdAt.toString(),
            updated_at = deck.updatedAt.toString(),
            version = deck.version,
            deleted_at = deck.deletedAt?.toString(),
            synced_at = deck.syncedAt?.toString()
        )
    }

    override suspend fun update(deck: Deck) {
        queries.updateDeck(
            name = deck.name,
            description = deck.description,
            color = deck.color,
            icon = deck.icon,
            parent_id = deck.parentId,
            updated_at = Clock.System.now().toString(),
            deleted_at = deck.deletedAt?.toString(),
            synced_at = deck.syncedAt?.toString(),
            id = deck.id
        )
    }

    override suspend fun delete(id: String) {
        val now = Clock.System.now().toString()
        queries.softDeleteCardsByDeck(now, now, id)
        queries.softDeleteDeck(now, now, id)
    }

    override suspend fun getUpdatedSince(since: Instant): List<Deck> {
        return queries.selectDecksUpdatedSince(since.toString()).executeAsList().map { it.toDeck() }
    }

    override suspend fun markSynced(ids: List<String>, syncedAt: Instant) {
        val ts = syncedAt.toString()
        ids.forEach { queries.updateDeckSyncedAt(ts, it) }
    }
}

class SqlDelightCardRepository(
    private val database: LumeCardDatabase
) : CardRepository {

    private val queries get() = database.lumeCardDatabaseQueries

    override fun getAll(): Flow<List<Card>> {
        return queries.selectAllCards().asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getByDeck(deckId: String): Flow<List<Card>> {
        return queries.selectCardsByDeck(deckId).asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getById(id: String): Card? {
        return queries.selectCardById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getDueCards(): Flow<List<Card>> {
        val now = Clock.System.now().toString()
        return queries.selectDueCards(now).asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun insert(card: Card) {
        queries.insertCard(
            id = card.id,
            deck_id = card.deckId,
            type = card.type.name,
            front = card.front,
            back = card.back,
            tags = Json.encodeToString(card.tags),
            media = Json.encodeToString(card.media),
            metadata = Json.encodeToString(card.metadata),
            created_at = card.createdAt.toString(),
            updated_at = card.updatedAt.toString(),
            last_reviewed_at = card.lastReviewedAt?.toString(),
            next_review_at = card.nextReviewAt?.toString(),
            version = card.version,
            deleted_at = card.deletedAt?.toString(),
            synced_at = card.syncedAt?.toString()
        )
        queries.insertCardFts(card.id, card.front, card.back, card.tags.joinToString(" "))
    }

    override suspend fun update(card: Card) {
        queries.updateCard(
            deck_id = card.deckId,
            type = card.type.name,
            front = card.front,
            back = card.back,
            tags = Json.encodeToString(card.tags),
            media = Json.encodeToString(card.media),
            metadata = Json.encodeToString(card.metadata),
            updated_at = Clock.System.now().toString(),
            last_reviewed_at = card.lastReviewedAt?.toString(),
            next_review_at = card.nextReviewAt?.toString(),
            id = card.id
        )
        queries.deleteCardFts(card.id)
        queries.insertCardFts(card.id, card.front, card.back, card.tags.joinToString(" "))
    }

    override suspend fun delete(id: String) {
        val now = Clock.System.now().toString()
        queries.softDeleteCard(now, now, id)
        queries.deleteCardFts(id)
    }

    override suspend fun search(query: String): Flow<List<Card>> {
        val likeQuery = "%${query.lowercase()}%"
        return queries.searchCards(likeQuery, likeQuery, likeQuery).asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getUpdatedSince(since: Instant): List<Card> {
        return queries.selectCardsUpdatedSince(since.toString()).executeAsList().map { it.toDomain() }
    }

    override suspend fun markSynced(ids: List<String>, syncedAt: Instant) {
        val ts = syncedAt.toString()
        ids.forEach { queries.updateCardSyncedAt(ts, it) }
    }

    override suspend fun rebuildFtsIndex() {
        queries.deleteAllCardFts()
        queries.rebuildCardFts()
    }
}

class SqlDelightReviewLogRepository(
    private val database: LumeCardDatabase
) : ReviewLogRepository {

    private val queries get() = database.lumeCardDatabaseQueries

    override fun getAll(): Flow<List<ReviewLog>> {
        return queries.selectAllReviewLogs().asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toReviewLog() }
        }
    }

    override fun getByCardId(cardId: String): Flow<List<ReviewLog>> {
        return queries.selectReviewLogsByCardId(cardId).asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toReviewLog() }
        }
    }

    override suspend fun insert(reviewLog: ReviewLog) {
        queries.insertReviewLog(
            id = reviewLog.id,
            card_id = reviewLog.cardId,
            rating = reviewLog.rating.toLong(),
            review_time = reviewLog.reviewTime.toLong(),
            interval = reviewLog.interval.toLong(),
            ease_factor = reviewLog.easeFactor.toDouble(),
            repetitions = reviewLog.repetitions.toLong(),
            lapse_count = reviewLog.lapseCount.toLong(),
            reviewed_at = reviewLog.reviewedAt.toString(),
            version = reviewLog.version,
            deleted_at = reviewLog.deletedAt?.toString(),
            synced_at = reviewLog.syncedAt?.toString()
        )
    }

    override suspend fun getStats(): ReviewStats {
        val allLogs = queries.selectAllReviewLogs().executeAsList()
        val totalReviews = allLogs.size
        val averageRating = if (totalReviews > 0) allLogs.map { it.rating }.average() else 0.0
        val goodReviews = allLogs.count { it.rating >= 3 }
        val retentionRate = if (totalReviews > 0) goodReviews.toDouble() / totalReviews else 0.0
        val studyTimeMinutes = (allLogs.sumOf { it.review_time } / 60000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        return ReviewStats(
            totalReviews = totalReviews,
            averageRating = averageRating,
            retentionRate = retentionRate,
            studyTimeMinutes = studyTimeMinutes
        )
    }

    override suspend fun getUpdatedSince(since: Instant): List<ReviewLog> {
        return queries.selectReviewLogsUpdatedSince(since.toString()).executeAsList().map { it.toReviewLog() }
    }

    override suspend fun markSynced(ids: List<String>, syncedAt: Instant) {
        val ts = syncedAt.toString()
        ids.forEach { queries.updateReviewLogSyncedAt(ts, it) }
    }
}

class SqlDelightSettingsRepository(
    private val database: LumeCardDatabase
) : SettingsRepository {

    private val queries get() = database.lumeCardDatabaseQueries

    override suspend fun get(key: String): String? {
        return queries.selectSetting(key).executeAsOneOrNull()?.setting_value
    }

    override suspend fun getAll(): Map<String, String> {
        return queries.selectAllSettings().executeAsList().associate { it.key to it.setting_value }
    }

    override suspend fun set(key: String, value: String) {
        queries.insertSetting(key, value)
    }

    override suspend fun delete(key: String) {
        queries.deleteSetting(key)
    }

    override suspend fun getInt(key: String, default: Int): Int {
        return get(key)?.toIntOrNull() ?: default
    }

    override suspend fun getBoolean(key: String, default: Boolean): Boolean {
        return get(key)?.toBooleanStrictOrNull() ?: default
    }
}

class SqlDelightAlgorithmStateRepository(
    private val database: LumeCardDatabase
) : AlgorithmStateRepository {

    private val queries get() = database.lumeCardDatabaseQueries

    override suspend fun get(cardId: String): String? {
        return queries.selectAlgorithmState(cardId).executeAsOneOrNull()?.state_json
    }

    override suspend fun getAll(): Map<String, String> {
        return queries.selectAllAlgorithmStates().executeAsList().associate { it.card_id to it.state_json }
    }

    override suspend fun save(cardId: String, mode: String, stateJson: String) {
        queries.insertAlgorithmState(cardId, mode, stateJson)
    }

    override suspend fun delete(cardId: String) {
        queries.deleteAlgorithmState(cardId)
    }
}

private fun com.lumecard.shared.database.KnowledgeBase.toKnowledgeBase() = KnowledgeBase(
    id = id,
    name = name,
    description = description,
    createdAt = safeInstant(created_at),
    updatedAt = safeInstant(updated_at),
    version = version,
    deletedAt = safeInstantOrNull(deleted_at),
    syncedAt = safeInstantOrNull(synced_at)
)

private fun com.lumecard.shared.database.Deck.toDeck() = Deck(
    id = id,
    knowledgeBaseId = knowledge_base_id,
    name = name,
    description = description,
    color = color ?: "#4CAF50",
    icon = icon ?: "\uD83D\uDCDA",
    parentId = parent_id,
    createdAt = safeInstant(created_at),
    updatedAt = safeInstant(updated_at),
    version = version,
    deletedAt = safeInstantOrNull(deleted_at),
    syncedAt = safeInstantOrNull(synced_at)
)

private fun com.lumecard.shared.database.SelectDueCards.toDomain() = Card(
    id = id,
    deckId = deck_id,
    type = safeCardType(type),
    front = front,
    back = back,
    tags = parseStringList(tags),
    createdAt = safeInstant(created_at),
    updatedAt = safeInstant(updated_at),
    lastReviewedAt = safeInstantOrNull(last_reviewed_at),
    nextReviewAt = safeInstant(next_review_at)
)

private fun com.lumecard.shared.database.Card.toDomain() = Card(
    id = id,
    deckId = deck_id,
    type = safeCardType(type),
    front = front,
    back = back,
    tags = parseStringList(tags),
    media = try { if (media.isNullOrBlank()) emptyList() else Json.decodeFromString(media) } catch (_: Exception) { emptyList() },
    metadata = try { if (metadata.isNullOrBlank()) emptyMap() else Json.decodeFromString(metadata) } catch (_: Exception) { emptyMap() },
    createdAt = safeInstant(created_at),
    updatedAt = safeInstant(updated_at),
    lastReviewedAt = safeInstantOrNull(last_reviewed_at),
    nextReviewAt = safeInstantOrNull(next_review_at),
    version = version,
    deletedAt = safeInstantOrNull(deleted_at),
    syncedAt = safeInstantOrNull(synced_at)
)

private fun com.lumecard.shared.database.ReviewLog.toReviewLog() = ReviewLog(
    id = id,
    cardId = card_id,
    rating = rating.toInt(),
    reviewTime = review_time.toInt(),
    interval = interval.toInt(),
    easeFactor = ease_factor.toFloat(),
    repetitions = repetitions.toInt(),
    lapseCount = lapse_count.toInt(),
    reviewedAt = safeInstant(reviewed_at),
    version = version,
    deletedAt = safeInstantOrNull(deleted_at),
    syncedAt = safeInstantOrNull(synced_at)
)

class SqlDelightMediaCacheRepository(
    private val database: LumeCardDatabase
) : MediaCacheRepository {

    private val queries get() = database.lumeCardDatabaseQueries

    override suspend fun get(path: String): MediaCacheEntry? {
        return queries.selectMediaCache(path).executeAsOneOrNull()?.toMediaCacheEntry()
    }

    override suspend fun getAll(): List<MediaCacheEntry> {
        return queries.selectAllMediaCache().executeAsList().map { it.toMediaCacheEntry() }
    }

    override suspend fun set(path: String, mtime: Long, sha1: String, syncedAt: Instant?) {
        queries.insertMediaCache(path, mtime, sha1, syncedAt?.toString())
    }

    override suspend fun remove(path: String) {
        queries.deleteMediaCache(path)
    }

    override suspend fun removeAll() {
        queries.deleteAllMediaCache()
    }
}

class SqlDelightLearningPlanRepository(
    private val database: LumeCardDatabase
) : LearningPlanRepository {

    private val queries get() = database.lumeCardDatabaseQueries

    override fun getAll(): Flow<List<com.lumecard.shared.model.LearningPlan>> {
        return queries.selectAllLearningPlans().asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toLearningPlan() }
        }
    }

    override suspend fun getById(id: String): com.lumecard.shared.model.LearningPlan? {
        return queries.selectLearningPlanById(id).executeAsOneOrNull()?.toLearningPlan()
    }

    override suspend fun getDefault(): com.lumecard.shared.model.LearningPlan? {
        return queries.selectDefaultLearningPlan().executeAsOneOrNull()?.toLearningPlan()
    }

    override suspend fun insert(plan: com.lumecard.shared.model.LearningPlan) {
        queries.insertLearningPlan(
            id = plan.id,
            name = plan.name,
            description = plan.description,
            status = plan.status.name,
            is_default = if (plan.isDefault) 1L else 0L,
            knowledge_base_ids = Json.encodeToString(plan.knowledgeBaseIds),
            deck_ids = Json.encodeToString(plan.deckIds),
            card_ids = Json.encodeToString(plan.cardIds),
            total_cards = plan.totalCards.toLong(),
            completed_cards = plan.completedCards.toLong(),
            created_at = plan.createdAt.toString(),
            updated_at = plan.updatedAt.toString(),
            version = plan.version,
            deleted_at = plan.deletedAt?.toString(),
            synced_at = plan.syncedAt?.toString()
        )
    }

    override suspend fun update(plan: com.lumecard.shared.model.LearningPlan) {
        queries.updateLearningPlan(
            name = plan.name,
            description = plan.description,
            status = plan.status.name,
            is_default = if (plan.isDefault) 1L else 0L,
            knowledge_base_ids = Json.encodeToString(plan.knowledgeBaseIds),
            deck_ids = Json.encodeToString(plan.deckIds),
            card_ids = Json.encodeToString(plan.cardIds),
            total_cards = plan.totalCards.toLong(),
            completed_cards = plan.completedCards.toLong(),
            updated_at = Clock.System.now().toString(),
            deleted_at = plan.deletedAt?.toString(),
            synced_at = plan.syncedAt?.toString(),
            id = plan.id
        )
    }

    override suspend fun delete(id: String) {
        val now = Clock.System.now().toString()
        queries.softDeleteLearningPlan(now, now, id)
    }

    override suspend fun getUpdatedSince(since: Instant): List<com.lumecard.shared.model.LearningPlan> {
        return queries.selectLearningPlansUpdatedSince(since.toString()).executeAsList().map { it.toLearningPlan() }
    }

    override suspend fun markSynced(ids: List<String>, syncedAt: Instant) {
        val ts = syncedAt.toString()
        ids.forEach { queries.updateLearningPlanSyncedAt(ts, it) }
    }
}

private fun com.lumecard.shared.database.LearningPlan.toLearningPlan() = com.lumecard.shared.model.LearningPlan(
    id = id,
    name = name,
    description = description,
    status = try { com.lumecard.shared.model.PlanStatus.valueOf(status) } catch (_: Exception) { com.lumecard.shared.model.PlanStatus.NOT_STARTED },
    isDefault = is_default == 1L,
    knowledgeBaseIds = parseStringList(knowledge_base_ids),
    deckIds = parseStringList(deck_ids),
    cardIds = parseStringList(card_ids),
    totalCards = total_cards.toInt(),
    completedCards = completed_cards.toInt(),
    createdAt = safeInstant(created_at),
    updatedAt = safeInstant(updated_at),
    version = version,
    deletedAt = safeInstantOrNull(deleted_at),
    syncedAt = safeInstantOrNull(synced_at)
)

private fun com.lumecard.shared.database.MediaCache.toMediaCacheEntry() = MediaCacheEntry(
    path = path,
    mtime = mtime,
    sha1 = sha1,
    syncedAt = synced_at?.let { Instant.parse(it) }
)

private fun parseStringList(value: String?): List<String> {
    if (value.isNullOrBlank()) return emptyList()
    return try {
        Json.decodeFromString(value)
    } catch (_: Exception) {
        value.split(",").filter { it.isNotBlank() }
    }
}
