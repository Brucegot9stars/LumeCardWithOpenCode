package com.lumecard.shared.domain.scheduler

import com.lumecard.shared.model.CardState
import com.lumecard.shared.model.FSRSCard
import com.lumecard.shared.model.Rating
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class FSRSAlgorithm(
    private val parameters: FSRSParameters = FSRSParameters(),
    private val desiredRetention: Double = 0.9,
    private val maxInterval: Int = 36500,
) {
    data class FSRSParameters(
        val w: List<Double> = listOf(
            0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01, 1.49, 0.14, 0.94, 2.18, 0.05, 0.34, 1.26, 0.29, 2.61,
            0.0, 0.0, 0.0, 0.3
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

    fun schedule(card: FSRSCard, rating: Rating, daysElapsed: Int = card.elapsedDays): FSRSCard {
        return when (card.state) {
            CardState.NEW -> initialSchedule(card, rating)
            CardState.LEARNING -> learningSchedule(card, rating, daysElapsed)
            CardState.REVIEW -> reviewSchedule(card, rating, daysElapsed)
            CardState.RELEARNING -> relearningSchedule(card, rating, daysElapsed)
        }
    }

    private fun initialSchedule(card: FSRSCard, rating: Rating): FSRSCard {
        val w = parameters.w
        val stability = w[0] + w[1] * rating.value
        val difficulty = (w[4] - exp(w[5] * (rating.value - 1)) + 1.0).coerceIn(1.0, 10.0)

        val scheduledDays = when (rating) {
            Rating.AGAIN -> 1
            Rating.HARD -> 1
            Rating.GOOD -> 1
            Rating.EASY -> 4
        }

        val due = Clock.System.now().plus(scheduledDays, DateTimeUnit.DAY, TimeZone.UTC)

        return card.copy(
            due = due,
            stability = stability,
            difficulty = difficulty,
            scheduledDays = scheduledDays,
            reps = 1,
            state = if (rating == Rating.EASY) CardState.REVIEW else CardState.LEARNING
        )
    }

    private fun learningSchedule(card: FSRSCard, rating: Rating, daysElapsed: Int): FSRSCard {
        val newDifficulty = calculateDifficulty(card.difficulty, rating)
        val newStability = calculateStability(card.stability, card.difficulty, rating, daysElapsed)

        val scheduledDays = if (rating == Rating.EASY) {
            // EASY in learning: graduate to REVIEW with calculated interval
            max(1, calculateInterval(newStability, rating))
        } else {
            // AGAIN/HARD/GOOD in learning: short interval (1-3 days based on stability)
            val baseInterval = (newStability * 0.1).toInt().coerceIn(1, 3)
            if (rating == Rating.GOOD && card.reps >= 1) baseInterval.coerceAtLeast(2) else baseInterval
        }

        val due = Clock.System.now().plus(scheduledDays, DateTimeUnit.DAY, TimeZone.UTC)

        return card.copy(
            due = due,
            stability = newStability,
            difficulty = newDifficulty,
            scheduledDays = scheduledDays,
            reps = card.reps + 1,
            state = if (rating == Rating.EASY) CardState.REVIEW else CardState.LEARNING
        )
    }

    private fun reviewSchedule(card: FSRSCard, rating: Rating, daysElapsed: Int): FSRSCard {
        val newDifficulty = calculateDifficulty(card.difficulty, rating)
        val newStability = calculateStability(card.stability, card.difficulty, rating, daysElapsed)

        val scheduledDays = calculateInterval(newStability, rating)
        val due = Clock.System.now().plus(scheduledDays, DateTimeUnit.DAY, TimeZone.UTC)

        return card.copy(
            due = due,
            stability = newStability,
            difficulty = newDifficulty,
            scheduledDays = scheduledDays,
            reps = card.reps + 1,
            state = if (rating == Rating.AGAIN) CardState.RELEARNING else CardState.REVIEW
        )
    }

    private fun relearningSchedule(card: FSRSCard, rating: Rating, daysElapsed: Int): FSRSCard {
        val newDifficulty = calculateDifficulty(card.difficulty, rating)
        val newStability = calculateStability(card.stability, card.difficulty, rating, daysElapsed)

        val scheduledDays = if (rating == Rating.EASY) {
            // EASY in relearning: graduate to REVIEW with calculated interval
            max(1, (newStability * 0.5).toInt())
        } else {
            // AGAIN/HARD/GOOD in relearning: short interval (1-3 days)
            val baseInterval = (newStability * 0.1).toInt().coerceIn(1, 3)
            if (rating == Rating.GOOD) baseInterval.coerceAtLeast(2) else baseInterval
        }

        val due = Clock.System.now().plus(scheduledDays, DateTimeUnit.DAY, TimeZone.UTC)

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
        val newDifficulty = currentDifficulty - w[6] * (rating.value - 3)
        return max(1.0, min(10.0, newDifficulty))
    }

    private fun calculateStability(stability: Double, difficulty: Double, rating: Rating, daysElapsed: Int): Double {
        val w = parameters.w

        return when (rating) {
            Rating.AGAIN -> max(w[0], stability * w[9] * (1 - difficulty * w[10]))
            Rating.HARD -> stability * (1 + w[11] * (10 - difficulty) * stability.pow(w[12] - 1))
            Rating.GOOD -> stability * (1 + w[4] * (10 - difficulty) * stability.pow(w[5] - 1))
            Rating.EASY -> stability * (1 + w[13] * (10 - difficulty) * stability.pow(w[14] - 1))
        }
    }

    private fun calculateInterval(stability: Double, rating: Rating): Int {
        if (rating == Rating.AGAIN) return 1

        val decayFactor = desiredRetention.pow(-1.0 / parameters.w[20]) - 1.0
        val rawDays = when (rating) {
            Rating.HARD -> stability * decayFactor
            Rating.GOOD -> stability * decayFactor
            Rating.EASY -> stability * 1.3 * decayFactor
            else -> 0.0
        }

        val fuzz = 0.95 + Random.nextDouble() * 0.1
        val interval = (rawDays * fuzz).toInt().coerceAtLeast(1)
        return min(interval, maxInterval)
    }

    fun getNextReviewTime(card: FSRSCard): Instant {
        return card.due
    }

    fun isDue(card: FSRSCard): Boolean {
        val now = Clock.System.now()
        return card.due <= now
    }
}
