package com.lumecard.shared.feature.quote.manager

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class IdleManager {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isIdle = MutableStateFlow(false)
    val isIdle: StateFlow<Boolean> = _isIdle.asStateFlow()

    private val _idleThresholdMs = MutableStateFlow(180_000L)
    val idleThresholdMs: StateFlow<Long> = _idleThresholdMs.asStateFlow()

    private var lastActivityNanos: Long = 0L
    private var checkJob: Job? = null

    init {
        startChecking()
    }

    fun reportActivity() {
        val now = currentTimeNanos()
        if (lastActivityNanos == 0L) lastActivityNanos = now
        else lastActivityNanos = now
        if (_isIdle.value) {
            _isIdle.value = false
        }
    }

    fun setIdleThreshold(ms: Long) {
        _idleThresholdMs.value = ms.coerceIn(60_000L, 3_600_000L)
    }

    private fun startChecking() {
        checkJob = scope.launch {
            while (isActive) {
                if (lastActivityNanos > 0L) {
                    val idleNanos = currentTimeNanos() - lastActivityNanos
                    val thresholdNanos = _idleThresholdMs.value * 1_000_000L
                    if (idleNanos >= thresholdNanos && !_isIdle.value) {
                        _isIdle.value = true
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun currentTimeNanos(): Long =
        kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeNanoseconds

    fun dispose() {
        checkJob?.cancel()
        scope.cancel()
    }
}
