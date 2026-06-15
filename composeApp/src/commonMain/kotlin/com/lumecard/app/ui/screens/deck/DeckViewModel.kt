package com.lumecard.app.ui.screens.deck

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.model.Deck
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val settingsRepository: SettingsRepository
) : ScreenModel {

    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sortConfig = MutableStateFlow(SortConfig())
    val sortConfig: StateFlow<SortConfig> = _sortConfig.asStateFlow()

    private val _deckCardCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val deckCardCounts: StateFlow<Map<String, Int>> = _deckCardCounts.asStateFlow()

    init {
        loadSortPref()
        loadDecks()
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

    fun loadDecks() {
        screenModelScope.launch {
            _isLoading.value = true
            combine(deckRepository.getAll(), _sortConfig) { deckList, sort ->
                sortDecks(deckList, sort)
            }.collect { sorted ->
                _decks.value = sorted
                _isLoading.value = false
                val allCards = cardRepository.getAll().first()
                _deckCardCounts.value = allCards.groupBy { it.deckId }.mapValues { it.value.size }
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
        val existingDecks = deckRepository.getAll().let { flow ->
            var result: List<Deck> = emptyList()
            flow.collect { result = it; return@collect }
            result
        }
        val deck = Deck(
            id = "deck_${UUID.randomUUID().toString().take(8)}",
            knowledgeBaseId = "default",
            name = name,
            description = description,
            color = deckColors[existingDecks.size % deckColors.size],
            icon = deckIcons[existingDecks.size % deckIcons.size],
            createdAt = kotlinx.datetime.Clock.System.now(),
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
        deckRepository.insert(deck)
    }

    suspend fun updateDeck(id: String, name: String, description: String?) {
        val deck = deckRepository.getById(id) ?: return
        val updated = deck.copy(
            name = name,
            description = description,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
        deckRepository.update(updated)
    }

    suspend fun deleteDeck(id: String) {
        deckRepository.delete(id)
    }

    suspend fun getDeckById(id: String): Deck? = deckRepository.getById(id)

    companion object {
        val deckColors = listOf("#4CAF50", "#2196F3", "#FF9800", "#E91E63", "#9C27B0", "#00BCD4", "#FF5722", "#607D8B")
        val deckIcons = listOf("📚", "🎓", "💡", "🌟", "🎯", "📝", "🔬", "🎨")
    }
}
