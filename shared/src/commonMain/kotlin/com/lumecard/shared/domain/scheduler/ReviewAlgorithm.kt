package com.lumecard.shared.domain.scheduler

import com.lumecard.shared.model.Rating
import kotlin.time.Instant
import kotlinx.serialization.Serializable

enum class ReviewMode {
    FSRS,
    SM2,
    LEITNER,
    SIMPLE
}

@Serializable
data class AlgorithmState(
    val intervalDays: Int = 0,
    val nextReviewAt: Instant = Instant.DISTANT_FUTURE,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val easeFactor: Float = 2.5f,
    val stage: Int = 0,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0
)

interface ReviewAlgorithm {
    val mode: ReviewMode
    fun initCard(): AlgorithmState
    fun schedule(state: AlgorithmState, rating: Rating): AlgorithmState
}
