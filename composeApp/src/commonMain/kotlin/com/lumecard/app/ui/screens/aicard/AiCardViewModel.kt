package com.lumecard.app.ui.screens.aicard

import cafe.adriel.voyager.core.model.ScreenModel
import com.lumecard.app.data.AiCardGenerationManager
import com.lumecard.shared.data.*
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import kotlinx.coroutines.flow.StateFlow

enum class AiCardScreenState {
    IDLE,
    GENERATING,
    DONE,
    ERROR,
}

data class AiCardUiState(
    val screenState: AiCardScreenState = AiCardScreenState.IDLE,
    val mode: AiCardMode = AiCardMode.AUTO,
    val knowledgeBases: List<KnowledgeBase> = emptyList(),
    val decks: List<Deck> = emptyList(),
    val selectedKbId: String? = null,
    val selectedDeckId: String? = null,
    val allConfigs: List<AiConfig> = emptyList(),
    val selectedConfigId: String? = null,
    val topic: String = "",
    val referenceMaterials: String = "",
    val cardCount: Int = 10,
    val prompt: String = "",
    val result: AiCardResult? = null,
    val errorMessage: String? = null,
    val configError: Boolean = false,
    val hasKb: Boolean = false,
    val hasDeck: Boolean = false,
    val downloadProgress: Pair<Long, Long?>? = null,
    val batchProgress: AiCardBatchProgress? = null,
    val autoClassifyDecks: Boolean = false,
)

data class AiCardBatchProgress(
    val currentBatch: Int,
    val totalBatches: Int,
    val savedCards: Int,
    val totalTarget: Int,
    val status: String,
)

class AiCardViewModel(
    private val manager: AiCardGenerationManager,
) : ScreenModel {

    val state: StateFlow<AiCardUiState> = manager.state

    init {
        manager.loadInitialData()
    }

    fun setMode(mode: AiCardMode) = manager.setMode(mode)
    fun selectKb(kbId: String) = manager.selectKb(kbId)
    fun selectDeck(deckId: String) = manager.selectDeck(deckId)
    fun selectConfig(configId: String) = manager.selectConfig(configId)
    fun setTopic(text: String) = manager.setTopic(text)
    fun setReferenceMaterials(text: String) = manager.setReferenceMaterials(text)
    fun appendReferenceMaterials(text: String) = manager.appendReferenceMaterials(text)
    fun setCardCount(count: Int) = manager.setCardCount(count)
    fun setAutoClassifyDecks(value: Boolean) = manager.setAutoClassifyDecks(value)
    fun setPrompt(prompt: String) = manager.setPrompt(prompt)
    fun restoreDefaultPrompt() = manager.restoreDefaultPrompt()
    fun savePrompt() = manager.savePrompt()
    fun generate() = manager.generate()
    fun resetState() = manager.resetState()
}
