package com.lumecard.app.ui.screens.study

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.domain.scheduler.FSRSAlgorithm
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.FSRSCard
import com.lumecard.shared.model.Rating
import com.lumecard.shared.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StudyViewModel(
    private val cardRepository: CardRepository,
    private val fsrsAlgorithm: FSRSAlgorithm
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
                // Initialize FSRS cards
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

        _isFlipped.value = false
        _completedCards.value++

        if (_currentCardIndex.value < _cards.value.size - 1) {
            _currentCardIndex.value++
        } else {
            _currentCardIndex.value = _cards.value.size
        }
    }

    fun getCurrentCard(): Card? {
        return _cards.value.getOrNull(_currentCardIndex.value)
    }

    fun isComplete(): Boolean {
        return _currentCardIndex.value >= _cards.value.size
    }
}
