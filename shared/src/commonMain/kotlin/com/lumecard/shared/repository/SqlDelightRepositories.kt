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
        queries.insertKnowledgeBase(
            id = knowledgeBase.id,
            name = knowledgeBase.name,
            description = knowledgeBase.description,
            created_at = knowledgeBase.createdAt.toString(),
            updated_at = Clock.System.now().toString(),
            version = knowledgeBase.version + 1,
            deleted_at = knowledgeBase.deletedAt?.toString(),
            synced_at = knowledgeBase.syncedAt?.toString()
        )
    }

    override suspend fun delete(id: String) {
        val kb = queries.selectKnowledgeBaseById(id).executeAsOneOrNull()
        if (kb != null) {
            queries.insertKnowledgeBase(
                id = kb.id,
                name = kb.name,
                description = kb.description,
                created_at = kb.created_at,
                updated_at = Clock.System.now().toString(),
                version = kb.version + 1,
                deleted_at = Clock.System.now().toString(),
                synced_at = null
            )
        }
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
        queries.insertDeck(
            id = deck.id,
            knowledge_base_id = deck.knowledgeBaseId,
            name = deck.name,
            description = deck.description,
            color = deck.color,
            icon = deck.icon,
            parent_id = deck.parentId,
            created_at = deck.createdAt.toString(),
            updated_at = Clock.System.now().toString(),
            version = deck.version + 1,
            deleted_at = deck.deletedAt?.toString(),
            synced_at = deck.syncedAt?.toString()
        )
    }

    override suspend fun delete(id: String) {
        val deck = queries.selectDeckById(id).executeAsOneOrNull()
        if (deck != null) {
            queries.insertDeck(
                id = deck.id,
                knowledge_base_id = deck.knowledge_base_id,
                name = deck.name,
                description = deck.description,
                color = deck.color,
                icon = deck.icon,
                parent_id = deck.parent_id,
                created_at = deck.created_at,
                updated_at = Clock.System.now().toString(),
                version = deck.version + 1,
                deleted_at = Clock.System.now().toString(),
                synced_at = null
            )
        }
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
            tags = card.tags.joinToString(","),
            media = "",
            metadata = "",
            created_at = card.createdAt.toString(),
            updated_at = card.updatedAt.toString(),
            last_reviewed_at = card.lastReviewedAt?.toString(),
            next_review_at = card.nextReviewAt?.toString(),
            version = card.version,
            deleted_at = card.deletedAt?.toString(),
            synced_at = card.syncedAt?.toString()
        )
    }

    override suspend fun update(card: Card) {
        queries.updateCard(
            deck_id = card.deckId,
            type = card.type.name,
            front = card.front,
            back = card.back,
            tags = card.tags.joinToString(","),
            media = "",
            metadata = "",
            updated_at = Clock.System.now().toString(),
            last_reviewed_at = card.lastReviewedAt?.toString(),
            next_review_at = card.nextReviewAt?.toString(),
            id = card.id
        )
    }

    override suspend fun delete(id: String) {
        val card = queries.selectCardById(id).executeAsOneOrNull()
        if (card != null) {
            queries.insertCard(
                id = card.id,
                deck_id = card.deck_id,
                type = card.type,
                front = card.front,
                back = card.back,
                tags = card.tags,
                media = card.media,
                metadata = card.metadata,
                created_at = card.created_at,
                updated_at = Clock.System.now().toString(),
                last_reviewed_at = card.last_reviewed_at,
                next_review_at = card.next_review_at,
                version = card.version + 1,
                deleted_at = Clock.System.now().toString(),
                synced_at = null
            )
        }
    }

    override suspend fun search(query: String): Flow<List<Card>> {
        return queries.selectAllCards().asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { it.toDomain() }.filter { card ->
                card.front.contains(query, ignoreCase = true) ||
                card.back.contains(query, ignoreCase = true) ||
                card.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            }
        }
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
        val studyTimeMinutes = allLogs.sumOf { it.review_time } / 60000

        return ReviewStats(
            totalReviews = totalReviews,
            averageRating = averageRating,
            retentionRate = retentionRate,
            studyTimeMinutes = studyTimeMinutes.toInt()
        )
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
    createdAt = Instant.parse(created_at),
    updatedAt = Instant.parse(updated_at),
    version = version,
    deletedAt = deleted_at?.let { Instant.parse(it) },
    syncedAt = synced_at?.let { Instant.parse(it) }
)

private fun com.lumecard.shared.database.Deck.toDeck() = Deck(
    id = id,
    knowledgeBaseId = knowledge_base_id,
    name = name,
    description = description,
    color = color ?: "#4CAF50",
    icon = icon ?: "\uD83D\uDCDA",
    parentId = parent_id,
    createdAt = Instant.parse(created_at),
    updatedAt = Instant.parse(updated_at),
    version = version,
    deletedAt = deleted_at?.let { Instant.parse(it) },
    syncedAt = synced_at?.let { Instant.parse(it) }
)

private fun com.lumecard.shared.database.SelectDueCards.toDomain() = Card(
    id = id,
    deckId = deck_id,
    type = CardType.valueOf(type),
    front = front,
    back = back,
    tags = (tags ?: "").split(",").filter { it.isNotBlank() },
    createdAt = Instant.parse(created_at),
    updatedAt = Instant.parse(updated_at),
    lastReviewedAt = last_reviewed_at?.let { Instant.parse(it) },
    nextReviewAt = Instant.parse(next_review_at)
)

private fun com.lumecard.shared.database.Card.toDomain() = Card(
    id = id,
    deckId = deck_id,
    type = CardType.valueOf(type),
    front = front,
    back = back,
    tags = (tags ?: "").split(",").filter { it.isNotBlank() },
    createdAt = Instant.parse(created_at),
    updatedAt = Instant.parse(updated_at),
    lastReviewedAt = last_reviewed_at?.let { Instant.parse(it) },
    nextReviewAt = next_review_at?.let { Instant.parse(it) },
    version = version,
    deletedAt = deleted_at?.let { Instant.parse(it) },
    syncedAt = synced_at?.let { Instant.parse(it) }
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
    reviewedAt = Instant.parse(reviewed_at),
    version = version,
    deletedAt = deleted_at?.let { Instant.parse(it) },
    syncedAt = synced_at?.let { Instant.parse(it) }
)

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
            knowledge_base_ids = plan.knowledgeBaseIds.joinToString(","),
            deck_ids = plan.deckIds.joinToString(","),
            card_ids = plan.cardIds.joinToString(","),
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
        queries.insertLearningPlan(
            id = plan.id,
            name = plan.name,
            description = plan.description,
            status = plan.status.name,
            is_default = if (plan.isDefault) 1L else 0L,
            knowledge_base_ids = plan.knowledgeBaseIds.joinToString(","),
            deck_ids = plan.deckIds.joinToString(","),
            card_ids = plan.cardIds.joinToString(","),
            total_cards = plan.totalCards.toLong(),
            completed_cards = plan.completedCards.toLong(),
            created_at = plan.createdAt.toString(),
            updated_at = Clock.System.now().toString(),
            version = plan.version + 1,
            deleted_at = plan.deletedAt?.toString(),
            synced_at = plan.syncedAt?.toString()
        )
    }

    override suspend fun delete(id: String) {
        val plan = queries.selectLearningPlanById(id).executeAsOneOrNull()
        if (plan != null) {
            queries.insertLearningPlan(
                id = plan.id,
                name = plan.name,
                description = plan.description,
                status = plan.status,
                is_default = plan.is_default,
                knowledge_base_ids = plan.knowledge_base_ids,
                deck_ids = plan.deck_ids,
                card_ids = plan.card_ids,
                total_cards = plan.total_cards,
                completed_cards = plan.completed_cards,
                created_at = plan.created_at,
                updated_at = Clock.System.now().toString(),
                version = plan.version + 1,
                deleted_at = Clock.System.now().toString(),
                synced_at = null
            )
        }
    }
}

private fun com.lumecard.shared.database.LearningPlan.toLearningPlan() = com.lumecard.shared.model.LearningPlan(
    id = id,
    name = name,
    description = description,
    status = try { com.lumecard.shared.model.PlanStatus.valueOf(status) } catch (_: Exception) { com.lumecard.shared.model.PlanStatus.NOT_STARTED },
    isDefault = is_default == 1L,
    knowledgeBaseIds = (knowledge_base_ids ?: "").split(",").filter { it.isNotBlank() },
    deckIds = (deck_ids ?: "").split(",").filter { it.isNotBlank() },
    cardIds = (card_ids ?: "").split(",").filter { it.isNotBlank() },
    totalCards = total_cards.toInt(),
    completedCards = completed_cards.toInt(),
    createdAt = Instant.parse(created_at),
    updatedAt = Instant.parse(updated_at),
    version = version,
    deletedAt = deleted_at?.let { Instant.parse(it) },
    syncedAt = synced_at?.let { Instant.parse(it) }
)
