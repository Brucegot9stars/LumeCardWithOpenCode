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
import kotlinx.datetime.Clock
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
        val kbs = cachedKBs
        val decks = cachedDecks
        val cards = cachedCards

        val tree = kbs.filter { kb ->
            kb.deletedAt == null && (query.isEmpty() || kb.name.lowercase().contains(query))
        }.map { kb ->
            val kbDecks = decks.filter { d ->
                d.knowledgeBaseId == kb.id && d.deletedAt == null &&
                (query.isEmpty() || d.name.lowercase().contains(query))
            }
            val kbCards = cards.filter { c ->
                c.deckId !in kbDecks.map { it.id } && c.deletedAt == null &&
                query.isNotEmpty() && (c.front.lowercase().contains(query) || c.back.lowercase().contains(query))
            }
            TreeNode(
                id = kb.id,
                name = kb.name,
                type = NodeType.KNOWLEDGE_BASE,
                isExpanded = kb.id in _expandedIds.value,
                children = kbDecks.map { deck ->
                    val deckCards = cards.filter { it.deckId == deck.id && it.deletedAt == null }
                    val filteredCards = if (query.isNotEmpty()) {
                        deckCards.filter { it.front.lowercase().contains(query) || it.back.lowercase().contains(query) }
                    } else {
                        deckCards
                    }
                    TreeNode(
                        id = deck.id,
                        name = deck.name,
                        type = NodeType.DECK,
                        isExpanded = deck.id in _expandedIds.value,
                        children = filteredCards.map { card ->
                            TreeNode(
                                id = card.id,
                                name = card.front.take(50),
                                type = NodeType.CARD,
                                data = card
                            )
                        }
                    )
                } + kbCards.map { card ->
                    TreeNode(id = card.id, name = card.front.take(50), type = NodeType.CARD, data = card)
                }
            )
        }
        _treeNodes.value = tree
    }

    fun toggleExpand(id: String) {
        _expandedIds.value = if (id in _expandedIds.value) _expandedIds.value - id else _expandedIds.value + id
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
        val icons = listOf("\uD83D\uDCDA", "\uD83C\uDF93", "\uD83D\uDCA1", "\uD83C\uDF1F", "\uD83C\uDFAF", "\uD83D\uDCDD", "\uD83D\uDD2C", "\uD83C\uDFA8")
        val colors = listOf("#4CAF50", "#2196F3", "#FF9800", "#E91E63", "#9C27B0", "#00BCD4", "#FF5722", "#607D8B")
        deckRepository.insert(Deck(
            id = "deck_${UUID.randomUUID().toString().take(8)}",
            knowledgeBaseId = kbId, name = name, description = description,
            color = colors[existingCount % colors.size], icon = icons[existingCount % icons.size],
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
