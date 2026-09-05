package com.lumecard.shared.feature.quote.timer

import com.lumecard.shared.feature.quote.manager.IdleManager
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class StudyTimerManager(
    private val idleManager: IdleManager,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _idlePauseEnabled = MutableStateFlow(true)
    val idlePauseEnabled: StateFlow<Boolean> = _idlePauseEnabled.asStateFlow()

    private val _idlePauseThresholdMs = MutableStateFlow(30_000L)
    val idlePauseThresholdMs: StateFlow<Long> = _idlePauseThresholdMs.asStateFlow()

    private var idleSubJob: Job? = null
    private var timerJob: Job? = null
    private var accumulatedMs: Long = 0L
    private val timeSource = TimeSource.Monotonic
    private var lastTickMark: TimeMark = timeSource.markNow()
    private var isStudyActive = false

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _studyTimeSeconds = MutableStateFlow(0L)
    val studyTimeSeconds: StateFlow<Long> = _studyTimeSeconds.asStateFlow()

    init {
        idleSubJob = scope.launch {
            idleManager.isIdle.collect { idle ->
                if (_idlePauseEnabled.value && isStudyActive) {
                    if (idle && !_isPaused.value) {
                        pauseStudy()
                    } else if (!idle && _isPaused.value) {
                        resumeStudy()
                    }
                }
            }
        }
    }

    fun setIdlePauseEnabled(enabled: Boolean) {
        _idlePauseEnabled.value = enabled
        if (!enabled && _isPaused.value && isStudyActive) {
            resumeStudy()
        }
    }

    fun setIdlePauseThreshold(ms: Long) {
        _idlePauseThresholdMs.value = ms.coerceIn(10_000L, 600_000L)
    }

    fun startStudy() {
        stopTimer()
        accumulatedMs = 0L
        isStudyActive = true
        _isPaused.value = false
        lastTickMark = timeSource.markNow()
        startTimer()
    }

    fun stopStudy() {
        if (isStudyActive) {
            if (!_isPaused.value) {
                accumulatedMs += elapsedMsSinceLastTick()
            }
            _studyTimeSeconds.value = accumulatedMs / 1000L
        }
        stopTimer()
        isStudyActive = false
        _isPaused.value = false
        accumulatedMs = 0L
    }

    private fun pauseStudy() {
        if (isStudyActive && !_isPaused.value) {
            accumulatedMs += elapsedMsSinceLastTick()
            _isPaused.value = true
            stopTimer()
        }
    }

    private fun resumeStudy() {
        if (isStudyActive && _isPaused.value) {
            lastTickMark = timeSource.markNow()
            _isPaused.value = false
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob = scope.launch {
            while (isActive) {
                if (!_isPaused.value && isStudyActive) {
                    val total = accumulatedMs + elapsedMsSinceLastTick()
                    _studyTimeSeconds.value = total / 1000L
                }
                delay(1000L)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun elapsedMsSinceLastTick(): Long = lastTickMark.elapsedNow().inWholeMilliseconds

    fun dispose() {
        idleSubJob?.cancel()
        stopTimer()
        scope.cancel()
    }
}
