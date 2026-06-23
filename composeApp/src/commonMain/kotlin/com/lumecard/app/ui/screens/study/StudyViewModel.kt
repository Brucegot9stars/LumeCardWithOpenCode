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
import com.lumecard.shared.repository.LearningPlanRepository
import com.lumecard.shared.repository.ReviewLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.util.UUID

enum class CardsStudyMode {
    DUE_FIRST,
    ALL_CARDS,
    NEW_CARDS,
    RANDOM
}

class StudyViewModel(
    private val cardRepository: CardRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val algorithmStateRepository: AlgorithmStateRepository,
    private val algorithm: ReviewAlgorithm,
    private val planRepository: LearningPlanRepository
) : ScreenModel {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() { _error.value = null }

    private fun reportError(e: Throwable, context: String) {
        val stackTrace = e.stackTraceToString()
        val msg = "[$context] ${e.message ?: e::class.simpleName}\n$stackTrace"
        println("[LumeCard ERROR] $msg")
        _error.value = msg
    }
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

    private val _history = MutableStateFlow<List<Pair<Int, Boolean>>>(emptyList())
    val history: StateFlow<List<Pair<Int, Boolean>>> = _history

    val canGoBack: Boolean get() = _history.value.isNotEmpty()

    private var timerJob: Job? = null

    private val _sessionStartTime = MutableStateFlow<kotlinx.datetime.Instant?>(null)
    val sessionStartTime: StateFlow<kotlinx.datetime.Instant?> = _sessionStartTime

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds

    private val _cardStartTimes = java.util.concurrent.ConcurrentHashMap<String, kotlinx.datetime.Instant>()

    private var activePlanIds: List<String> = emptyList()
    private var activeDeckIds: List<String> = emptyList()
    private var loadCardsJob: kotlinx.coroutines.Job? = null
    private val rateMutex = Mutex()

    private var _hasStartedStudying = false

    private val _allCardsCache = MutableStateFlow<List<Card>>(emptyList())
    val totalCardCount: Int get() = _allCardsCache.value.size
    val unlearnedCardCount: Int get() = _allCardsCache.value.count { it.lastReviewedAt == null }

    fun loadCards(deckIds: List<String>, planIds: List<String> = emptyList(), mode: CardsStudyMode = CardsStudyMode.DUE_FIRST, limit: Int = 20) {
        activePlanIds = planIds
        activeDeckIds = deckIds
        _hasStartedStudying = false
        loadCardsJob?.cancel()
        loadCardsJob = screenModelScope.launch {
            try {
                println("[LumeCard] loadCards called mode=$mode limit=$limit deckIds=$deckIds")
                val allCards = mutableListOf<Card>()
                for (deckId in deckIds) {
                    val deckCards = cardRepository.getByDeck(deckId).first()
                    println("[LumeCard] Found ${deckCards.size} cards for deck $deckId")
                    allCards.addAll(deckCards)
                }
                _allCardsCache.value = allCards.toList()

                val selected = when (mode) {
                    CardsStudyMode.DUE_FIRST -> {
                        val now = Clock.System.now()
                        val dueCards = allCards.filter { card ->
                            val next = card.nextReviewAt
                            next == null || next <= now
                        }
                        if (dueCards.isEmpty() && allCards.isNotEmpty()) {
                            println("[LumeCard] No due cards, using all cards")
                            allCards
                        } else {
                            dueCards
                        }
                    }
                    CardsStudyMode.ALL_CARDS -> allCards
                    CardsStudyMode.NEW_CARDS -> {
                        val unlearned = allCards.filter { it.lastReviewedAt == null }
                        val count = unlearned.size.coerceAtMost(limit)
                        if (count <= 0) allCards else unlearned.shuffled().take(count)
                    }
                    CardsStudyMode.RANDOM -> {
                        val count = allCards.size.coerceAtMost(limit)
                        if (count <= 0) allCards else allCards.shuffled().take(count)
                    }
                }
                println("[LumeCard] Total: ${allCards.size}, studying: ${selected.size}")
                val shuffled = selected.shuffled()
                _cards.value = shuffled
                shuffled.forEach { card ->
                    if (!_algorithmStates.value.containsKey(card.id)) {
                        val existing = algorithmStateRepository.get(card.id)
                        val state = if (existing != null) {
                            deserializeState(existing)
                        } else {
                            algorithm.initCard()
                        }
                        _algorithmStates.update { it + (card.id to state) }
                    }
                }
                shuffled.firstOrNull()?.let { _cardStartTimes[it.id] = Clock.System.now() }
                println("[LumeCard] loadCards completed successfully, ${shuffled.size} cards ready")
            } catch (e: Exception) {
                reportError(e, "loadCards")
            }
        }
    }

    fun reloadWithMode(mode: CardsStudyMode, limit: Int = 20) {
        _cards.value = emptyList()
        _currentCardIndex.value = 0
        _completedCards.value = 0
        _isFlipped.value = false
        _history.value = emptyList()
        _hasStartedStudying = false
        loadCards(activeDeckIds, activePlanIds, mode, limit)
    }

    private fun startTimerIfNeeded() {
        if (_hasStartedStudying) return
        _hasStartedStudying = true
        _sessionStartTime.value = Clock.System.now()
        _elapsedSeconds.value = 0
        timerJob?.cancel()
        timerJob = screenModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val start = _sessionStartTime.value ?: continue
                _elapsedSeconds.value = ((Clock.System.now().toEpochMilliseconds() - start.toEpochMilliseconds()) / 1000).toInt()
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onDispose() {
        stopTimer()
        super.onDispose()
    }

    fun flipCard() {
        _isFlipped.value = !_isFlipped.value
    }

    fun goBack() {
        val hist = _history.value.toMutableList()
        if (hist.isEmpty()) return
        val (prevIndex, wasFlipped) = hist.removeLast()
        _history.value = hist
        _currentCardIndex.value = prevIndex
        _isFlipped.value = wasFlipped
        if (_completedCards.value > 0) _completedCards.value--
        _cards.value.getOrNull(prevIndex)?.let { _cardStartTimes[it.id] = Clock.System.now() }
    }

    fun rateCard(rating: Rating) {
        val currentCard = _cards.value.getOrNull(_currentCardIndex.value) ?: return
        val state = _algorithmStates.value[currentCard.id] ?: return

        startTimerIfNeeded()

        val updatedState = algorithm.schedule(state, rating)
        _algorithmStates.update { it + (currentCard.id to updatedState) }

        val now = Clock.System.now()
        val startTime = _cardStartTimes.remove(currentCard.id) ?: now
        val reviewTimeMs = ((now.toEpochMilliseconds() - startTime.toEpochMilliseconds()).coerceAtLeast(0)).toInt()

        _history.update { it + (_currentCardIndex.value to _isFlipped.value) }

        screenModelScope.launch {
            try {
                val reviewLog = ReviewLog(
                    id = UUID.randomUUID().toString(),
                    cardId = currentCard.id,
                    rating = rating.value,
                    reviewTime = reviewTimeMs,
                    interval = updatedState.intervalDays,
                    easeFactor = updatedState.easeFactor,
                    repetitions = updatedState.repetitions,
                    lapseCount = updatedState.lapses,
                    reviewedAt = now
                )
                rateMutex.withLock {
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

                    _isFlipped.value = false
                    _completedCards.update { it + 1 }

                    if (_currentCardIndex.value < _cards.value.size - 1) {
                        _currentCardIndex.value++
                        _cards.value.getOrNull(_currentCardIndex.value)?.let { _cardStartTimes[it.id] = Clock.System.now() }
                    } else {
                        _currentCardIndex.value = _cards.value.size
                        updatePlanProgress()
                    }
                }
            } catch (e: Exception) {
                reportError(e, "rateCard-persist")
            }
        }
    }

    private fun updatePlanProgress() {
        if (activePlanIds.isEmpty()) return
        screenModelScope.launch {
            try {
                for (planId in activePlanIds) {
                    val plan = planRepository.getById(planId) ?: continue
                    val newCompleted = (plan.completedCards + _completedCards.value).coerceAtMost(plan.totalCards)
                    val newStatus = if (newCompleted >= plan.totalCards) {
                        com.lumecard.shared.model.PlanStatus.COMPLETED
                    } else if (newCompleted > 0) {
                        com.lumecard.shared.model.PlanStatus.IN_PROGRESS
                    } else {
                        plan.status
                    }
                    planRepository.update(plan.copy(
                        completedCards = newCompleted,
                        status = newStatus,
                        updatedAt = kotlinx.datetime.Clock.System.now()
                    ))
                }
            } catch (e: Exception) {
                reportError(e, "updatePlanProgress")
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun serializeState(state: AlgorithmState): String {
        return json.encodeToString(AlgorithmState.serializer(), state)
    }

    private fun deserializeState(data: String): AlgorithmState {
        return try {
            json.decodeFromString(AlgorithmState.serializer(), data)
        } catch (_: Exception) {
            deserializeLegacyState(data)
        }
    }

    private fun deserializeLegacyState(data: String): AlgorithmState {
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

