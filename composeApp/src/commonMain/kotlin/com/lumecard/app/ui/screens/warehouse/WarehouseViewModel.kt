package com.lumecard.app.ui.screens.warehouse

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.model.*
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.KnowledgeBaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import java.util.UUID

data class TreeNode(
    val id: String,
    val name: String,
    val type: NodeType,
    val children: List<TreeNode> = emptyList(),
    val isExpanded: Boolean = false,
    val isSelected: Boolean = false,
    val data: Any? = null
)

enum class NodeType { KNOWLEDGE_BASE, DECK, CARD }

class WarehouseViewModel(
    private val kbRepository: KnowledgeBaseRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository
) : ScreenModel {

    private val _treeNodes = MutableStateFlow<List<TreeNode>>(emptyList())
    val treeNodes: StateFlow<List<TreeNode>> = _treeNodes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _expandedIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedIds: StateFlow<Set<String>> = _expandedIds.asStateFlow()

    private var cachedKBs: List<KnowledgeBase> = emptyList()
    private var cachedDecks: List<Deck> = emptyList()
    private var cachedCards: List<Card> = emptyList()
    private var searchJob: kotlinx.coroutines.Job? = null

    init {
        loadData()
    }

    fun loadData() {
        screenModelScope.launch {
            _isLoading.value = true
            cachedKBs = kbRepository.getAll().first()
            cachedDecks = deckRepository.getAll().first()
            cachedCards = cardRepository.getAll().first()
            rebuildTree()
            _isLoading.value = false
        }
    }

    private fun rebuildTree() {
        val query = _searchQuery.value.lowercase()
        val hasQuery = query.isNotEmpty()
        val kbs = cachedKBs.filter { it.deletedAt == null }
        val decks = cachedDecks.filter { it.deletedAt == null }
        val cards = cachedCards.filter { it.deletedAt == null }

        val tree = kbs.mapNotNull { kb ->
            val kbNameMatch = !hasQuery || kb.name.lowercase().contains(query)
            val kbDecks = decks.filter { it.knowledgeBaseId == kb.id }

            val deckNodes = kbDecks.mapNotNull { deck ->
                val deckNameMatch = !hasQuery || deck.name.lowercase().contains(query)
                val deckCards = cards.filter { it.deckId == deck.id }
                val matchedCards = if (hasQuery) {
                    deckCards.filter { it.front.lowercase().contains(query) || it.back.lowercase().contains(query) }
                } else deckCards

                val showDeck = !hasQuery || deckNameMatch || matchedCards.isNotEmpty()
                if (!showDeck) return@mapNotNull null

                val cardNodes = (if (hasQuery && !deckNameMatch && !kbNameMatch) matchedCards else deckCards).map { card ->
                    TreeNode(id = card.id, name = card.front.take(50), type = NodeType.CARD, data = card)
                }
                TreeNode(id = deck.id, name = deck.name, type = NodeType.DECK, isExpanded = if (hasQuery) cardNodes.isNotEmpty() else deck.id in _expandedIds.value, children = cardNodes)
            }

            if (!hasQuery || kbNameMatch || deckNodes.isNotEmpty()) {
                TreeNode(id = kb.id, name = kb.name, type = NodeType.KNOWLEDGE_BASE, isExpanded = if (hasQuery) deckNodes.isNotEmpty() else kb.id in _expandedIds.value, children = deckNodes)
            } else null
        }
        _treeNodes.value = tree
    }

    fun toggleExpand(id: String) {
        _expandedIds.value = if (id in _expandedIds.value) _expandedIds.value - id else _expandedIds.value + id
        rebuildTree()
    }

    fun expandAll() {
        _expandedIds.value = _treeNodes.value.flatMap { node ->
            listOf(node.id) + node.children.map { it.id }
        }.toSet()
        rebuildTree()
    }

    fun collapseAll() {
        _expandedIds.value = emptySet()
        rebuildTree()
    }

    fun toggleSelect(id: String) {
        _selectedIds.value = if (id in _selectedIds.value) _selectedIds.value - id else _selectedIds.value + id
    }

    fun selectAll() {
        val allIds = _treeNodes.value.flatMap { node ->
            listOf(node.id) + node.children.flatMap { listOf(it.id) + it.children.map { c -> c.id } }
        }
        _selectedIds.value = allIds.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = screenModelScope.launch {
            kotlinx.coroutines.delay(300)
            loadData()
        }
    }

    suspend fun createKnowledgeBase(name: String, description: String?) {
        kbRepository.insert(KnowledgeBase(
            id = "kb_${UUID.randomUUID().toString().take(8)}",
            name = name, description = description,
            createdAt = Clock.System.now(), updatedAt = Clock.System.now()
        ))
        loadData()
    }

    suspend fun updateKnowledgeBase(id: String, name: String, description: String?) {
        val kb = kbRepository.getById(id) ?: return
        kbRepository.update(kb.copy(name = name, description = description, updatedAt = Clock.System.now()))
        loadData()
    }

    suspend fun deleteKnowledgeBase(id: String) {
        kbRepository.delete(id)
        loadData()
    }

    suspend fun createDeck(kbId: String, name: String, description: String?) {
        val decks = deckRepository.getAll().first()
        val existingCount = decks.size
        deckRepository.insert(Deck(
            id = "deck_${UUID.randomUUID().toString().take(8)}",
            knowledgeBaseId = kbId, name = name, description = description,
            color = Deck.colors[existingCount % Deck.colors.size], icon = Deck.icons[existingCount % Deck.icons.size],
            createdAt = Clock.System.now(), updatedAt = Clock.System.now()
        ))
        _expandedIds.value = _expandedIds.value + kbId
        loadData()
    }

    suspend fun updateDeck(id: String, name: String, description: String?) {
        val deck = deckRepository.getById(id) ?: return
        deckRepository.update(deck.copy(name = name, description = description, updatedAt = Clock.System.now()))
        loadData()
    }

    suspend fun deleteDeck(id: String) {
        deckRepository.delete(id)
        loadData()
    }

    suspend fun createCard(deckId: String, front: String, back: String, type: CardType = CardType.BASIC) {
        cardRepository.insert(Card(
            id = "card_${UUID.randomUUID().toString().take(8)}",
            deckId = deckId, type = type, front = front, back = back,
            createdAt = Clock.System.now(), updatedAt = Clock.System.now()
        ))
        loadData()
    }

    suspend fun updateCard(id: String, front: String, back: String) {
        val card = cardRepository.getById(id) ?: return
        cardRepository.update(card.copy(front = front, back = back, updatedAt = Clock.System.now()))
        loadData()
    }

    suspend fun deleteCard(id: String) {
        cardRepository.delete(id)
        loadData()
    }

    suspend fun batchDelete() {
        val ids = _selectedIds.value
        for (id in ids) {
            when {
                _treeNodes.value.any { it.id == id && it.type == NodeType.KNOWLEDGE_BASE } -> kbRepository.delete(id)
                _treeNodes.value.any { it.type == NodeType.DECK && it.children.any { c -> c.id == id } || it.id == id } -> {
                    val allDecks = deckRepository.getAll().first()
                    if (allDecks.any { it.id == id }) deckRepository.delete(id)
                    else cardRepository.delete(id)
                }
                else -> cardRepository.delete(id)
            }
        }
        _selectedIds.value = emptySet()
        loadData()
    }
}
