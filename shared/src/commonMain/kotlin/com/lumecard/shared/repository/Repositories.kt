package com.lumecard.shared.repository

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.model.ReviewLog
import kotlinx.coroutines.flow.Flow

interface KnowledgeBaseRepository {
    fun getAll(): Flow<List<KnowledgeBase>>
    suspend fun getById(id: String): KnowledgeBase?
    suspend fun insert(knowledgeBase: KnowledgeBase)
    suspend fun update(knowledgeBase: KnowledgeBase)
    suspend fun delete(id: String)
}

interface DeckRepository {
    fun getAll(): Flow<List<Deck>>
    fun getByKnowledgeBase(knowledgeBaseId: String): Flow<List<Deck>>
    suspend fun getById(id: String): Deck?
    suspend fun insert(deck: Deck)
    suspend fun update(deck: Deck)
    suspend fun delete(id: String)
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
}

interface ReviewLogRepository {
    fun getAll(): Flow<List<ReviewLog>>
    fun getByCardId(cardId: String): Flow<List<ReviewLog>>
    suspend fun insert(reviewLog: ReviewLog)
    suspend fun getStats(): ReviewStats
}

data class ReviewStats(
    val totalReviews: Int,
    val averageRating: Double,
    val retentionRate: Double,
    val studyTimeMinutes: Int
)
