package com.lumecard.shared.data.ai.event

import com.lumecard.shared.data.AiCardResponseJson
import com.lumecard.shared.data.AiConfig
import com.lumecard.shared.model.Card
import kotlin.time.Instant

sealed interface AiEvent {
    val timestamp: Instant
    val sessionId: String
}

data class SessionStarted(
    override val sessionId: String,
    override val timestamp: Instant,
    val config: AiConfig,
    val cardCount: Int,
    val batchSize: Int,
) : AiEvent

data class RequestStarted(
    override val sessionId: String,
    override val timestamp: Instant,
    val batchIndex: Int,
) : AiEvent

data class TextDelta(
    override val sessionId: String,
    override val timestamp: Instant,
    val delta: String,
    val accumulated: String,
) : AiEvent

data class CardParsed(
    override val sessionId: String,
    override val timestamp: Instant,
    val rawJson: String,
    val cardIndex: Int,
    val totalSoFar: Int,
) : AiEvent

data class CardValidated(
    override val sessionId: String,
    override val timestamp: Instant,
    val cardIndex: Int,
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
) : AiEvent

data class CardSaved(
    override val sessionId: String,
    override val timestamp: Instant,
    val cardLocalId: String,
    val cardIndex: Int,
    val card: Card,
) : AiEvent

data class BatchProgress(
    override val sessionId: String,
    override val timestamp: Instant,
    val batchIndex: Int,
    val batchTotal: Int,
    val overallSaved: Int,
    val overallTarget: Int,
) : AiEvent

data class ParseProgress(
    override val sessionId: String,
    override val timestamp: Instant,
    val bytesReceived: Long,
    val contentLength: Long?,
    val parsedCards: Int,
) : AiEvent

data class Metrics(
    override val sessionId: String,
    override val timestamp: Instant,
    val elapsedSeconds: Long,
    val cardsPerSecond: Double,
    val etaSeconds: Long?,
    val tokensIn: Int? = null,
    val tokensOut: Int? = null,
) : AiEvent

data class BatchResponseReceived(
    override val sessionId: String,
    override val timestamp: Instant,
    val batchIndex: Int,
    val response: AiCardResponseJson,
) : AiEvent

data class Warning(
    override val sessionId: String,
    override val timestamp: Instant,
    val message: String,
    val code: String? = null,
) : AiEvent

data class Error(
    override val sessionId: String,
    override val timestamp: Instant,
    val message: String,
    val code: String? = null,
    val recoverable: Boolean = false,
) : AiEvent

data class Completed(
    override val sessionId: String,
    override val timestamp: Instant,
    val totalCardsCreated: Int,
    val totalBatches: Int,
    val elapsedSeconds: Long,
) : AiEvent

data class Cancelled(
    override val sessionId: String,
    override val timestamp: Instant,
    val savedCards: Int,
    val reason: String = "User cancelled",
) : AiEvent
