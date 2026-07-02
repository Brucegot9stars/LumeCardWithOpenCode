package com.lumecard.app.ui.screens.dashboard

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.LearningPlan
import com.lumecard.shared.model.PlanStatus
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.KnowledgeBaseRepository
import com.lumecard.shared.repository.LearningPlanRepository
import com.lumecard.shared.repository.ReviewLogRepository
import com.lumecard.shared.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class DeckWithCount(
    val deck: Deck,
    val cardCount: Int
)

class DashboardViewModel(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val settingsRepository: SettingsRepository,
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val planRepository: LearningPlanRepository,
) : ScreenModel {
    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks

    private val _decksWithCount = MutableStateFlow<List<DeckWithCount>>(emptyList())
    val decksWithCount: StateFlow<List<DeckWithCount>> = _decksWithCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _todayReviews = MutableStateFlow(0)
    val todayReviews: StateFlow<Int> = _todayReviews.asStateFlow()

    private val _dailyGoal = MutableStateFlow(20)
    val dailyGoal: StateFlow<Int> = _dailyGoal.asStateFlow()

    private val _totalDueCards = MutableStateFlow(0)
    val totalDueCards: StateFlow<Int> = _totalDueCards.asStateFlow()

    private val _kbCount = MutableStateFlow(0)
    val kbCount: StateFlow<Int> = _kbCount.asStateFlow()

    private val _activePlanCount = MutableStateFlow(0)
    val activePlanCount: StateFlow<Int> = _activePlanCount.asStateFlow()

    private val _duePlanCount = MutableStateFlow(0)
    val duePlanCount: StateFlow<Int> = _duePlanCount.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadGoal()
        loadDecks()
    }

    fun refresh() {
        loadGoal()
        loadDecks()
    }

    private fun loadGoal() {
        screenModelScope.launch {
            _dailyGoal.update { settingsRepository.getInt("dailyGoal", 20) }
        }
    }

    private fun loadDecks() {
        loadJob?.cancel()
        loadJob = screenModelScope.launch {
            _isLoading.value = true
            coroutineScope {
                launch {
                    knowledgeBaseRepository.getAll().collect { list ->
                        _kbCount.update { list.size }
                    }
                }
                launch {
                    planRepository.getAll().collect { list ->
                        _activePlanCount.update { list.count { it.status == PlanStatus.IN_PROGRESS } }
                    }
                }
                launch {
                    val deckList = withContext(Dispatchers.IO) { deckRepository.getAll().first() }
                    val withCount = deckList.map { deck ->
                        val cards = withContext(Dispatchers.IO) { cardRepository.getByDeck(deck.id).first() }
                        DeckWithCount(deck, cards.size)
                    }
                    _decks.update { deckList }
                    _decksWithCount.update { withCount }
                    _isLoading.value = false
                }
            }
        }
    }

    fun loadStats() {
        screenModelScope.launch {
            val allLogs = withContext(Dispatchers.IO) { reviewLogRepository.getAll().first() }
            val allCards = withContext(Dispatchers.IO) { cardRepository.getAll().first() }
            val allPlans = withContext(Dispatchers.IO) { planRepository.getAll().first() }
            val allDecks = withContext(Dispatchers.IO) { deckRepository.getAll().first() }

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val todayCount = withContext(Dispatchers.Default) {
                allLogs.count { log ->
                    log.reviewedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date == today
                }
            }
            _todayReviews.value = todayCount

            val now = Clock.System.now()
            val dueCount = withContext(Dispatchers.Default) {
                allCards.count { card ->
                    val next = card.nextReviewAt
                    next != null && next <= now
                }
            }
            _totalDueCards.value = dueCount

            val duePlanCount = withContext(Dispatchers.Default) {
                val planCardIdsSet = allPlans.map { it.cardIds.toSet() }
                val planDeckIdsSet = allPlans.map { it.deckIds.toSet() }
                val planKbIdsSet = allPlans.map { it.knowledgeBaseIds.toSet() }
                allPlans.indices.count { i ->
                    allCards.any { card ->
                        card.deletedAt == null && (
                            card.id in planCardIdsSet[i] ||
                            card.deckId in planDeckIdsSet[i] ||
                            allDecks.any { it.id == card.deckId && it.knowledgeBaseId in planKbIdsSet[i] }
                        ) && card.nextReviewAt?.let { it <= now } == true
                    }
                }
            }
            _duePlanCount.value = duePlanCount
        }
    }
}
