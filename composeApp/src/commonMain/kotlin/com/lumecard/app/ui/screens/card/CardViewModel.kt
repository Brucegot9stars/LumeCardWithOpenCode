package com.lumecard.app.ui.screens.card

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class CardViewModel(
    private val cardRepository: CardRepository
) : ScreenModel {
    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _front = MutableStateFlow("")
    val front: StateFlow<String> = _front

    private val _back = MutableStateFlow("")
    val back: StateFlow<String> = _back

    private val _cardType = MutableStateFlow(CardType.BASIC)
    val cardType: StateFlow<CardType> = _cardType

    private val _tags = MutableStateFlow("")
    val tags: StateFlow<String> = _tags

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    fun loadCards(deckId: String) {
        screenModelScope.launch {
            _isLoading.value = true
            cardRepository.getByDeck(deckId).collect { cards ->
                _cards.value = cards
                _isLoading.value = false
            }
        }
    }

    fun updateFront(value: String) {
        _front.value = value
    }

    fun updateBack(value: String) {
        _back.value = value
    }

    fun updateCardType(value: CardType) {
        _cardType.value = value
    }

    fun updateTags(value: String) {
        _tags.value = value
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
        }
    }

    fun deleteCard(id: String) {
        screenModelScope.launch {
            cardRepository.delete(id)
        }
    }

    fun saveCard(deckId: String, onSuccess: () -> Unit) {
        if (_front.value.isBlank() || _back.value.isBlank()) return

        screenModelScope.launch {
            _isSaving.value = true
            try {
                val card = Card(
                    id = kotlin.uuid.Uuid.random().toString(),
                    deckId = deckId,
                    type = _cardType.value,
                    front = _front.value,
                    back = _back.value,
                    tags = _tags.value.split(",").map { it.trim() }.filter { it.isNotBlank() }
                )
                cardRepository.insert(card)
                onSuccess()
            } finally {
                _isSaving.value = false
            }
        }
    }
}
