package com.lumecard.app.ui.screens.card

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.app.ui.screens.deck.SortConfig
import com.lumecard.app.ui.screens.deck.SortField
import com.lumecard.app.ui.screens.deck.SortOrder
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class CardViewModel(
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository
) : ScreenModel {
    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _sortConfig = MutableStateFlow(SortConfig())
    val sortConfig: StateFlow<SortConfig> = _sortConfig

    private var currentDeckId: String? = null
    private var loadCardsJob: kotlinx.coroutines.Job? = null

    fun loadSortPref() {
        screenModelScope.launch {
            val field = settingsRepository.get("card_sort_field") ?: SortField.CREATED_AT.name
            val order = settingsRepository.get("card_sort_order") ?: SortOrder.DESC.name
            _sortConfig.value = SortConfig(
                field = try { SortField.valueOf(field) } catch (_: Exception) { SortField.CREATED_AT },
                order = try { SortOrder.valueOf(order) } catch (_: Exception) { SortOrder.DESC }
            )
        }
    }

    fun setSortConfig(config: SortConfig) {
        _sortConfig.value = config
        screenModelScope.launch {
            settingsRepository.set("card_sort_field", config.field.name)
            settingsRepository.set("card_sort_order", config.order.name)
        }
    }

    fun loadCards(deckId: String) {
        currentDeckId = deckId
        loadCardsJob?.cancel()
        loadCardsJob = screenModelScope.launch {
            _isLoading.value = true
            combine(cardRepository.getByDeck(deckId), _sortConfig) { cards, sort ->
                sortCards(cards, sort)
            }.collect { sorted ->
                _cards.update { sorted }
                _isLoading.value = false
            }
        }
    }

    private fun sortCards(cards: List<Card>, config: SortConfig): List<Card> {
        val sorted = when (config.field) {
            SortField.NAME -> cards.sortedBy { it.front.lowercase() }
            SortField.CREATED_AT -> cards.sortedBy { it.createdAt }
            SortField.UPDATED_AT -> cards.sortedBy { it.updatedAt }
            SortField.STUDY_TIME -> cards
        }
        return if (config.order == SortOrder.DESC) sorted.reversed() else sorted
    }

    fun getCardCount(deckId: String, onResult: (Int) -> Unit) {
        screenModelScope.launch {
            val count = cardRepository.getByDeck(deckId).first().size
            onResult(count)
        }
    }

    fun createCard(
        deckId: String,
        front: String,
        back: String,
        type: CardType = CardType.BASIC,
        tags: List<String> = emptyList(),
        horizontalCenter: Boolean = false,
        verticalCenter: Boolean = false,
        fontSize: Int = 16,
        fontFamily: String = "",
    ) {
        if (front.isBlank() || back.isBlank()) return

        screenModelScope.launch {
            val card = Card(
                id = kotlin.uuid.Uuid.random().toString(),
                deckId = deckId,
                type = type,
                front = front,
                back = back,
                tags = tags,
                metadata = mutableMapOf<String, String>().apply {
                    if (horizontalCenter) put("hcenter", "true")
                    if (verticalCenter) put("vcenter", "true")
                    put("fontSize", fontSize.toString())
                    if (fontFamily.isNotBlank()) put("fontFamily", fontFamily)
                }
            )
            cardRepository.insert(card)
        }
    }

    fun updateCard(
        card: Card,
        front: String,
        back: String,
        type: CardType,
        tags: List<String>,
        horizontalCenter: Boolean = false,
        verticalCenter: Boolean = false,
        fontSize: Int = 16,
        fontFamily: String = "",
    ) {
        screenModelScope.launch {
            val metadata = card.metadata + mutableMapOf<String, String>().apply {
                if (horizontalCenter) put("hcenter", "true")
                if (verticalCenter) put("vcenter", "true")
                put("fontSize", fontSize.toString())
                if (fontFamily.isNotBlank()) put("fontFamily", fontFamily) else remove("fontFamily")
            }
            val updated = card.copy(
                front = front,
                back = back,
                type = type,
                tags = tags,
                metadata = metadata,
                updatedAt = Clock.System.now()
            )
            cardRepository.update(updated)
        }
    }

    fun deleteCard(id: String) {
        screenModelScope.launch {
            cardRepository.delete(id)
        }
    }
}
