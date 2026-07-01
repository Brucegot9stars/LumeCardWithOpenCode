package com.lumecard.shared.repository

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.model.ReviewLog
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface KnowledgeBaseRepository {
    fun getAll(): Flow<List<KnowledgeBase>>
    suspend fun getById(id: String): KnowledgeBase?
    suspend fun getByName(name: String): KnowledgeBase?
    suspend fun insert(knowledgeBase: KnowledgeBase)
    suspend fun update(knowledgeBase: KnowledgeBase)
    suspend fun delete(id: String)
    suspend fun getUpdatedSince(since: Instant): List<KnowledgeBase>
    suspend fun markSynced(ids: List<String>, syncedAt: Instant)
}

interface DeckRepository {
    fun getAll(): Flow<List<Deck>>
    fun getByKnowledgeBase(knowledgeBaseId: String): Flow<List<Deck>>
    suspend fun getById(id: String): Deck?
    suspend fun getByNameInKnowledgeBase(knowledgeBaseId: String, name: String): Deck?
    suspend fun insert(deck: Deck)
    suspend fun update(deck: Deck)
    suspend fun delete(id: String)
    suspend fun getUpdatedSince(since: Instant): List<Deck>
    suspend fun markSynced(ids: List<String>, syncedAt: Instant)
}

interface CardRepository {
    fun getAll(): Flow<List<Card>>
    fun getByDeck(deckId: String): Flow<List<Card>>
    suspend fun getById(id: String): Card?
    suspend fun getDueCards(): Flow<List<Card>>
    suspend fun insert(card: Card)
    suspend fun update(card: Card)
    suspend fun delete(id: String)
    suspend fun search(query: String): Flow<List<Card>>
    suspend fun getUpdatedSince(since: Instant): List<Card>
    suspend fun markSynced(ids: List<String>, syncedAt: Instant)
    suspend fun rebuildFtsIndex()
}

interface ReviewLogRepository {
    fun getAll(): Flow<List<ReviewLog>>
    fun getByCardId(cardId: String): Flow<List<ReviewLog>>
    suspend fun insert(reviewLog: ReviewLog)
    suspend fun getStats(): ReviewStats
    suspend fun getUpdatedSince(since: Instant): List<ReviewLog>
    suspend fun markSynced(ids: List<String>, syncedAt: Instant)
}

data class ReviewStats(
    val totalReviews: Int,
    val averageRating: Double,
    val retentionRate: Double,
    val studyTimeMinutes: Int
)

interface SettingsRepository {
    suspend fun get(key: String): String?
    suspend fun getAll(): Map<String, String>
    suspend fun set(key: String, value: String)
    suspend fun delete(key: String)
    suspend fun getInt(key: String, default: Int = 0): Int
    suspend fun getBoolean(key: String, default: Boolean = false): Boolean
}

interface AlgorithmStateRepository {
    suspend fun get(cardId: String): String? // returns state_json
    suspend fun getAll(): Map<String, String> // card_id -> state_json
    suspend fun save(cardId: String, mode: String, stateJson: String)
    suspend fun delete(cardId: String)
}

data class MediaCacheEntry(
    val path: String,
    val mtime: Long,
    val sha1: String,
    val syncedAt: Instant? = null
)

interface MediaCacheRepository {
    suspend fun get(path: String): MediaCacheEntry?
    suspend fun getAll(): List<MediaCacheEntry>
    suspend fun set(path: String, mtime: Long, sha1: String, syncedAt: Instant? = null)
    suspend fun remove(path: String)
    suspend fun removeAll()
}

interface LearningPlanRepository {
    fun getAll(): Flow<List<com.lumecard.shared.model.LearningPlan>>
    suspend fun getById(id: String): com.lumecard.shared.model.LearningPlan?
    suspend fun getDefault(): com.lumecard.shared.model.LearningPlan?
    suspend fun insert(plan: com.lumecard.shared.model.LearningPlan)
    suspend fun update(plan: com.lumecard.shared.model.LearningPlan)
    suspend fun delete(id: String)
    suspend fun getUpdatedSince(since: Instant): List<com.lumecard.shared.model.LearningPlan>
    suspend fun markSynced(ids: List<String>, syncedAt: Instant)
}
