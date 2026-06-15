package com.lumecard.app.ui.screens.dashboard

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.model.Deck
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DeckWithCount(
    val deck: Deck,
    val cardCount: Int
)

class DashboardViewModel(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository
) : ScreenModel {
    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks

    private val _decksWithCount = MutableStateFlow<List<DeckWithCount>>(emptyList())
    val decksWithCount: StateFlow<List<DeckWithCount>> = _decksWithCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadDecks()
    }

    private fun loadDecks() {
        screenModelScope.launch {
            _isLoading.value = true
            deckRepository.getAll().collect { deckList ->
                _decks.value = deckList
                val withCount = deckList.map { deck ->
                    val cards = cardRepository.getByDeck(deck.id).first()
                    DeckWithCount(deck, cards.size)
                }
                _decksWithCount.value = withCount
                _isLoading.value = false
            }
        }
    }
}
