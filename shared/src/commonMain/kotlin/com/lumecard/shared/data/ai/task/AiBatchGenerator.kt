package com.lumecard.shared.data.ai.task

import com.lumecard.shared.data.AiCardGenerator
import com.lumecard.shared.data.AiCardRequest
import com.lumecard.shared.data.AiCardResult
import com.lumecard.shared.data.ai.event.*
import com.lumecard.shared.data.ai.progress.AiProgressManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock

class AiBatchGenerator(
    private val cardGenerator: AiCardGenerator,
    private val eventBus: AiEventBus,
) {
    private val _taskState = MutableStateFlow(AiTaskState())
    val taskState: StateFlow<AiTaskState> = _taskState.asStateFlow()

    private var currentJob: Job? = null
    private val cancelSignal = CompletableDeferred<Unit>()

    private val progressManager = AiProgressManager()
    private val stateMachine = AiTaskStateMachine()
    private var totalCardsCreated = 0
    private var allCardIds = mutableListOf<String>()
    private var resolvedKbId: String? = null
    private var resolvedDeckId: String? = null

    fun startTask(task: AiCardTask, scope: CoroutineScope) {
        if (currentJob?.isActive == true) return
        reset()
        progressManager.onStarted(task.totalTargetCount, calculateBatchCount(task.totalTargetCount, task.batchSize))
        _taskState.value = progressManager.state

        currentJob = scope.launch {
            try {
                runGeneration(task)
            } catch (e: kotlinx.coroutines.CancellationException) {
                onCancelled()
            } catch (e: Exception) {
                progressManager.onFailed(listOf(e.message ?: "Unknown error"))
                eventBus.emit(Error(
                    sessionId = task.sessionId,
                    timestamp = Clock.System.now(),
                    message = e.message ?: "Unknown error",
                    code = "GENERATION_FAILED",
                ))
            } finally {
                _taskState.value = progressManager.state
            }
        }
    }

    fun cancel() {
        cancelSignal.complete(Unit)
        currentJob?.cancel()
        stateMachine.transition(AiTaskStatus.CANCELLED)
    }

    private suspend fun runGeneration(task: AiCardTask) {
        val sessionId = task.sessionId
        var remainingCards = task.totalTargetCount
        var batchIndex = 0

        eventBus.emit(SessionStarted(
            sessionId = sessionId,
            timestamp = Clock.System.now(),
            config = task.config,
            cardCount = task.totalTargetCount,
            batchSize = task.batchSize,
        ))

        while (remainingCards > 0 && isActive()) {
            checkCancelled()
            val currentBatchSize = task.batchSize.coerceAtMost(remainingCards)

            stateMachine.transition(AiTaskStatus.CONNECTING)
            progressManager.onStatusChanged(AiTaskStatus.CONNECTING)
            progressManager.onBatchProgress(batchIndex, calculateBatchCount(task.totalTargetCount, task.batchSize))
            _taskState.value = progressManager.state

            eventBus.emit(RequestStarted(
                sessionId = sessionId,
                timestamp = Clock.System.now(),
                batchIndex = batchIndex,
            ))

            val batchRequest = task.copy(
                totalTargetCount = currentBatchSize,
                userMessage = buildBatchPrompt(task, currentBatchSize, batchIndex),
            )

            stateMachine.transition(AiTaskStatus.STREAMING)
            progressManager.onStatusChanged(AiTaskStatus.STREAMING)
            _taskState.value = progressManager.state

            val result = executeBatch(batchRequest, sessionId, batchIndex)

            result.fold(
                onSuccess = { batchResult ->
                    remainingCards -= currentBatchSize
                    totalCardsCreated += batchResult.cardsCreated
                    allCardIds.addAll(batchResult.cardIds)
                    if (resolvedKbId == null) resolvedKbId = batchResult.knowledgeBaseId
                    if (resolvedDeckId == null) resolvedDeckId = batchResult.deckId

                    progressManager.onBatchProgress(batchIndex, calculateBatchCount(task.totalTargetCount, task.batchSize))
                    progressManager.onCardSaved(totalCardsCreated)
                    _taskState.value = progressManager.state

                    eventBus.emit(BatchProgress(
                        sessionId = sessionId,
                        timestamp = Clock.System.now(),
                        batchIndex = batchIndex,
                        batchTotal = currentBatchSize,
                        overallSaved = totalCardsCreated,
                        overallTarget = task.totalTargetCount,
                    ))
                },
                onFailure = { e ->
                    progressManager.addError(e.message ?: "Batch failed")
                    eventBus.emit(Error(
                        sessionId = sessionId,
                        timestamp = Clock.System.now(),
                        message = "Batch $batchIndex failed: ${e.message}",
                        code = "BATCH_FAILED",
                        recoverable = true,
                    ))
                    break
                },
            )

            batchIndex++
        }

        if (isActive()) {
            stateMachine.transition(AiTaskStatus.COMPLETED)
            progressManager.onCompleted()
            _taskState.value = progressManager.state

            eventBus.emit(Completed(
                sessionId = sessionId,
                timestamp = Clock.System.now(),
                totalCardsCreated = totalCardsCreated,
                totalBatches = batchIndex,
                elapsedSeconds = progressManager.state.elapsedSeconds,
            ))
        } else {
            onCancelled()
        }
    }

    private suspend fun executeBatch(
        request: AiCardTask,
        sessionId: String,
        batchIndex: Int,
    ): Result<AiCardResult> {
        return try {
            val aiRequest = AiCardRequest(
                config = request.config,
                mode = request.mode,
                knowledgeBaseId = request.knowledgeBaseId,
                deckId = request.deckId,
                topic = request.userMessage,
                referenceMaterials = "",
                cardCount = request.totalTargetCount,
                systemPrompt = request.systemPrompt,
                knowledgeBaseName = null,
                deckName = null,
            )

            val collectedLogs = mutableListOf<com.lumecard.shared.data.LogEntry>()
            val result = cardGenerator.generate(
                aiRequest,
                onLog = { entry ->
                    collectedLogs.add(entry)
                    stateMachine.update { it.copy(logEntries = it.logEntries + entry) }
                    _taskState.value = stateMachine.state
                },
            )
            val now = Clock.System.now()
            for (entry in collectedLogs) {
                when (entry.type) {
                    com.lumecard.shared.data.LogEntryType.WARNING ->
                        eventBus.emit(Warning(sessionId, now, entry.content, entry.title))
                    com.lumecard.shared.data.LogEntryType.ERROR ->
                        eventBus.emit(Error(sessionId, now, entry.content, entry.title))
                    else -> {}
                }
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun isActive(): Boolean {
        return currentJob?.isActive == true && !cancelSignal.isCompleted
    }

    private suspend fun checkCancelled() {
        if (cancelSignal.isCompleted) throw kotlinx.coroutines.CancellationException("Task cancelled")
    }

    private suspend fun onCancelled() {
        stateMachine.transition(AiTaskStatus.CANCELLED)
        progressManager.onCancelled()
        _taskState.value = progressManager.state
        eventBus.emit(Cancelled(
            sessionId = "",
            timestamp = Clock.System.now(),
            savedCards = totalCardsCreated,
        ))
    }

    private fun buildBatchPrompt(task: AiCardTask, batchSize: Int, batchIndex: Int): String {
        val base = task.userMessage
        val totalCards = task.totalTargetCount
        val startCard = batchIndex * task.batchSize + 1
        val endCard = (startCard + batchSize - 1).coerceAtMost(totalCards)
        return "$base\n\n(Batch $batchIndex: generating cards $startCard to $endCard of $totalCards - generate exactly $batchSize cards)"
    }

    private fun calculateBatchCount(totalCards: Int, batchSize: Int): Int {
        return (totalCards + batchSize - 1) / batchSize
    }

    private fun reset() {
        totalCardsCreated = 0
        allCardIds.clear()
        resolvedKbId = null
        resolvedDeckId = null
        stateMachine.reset()
        progressManager.reset()
        _taskState.value = progressManager.state
    }
}
