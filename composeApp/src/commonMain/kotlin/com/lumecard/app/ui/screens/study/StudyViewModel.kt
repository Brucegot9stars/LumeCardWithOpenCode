package com.lumecard.app.ui.screens.study

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.domain.scheduler.FSRSAlgorithm
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.FSRSCard
import com.lumecard.shared.model.Rating
import com.lumecard.shared.model.ReviewLog
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.ReviewLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID

class StudyViewModel(
    private val cardRepository: CardRepository,
    private val fsrsAlgorithm: FSRSAlgorithm,
    private val reviewLogRepository: ReviewLogRepository
) : ScreenModel {
    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards

    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex

    private val _isFlipped = MutableStateFlow(false)
    val isFlipped: StateFlow<Boolean> = _isFlipped

    private val _completedCards = MutableStateFlow(0)
    val completedCards: StateFlow<Int> = _completedCards

    private val _fsrsCards = MutableStateFlow<Map<String, FSRSCard>>(emptyMap())
    val fsrsCards: StateFlow<Map<String, FSRSCard>> = _fsrsCards

    fun loadCards(deckId: String) {
        screenModelScope.launch {
            cardRepository.getByDeck(deckId).collect { cards ->
                _cards.value = cards
                cards.forEach { card ->
                    if (!_fsrsCards.value.containsKey(card.id)) {
                        val fsrsCard = fsrsAlgorithm.initCard().copy(id = card.id)
                        _fsrsCards.value = _fsrsCards.value + (card.id to fsrsCard)
                    }
                }
            }
        }
    }

    fun flipCard() {
        _isFlipped.value = !_isFlipped.value
    }

    fun rateCard(rating: Rating) {
        val currentCard = _cards.value.getOrNull(_currentCardIndex.value) ?: return
        val fsrsCard = _fsrsCards.value[currentCard.id] ?: return

        val updatedFsrsCard = fsrsAlgorithm.schedule(fsrsCard, rating)
        _fsrsCards.value = _fsrsCards.value + (currentCard.id to updatedFsrsCard)

        val now = Clock.System.now()

        screenModelScope.launch {
            // Persist ReviewLog
            val reviewLog = ReviewLog(
                id = UUID.randomUUID().toString(),
                cardId = currentCard.id,
                rating = rating.value,
                reviewTime = 0,
                interval = updatedFsrsCard.scheduledDays,
                easeFactor = 2.5f,
                repetitions = updatedFsrsCard.reps,
                lapseCount = updatedFsrsCard.lapses,
                reviewedAt = now
            )
            reviewLogRepository.insert(reviewLog)

            // Update Card scheduling fields
            val updatedCard = currentCard.copy(
                lastReviewedAt = now,
                nextReviewAt = updatedFsrsCard.due,
                updatedAt = now
            )
            cardRepository.update(updatedCard)
        }

        _isFlipped.value = false
        _completedCards.value++

        if (_currentCardIndex.value < _cards.value.size - 1) {
            _currentCardIndex.value++
        } else {
            _currentCardIndex.value = _cards.value.size
        }
    }
}
