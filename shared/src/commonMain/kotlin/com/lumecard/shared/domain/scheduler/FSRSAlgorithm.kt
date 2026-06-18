package com.lumecard.shared.domain.scheduler

import com.lumecard.shared.model.CardState
import com.lumecard.shared.model.FSRSCard
import com.lumecard.shared.model.Rating
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class FSRSAlgorithm(
    private val parameters: FSRSParameters = FSRSParameters()
) {
    data class FSRSParameters(
        val w: List<Double> = listOf(
            0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01, 1.49, 0.14, 0.94, 2.18, 0.05, 0.34, 1.26, 0.29, 2.61
        )
    )

    fun initCard(): FSRSCard {
        val now = Clock.System.now()
        return FSRSCard(
            id = "",
            due = now,
            stability = 0.0,
            difficulty = 0.0,
            elapsedDays = 0,
            scheduledDays = 0,
            reps = 0,
            lapses = 0,
            state = CardState.NEW
        )
    }

    fun schedule(card: FSRSCard, rating: Rating): FSRSCard {
        return when (card.state) {
            CardState.NEW -> initialSchedule(card, rating)
            CardState.LEARNING -> learningSchedule(card, rating)
            CardState.REVIEW -> reviewSchedule(card, rating)
            CardState.RELEARNING -> relearningSchedule(card, rating)
        }
    }

    private fun initialSchedule(card: FSRSCard, rating: Rating): FSRSCard {
        val w = parameters.w
        val stability = w[0] + w[1] * rating.value
        val difficulty = w[2] + w[3] * (rating.value - 3)

        val scheduledDays = when (rating) {
            Rating.AGAIN -> 1
            Rating.HARD -> 1
            Rating.GOOD -> 1
            Rating.EASY -> 4
        }

        val due = Clock.System.now().plus(DateTimePeriod(days = scheduledDays), TimeZone.UTC)

        return card.copy(
            due = due,
            stability = stability,
            difficulty = difficulty,
            scheduledDays = scheduledDays,
            reps = 1,
            state = if (rating == Rating.EASY) CardState.REVIEW else CardState.LEARNING
        )
    }

    private fun learningSchedule(card: FSRSCard, rating: Rating): FSRSCard {
        val newDifficulty = calculateDifficulty(card.difficulty, rating)
        val newStability = calculateStability(card, rating)

        val scheduledDays = when (rating) {
            Rating.AGAIN -> 1
            Rating.HARD -> 1
            Rating.GOOD -> 1
            Rating.EASY -> 4
        }

        val due = Clock.System.now().plus(DateTimePeriod(days = scheduledDays), TimeZone.UTC)

        return card.copy(
            due = due,
            stability = newStability,
            difficulty = newDifficulty,
            scheduledDays = scheduledDays,
            reps = card.reps + 1,
            state = if (rating == Rating.EASY) CardState.REVIEW else CardState.LEARNING
        )
    }

    private fun reviewSchedule(card: FSRSCard, rating: Rating): FSRSCard {
        val newDifficulty = calculateDifficulty(card.difficulty, rating)
        val newStability = calculateStability(card, rating)

        val scheduledDays = calculateInterval(newStability, rating)
        val due = Clock.System.now().plus(DateTimePeriod(days = scheduledDays), TimeZone.UTC)

        return card.copy(
            due = due,
            stability = newStability,
            difficulty = newDifficulty,
            scheduledDays = scheduledDays,
            reps = card.reps + 1,
            state = if (rating == Rating.AGAIN) CardState.RELEARNING else CardState.REVIEW
        )
    }

    private fun relearningSchedule(card: FSRSCard, rating: Rating): FSRSCard {
        val newDifficulty = calculateDifficulty(card.difficulty, rating)
        val newStability = calculateStability(card, rating)

        val scheduledDays = when (rating) {
            Rating.AGAIN -> 1
            Rating.HARD -> 1
            Rating.GOOD -> 1
            Rating.EASY -> max(1, (newStability * 0.5).toInt())
        }

        val due = Clock.System.now().plus(DateTimePeriod(days = scheduledDays), TimeZone.UTC)

        return card.copy(
            due = due,
            stability = newStability,
            difficulty = newDifficulty,
            scheduledDays = scheduledDays,
            lapses = card.lapses + if (rating == Rating.AGAIN) 1 else 0,
            state = if (rating == Rating.EASY) CardState.REVIEW else CardState.RELEARNING
        )
    }

    private fun calculateDifficulty(currentDifficulty: Double, rating: Rating): Double {
        val w = parameters.w
        val newDifficulty = currentDifficulty + w[6] * (rating.value - 3)
        return max(1.0, min(10.0, newDifficulty))
    }

    private fun calculateStability(card: FSRSCard, rating: Rating): Double {
        val w = parameters.w

        return when (rating) {
            Rating.AGAIN -> max(w[0], card.stability * w[9] * (1 - card.difficulty * w[10]))
            Rating.HARD -> card.stability * (1 + w[11] * (10 - card.difficulty) * card.stability.pow(w[12] - 1))
            Rating.GOOD -> card.stability
            Rating.EASY -> card.stability * (1 + w[13] * (10 - card.difficulty) * card.stability.pow(w[14] - 1))
        }
    }

    private fun calculateInterval(stability: Double, rating: Rating): Int {
        val w = parameters.w

        return when (rating) {
            Rating.AGAIN -> 1
            Rating.HARD -> max(1, (stability * w[15]).toInt())
            Rating.GOOD -> max(1, (stability * w[16]).toInt())
            Rating.EASY -> max(1, (stability * w[16] * 1.3).toInt())
        }
    }

    fun getNextReviewTime(card: FSRSCard): Instant {
        return card.due
    }

    fun isDue(card: FSRSCard): Boolean {
        val now = Clock.System.now()
        return card.due <= now
    }
}
