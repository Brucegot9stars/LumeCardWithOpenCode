package com.lumecard.shared.repository

import com.lumecard.shared.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class InMemoryKnowledgeBaseRepository : KnowledgeBaseRepository {
    private val knowledgeBases = MutableStateFlow<List<KnowledgeBase>>(emptyList())

    override fun getAll(): Flow<List<KnowledgeBase>> = knowledgeBases

    override suspend fun getById(id: String): KnowledgeBase? {
        return knowledgeBases.value.find { it.id == id }
    }

    override suspend fun insert(knowledgeBase: KnowledgeBase) {
        knowledgeBases.value = knowledgeBases.value + knowledgeBase
    }

    override suspend fun update(knowledgeBase: KnowledgeBase) {
        knowledgeBases.value = knowledgeBases.value.map {
            if (it.id == knowledgeBase.id) knowledgeBase.copy(updatedAt = Clock.System.now()) else it
        }
    }

    override suspend fun delete(id: String) {
        knowledgeBases.value = knowledgeBases.value.filter { it.id != id }
    }
}

class InMemoryDeckRepository : DeckRepository {
    private val decks = MutableStateFlow<List<Deck>>(emptyList())

    override fun getAll(): Flow<List<Deck>> = decks

    override fun getByKnowledgeBase(knowledgeBaseId: String): Flow<List<Deck>> {
        return decks.map { list -> list.filter { it.knowledgeBaseId == knowledgeBaseId } }
    }

    override suspend fun getById(id: String): Deck? {
        return decks.value.find { it.id == id }
    }

    override suspend fun insert(deck: Deck) {
        decks.value = decks.value + deck
    }

    override suspend fun update(deck: Deck) {
        decks.value = decks.value.map {
            if (it.id == deck.id) deck.copy(updatedAt = Clock.System.now()) else it
        }
    }

    override suspend fun delete(id: String) {
        decks.value = decks.value.filter { it.id != id }
    }
}

class InMemoryCardRepository : CardRepository {
    private val cards = MutableStateFlow<List<Card>>(emptyList())

    override fun getAll(): Flow<List<Card>> = cards

    override fun getByDeck(deckId: String): Flow<List<Card>> {
        return cards.map { list -> list.filter { it.deckId == deckId } }
    }

    override suspend fun getById(id: String): Card? {
        return cards.value.find { it.id == id }
    }

    override suspend fun getDueCards(): Flow<List<Card>> {
        val now = Clock.System.now()
        return cards.map { list ->
            list.filter { card ->
                card.nextReviewAt?.let { it <= now } ?: true
            }
        }
    }

    override suspend fun insert(card: Card) {
        cards.value = cards.value + card
    }

    override suspend fun update(card: Card) {
        cards.value = cards.value.map {
            if (it.id == card.id) card.copy(updatedAt = Clock.System.now()) else it
        }
    }

    override suspend fun delete(id: String) {
        cards.value = cards.value.filter { it.id != id }
    }

    override suspend fun search(query: String): Flow<List<Card>> {
        return cards.map { list ->
            list.filter { card ->
                card.front.contains(query, ignoreCase = true) ||
                card.back.contains(query, ignoreCase = true) ||
                card.tags.any { it.contains(query, ignoreCase = true) }
            }
        }
    }
}

class InMemoryReviewLogRepository : ReviewLogRepository {
    private val reviewLogs = MutableStateFlow<List<ReviewLog>>(emptyList())

    override fun getAll(): Flow<List<ReviewLog>> = reviewLogs

    override fun getByCardId(cardId: String): Flow<List<ReviewLog>> {
        return reviewLogs.map { list -> list.filter { it.cardId == cardId } }
    }

    override suspend fun insert(reviewLog: ReviewLog) {
        reviewLogs.value = reviewLogs.value + reviewLog
    }

    override suspend fun getStats(): ReviewStats {
        val logs = reviewLogs.value
        val totalReviews = logs.size
        val averageRating = if (totalReviews > 0) logs.map { it.rating }.average() else 0.0
        val goodReviews = logs.count { it.rating >= 3 }
        val retentionRate = if (totalReviews > 0) goodReviews.toDouble() / totalReviews else 0.0
        val studyTimeMinutes = logs.sumOf { it.reviewTime } / 60000

        return ReviewStats(
            totalReviews = totalReviews,
            averageRating = averageRating,
            retentionRate = retentionRate,
            studyTimeMinutes = studyTimeMinutes
        )
    }
}
