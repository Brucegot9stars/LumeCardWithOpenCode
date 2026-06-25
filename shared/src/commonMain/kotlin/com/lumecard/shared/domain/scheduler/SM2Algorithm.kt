package com.lumecard.shared.domain.scheduler

import com.lumecard.shared.model.Rating
import kotlin.time.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.math.max

class SM2Algorithm : ReviewAlgorithm {
    override val mode: ReviewMode = ReviewMode.SM2

    override fun initCard(): AlgorithmState = AlgorithmState(
        intervalDays = 0,
        nextReviewAt = Clock.System.now(),
        easeFactor = 2.5f
    )

    override fun schedule(state: AlgorithmState, rating: Rating): AlgorithmState {
        val ef = calculateEaseFactor(state.easeFactor, rating).coerceIn(1.3f, Float.MAX_VALUE)

        if (rating.value < 3) {
            val interval = 1
            val due = Clock.System.now().plus(DateTimePeriod(days = interval), TimeZone.UTC)
            return state.copy(
                intervalDays = interval,
                nextReviewAt = due,
                repetitions = 0,
                lapses = state.lapses + 1,
                easeFactor = ef
            )
        }

        val newReps = state.repetitions + 1
        val interval = when (newReps) {
            1 -> 1
            2 -> 6
            else -> (state.repetitions.toDouble() * ef).toInt().coerceAtLeast(1)
        }
        val due = Clock.System.now().plus(DateTimePeriod(days = interval), TimeZone.UTC)

        return state.copy(
            intervalDays = interval,
            nextReviewAt = due,
            repetitions = newReps,
            easeFactor = ef
        )
    }

    private fun calculateEaseFactor(current: Float, rating: Rating): Float {
        val q = rating.value
        return current + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))
    }
}
