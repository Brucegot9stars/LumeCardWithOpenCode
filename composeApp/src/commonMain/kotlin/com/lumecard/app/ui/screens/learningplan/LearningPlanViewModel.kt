package com.lumecard.app.ui.screens.learningplan

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.data.LearningPlanCardResolver
import com.lumecard.shared.data.ReviewService
import com.lumecard.shared.model.*
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.KnowledgeBaseRepository
import com.lumecard.shared.repository.LearningPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import com.lumecard.shared.data.generateId

class LearningPlanViewModel(
    private val planRepository: LearningPlanRepository,
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val reviewService: ReviewService,
) : ScreenModel {

    private val _plans = MutableStateFlow<List<LearningPlan>>(emptyList())
    val plans: StateFlow<List<LearningPlan>> = _plans.asStateFlow()

    private val _knowledgeBases = MutableStateFlow<List<KnowledgeBase>>(emptyList())
    val knowledgeBases: StateFlow<List<KnowledgeBase>> = _knowledgeBases.asStateFlow()

    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks.asStateFlow()

    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private var plansJob: Job? = null
    private var selectionJob: Job? = null

    init {
        loadPlans()
        loadSelectionData()
    }

    fun loadSelectionData() {
        if (selectionJob?.isActive == true) return
        selectionJob = screenModelScope.launch {
            combine(
                knowledgeBaseRepository.getAll(),
                deckRepository.getAll(),
                cardRepository.getAll()
            ) { kbs, decks, cards -> Triple(kbs, decks, cards) }.collect { (kbs, decks, cards) ->
                _knowledgeBases.value = kbs.filter { it.deletedAt == null }
                _decks.value = decks.filter { it.deletedAt == null }
                _cards.value = cards.filter { it.deletedAt == null }
            }
        }
    }

    suspend fun getPlanById(id: String): com.lumecard.shared.model.LearningPlan? {
        return planRepository.getById(id)
    }

    fun loadPlans() {
        if (plansJob?.isActive == true) return
        plansJob = screenModelScope.launch {
            _isLoading.value = true
            combine(planRepository.getAll(), cardRepository.getAll(), deckRepository.getAll()) { plans, cards, decks ->
                Triple(plans, cards, decks)
            }.collect { (list, cards, decks) ->
                val cardRefs = LearningPlanCardResolver.cardRefs(cards)
                val deckRefs = LearningPlanCardResolver.deckRefs(decks)
                _plans.value = list.map { plan ->
                    val total = LearningPlanCardResolver.cardIds(
                        LearningPlanCardResolver.selection(plan), cardRefs, deckRefs
                    ).size
                    plan.copy(totalCards = total, completedCards = plan.completedCards.coerceAtMost(total))
                }
                _isLoading.value = false
            }
        }
    }

    fun getActivePlanCount(): Int {
        return _plans.value.count { it.status == PlanStatus.IN_PROGRESS }
    }

    suspend fun createPlan(
        name: String,
        description: String?,
        knowledgeBaseIds: List<String>,
        deckIds: List<String>,
        cardIds: List<String>,
        isDefault: Boolean = false
    ): LearningPlan {
        val allCardIds = resolveCardIds(knowledgeBaseIds, deckIds, cardIds)
        val plan = LearningPlan(
            id = generateId("plan"),
            name = name,
            description = description,
            status = PlanStatus.NOT_STARTED,
            isDefault = isDefault,
            knowledgeBaseIds = knowledgeBaseIds,
            deckIds = deckIds,
            cardIds = cardIds,
            totalCards = allCardIds.size,
            completedCards = 0,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        if (isDefault) {
            clearDefaultPlan()
        }
        planRepository.insert(plan)
        return plan
    }

    suspend fun updatePlan(
        id: String,
        name: String,
        description: String?,
        knowledgeBaseIds: List<String>,
        deckIds: List<String>,
        cardIds: List<String>,
        isDefault: Boolean
    ) {
        val existing = planRepository.getById(id) ?: return
        val allCardIds = resolveCardIds(knowledgeBaseIds, deckIds, cardIds)
        val updated = existing.copy(
            name = name,
            description = description,
            knowledgeBaseIds = knowledgeBaseIds,
            deckIds = deckIds,
            cardIds = cardIds,
            totalCards = allCardIds.size,
            isDefault = isDefault,
            updatedAt = Clock.System.now()
        )
        if (isDefault) {
            clearDefaultPlan()
        }
        planRepository.update(updated)
        withContext(Dispatchers.IO) { reviewService.reconcilePlan(id) }
    }

    suspend fun deletePlan(id: String) {
        val plan = planRepository.getById(id)
        planRepository.delete(id)
        if (plan != null && plan.isDefault) {
            val remaining = planRepository.getAll().first().filter { it.id != id && it.deletedAt == null }
            remaining.firstOrNull()?.let { planRepository.update(it.copy(isDefault = true, updatedAt = Clock.System.now())) }
        }
    }

    suspend fun setDefault(id: String) {
        val plan = planRepository.getById(id) ?: return
        clearDefaultPlan()
        planRepository.update(plan.copy(isDefault = true, updatedAt = Clock.System.now()))
    }

    suspend fun unsetDefault(id: String) {
        val plan = planRepository.getById(id) ?: return
        planRepository.update(plan.copy(isDefault = false, updatedAt = Clock.System.now()))
    }

    suspend fun resetProgress(id: String) {
        withContext(Dispatchers.IO) { reviewService.resetPlanProgress(id) }
    }

    suspend fun startPlan(id: String) {
        val plan = planRepository.getById(id) ?: return
        if (plan.status == PlanStatus.NOT_STARTED) {
            planRepository.update(plan.copy(
                status = PlanStatus.IN_PROGRESS,
                updatedAt = Clock.System.now()
            ))
        }
    }

    suspend fun completePlan(id: String) {
        val plan = planRepository.getById(id) ?: return
        planRepository.update(plan.copy(
            status = PlanStatus.COMPLETED,
            completedCards = plan.totalCards,
            updatedAt = Clock.System.now()
        ))
    }

    suspend fun getCardsForPlan(plan: LearningPlan): List<Card> {
        val allCards = cardRepository.getAll().first()
        val allDecks = deckRepository.getAll().first()
        val ids = LearningPlanCardResolver.cardIds(LearningPlanCardResolver.selection(plan),
            LearningPlanCardResolver.cardRefs(allCards), LearningPlanCardResolver.deckRefs(allDecks))
        return allCards.filter { it.id in ids }
    }

    suspend fun getDeckIdsForPlan(plan: LearningPlan): List<String> {
        val allCards = cardRepository.getAll().first()
        val allDecks = deckRepository.getAll().first()
        return LearningPlanCardResolver.deckIds(LearningPlanCardResolver.selection(plan),
            LearningPlanCardResolver.cardRefs(allCards), LearningPlanCardResolver.deckRefs(allDecks)).toList()
    }

    suspend fun getDeckCountForPlan(plan: LearningPlan): Int {
        return getDeckIdsForPlan(plan).size
    }

    suspend fun getDueCardCountForPlan(plan: LearningPlan): Int {
        val cards = getCardsForPlan(plan)
        val now = kotlin.time.Clock.System.now()
        return cards.count { it.nextReviewAt?.let { t -> t <= now } == true }
    }

    suspend fun getNewCardCountForPlan(plan: LearningPlan): Int {
        val cards = getCardsForPlan(plan)
        return cards.count { it.lastReviewedAt == null }
    }

    private suspend fun resolveCardIds(kbIds: List<String>, deckIds: List<String>, cardIds: List<String>): Set<String> {
        val allCards = cardRepository.getAll().first()
        val allDecks = deckRepository.getAll().first()
        return LearningPlanCardResolver.cardIds(
            com.lumecard.shared.data.PlanSelection(kbIds, deckIds, cardIds),
            LearningPlanCardResolver.cardRefs(allCards), LearningPlanCardResolver.deckRefs(allDecks))
    }

    private suspend fun clearDefaultPlan() {
        val current = planRepository.getDefault()
        if (current != null) {
            planRepository.update(current.copy(isDefault = false, updatedAt = Clock.System.now()))
        }
    }
}
