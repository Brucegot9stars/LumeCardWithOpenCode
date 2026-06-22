package com.lumecard.app.ui.screens.learningplan

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.model.*
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.LearningPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID

class LearningPlanViewModel(
    private val planRepository: LearningPlanRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository
) : ScreenModel {

    private val _plans = MutableStateFlow<List<LearningPlan>>(emptyList())
    val plans: StateFlow<List<LearningPlan>> = _plans.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadPlans()
    }

    suspend fun getPlanById(id: String): com.lumecard.shared.model.LearningPlan? {
        return try { planRepository.getById(id) } catch (_: Exception) { null }
    }

    fun loadPlans() {
        screenModelScope.launch {
            _isLoading.value = true
            planRepository.getAll().collect { list ->
                _plans.value = list
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
            id = "plan_${UUID.randomUUID().toString().take(8)}",
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
    }

    suspend fun deletePlan(id: String) {
        planRepository.delete(id)
    }

    suspend fun setDefault(id: String) {
        clearDefaultPlan()
        val plan = planRepository.getById(id) ?: return
        planRepository.update(plan.copy(isDefault = true, updatedAt = Clock.System.now()))
    }

    suspend fun unsetDefault(id: String) {
        val plan = planRepository.getById(id) ?: return
        planRepository.update(plan.copy(isDefault = false, updatedAt = Clock.System.now()))
    }

    suspend fun resetProgress(id: String) {
        val plan = planRepository.getById(id) ?: return
        planRepository.update(plan.copy(
            status = PlanStatus.NOT_STARTED,
            completedCards = 0,
            updatedAt = Clock.System.now()
        ))
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
        val planCardIds = plan.cardIds.toSet()
        val planDeckIds = plan.deckIds.toSet()
        val planKbIds = plan.knowledgeBaseIds.toSet()

        return allCards.filter { card ->
            card.deletedAt == null && (
                card.id in planCardIds ||
                card.deckId in planDeckIds ||
                run {
                    val deck = deckRepository.getById(card.deckId)
                    deck != null && deck.knowledgeBaseId in planKbIds
                }
            )
        }
    }

    suspend fun getDeckCountForPlan(plan: LearningPlan): Int {
        val allDecks = deckRepository.getAll().first()
        return allDecks.count { deck ->
            deck.deletedAt == null && (
                deck.id in plan.deckIds ||
                deck.knowledgeBaseId in plan.knowledgeBaseIds
            )
        }
    }

    private suspend fun resolveCardIds(kbIds: List<String>, deckIds: List<String>, cardIds: List<String>): Set<String> {
        val allCards = cardRepository.getAll().first()
        val allDecks = deckRepository.getAll().first()
        val result = mutableSetOf<String>()
        result.addAll(cardIds)
        for (deckId in deckIds) {
            result.addAll(allCards.filter { it.deckId == deckId && it.deletedAt == null }.map { it.id })
        }
        for (kbId in kbIds) {
            val kbDeckIds = allDecks.filter { it.knowledgeBaseId == kbId && it.deletedAt == null }.map { it.id }.toSet()
            result.addAll(allCards.filter { it.deckId in kbDeckIds && it.deletedAt == null }.map { it.id })
        }
        return result
    }

    private suspend fun clearDefaultPlan() {
        val current = planRepository.getDefault()
        if (current != null) {
            planRepository.update(current.copy(isDefault = false, updatedAt = Clock.System.now()))
        }
    }
}
