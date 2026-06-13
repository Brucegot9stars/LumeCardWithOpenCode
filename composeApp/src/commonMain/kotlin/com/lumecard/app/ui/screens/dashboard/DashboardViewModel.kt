package com.lumecard.app.ui.screens.dashboard

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.model.Deck
import com.lumecard.shared.repository.DeckRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class DashboardViewModel(
    private val deckRepository: DeckRepository
) : ScreenModel, KoinComponent {
    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadDecks()
    }

    private fun loadDecks() {
        screenModelScope.launch {
            _isLoading.value = true
            deckRepository.getAll().collect { decks ->
                _decks.value = decks
                _isLoading.value = false
            }
        }
    }

    fun createDeck(name: String, description: String?) {
        screenModelScope.launch {
            val deck = Deck(
                id = kotlin.uuid.Uuid.random().toString(),
                knowledgeBaseId = "default",
                name = name,
                description = description
            )
            deckRepository.insert(deck)
        }
    }

    fun deleteDeck(id: String) {
        screenModelScope.launch {
            deckRepository.delete(id)
        }
    }
}
