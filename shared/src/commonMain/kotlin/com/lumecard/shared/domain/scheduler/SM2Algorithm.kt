package com.lumecard.shared.domain.scheduler

import com.lumecard.shared.model.Rating
import kotlin.time.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.math.max
import kotlin.math.roundToInt

class SM2Algorithm : ReviewAlgorithm {
    override val mode: ReviewMode = ReviewMode.SM2

    override fun initCard(): AlgorithmState = AlgorithmState(
        intervalDays = 0,
        nextReviewAt = Clock.System.now(),
        easeFactor = 2.5f
    )

    override fun schedule(state: AlgorithmState, rating: Rating, daysElapsed: Int): AlgorithmState {
        val ef = calculateEaseFactor(state.easeFactor, rating).coerceIn(1.3f, Float.MAX_VALUE)

        if (rating == Rating.AGAIN) {
            // ONLY AGAIN is a fail: reset to 1 day
            val interval = 1
            val due = Clock.System.now().plus(DateTimePeriod(days = interval), TimeZone.UTC)
            return state.copy(
                intervalDays = interval,
                nextReviewAt = due,
                repetitions = 0,
                lapses = state.lapses + 1,
                easeFactor = state.easeFactor  // 严格 SM-2：失败时 EF 不变
            )
        }

        val newReps = state.repetitions + 1
        val interval = when (newReps) {
            1 -> 1
            2 -> 6
            else -> {
                val baseInterval = (state.intervalDays.toDouble() * ef).roundToInt().coerceAtLeast(1)
                when (rating) {
                    Rating.HARD -> (baseInterval * 0.8).roundToInt().coerceAtLeast(1)  // HARD: shorter interval
                    Rating.EASY -> (baseInterval * 1.3).roundToInt()                    // EASY: longer interval
                    else -> baseInterval                                                  // GOOD: normal interval
                }
            }
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
        val q = rating.value + 1  // Map Rating(1-4) to SM-2 quality(2-5)
        return current + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))
    }
}
