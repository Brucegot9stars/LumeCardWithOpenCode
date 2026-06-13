package com.lumecard.app.ui.screens.study

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.domain.scheduler.AlgorithmState
import com.lumecard.shared.domain.scheduler.ReviewAlgorithm
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Rating
import com.lumecard.shared.model.ReviewLog
import com.lumecard.shared.repository.AlgorithmStateRepository
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.ReviewLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID

class StudyViewModel(
    private val cardRepository: CardRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val algorithmStateRepository: AlgorithmStateRepository,
    private val algorithm: ReviewAlgorithm
) : ScreenModel {
    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards

    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex

    private val _isFlipped = MutableStateFlow(false)
    val isFlipped: StateFlow<Boolean> = _isFlipped

    private val _completedCards = MutableStateFlow(0)
    val completedCards: StateFlow<Int> = _completedCards

    private val _algorithmStates = MutableStateFlow<Map<String, AlgorithmState>>(emptyMap())
    val algorithmStates: StateFlow<Map<String, AlgorithmState>> = _algorithmStates

    fun loadCards(deckId: String) {
        screenModelScope.launch {
            cardRepository.getByDeck(deckId).collect { cards ->
                _cards.value = cards
                cards.forEach { card ->
                    if (!_algorithmStates.value.containsKey(card.id)) {
                        val existing = algorithmStateRepository.get(card.id)
                        val state = if (existing != null) {
                            deserializeState(existing)
                        } else {
                            algorithm.initCard()
                        }
                        _algorithmStates.value = _algorithmStates.value + (card.id to state)
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
        val state = _algorithmStates.value[currentCard.id] ?: return

        val updatedState = algorithm.schedule(state, rating)
        _algorithmStates.value = _algorithmStates.value + (currentCard.id to updatedState)

        val now = Clock.System.now()

        screenModelScope.launch {
            val reviewLog = ReviewLog(
                id = UUID.randomUUID().toString(),
                cardId = currentCard.id,
                rating = rating.value,
                reviewTime = 0,
                interval = updatedState.intervalDays,
                easeFactor = updatedState.easeFactor,
                repetitions = updatedState.repetitions,
                lapseCount = updatedState.lapses,
                reviewedAt = now
            )
            reviewLogRepository.insert(reviewLog)

            val updatedCard = currentCard.copy(
                lastReviewedAt = now,
                nextReviewAt = updatedState.nextReviewAt,
                updatedAt = now
            )
            cardRepository.update(updatedCard)

            algorithmStateRepository.save(
                cardId = currentCard.id,
                mode = algorithm.mode.name,
                stateJson = serializeState(updatedState)
            )
        }

        _isFlipped.value = false
        _completedCards.value++

        if (_currentCardIndex.value < _cards.value.size - 1) {
            _currentCardIndex.value++
        } else {
            _currentCardIndex.value = _cards.value.size
        }
    }

    private fun serializeState(state: AlgorithmState): String {
        return buildString {
            append("${state.intervalDays}|")
            append("${state.nextReviewAt}|")
            append("${state.repetitions}|")
            append("${state.lapses}|")
            append("${state.easeFactor}|")
            append("${state.stage}|")
            append("${state.stability}|")
            append(state.difficulty)
        }
    }

    private fun deserializeState(data: String): AlgorithmState {
        val parts = data.split("|")
        return AlgorithmState(
            intervalDays = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            nextReviewAt = parts.getOrNull(1)?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { Clock.System.now() } } ?: Clock.System.now(),
            repetitions = parts.getOrNull(2)?.toIntOrNull() ?: 0,
            lapses = parts.getOrNull(3)?.toIntOrNull() ?: 0,
            easeFactor = parts.getOrNull(4)?.toFloatOrNull() ?: 2.5f,
            stage = parts.getOrNull(5)?.toIntOrNull() ?: 0,
            stability = parts.getOrNull(6)?.toDoubleOrNull() ?: 0.0,
            difficulty = parts.getOrNull(7)?.toDoubleOrNull() ?: 0.0
        )
    }
}
