package com.lumecard.shared.domain.scheduler

import com.lumecard.shared.model.Rating
import kotlin.time.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

class LeitnerAlgorithm : ReviewAlgorithm {
    override val mode: ReviewMode = ReviewMode.LEITNER

    private val boxIntervals = listOf(1, 2, 4, 7, 15, 30, 60, 120)

    override fun initCard(): AlgorithmState = AlgorithmState(
        intervalDays = boxIntervals[0],
        nextReviewAt = Clock.System.now(),
        stage = 0
    )

    override fun schedule(state: AlgorithmState, rating: Rating, daysElapsed: Int): AlgorithmState {
        val currentBox = state.stage.coerceIn(0, boxIntervals.size - 1)
        val newBox = when (rating) {
            Rating.AGAIN -> 0                    // Reset to box 0
            Rating.HARD -> currentBox            // Stay in current box
            Rating.GOOD -> (currentBox + 1).coerceAtMost(boxIntervals.size - 1)  // Advance one box
            Rating.EASY -> (currentBox + 1).coerceAtMost(boxIntervals.size - 1)  // Advance one box
        }
        val interval = boxIntervals[newBox]
        val due = Clock.System.now().plus(DateTimePeriod(days = interval), TimeZone.UTC)

        return state.copy(
            intervalDays = interval,
            nextReviewAt = due,
            repetitions = if (rating != Rating.AGAIN) state.repetitions + 1 else 0,
            lapses = state.lapses + if (rating == Rating.AGAIN) 1 else 0,
            stage = newBox
        )
    }
}
