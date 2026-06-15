package com.lumecard.shared.domain.scheduler

import com.lumecard.shared.model.CardState
import com.lumecard.shared.model.FSRSCard
import com.lumecard.shared.model.Rating
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FSRSAlgorithmAdapter(private val inner: FSRSAlgorithm) : ReviewAlgorithm {
    override val mode: ReviewMode = ReviewMode.FSRS

    override fun initCard(): AlgorithmState {
        val card = inner.initCard()
        return card.toAlgorithmState()
    }

    override fun schedule(state: AlgorithmState, rating: Rating): AlgorithmState {
        val fsrsCard = state.toFSRSCard()
        val updated = inner.schedule(fsrsCard, rating)
        return updated.toAlgorithmState()
    }

    companion object {
        fun FSRSCard.toAlgorithmState() = AlgorithmState(
            intervalDays = scheduledDays,
            nextReviewAt = due,
            repetitions = reps,
            lapses = lapses,
            easeFactor = if (difficulty > 0) difficulty.toFloat() else 2.5f,
            stage = state.ordinal,
            stability = stability,
            difficulty = difficulty
        )

        fun AlgorithmState.toFSRSCard() = FSRSCard(
            id = "",
            due = nextReviewAt,
            stability = stability,
            difficulty = difficulty,
            elapsedDays = ((Clock.System.now().toEpochMilliseconds() - nextReviewAt.toEpochMilliseconds()) / 86400000).coerceAtLeast(0).toInt(),
            scheduledDays = intervalDays,
            reps = repetitions,
            lapses = lapses,
            state = CardState.entries[stage.coerceIn(0, CardState.entries.size - 1)]
        )
    }
}
