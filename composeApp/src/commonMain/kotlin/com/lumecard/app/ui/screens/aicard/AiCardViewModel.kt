package com.lumecard.app.ui.screens.aicard

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.data.*
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.KnowledgeBaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val aiCardGenerator: AiCardGenerator,
    private val aiConfigManager: AiConfigManager,
    private val promptManager: AiCardPromptManager,
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val deckRepository: DeckRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(AiCardUiState())
    val state: StateFlow<AiCardUiState> = _state.asStateFlow()

    init {
        val cached = draftCache
        if (cached != null) {
            _state.update {
                it.copy(
                    mode = cached.mode,
                    selectedKbId = cached.selectedKbId,
                    selectedDeckId = cached.selectedDeckId,
                    selectedConfigId = cached.selectedConfigId,
                    topic = cached.topic,
                    referenceMaterials = cached.referenceMaterials,
                    cardCount = cached.cardCount,
                    prompt = cached.prompt,
                    autoClassifyDecks = cached.autoClassifyDecks,
                )
            }
        }
        loadInitialData()
    }

    private data class Draft(
        val mode: AiCardMode,
        val selectedKbId: String?,
        val selectedDeckId: String?,
        val selectedConfigId: String?,
        val topic: String,
        val referenceMaterials: String,
        val cardCount: Int,
        val prompt: String,
        val autoClassifyDecks: Boolean,
    )

    private companion object {
        private var draftCache: Draft? = null
    }

    private fun cacheDraft(state: AiCardUiState) {
        draftCache = Draft(
            mode = state.mode,
            selectedKbId = state.selectedKbId,
            selectedDeckId = state.selectedDeckId,
            selectedConfigId = state.selectedConfigId,
            topic = state.topic,
            referenceMaterials = state.referenceMaterials,
            cardCount = state.cardCount,
            prompt = state.prompt,
            autoClassifyDecks = state.autoClassifyDecks,
        )
    }

    private fun loadInitialData() {
        screenModelScope.launch {
            try {
                val (kbs, dbPrompt, configs) = withContext(Dispatchers.IO) {
                    val kbList = knowledgeBaseRepository.getAll().first()
                    val p = promptManager.getActivePrompt()
                    val cfgs = aiConfigManager.getAll()
                    Triple(kbList, p, cfgs)
                }
                val defaultConfig = configs.firstOrNull { it.isDefault }
                _state.update {
                    val cached = draftCache
                    it.copy(
                        knowledgeBases = kbs,
                        hasKb = kbs.isNotEmpty(),
                        prompt = cached?.prompt ?: dbPrompt,
                        allConfigs = configs,
                        selectedConfigId = cached?.selectedConfigId ?: defaultConfig?.id,
                        configError = configs.isEmpty(),
                    )
                }
                val needsDeck = draftCache?.let { it.mode == AiCardMode.SPECIFY_BOTH && it.selectedKbId != null } ?: false
                if (needsDeck) loadDecks()
            } catch (_: Exception) { }
        }
    }

    fun setMode(mode: AiCardMode) {
        _state.update { it.copy(mode = mode, selectedDeckId = null, result = null, errorMessage = null, screenState = AiCardScreenState.IDLE) }
        cacheDraft(_state.value)
        if (mode == AiCardMode.SPECIFY_KB || mode == AiCardMode.SPECIFY_BOTH) {
            loadDecks()
        }
    }

    fun selectKb(kbId: String) {
        _state.update { it.copy(selectedKbId = kbId, selectedDeckId = null, result = null, errorMessage = null, screenState = AiCardScreenState.IDLE) }
        cacheDraft(_state.value)
        if (_state.value.mode == AiCardMode.SPECIFY_BOTH) {
            loadDecks()
        }
    }

    fun selectDeck(deckId: String) {
        _state.update { it.copy(selectedDeckId = deckId, result = null, errorMessage = null, screenState = AiCardScreenState.IDLE) }
        cacheDraft(_state.value)
    }

    private fun loadDecks() {
        screenModelScope.launch {
            val kbId = _state.value.selectedKbId ?: return@launch
            try {
                val decks = withContext(Dispatchers.IO) {
                    deckRepository.getByKnowledgeBase(kbId).first()
                }
                _state.update { it.copy(decks = decks, hasDeck = decks.isNotEmpty()) }
            } catch (_: Exception) { }
        }
    }

    fun selectConfig(configId: String) {
        _state.update { it.copy(selectedConfigId = configId, result = null, errorMessage = null, screenState = AiCardScreenState.IDLE) }
        cacheDraft(_state.value)
    }

    fun setTopic(text: String) {
        _state.update { it.copy(topic = text, result = null, errorMessage = null, screenState = AiCardScreenState.IDLE) }
        cacheDraft(_state.value)
    }

    fun setReferenceMaterials(text: String) {
        _state.update { it.copy(referenceMaterials = text, result = null, errorMessage = null, screenState = AiCardScreenState.IDLE) }
        cacheDraft(_state.value)
    }

    fun appendReferenceMaterials(text: String) {
        val current = _state.value.referenceMaterials
        val separator = if (current.isBlank() || current.endsWith("\n")) "" else "\n\n"
        _state.update { it.copy(referenceMaterials = current + separator + text, result = null, errorMessage = null, screenState = AiCardScreenState.IDLE) }
        cacheDraft(_state.value)
    }

    fun setCardCount(count: Int) {
        _state.update { it.copy(cardCount = count.coerceIn(1, 1000)) }
        cacheDraft(_state.value)
    }

    fun setAutoClassifyDecks(value: Boolean) {
        _state.update { it.copy(autoClassifyDecks = value) }
    }

    fun setPrompt(prompt: String) {
        _state.update { it.copy(prompt = prompt) }
        cacheDraft(_state.value)
    }

    fun restoreDefaultPrompt() {
        screenModelScope.launch {
            try {
                val defaultPrompt = withContext(Dispatchers.IO) { promptManager.resetToDefault() }
                _state.update { it.copy(prompt = defaultPrompt) }
                cacheDraft(_state.value)
            } catch (_: Exception) { }
        }
    }

    fun savePrompt() {
        screenModelScope.launch {
            try {
                withContext(Dispatchers.IO) { promptManager.savePrompt(_state.value.prompt) }
            } catch (_: Exception) { }
        }
    }

    fun generate() {
        val current = _state.value
        if (current.configError || current.selectedConfigId == null) {
            _state.value = current.copy(screenState = AiCardScreenState.ERROR, errorMessage = "请先配置 AI 服务")
            return
        }
        if (current.topic.isBlank()) {
            _state.value = current.copy(screenState = AiCardScreenState.ERROR, errorMessage = "请输入制卡主题")
            return
        }

        _state.value = current.copy(screenState = AiCardScreenState.GENERATING, errorMessage = null, result = null)

        val configId = current.selectedConfigId
        val cardCount = current.cardCount
        val mode = current.mode
        val topic = current.topic
        val prompt = current.prompt
        val refMaterials = current.referenceMaterials
        val kbId = current.selectedKbId
        val deckId = current.selectedDeckId
        val autoClassifyDecks = current.autoClassifyDecks

        screenModelScope.launch {
            try {
                val config = withContext(Dispatchers.IO) { aiConfigManager.getById(configId) }
                if (config == null) {
                    _state.value = _state.value.copy(screenState = AiCardScreenState.ERROR, errorMessage = "请先配置 AI 服务")
                    return@launch
                }

                val batchSize = 5
                var remaining = cardCount
                var totalCreated = 0
                val allCardIds = mutableListOf<String>()
                var lastKbId = ""
                var lastDeckId = ""

                while (remaining > 0) {
                    val currentBatch = (cardCount - remaining) / batchSize + 1
                    val totalBatches = (cardCount + batchSize - 1) / batchSize
                    _state.value = _state.value.copy(
                        batchProgress = AiCardBatchProgress(
                            currentBatch = currentBatch,
                            totalBatches = totalBatches,
                            savedCards = totalCreated,
                            totalTarget = cardCount,
                            status = "生成中",
                        ),
                    )

                    val batchCount = batchSize.coerceAtMost(remaining)
                    val requestKbId = if (lastKbId.isNotEmpty()) lastKbId else kbId
                    val requestDeckId = if (!autoClassifyDecks && lastDeckId.isNotEmpty()) lastDeckId else deckId
                    val request = AiCardRequest(
                        config = config,
                        mode = mode,
                        knowledgeBaseId = requestKbId,
                        deckId = requestDeckId,
                        topic = topic,
                        referenceMaterials = refMaterials,
                        cardCount = batchCount,
                        systemPrompt = prompt,
                    )

                    val result = withContext(Dispatchers.IO) {
                        aiCardGenerator.generate(request) { received, total ->
                            _state.value = _state.value.copy(downloadProgress = received to total)
                        }
                    }

                    result.fold(
                        onSuccess = { res ->
                            remaining -= batchCount
                            totalCreated += res.cardsCreated
                            allCardIds.addAll(res.cardIds)
                            lastKbId = res.knowledgeBaseId
                            lastDeckId = res.deckId
                        },
                        onFailure = { e ->
                            _state.value = _state.value.copy(
                                screenState = AiCardScreenState.ERROR,
                                batchProgress = null,
                                errorMessage = "第 $currentBatch 批生成失败：${e.message ?: "未知错误"}",
                            )
                            return@launch
                        },
                    )
                }

                _state.value = _state.value.copy(
                    screenState = AiCardScreenState.DONE,
                    batchProgress = null,
                    result = AiCardResult(
                        knowledgeBaseName = "AI Cards",
                        deckName = "AI Cards",
                        knowledgeBaseId = lastKbId,
                        deckId = lastDeckId,
                        cardsCreated = totalCreated,
                        cardIds = allCardIds,
                    ),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    screenState = AiCardScreenState.ERROR,
                    batchProgress = null,
                    errorMessage = "生成失败：${e.message ?: "未知错误"}",
                )
            }
        }
    }

    fun resetState() {
        draftCache = null
        _state.update { it.copy(screenState = AiCardScreenState.IDLE, result = null, errorMessage = null, batchProgress = null) }
    }

}

