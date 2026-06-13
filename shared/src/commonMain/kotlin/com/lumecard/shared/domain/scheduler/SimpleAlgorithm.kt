package com.lumecard.shared.domain.scheduler

import com.lumecard.shared.model.Rating
import kotlinx.datetime.Clock
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

    override fun schedule(state: AlgorithmState, rating: Rating): AlgorithmState {
        val currentStage = state.stage.coerceIn(0, fixedIntervals.size - 1)
        val newStage = if (rating.value >= 3) {
            (currentStage + 1).coerceAtMost(fixedIntervals.size - 1)
        } else {
            0
        }
        val interval = fixedIntervals[newStage]
        val due = Clock.System.now().plus(DateTimePeriod(days = interval), TimeZone.UTC)

        return state.copy(
            intervalDays = interval,
            nextReviewAt = due,
            repetitions = if (rating.value >= 3) state.repetitions + 1 else 0,
            lapses = state.lapses + if (rating.value < 3) 1 else 0,
            stage = newStage
        )
    }
}
