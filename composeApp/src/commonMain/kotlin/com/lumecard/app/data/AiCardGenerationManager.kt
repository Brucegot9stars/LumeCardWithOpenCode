package com.lumecard.app.data

import com.lumecard.app.i18n.AppLocale
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.screens.aicard.AiCardBatchProgress
import com.lumecard.app.ui.screens.aicard.AiCardScreenState
import com.lumecard.app.ui.screens.aicard.AiCardUiState
import com.lumecard.shared.data.*
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.KnowledgeBaseRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

private fun AppLocale.toPromptLanguage(): String = when (this) {
    AppLocale.ZH_CN, AppLocale.ZH_TW -> "中文"
    AppLocale.EN -> "English"
    AppLocale.JA -> "日本語"
    AppLocale.ES -> "Español"
    AppLocale.SYSTEM -> "中文"
}

class AiCardGenerationManager(
    private val aiCardGenerator: AiCardGenerator,
    private val aiConfigManager: AiConfigManager,
    private val promptManager: AiCardPromptManager,
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val deckRepository: DeckRepository,
    private val i18nManager: I18nManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AiCardUiState())
    val state: StateFlow<AiCardUiState> = _state.asStateFlow()

    private var generateJob: Job? = null

    private data class Draft(
        val mode: AiCardMode,
        val selectedKbId: String?,
        val selectedDeckId: String?,
        val selectedConfigId: String?,
        val topic: String,
        val referenceMaterials: String,
        val additionalRequirements: String,
        val cardCount: Int,
        val prompt: String,
        val autoClassifyDecks: Boolean,
    )

    private var draftCache: Draft? = null

    private fun cacheDraft(s: AiCardUiState) {
        draftCache = Draft(
            mode = s.mode,
            selectedKbId = s.selectedKbId,
            selectedDeckId = s.selectedDeckId,
            selectedConfigId = s.selectedConfigId,
            topic = s.topic,
            referenceMaterials = s.referenceMaterials,
            additionalRequirements = s.additionalRequirements,
            cardCount = s.cardCount,
            prompt = s.prompt,
            autoClassifyDecks = s.autoClassifyDecks,
        )
    }

    fun loadInitialData() {
        scope.launch {
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
                        mode = cached?.mode ?: it.mode,
                        selectedKbId = cached?.selectedKbId ?: it.selectedKbId,
                        selectedDeckId = cached?.selectedDeckId ?: it.selectedDeckId,
                        topic = cached?.topic ?: it.topic,
                        referenceMaterials = cached?.referenceMaterials ?: it.referenceMaterials,
                        additionalRequirements = cached?.additionalRequirements ?: it.additionalRequirements,
                        cardCount = cached?.cardCount ?: it.cardCount,
                        autoClassifyDecks = cached?.autoClassifyDecks ?: it.autoClassifyDecks,
                    )
                }
                val cur = _state.value
                if ((cur.mode == AiCardMode.SPECIFY_KB || cur.mode == AiCardMode.SPECIFY_BOTH) && cur.selectedKbId != null) {
                    loadDecks()
                }
            } catch (_: Exception) { }
        }
    }

    private suspend fun loadDecks() {
        val kbId = _state.value.selectedKbId ?: return
        try {
            val decks = withContext(Dispatchers.IO) {
                deckRepository.getByKnowledgeBase(kbId).first()
            }
            _state.update { it.copy(decks = decks, hasDeck = decks.isNotEmpty()) }
        } catch (_: Exception) { }
    }

    fun setMode(mode: AiCardMode) {
        _state.update { it.copy(mode = mode, selectedDeckId = null, result = null, errorMessage = null, screenState = AiCardScreenState.IDLE) }
        cacheDraft(_state.value)
        if (mode == AiCardMode.SPECIFY_KB || mode == AiCardMode.SPECIFY_BOTH) {
            scope.launch { loadDecks() }
        }
    }

    fun selectKb(kbId: String) {
        _state.update { it.copy(selectedKbId = kbId, selectedDeckId = null, result = null, errorMessage = null, screenState = AiCardScreenState.IDLE) }
        cacheDraft(_state.value)
        if (_state.value.mode == AiCardMode.SPECIFY_BOTH) {
            scope.launch { loadDecks() }
        }
    }

    fun selectDeck(deckId: String) {
        _state.update { it.copy(selectedDeckId = deckId, result = null, errorMessage = null, screenState = AiCardScreenState.IDLE) }
        cacheDraft(_state.value)
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

    fun setAdditionalRequirements(text: String) {
        _state.update { it.copy(additionalRequirements = text, result = null, errorMessage = null, screenState = AiCardScreenState.IDLE) }
        cacheDraft(_state.value)
    }

    fun setCardCount(count: Int) {
        _state.update { it.copy(cardCount = count.coerceIn(1, 1000)) }
        cacheDraft(_state.value)
    }

    fun setAutoClassifyDecks(value: Boolean) {
        _state.update { it.copy(autoClassifyDecks = value) }
        cacheDraft(_state.value)
    }

    fun setPrompt(prompt: String) {
        _state.update { it.copy(prompt = prompt) }
        cacheDraft(_state.value)
    }

    fun restoreDefaultPrompt() {
        scope.launch {
            try {
                val defaultPrompt = withContext(Dispatchers.IO) { promptManager.resetToDefault() }
                _state.update { it.copy(prompt = defaultPrompt) }
                cacheDraft(_state.value)
            } catch (_: Exception) { }
        }
    }

    fun savePrompt() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { promptManager.savePrompt(_state.value.prompt) }
            } catch (_: Exception) { }
        }
    }

    fun generate() {
        val current = _state.value
        if (current.screenState == AiCardScreenState.GENERATING) return

        if (current.configError || current.selectedConfigId == null) {
            _state.update { it.copy(screenState = AiCardScreenState.ERROR, errorMessage = "请先配置 AI 服务") }
            return
        }
        if (current.topic.isBlank()) {
            _state.update { it.copy(screenState = AiCardScreenState.ERROR, errorMessage = "请输入制卡主题") }
            return
        }

        _state.update { it.copy(screenState = AiCardScreenState.GENERATING, errorMessage = null, result = null, downloadProgress = null, batchProgress = null, logEntries = emptyList()) }

        val configId = current.selectedConfigId
        val cardCount = current.cardCount
        val mode = current.mode
        val topic = current.topic
        val prompt = current.prompt
        val refMaterials = current.referenceMaterials
        val addReqs = current.additionalRequirements
        val appLanguage = i18nManager.strings.let {
            val locale = if (i18nManager.currentLocale == AppLocale.SYSTEM) null else i18nManager.currentLocale
            (locale ?: AppLocale.ZH_CN).toPromptLanguage()
        }
        val kbId = current.selectedKbId
        val deckId = current.selectedDeckId
        val autoClassifyDecks = current.autoClassifyDecks

        generateJob = scope.launch {
            try {
                val config = withContext(Dispatchers.IO) { aiConfigManager.getById(configId) }
                if (config == null) {
                    _state.update { it.copy(screenState = AiCardScreenState.ERROR, errorMessage = "请先配置 AI 服务") }
                    return@launch
                }

                val batchSize = 5
                var remaining = cardCount
                var totalCreated = 0
                val allCardIds = mutableListOf<String>()
                var lastKbId = ""
                var lastDeckId = ""

                var logBatchIndex = 0
                while (remaining > 0) {
                    val currentBatch = (cardCount - remaining) / batchSize + 1
                    val totalBatches = (cardCount + batchSize - 1) / batchSize
                    val batchIdx = logBatchIndex++
                    _state.update { it.copy(logEntries = it.logEntries + LogEntry(0, LogEntryType.INFO, "Batch $currentBatch/$totalBatches", "Requesting ${batchSize.coerceAtMost(remaining)} cards (${totalCreated + allCardIds.size}/$cardCount so far)", batchIndex = batchIdx)) }
                    _state.update {
                        it.copy(
                            batchProgress = AiCardBatchProgress(
                                currentBatch = currentBatch,
                                totalBatches = totalBatches,
                                savedCards = totalCreated,
                                totalTarget = cardCount,
                                status = "生成中",
                            ),
                        )
                    }

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
                        additionalRequirements = addReqs,
                        cardCount = batchCount,
                        systemPrompt = prompt,
                        appLanguage = appLanguage,
                    )

                    val result = withContext(Dispatchers.IO) {
                        aiCardGenerator.generate(
                            request = request,
                            onProgress = { received, total ->
                                _state.update { it.copy(downloadProgress = received to total) }
                            },
                            onLog = { entry ->
                                _state.update { it.copy(logEntries = it.logEntries + entry) }
                            },
                        )
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
                            val msg = e.message ?: "未知错误"
                            val parts = msg.split("|||", limit = 2)
                            val shortMsg = parts[0]
                            val detailed = parts.getOrNull(1) ?: msg
                            _state.update {
                                it.copy(
                                    screenState = AiCardScreenState.ERROR,
                                    batchProgress = null,
                                    errorMessage = "第 $currentBatch 批生成失败：$shortMsg",
                                    detailedError = detailed,
                                )
                            }
                            return@launch
                        },
                    )
                }

                _state.update {
                    it.copy(
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
                }
                generateJob = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.update {
                    it.copy(
                        screenState = AiCardScreenState.IDLE,
                        batchProgress = null,
                        errorMessage = "已取消",
                        detailedError = null,
                    )
                }
                generateJob = null
            } catch (e: Exception) {
                val msg = e.message ?: "未知错误"
                val parts = msg.split("|||", limit = 2)
                _state.update {
                    it.copy(
                        screenState = AiCardScreenState.ERROR,
                        batchProgress = null,
                        errorMessage = "生成失败：${parts[0]}",
                        detailedError = parts.getOrNull(1) ?: msg,
                    )
                }
                generateJob = null
            }
        }
    }

    fun cancelGeneration() {
        generateJob?.cancel()
    }

    fun resetState() {
        generateJob = null
        draftCache = null
        _state.update { it.copy(screenState = AiCardScreenState.IDLE, result = null, errorMessage = null, detailedError = null, batchProgress = null, downloadProgress = null) }
    }

    fun clearDraftCache() {
        draftCache = null
    }
}
