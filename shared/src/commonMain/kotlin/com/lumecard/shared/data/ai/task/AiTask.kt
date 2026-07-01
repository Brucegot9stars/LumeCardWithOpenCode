package com.lumecard.shared.data.ai.task

import com.lumecard.shared.data.AiConfig
import com.lumecard.shared.data.LogEntry
import kotlin.time.Clock
import kotlin.time.Instant

enum class AiTaskStatus {
    PENDING,
    INITIALIZING,
    CONNECTING,
    STREAMING,
    PARSING,
    VALIDATING,
    SAVING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

data class AiCardTask(
    val taskId: String,
    val config: AiConfig,
    val totalTargetCount: Int,
    val batchSize: Int = 25,
    val systemPrompt: String = "",
    val userMessage: String = "",
    val knowledgeBaseId: String? = null,
    val deckId: String? = null,
    val mode: com.lumecard.shared.data.AiCardMode = com.lumecard.shared.data.AiCardMode.AUTO,
    val knowledgeBaseName: String? = null,
    val deckName: String? = null,
) {
    val sessionId: String get() = taskId
}

data class AiTaskState(
    val status: AiTaskStatus = AiTaskStatus.PENDING,
    val currentBatch: Int = 0,
    val totalBatches: Int = 0,
    val parsedCards: Int = 0,
    val savedCards: Int = 0,
    val totalTarget: Int = 0,
    val bytesReceived: Long = 0L,
    val contentLength: Long? = null,
    val elapsedSeconds: Long = 0L,
    val cardsPerSecond: Double = 0.0,
    val etaSeconds: Long? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val logEntries: List<LogEntry> = emptyList(),
    val startTime: Instant = Clock.System.now(),
    val endTime: Instant? = null,
) {
    val progress: Float
        get() = if (totalTarget > 0) (savedCards.toFloat() / totalTarget).coerceIn(0f, 1f) else 0f

    val isActive: Boolean
        get() = status == AiTaskStatus.INITIALIZING || status == AiTaskStatus.CONNECTING ||
                status == AiTaskStatus.STREAMING || status == AiTaskStatus.PARSING ||
                status == AiTaskStatus.VALIDATING || status == AiTaskStatus.SAVING

    val isTerminal: Boolean
        get() = status == AiTaskStatus.COMPLETED || status == AiTaskStatus.CANCELLED || status == AiTaskStatus.FAILED
}

class AiTaskStateMachine {
    private var _state = AiTaskState()
    val state: AiTaskState get() = _state

    fun transition(newStatus: AiTaskStatus, updater: (AiTaskState) -> AiTaskState = { it }): AiTaskState {
        val allowed = isTransitionAllowed(_state.status, newStatus)
        if (!allowed) {
            throw IllegalStateException("Invalid transition: ${_state.status} -> $newStatus")
        }
        _state = updater(_state.copy(status = newStatus))
        return _state
    }

    fun update(updater: (AiTaskState) -> AiTaskState): AiTaskState {
        _state = updater(_state)
        return _state
    }

    private fun isTransitionAllowed(from: AiTaskStatus, to: AiTaskStatus): Boolean {
        if (from == to) return true
        if (_state.isTerminal && to != AiTaskStatus.PENDING) return false
        return true
    }

    fun reset() {
        _state = AiTaskState()
    }
}
