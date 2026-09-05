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

/**
 * FSRS-6 (Free Spaced Repetition Scheduler) implementation.
 *
 * Implements the official 21-parameter FSRS-6 model (awesome-fsrs "The Algorithm" wiki).
 * Compared with the previous FSRS-4.5 build this adds:
 *  - 21 weights (w[0]..w[20]) instead of 17.
 *  - Exponential initial difficulty: D0(G) = w[4] - exp(w[5]*(G-1)) + 1, clamped to [1, 10].
 *  - Trainable forgetting curve: R(t, S) = (1 + factor * t / S)^(-w[20]), where
 *    factor = 0.9^(1/DECAY) - 1 and DECAY = -w[20] (so R(S, S) = 90%).
 *  - Same-day review growth: when a card is reviewed again on the same day, stability uses
 *    S' = S * e^{w[17]*(G-3+w[18])*S^(-w[19])} (grows fast when small, slow when large,
 *    converges; SInc forced >= 1 for G >= 3).
 *  - Lapse stability is clamped to sMin = S / exp(w[17]*w[18]).
 *
 * NOTE: we deliberately keep the interval deterministic (no fuzz) for testability, and we do
 * NOT replicate FSRS-Kotlin's `coerceAtMost(0.1)` bug in initStability — here S0(G) = w[G-1]
 * exactly as the spec. R (retrievability) is computed from the ACTUAL elapsed days, so reviewing
 * early vs. late correctly affects the stability update.
 */
class FSRSAlgorithm(
    private val parameters: FSRSParameters = FSRSParameters(),
    private val desiredRetention: Double = 0.9,
    private val maxInterval: Int = 36500,
) {
    data class FSRSParameters(
        val w: List<Double> = listOf(
            // w[0..3]  initial stability S0(Again/Hard/Good/Easy)
            0.212, 1.2931, 2.3065, 8.2956,
            // w[4..7]  difficulty (init, delta, mean-reversion)
            6.4133, 0.8334, 3.0194, 0.001,
            // w[8..10] successful-recall stability main term
            1.8722, 0.1666, 0.796,
            // w[11..14] forgetting (lapse) stability
            1.4835, 0.0614, 0.2629, 1.6483,
            // w[15..16] Hard / Easy multipliers on successful recall
            0.6014, 1.8729,
            // w[17..20] same-day growth + trainable forgetting-curve decay
            0.5425, 0.0912, 0.0658, 0.1542
        )
    )

    private val decay: Double get() = -parameters.w[20]
    // factor is calibrated so that R(S, S) == 0.9 (the "stability == interval" point).
    private val factor: Double get() = 0.9.pow(1.0 / decay) - 1.0

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
            CardState.NEW -> initialReview(card, rating)
            CardState.LEARNING -> learningStep(card, rating, daysElapsed)
            CardState.REVIEW -> review(card, rating, daysElapsed)
            CardState.RELEARNING -> relearning(card, rating, daysElapsed)
        }
    }

    // ---- State transitions ----

    private fun initialReview(card: FSRSCard, rating: Rating): FSRSCard {
        val stability = initStability(rating)
        val difficulty = initDifficulty(rating)
        val now = Clock.System.now()
        return when (rating) {
            Rating.AGAIN -> card.copy(
                due = now.plus(1, DateTimeUnit.DAY, TimeZone.UTC),
                stability = stability,
                difficulty = difficulty,
                scheduledDays = 1,
                reps = 1,
                lapses = 0,
                state = CardState.LEARNING
            )
            else -> {
                val interval = nextInterval(stability)
                card.copy(
                    due = now.plus(interval, DateTimeUnit.DAY, TimeZone.UTC),
                    stability = stability,
                    difficulty = difficulty,
                    scheduledDays = interval,
                    reps = 1,
                    lapses = 0,
                    state = CardState.REVIEW
                )
            }
        }
    }

    // Learning step (card failed during initial learning, never graduated yet).
    private fun learningStep(card: FSRSCard, rating: Rating, daysElapsed: Int): FSRSCard {
        val r = retrievability(card.stability, daysElapsed)
        val stability = if (rating == Rating.AGAIN) {
            nextForgetStability(card.stability, card.difficulty, r)
        } else {
            nextShortTermStability(card.stability, rating)
        }
        val difficulty = nextDifficulty(card.difficulty, rating)
        val now = Clock.System.now()
        return when (rating) {
            Rating.AGAIN -> card.copy(
                due = now.plus(1, DateTimeUnit.DAY, TimeZone.UTC),
                stability = stability,
                difficulty = difficulty,
                scheduledDays = 1,
                reps = card.reps + 1,
                lapses = card.lapses,
                state = CardState.RELEARNING
            )
            else -> {
                val interval = nextInterval(stability)
                card.copy(
                    due = now.plus(interval, DateTimeUnit.DAY, TimeZone.UTC),
                    stability = stability,
                    difficulty = difficulty,
                    scheduledDays = interval,
                    reps = card.reps + 1,
                    state = CardState.REVIEW
                )
            }
        }
    }

    // Mature review.
    private fun review(card: FSRSCard, rating: Rating, daysElapsed: Int): FSRSCard {
        val r = retrievability(card.stability, daysElapsed)
        val stability = if (rating == Rating.AGAIN) {
            nextForgetStability(card.stability, card.difficulty, r)
        } else {
            nextRecallStability(card.stability, card.difficulty, r, rating)
        }
        val difficulty = nextDifficulty(card.difficulty, rating)
        val now = Clock.System.now()
        return when (rating) {
            Rating.AGAIN -> card.copy(
                due = now.plus(1, DateTimeUnit.DAY, TimeZone.UTC),
                stability = stability,
                difficulty = difficulty,
                scheduledDays = 1,
                reps = card.reps + 1,
                lapses = card.lapses + 1,
                state = CardState.RELEARNING
            )
            else -> {
                val interval = nextInterval(stability)
                card.copy(
                    due = now.plus(interval, DateTimeUnit.DAY, TimeZone.UTC),
                    stability = stability,
                    difficulty = difficulty,
                    scheduledDays = interval,
                    reps = card.reps + 1,
                    state = CardState.REVIEW
                )
            }
        }
    }

    // Relearning after a lapse.
    private fun relearning(card: FSRSCard, rating: Rating, daysElapsed: Int): FSRSCard {
        val r = retrievability(card.stability, daysElapsed)
        val stability = if (rating == Rating.AGAIN) {
            nextForgetStability(card.stability, card.difficulty, r)
        } else {
            nextShortTermStability(card.stability, rating)
        }
        val difficulty = nextDifficulty(card.difficulty, rating)
        val now = Clock.System.now()
        return when (rating) {
            Rating.AGAIN -> card.copy(
                due = now.plus(1, DateTimeUnit.DAY, TimeZone.UTC),
                stability = stability,
                difficulty = difficulty,
                scheduledDays = 1,
                reps = card.reps + 1,
                lapses = card.lapses + 1,
                state = CardState.RELEARNING
            )
            else -> {
                val interval = nextInterval(stability)
                card.copy(
                    due = now.plus(interval, DateTimeUnit.DAY, TimeZone.UTC),
                    stability = stability,
                    difficulty = difficulty,
                    scheduledDays = interval,
                    reps = card.reps + 1,
                    state = CardState.REVIEW
                )
            }
        }
    }

    // ---- FSRS-6 core math ----

    /** Initial stability: S0(G) = w[G-1] (G = rating value 1..4). */
    private fun initStability(rating: Rating): Double = parameters.w[rating.value - 1]

    /** Initial difficulty: D0(G) = w[4] - exp(w[5]*(G-1)) + 1, clamped to [1, 10]. */
    private fun initDifficulty(rating: Rating): Double =
        (parameters.w[4] - exp(parameters.w[5] * (rating.value - 1)) + 1.0).coerceIn(1.0, 10.0)

    /**
     * Next difficulty: linear damping deltaD = -w[6]*(G-3), damped by (10-D)/9, then mean-reverted
     * toward D0(4) with weight w[7], clamped to [1, 10].
     */
    private fun nextDifficulty(currentDifficulty: Double, rating: Rating): Double {
        val w = parameters.w
        val deltaD = -w[6] * (rating.value - 3)
        val damped = deltaD * (10.0 - currentDifficulty) / 9.0
        val nextD = currentDifficulty + damped
        val reverted = w[7] * initDifficulty(Rating.EASY) + (1.0 - w[7]) * nextD
        return reverted.coerceIn(1.0, 10.0)
    }

    /** Retrievability at the moment of review (FSRS-6 trainable forgetting curve). */
    private fun retrievability(stability: Double, daysElapsed: Int): Double {
        val s = max(stability, 1e-6)
        val base = max(1.0 + factor * daysElapsed / s, 1e-6)
        return base.pow(decay).coerceIn(0.0, 1.0)
    }

    /** Successful recall (mature review): FSRS-4.5/5/6 main formula with w[8..16]. */
    private fun nextRecallStability(stability: Double, difficulty: Double, r: Double, rating: Rating): Double {
        val w = parameters.w
        val s = max(stability, 1e-6)
        val hardEasy = when (rating) {
            Rating.HARD -> w[15]
            Rating.EASY -> w[16]
            else -> 1.0
        }
        return s * (1.0 + exp(w[8]) * (11.0 - difficulty) * s.pow(-w[9]) * (exp(w[10] * (1.0 - r)) - 1.0) * hardEasy)
    }

    /** Same-day review (learning / relearning steps): FSRS-6 w[17..19] growth term. */
    private fun nextShortTermStability(stability: Double, rating: Rating): Double {
        val w = parameters.w
        val s = max(stability, 1e-6)
        var sInc = exp(w[17] * (rating.value - 3 + w[18]) * s.pow(-w[19]))
        if (rating.value >= 3) sInc = max(sInc, 1.0)
        return s * sInc
    }

    /** Forgetting / lapse: FSRS-4.5/5/6 w[11..14], clamped to sMin = S / exp(w[17]*w[18]). */
    private fun nextForgetStability(stability: Double, difficulty: Double, r: Double): Double {
        val w = parameters.w
        val s = max(stability, 1e-6)
        val sMin = s / exp(w[17] * w[18])
        val result = w[11] * difficulty.pow(-w[12]) * ((s + 1.0).pow(w[13]) - 1.0) * exp(w[14] * (1.0 - r))
        return max(min(result, sMin), 1e-6)
    }

    /** Next interval (in days) for a given stability at the desired retention level. */
    private fun nextInterval(stability: Double): Int {
        val s = max(stability, 1e-6)
        val raw = (s / factor) * (desiredRetention.pow(1.0 / decay) - 1.0)
        return min(raw.toInt().coerceAtLeast(1), maxInterval)
    }

    fun getNextReviewTime(card: FSRSCard): Instant = card.due

    fun isDue(card: FSRSCard): Boolean = card.due <= Clock.System.now()
}
