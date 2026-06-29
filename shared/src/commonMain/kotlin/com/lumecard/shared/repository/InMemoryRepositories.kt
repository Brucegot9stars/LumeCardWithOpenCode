package com.lumecard.shared.repository

import com.lumecard.shared.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.update

class InMemoryKnowledgeBaseRepository : KnowledgeBaseRepository {
    private val knowledgeBases = MutableStateFlow<List<KnowledgeBase>>(emptyList())

    override fun getAll(): Flow<List<KnowledgeBase>> = knowledgeBases.map { list -> list.filter { it.deletedAt == null } }

    override suspend fun getById(id: String): KnowledgeBase? {
        return knowledgeBases.value.find { it.id == id && it.deletedAt == null }
    }

    override suspend fun getByName(name: String): KnowledgeBase? {
        return knowledgeBases.value.find { it.name == name && it.deletedAt == null }
    }

    override suspend fun insert(knowledgeBase: KnowledgeBase) {
        knowledgeBases.update { it + knowledgeBase }
    }

    override suspend fun update(knowledgeBase: KnowledgeBase) {
        knowledgeBases.update { list ->
            list.map { if (it.id == knowledgeBase.id) knowledgeBase.copy(updatedAt = Clock.System.now()) else it }
        }
    }

    override suspend fun delete(id: String) {
        knowledgeBases.update { it.filter { kb -> kb.id != id } }
    }

    override suspend fun getUpdatedSince(since: Instant): List<KnowledgeBase> {
        return knowledgeBases.value.filter { it.deletedAt == null && it.updatedAt > since }
    }

    override suspend fun markSynced(ids: List<String>, syncedAt: Instant) {
        println("[InMemory] markSynced no-op: ${ids.size} items")
    }
}

class InMemoryDeckRepository : DeckRepository {
    private val decks = MutableStateFlow<List<Deck>>(emptyList())

    override fun getAll(): Flow<List<Deck>> = decks.map { list -> list.filter { it.deletedAt == null } }

    override fun getByKnowledgeBase(knowledgeBaseId: String): Flow<List<Deck>> {
        return decks.map { list -> list.filter { it.knowledgeBaseId == knowledgeBaseId && it.deletedAt == null } }
    }

    override suspend fun getById(id: String): Deck? {
        return decks.value.find { it.id == id && it.deletedAt == null }
    }

    override suspend fun getByNameInKnowledgeBase(knowledgeBaseId: String, name: String): Deck? {
        return decks.value.find { it.knowledgeBaseId == knowledgeBaseId && it.name == name && it.deletedAt == null }
    }

    override suspend fun insert(deck: Deck) {
        decks.update { it + deck }
    }

    override suspend fun update(deck: Deck) {
        decks.update { list ->
            list.map { if (it.id == deck.id) deck.copy(updatedAt = Clock.System.now()) else it }
        }
    }

    override suspend fun delete(id: String) {
        decks.update { it.filter { d -> d.id != id } }
    }

    override suspend fun getUpdatedSince(since: Instant): List<Deck> {
        return decks.value.filter { it.deletedAt == null && it.updatedAt > since }
    }

    override suspend fun markSynced(ids: List<String>, syncedAt: Instant) {
        println("[InMemory] markSynced no-op: ${ids.size} items")
    }
}

class InMemoryCardRepository : CardRepository {
    private val cards = MutableStateFlow<List<Card>>(emptyList())

    override fun getAll(): Flow<List<Card>> = cards.map { list -> list.filter { it.deletedAt == null } }

    override fun getByDeck(deckId: String): Flow<List<Card>> {
        return cards.map { list -> list.filter { it.deckId == deckId && it.deletedAt == null } }
    }

    override suspend fun getById(id: String): Card? {
        return cards.value.find { it.id == id && it.deletedAt == null }
    }

    override suspend fun getDueCards(): Flow<List<Card>> {
        val now = Clock.System.now()
        return cards.map { list ->
            list.filter { card ->
                card.deletedAt == null && card.nextReviewAt != null && card.nextReviewAt <= now
            }
        }
    }

    override suspend fun insert(card: Card) {
        cards.update { it + card }
    }

    override suspend fun update(card: Card) {
        cards.update { list ->
            list.map { if (it.id == card.id) card.copy(updatedAt = Clock.System.now()) else it }
        }
    }

    override suspend fun delete(id: String) {
        cards.update { it.filter { c -> c.id != id } }
    }

    override suspend fun search(query: String): Flow<List<Card>> {
        return cards.map { list ->
            list.filter { card ->
                card.deletedAt == null && (
                    card.front.contains(query, ignoreCase = true) ||
                    card.back.contains(query, ignoreCase = true) ||
                    card.tags.any { it.contains(query, ignoreCase = true) }
                )
            }
        }
    }

    override suspend fun getUpdatedSince(since: Instant): List<Card> {
        return cards.value.filter { it.deletedAt == null && it.updatedAt > since }
    }

    override suspend fun markSynced(ids: List<String>, syncedAt: Instant) {
        println("[InMemory] markSynced no-op: ${ids.size} items")
    }

    override suspend fun rebuildFtsIndex() {
    }
}

class InMemoryReviewLogRepository : ReviewLogRepository {
    private val reviewLogs = MutableStateFlow<List<ReviewLog>>(emptyList())

    override fun getAll(): Flow<List<ReviewLog>> = reviewLogs.map { list -> list.filter { it.deletedAt == null } }

    override fun getByCardId(cardId: String): Flow<List<ReviewLog>> {
        return reviewLogs.map { list -> list.filter { it.cardId == cardId && it.deletedAt == null } }
    }

    override suspend fun insert(reviewLog: ReviewLog) {
        reviewLogs.update { it + reviewLog }
    }

    override suspend fun getStats(): ReviewStats {
        val logs = reviewLogs.value
        val totalReviews = logs.size
        val averageRating = if (totalReviews > 0) logs.map { it.rating }.average() else 0.0
        val goodReviews = logs.count { it.rating >= 3 }
        val retentionRate = if (totalReviews > 0) goodReviews.toDouble() / totalReviews else 0.0
        val studyTimeMinutes = (logs.sumOf { it.reviewTime.toLong() } / 60000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        return ReviewStats(
            totalReviews = totalReviews,
            averageRating = averageRating,
            retentionRate = retentionRate,
            studyTimeMinutes = studyTimeMinutes
        )
    }

    override suspend fun getUpdatedSince(since: Instant): List<ReviewLog> {
        return reviewLogs.value.filter { it.deletedAt == null && it.reviewedAt > since }
    }

    override suspend fun markSynced(ids: List<String>, syncedAt: Instant) {
        println("[InMemory] markSynced no-op: ${ids.size} items")
    }
}

class InMemoryMediaCacheRepository : MediaCacheRepository {
    private val cache = mutableMapOf<String, MediaCacheEntry>()

    override suspend fun get(path: String): MediaCacheEntry? = cache[path]

    override suspend fun getAll(): List<MediaCacheEntry> = cache.values.toList()

    override suspend fun set(path: String, mtime: Long, sha1: String, syncedAt: Instant?) {
        cache[path] = MediaCacheEntry(path, mtime, sha1, syncedAt)
    }

    override suspend fun remove(path: String) { cache.remove(path) }

    override suspend fun removeAll() { cache.clear() }
}

class InMemoryLearningPlanRepository : LearningPlanRepository {
    private val plans = MutableStateFlow<List<LearningPlan>>(emptyList())

    override fun getAll(): Flow<List<LearningPlan>> = plans.map { list -> list.filter { it.deletedAt == null } }

    override suspend fun getById(id: String): LearningPlan? {
        return plans.value.find { it.id == id && it.deletedAt == null }
    }

    override suspend fun getDefault(): LearningPlan? {
        return plans.value.find { it.isDefault && it.deletedAt == null }
    }

    override suspend fun insert(plan: LearningPlan) {
        plans.update { it + plan }
    }

    override suspend fun update(plan: LearningPlan) {
        plans.update { list ->
            list.map { if (it.id == plan.id) plan.copy(updatedAt = Clock.System.now()) else it }
        }
    }

    override suspend fun delete(id: String) {
        plans.update { it.filter { p -> p.id != id } }
    }

    override suspend fun getUpdatedSince(since: Instant): List<LearningPlan> {
        return plans.value.filter { it.deletedAt == null && it.updatedAt > since }
    }

    override suspend fun markSynced(ids: List<String>, syncedAt: Instant) {
        println("[InMemory] markSynced no-op: ${ids.size} items")
    }
}
