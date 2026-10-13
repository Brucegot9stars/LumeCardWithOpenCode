package com.lumecard.app.ui.screens.study

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.data.LearningPlanCardResolver
import com.lumecard.shared.data.ReviewService
import com.lumecard.shared.data.generateId
import com.lumecard.shared.domain.scheduler.AlgorithmState
import com.lumecard.shared.feature.quote.facade.QuoteFeature
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Rating
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.LearningPlanRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

enum class CardsStudyMode { DUE_FIRST, DUE_ONLY, ALL_CARDS, NEW_CARDS, RANDOM }

class StudyViewModel(
    private val cardRepository: CardRepository,
    private val planRepository: LearningPlanRepository,
    private val deckRepository: DeckRepository,
    private val reviewService: ReviewService,
    private val quoteFeature: QuoteFeature,
) : ScreenModel {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    fun clearError() { _error.value = null }

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
    val canGoBack: Boolean get() = _history.value.isNotEmpty() && !isRating
    private val _sessionStartTime = MutableStateFlow<Instant?>(null)
    val sessionStartTime: StateFlow<Instant?> = _sessionStartTime
    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds
    private val _allCardsCache = MutableStateFlow<List<Card>>(emptyList())
    val totalCardCount get() = _allCardsCache.value.size
    val unlearnedCardCount get() = _allCardsCache.value.count { it.lastReviewedAt == null }

    private val cardStartTimes = mutableMapOf<String, Instant>()
    private var activePlanIds: List<String> = emptyList()
    private var activeDeckIds: List<String> = emptyList()
    private var loadCardsJob: Job? = null
    private var isRating = false
    private var sessionGeneration = 0
    private var isTimerRunning = false
    private var elapsedBeforeResume = 0

    init {
        screenModelScope.launch {
            quoteFeature.studyTimeSeconds.collect {
                if (_sessionStartTime.value != null && isTimerRunning) {
                    _elapsedSeconds.value = (elapsedBeforeResume.toLong() + it)
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
            }
        }
    }

    fun loadCards(deckIds: List<String>, planIds: List<String> = emptyList(), mode: CardsStudyMode = CardsStudyMode.DUE_FIRST, limit: Int = 20) {
        if (isRating) return
        stopTimer()
        _sessionStartTime.value = null
        _elapsedSeconds.value = 0
        elapsedBeforeResume = 0
        _cards.value = emptyList()
        _allCardsCache.value = emptyList()
        _algorithmStates.value = emptyMap()
        _currentCardIndex.value = 0
        _completedCards.value = 0
        _history.value = emptyList()
        _isFlipped.value = false
        cardStartTimes.clear()
        activeDeckIds = deckIds.distinct()
        activePlanIds = planIds.distinct()
        val generation = ++sessionGeneration
        loadCardsJob?.cancel()
        loadCardsJob = screenModelScope.launch {
            try {
                val (allCards, states) = withContext(Dispatchers.IO) {
                    val cards = cardRepository.getAll().first()
                    val decks = deckRepository.getAll().first()
                    val activeDecks = decks.filter { it.deletedAt == null }.map { it.id }.toSet()
                    val eligible = if (activePlanIds.isEmpty()) null else {
                        val selectedPlans = activePlanIds.mapNotNull { planRepository.getById(it) }
                            .filter { it.deletedAt == null }
                        val cardRefs = LearningPlanCardResolver.cardRefs(cards)
                        val deckRefs = LearningPlanCardResolver.deckRefs(decks)
                        selectedPlans.flatMap {
                            LearningPlanCardResolver.cardIds(LearningPlanCardResolver.selection(it), cardRefs, deckRefs)
                        }.toSet()
                    }
                    val selectedCards = cards.filter {
                        it.deletedAt == null && it.deckId in activeDecks && it.deckId in activeDeckIds &&
                            (eligible == null || it.id in eligible)
                    }.distinctBy { it.id }
                    selectedCards to selectedCards.associate { it.id to reviewService.stateForCard(it.id) }
                }
                if (generation != sessionGeneration) return@launch
                _allCardsCache.value = allCards
                val now = Clock.System.now()
                val selected = when (mode) {
                    CardsStudyMode.DUE_FIRST -> allCards.filter { it.nextReviewAt == null || it.nextReviewAt <= now }
                        .ifEmpty { allCards }
                    CardsStudyMode.DUE_ONLY -> allCards.filter { it.nextReviewAt != null && it.nextReviewAt <= now }
                    CardsStudyMode.ALL_CARDS -> allCards
                    CardsStudyMode.NEW_CARDS -> allCards.filter { it.lastReviewedAt == null }.shuffled().take(limit.coerceAtLeast(0))
                    CardsStudyMode.RANDOM -> allCards.shuffled().take(limit.coerceAtLeast(0))
                }
                _cards.value = selected.shuffled()
                _algorithmStates.value = states.filterKeys { id -> selected.any { it.id == id } }
                _currentCardIndex.value = 0
                _completedCards.value = 0
                _history.value = emptyList()
                _isFlipped.value = false
                cardStartTimes.clear()
                _cards.value.firstOrNull()?.let { cardStartTimes[it.id] = Clock.System.now() }
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportError(e, "loadCards")
            }
        }
    }

    fun reloadWithMode(mode: CardsStudyMode, limit: Int = 20) {
        if (isRating) return
        loadCards(activeDeckIds, activePlanIds, mode, limit)
    }

    fun refreshCurrentCard() {
        if (isRating) return
        val cardId = _cards.value.getOrNull(_currentCardIndex.value)?.id ?: return
        val generation = sessionGeneration
        screenModelScope.launch {
            try {
                val fresh = withContext(Dispatchers.IO) { cardRepository.getById(cardId) } ?: return@launch
                if (generation != sessionGeneration) return@launch
                val index = _cards.value.indexOfFirst { it.id == cardId }
                if (index >= 0) _cards.value = _cards.value.toMutableList().also { it[index] = fresh }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { reportError(e, "refreshCurrentCard") }
        }
    }

    private fun startTimerIfNeeded() {
        if (isTimerRunning) return
        isTimerRunning = true
        if (_sessionStartTime.value == null) {
            _sessionStartTime.value = Clock.System.now()
            _elapsedSeconds.value = 0
        }
        quoteFeature.startStudy()
    }

    fun stopTimer() {
        if (isTimerRunning) {
            quoteFeature.stopStudy()
            elapsedBeforeResume = (elapsedBeforeResume.toLong() + quoteFeature.studyTimeSeconds.value)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            _elapsedSeconds.value = elapsedBeforeResume
        }
        isTimerRunning = false
    }

    override fun onDispose() {
        stopTimer()
        super.onDispose()
    }

    fun flipCard() { if (!isRating) _isFlipped.value = !_isFlipped.value }

    fun goBack() {
        if (isRating) return
        val last = _history.value.lastOrNull() ?: return
        _history.value = _history.value.dropLast(1)
        _currentCardIndex.value = last.first
        _isFlipped.value = last.second
        _completedCards.value = (_completedCards.value - 1).coerceAtLeast(0)
        _cards.value.getOrNull(last.first)?.let { cardStartTimes[it.id] = Clock.System.now() }
    }

    fun rateCard(rating: Rating) {
        if (isRating) return
        val index = _currentCardIndex.value
        val card = _cards.value.getOrNull(index) ?: return
        isRating = true // Set before launch: a second click cannot create a duplicate review.
        val wasFlipped = _isFlipped.value
        val generation = sessionGeneration
        val startedAt = cardStartTimes[card.id] ?: Clock.System.now()
        screenModelScope.launch {
            try {
                val duration = (Clock.System.now().toEpochMilliseconds() - startedAt.toEpochMilliseconds())
                    .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                val result = withContext(Dispatchers.IO) {
                    reviewService.review(card.id, rating, duration, generateId("review"))
                }
                if (generation != sessionGeneration) return@launch
                _algorithmStates.value = _algorithmStates.value + (card.id to result.state)
                _cards.value = _cards.value.toMutableList().also { list ->
                    val position = list.indexOfFirst { it.id == card.id }
                    if (position >= 0) list[position] = list[position].copy(
                        lastReviewedAt = result.reviewedAt, nextReviewAt = result.state.nextReviewAt,
                        updatedAt = result.reviewedAt
                    )
                }
                cardStartTimes.remove(card.id)
                _history.value = _history.value + (index to wasFlipped)
                _completedCards.value++
                _isFlipped.value = false
                _currentCardIndex.value = index + 1
                _cards.value.getOrNull(index + 1)?.let { cardStartTimes[it.id] = Clock.System.now() }
                startTimerIfNeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportError(e, "rateCard")
            } finally {
                isRating = false
            }
        }
    }

    private fun reportError(e: Throwable, context: String) {
        val msg = "[$context] ${e.message ?: e::class.simpleName}\n${e.stackTraceToString()}"
        println("[LumeCard ERROR] $msg")
        _error.value = msg
    }
}
