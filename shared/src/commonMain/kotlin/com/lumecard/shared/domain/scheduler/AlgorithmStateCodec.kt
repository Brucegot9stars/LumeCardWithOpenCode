package com.lumecard.shared.domain.scheduler

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

/** Reads old pipe-delimited states as well as the current JSON format. */
object AlgorithmStateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(state: AlgorithmState): String = json.encodeToString(state)

    fun decode(value: String): AlgorithmState = try {
        json.decodeFromString<AlgorithmState>(value)
    } catch (jsonError: Exception) {
        val parts = value.split('|')
        if (parts.size != 8 || parts[0].toIntOrNull() == null) throw jsonError
        AlgorithmState(
            intervalDays = parts[0].toInt(),
            nextReviewAt = parts[1].let { runCatching { Instant.parse(it) }.getOrElse { Clock.System.now() } },
            repetitions = parts[2].toIntOrNull() ?: 0,
            lapses = parts[3].toIntOrNull() ?: 0,
            easeFactor = parts[4].toFloatOrNull() ?: 2.5f,
            stage = parts[5].toIntOrNull() ?: 0,
            stability = parts[6].toDoubleOrNull() ?: 0.0,
            difficulty = parts[7].toDoubleOrNull() ?: 0.0,
        )
    }
}
