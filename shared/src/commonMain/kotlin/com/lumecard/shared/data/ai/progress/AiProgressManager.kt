package com.lumecard.shared.data.ai.progress

import com.lumecard.shared.data.ai.task.AiTaskState
import com.lumecard.shared.data.ai.task.AiTaskStatus
import kotlin.time.Clock

class AiProgressManager {
    private var _state = AiTaskState()
    private var lastUpdateTime = Clock.System.now()
    private var lastSavedCards = 0

    val state: AiTaskState get() = _state

    fun onStarted(totalTarget: Int, totalBatches: Int) {
        _state = _state.copy(
            status = AiTaskStatus.INITIALIZING,
            totalTarget = totalTarget,
            totalBatches = totalBatches,
            startTime = Clock.System.now(),
        )
        lastUpdateTime = Clock.System.now()
        lastSavedCards = 0
    }

    fun onStatusChanged(status: AiTaskStatus) {
        _state = _state.copy(status = status)
    }

    fun onBytesReceived(bytes: Long, contentLength: Long?) {
        val now = Clock.System.now()
        val elapsed = (now.toEpochMilliseconds() - _state.startTime.toEpochMilliseconds()) / 1000
        _state = _state.copy(
            bytesReceived = bytes,
            contentLength = contentLength,
            elapsedSeconds = elapsed,
        )
    }

    fun onBatchProgress(batchIndex: Int, totalBatches: Int) {
        _state = _state.copy(currentBatch = batchIndex, totalBatches = totalBatches)
    }

    fun onCardParsed(count: Int) {
        _state = _state.copy(parsedCards = count)
    }

    fun onCardSaved(count: Int) {
        val now = Clock.System.now()
        val elapsed = ((now.toEpochMilliseconds() - _state.startTime.toEpochMilliseconds()) / 1000).coerceAtLeast(1)
        val deltaSaved = count - lastSavedCards
        val timeSinceLast = ((now.toEpochMilliseconds() - lastUpdateTime.toEpochMilliseconds()) / 1000).coerceAtLeast(1)

        val instantRate = if (timeSinceLast > 0 && deltaSaved > 0) deltaSaved.toDouble() / timeSinceLast else _state.cardsPerSecond
        val avgRate = count.toDouble() / elapsed

        val remaining = (_state.totalTarget - count).coerceAtLeast(0)
        val eta = if (avgRate > 0) (remaining / avgRate).toLong() else null

        _state = _state.copy(
            savedCards = count,
            elapsedSeconds = elapsed,
            cardsPerSecond = avgRate,
            etaSeconds = eta,
        )
        lastSavedCards = count
        lastUpdateTime = now
    }

    fun onCompleted() {
        val now = Clock.System.now()
        val elapsed = ((now.toEpochMilliseconds() - _state.startTime.toEpochMilliseconds()) / 1000).coerceAtLeast(1)
        _state = _state.copy(
            status = AiTaskStatus.COMPLETED,
            endTime = now,
            elapsedSeconds = elapsed,
        )
    }

    fun onCancelled() {
        _state = _state.copy(
            status = AiTaskStatus.CANCELLED,
            endTime = Clock.System.now(),
        )
    }

    fun onFailed(errors: List<String>) {
        _state = _state.copy(
            status = AiTaskStatus.FAILED,
            endTime = Clock.System.now(),
            errors = _state.errors + errors,
        )
    }

    fun onWarning(warning: String) {
        _state = _state.copy(warnings = _state.warnings + warning)
    }

    fun addError(error: String) {
        _state = _state.copy(errors = _state.errors + error)
    }

    fun reset() {
        _state = AiTaskState()
        lastUpdateTime = Clock.System.now()
        lastSavedCards = 0
    }
}
