package com.lumecard.app.ui.screens.deck

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.model.Deck
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.LearningPlanRepository
import com.lumecard.shared.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class SortField { NAME, CREATED_AT, UPDATED_AT, STUDY_TIME }
enum class SortOrder { ASC, DESC }

data class SortConfig(
    val field: SortField = SortField.NAME,
    val order: SortOrder = SortOrder.ASC
)

class DeckViewModel(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
    private val planRepository: LearningPlanRepository
) : ScreenModel {

    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sortConfig = MutableStateFlow(SortConfig())
    val sortConfig: StateFlow<SortConfig> = _sortConfig.asStateFlow()

    private val _deckCardCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val deckCardCounts: StateFlow<Map<String, Int>> = _deckCardCounts.asStateFlow()

    private var currentKnowledgeBaseId: String = "default"
    private var loadDecksJob: kotlinx.coroutines.Job? = null

    init {
        loadSortPref()
    }

    private fun loadSortPref() {
        screenModelScope.launch {
            val field = settingsRepository.get("deck_sort_field") ?: SortField.NAME.name
            val order = settingsRepository.get("deck_sort_order") ?: SortOrder.ASC.name
            _sortConfig.value = SortConfig(
                field = try { SortField.valueOf(field) } catch (_: Exception) { SortField.NAME },
                order = try { SortOrder.valueOf(order) } catch (_: Exception) { SortOrder.ASC }
            )
        }
    }

    fun setSortConfig(config: SortConfig) {
        _sortConfig.value = config
        screenModelScope.launch {
            settingsRepository.set("deck_sort_field", config.field.name)
            settingsRepository.set("deck_sort_order", config.order.name)
        }
    }

    fun loadDecks(knowledgeBaseId: String? = null) {
        currentKnowledgeBaseId = knowledgeBaseId ?: "default"
        loadDecksJob?.cancel()
        loadDecksJob = screenModelScope.launch {
            _isLoading.value = true
            combine(deckRepository.getAll(), _sortConfig) { deckList, sort ->
                val filtered = if (knowledgeBaseId != null) {
                    deckList.filter { it.knowledgeBaseId == knowledgeBaseId && it.deletedAt == null }
                } else {
                    deckList.filter { it.deletedAt == null }
                }
                sortDecks(filtered, sort)
            }.collect { sorted ->
                _decks.update { sorted }
                _isLoading.value = false
                val allCards = cardRepository.getAll().first()
                _deckCardCounts.update { allCards.groupBy { it.deckId }.mapValues { it.value.size } }
            }
        }
    }

    private fun sortDecks(decks: List<Deck>, config: SortConfig): List<Deck> {
        val sorted = when (config.field) {
            SortField.NAME -> decks.sortedBy { it.name.lowercase() }
            SortField.CREATED_AT -> decks.sortedBy { it.createdAt }
            SortField.UPDATED_AT -> decks.sortedBy { it.updatedAt }
            SortField.STUDY_TIME -> decks
        }
        return if (config.order == SortOrder.DESC) sorted.reversed() else sorted
    }

    suspend fun createDeck(name: String, description: String?) {
        val existingCount = _decks.value.size
        val deck = Deck(
            id = "deck_${UUID.randomUUID().toString().take(8)}",
            knowledgeBaseId = currentKnowledgeBaseId,
            name = name,
            description = description,
            color = deckColors[existingCount % deckColors.size],
            icon = deckIcons[existingCount % deckIcons.size],
            createdAt = kotlin.time.Clock.System.now(),
            updatedAt = kotlin.time.Clock.System.now()
        )
        deckRepository.insert(deck)
    }

    suspend fun updateDeck(id: String, name: String, description: String?) {
        val deck = deckRepository.getById(id) ?: return
        val updated = deck.copy(
            name = name,
            description = description,
            updatedAt = kotlin.time.Clock.System.now()
        )
        deckRepository.update(updated)
    }

    suspend fun deleteDeck(id: String) {
        deckRepository.delete(id)
        val plans = planRepository.getAll().first()
        for (plan in plans) {
            if (id in plan.deckIds) {
                planRepository.update(plan.copy(
                    deckIds = plan.deckIds - id,
                    updatedAt = kotlin.time.Clock.System.now()
                ))
            }
        }
    }

    suspend fun getDeckById(id: String): Deck? = deckRepository.getById(id)

    companion object {
        val deckColors get() = Deck.colors
        val deckIcons get() = Deck.icons
    }
}
