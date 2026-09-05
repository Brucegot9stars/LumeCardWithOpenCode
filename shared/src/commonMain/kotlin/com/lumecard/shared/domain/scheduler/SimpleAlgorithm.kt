package com.lumecard.shared.domain.scheduler

import com.lumecard.shared.model.Rating
import kotlin.time.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

class SimpleAlgorithm : ReviewAlgorithm {
    override val mode: ReviewMode = ReviewMode.SIMPLE

    private val fixedIntervals = listOf(1, 3, 7, 15, 30)

    override fun initCard(): AlgorithmState = AlgorithmState(
        intervalDays = fixedIntervals[0],
        nextReviewAt = Clock.System.now(),
        stage = 0
    )

    override fun schedule(state: AlgorithmState, rating: Rating, daysElapsed: Int): AlgorithmState {
        val currentStage = state.stage.coerceIn(0, fixedIntervals.size - 1)
        val newStage = when (rating) {
            Rating.AGAIN -> 0                    // Reset to stage 0
            Rating.HARD -> currentStage          // Stay in current stage
            Rating.GOOD -> (currentStage + 1).coerceAtMost(fixedIntervals.size - 1)  // Advance one stage
            Rating.EASY -> (currentStage + 2).coerceAtMost(fixedIntervals.size - 1)  // Advance two stages (skip ahead)
        }
        val interval = fixedIntervals[newStage]
        val due = Clock.System.now().plus(DateTimePeriod(days = interval), TimeZone.UTC)

        return state.copy(
            intervalDays = interval,
            nextReviewAt = due,
            repetitions = if (rating != Rating.AGAIN) state.repetitions + 1 else 0,
            lapses = state.lapses + if (rating == Rating.AGAIN) 1 else 0,
            stage = newStage
        )
    }
}
