package com.lumecard.shared.data.ai.event

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

class AiEventBus {
    private val _events = MutableSharedFlow<AiEvent>(
        replay = 64,
        extraBufferCapacity = 512,
    )

    val events: Flow<AiEvent> = _events.asSharedFlow()

    suspend fun emit(event: AiEvent) {
        _events.emit(event)
    }

    inline fun <reified T : AiEvent> eventsOfType(): Flow<T> {
        return events.filterIsInstance<T>()
    }

    fun eventsBySession(sessionId: String): Flow<AiEvent> {
        return events.filter { it.sessionId == sessionId }
    }

    inline fun <reified T : AiEvent> eventsBySessionOfType(sessionId: String): Flow<T> {
        return eventsBySession(sessionId).filterIsInstance<T>()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clear() {
        _events.resetReplayCache()
    }
}

@PublishedApi
internal inline fun <reified T> Flow<*>.filterIsInstance(): Flow<T> {
    return filter { it is T }.map { it as T }
}
