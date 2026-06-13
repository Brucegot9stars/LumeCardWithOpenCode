package com.lumecard.app.ui.screens.card

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class CardViewModel(
    private val cardRepository: CardRepository
) : ScreenModel {
    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadCards(deckId: String) {
        screenModelScope.launch {
            _isLoading.value = true
            cardRepository.getByDeck(deckId).collect { cards ->
                _cards.value = cards
                _isLoading.value = false
            }
        }
    }

    fun createCard(
        deckId: String,
        front: String,
        back: String,
        type: CardType = CardType.BASIC,
        tags: List<String> = emptyList()
    ) {
        if (front.isBlank() || back.isBlank()) return

        screenModelScope.launch {
            val card = Card(
                id = kotlin.uuid.Uuid.random().toString(),
                deckId = deckId,
                type = type,
                front = front,
                back = back,
                tags = tags
            )
            cardRepository.insert(card)
            loadCards(deckId)
        }
    }

    fun updateCard(
        card: Card,
        front: String,
        back: String,
        type: CardType,
        tags: List<String>
    ) {
        screenModelScope.launch {
            val updated = card.copy(
                front = front,
                back = back,
                type = type,
                tags = tags,
                updatedAt = Clock.System.now()
            )
            cardRepository.update(updated)
            loadCards(card.deckId)
        }
    }

    fun deleteCard(id: String) {
        screenModelScope.launch {
            cardRepository.delete(id)
        }
    }
}
