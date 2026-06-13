package com.lumecard.app.ui.screens.deck

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.model.Deck
import com.lumecard.shared.repository.DeckRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class DeckViewModel(
    private val deckRepository: DeckRepository
) : ScreenModel {

    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadDecks()
    }

    fun loadDecks() {
        screenModelScope.launch {
            _isLoading.value = true
            deckRepository.getAll().collect { deckList ->
                _decks.value = deckList
                _isLoading.value = false
            }
        }
    }

    suspend fun createDeck(name: String, description: String?) {
        val existingDecks = deckRepository.getAll().first()
        val count = existingDecks.size
        val deck = Deck(
            id = "deck_${UUID.randomUUID().toString().take(8)}",
            knowledgeBaseId = "default",
            name = name,
            description = description,
            color = deckColors[count % deckColors.size],
            icon = deckIcons[count % deckIcons.size],
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
