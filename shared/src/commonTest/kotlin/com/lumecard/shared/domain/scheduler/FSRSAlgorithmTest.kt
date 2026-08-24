package com.lumecard.shared.domain.scheduler

import com.lumecard.shared.model.CardState
import com.lumecard.shared.model.FSRSCard
import com.lumecard.shared.model.Rating
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FSRSAlgorithmTest {

    private val algo = FSRSAlgorithm()

    private fun matureCard(stability: Double, difficulty: Double, lapses: Int = 0) = FSRSCard(
        id = "c",
        due = Clock.System.now(),
        stability = stability,
        difficulty = difficulty,
        elapsedDays = 0,
        scheduledDays = stability.toInt().coerceAtLeast(1),
        reps = 3,
        lapses = lapses,
        state = CardState.REVIEW
    )

    // region Initial review (NEW card)

    @Test
    fun `new card GOOD sets initial stability 2_3065 and graduates`() {
        val card = algo.initCard()
        val updated = algo.schedule(card, Rating.GOOD)
        // FSRS-6 initial stability S0(G) = w[G-1]; GOOD(G=3) -> w[2] = 2.3065
        assertEquals(2.3065, updated.stability, 1e-9, "GOOD initial stability must be w[2]=2.3065")
        // FSRS-6 initial difficulty D0(G) = w4 - exp(w5*(G-1)) + 1; GOOD -> ~2.1184
        assertEquals(2.1184, updated.difficulty, 1e-3, "GOOD initial difficulty D0(3)")
        assertEquals(CardState.REVIEW, updated.state, "first non-AGAIN should graduate to REVIEW")
        assertEquals(2, updated.scheduledDays, "interval at R=0.9 should equal stability (~2.3 -> 2)")
        assertEquals(0, updated.lapses)
    }

    @Test
    fun `new card EASY uses w3 stability and is easier`() {
        val updated = algo.schedule(algo.initCard(), Rating.EASY)
        // EASY(G=4) -> w[3] = 8.2956
        assertEquals(8.2956, updated.stability, 1e-9, "EASY initial stability must be w[3]=8.2956")
        // D0(4) clamps to 1.0 for the default FSRS-6 weights
        assertEquals(1.0, updated.difficulty, 1e-9, "EASY initial difficulty clamps to 1.0")
        assertEquals(CardState.REVIEW, updated.state)
        assertTrue(updated.scheduledDays > 2, "EASY should schedule longer than GOOD")
    }

    @Test
    fun `new card AGAIN keeps learning and gets small stability`() {
        val updated = algo.schedule(algo.initCard(), Rating.AGAIN)
        // AGAIN(G=1) -> w[0] = 0.212
        assertEquals(0.212, updated.stability, 1e-9, "AGAIN initial stability must be w[0]=0.212")
        assertEquals(CardState.LEARNING, updated.state)
        assertEquals(1, updated.scheduledDays)
        assertEquals(0, updated.lapses, "a brand-new card failing is not a lapse yet")
    }

    // endregion

    // region Mature review uses elapsed days

    @Test
    fun `mature GOOD on time increases stability`() {
        val card = matureCard(10.0, 5.0)
        // daysElapsed == scheduled interval (~stability) => R ~ 0.9
        val updated = algo.schedule(card, Rating.GOOD, daysElapsed = 10)
        assertTrue(updated.stability > card.stability, "good recall on time must grow stability")
        assertTrue(updated.stability in 25.0..35.0, "expected ~32 at R=0.9, got ${updated.stability}")
        assertTrue(updated.scheduledDays >= 20, "next interval should be substantially longer")
        assertEquals(CardState.REVIEW, updated.state)
    }

    @Test
    fun `reviewing early grows stability less than on time`() {
        val card = matureCard(10.0, 5.0)
        val onTime = algo.schedule(card, Rating.GOOD, daysElapsed = 10)
        val early = algo.schedule(card, Rating.GOOD, daysElapsed = 1)
        assertTrue(early.stability < onTime.stability, "daysElapsed must affect the stability update")
    }

    @Test
    fun `mature AGAIN lapses and drops stability`() {
        val card = matureCard(10.0, 5.0, lapses = 0)
        val updated = algo.schedule(card, Rating.AGAIN, daysElapsed = 10)
        assertEquals(CardState.RELEARNING, updated.state)
        assertEquals(1, updated.lapses, "forgetting a mature card must count a lapse")
        assertTrue(updated.stability < card.stability, "lapse must reduce stability")
        assertEquals(1, updated.scheduledDays)
    }

    @Test
    fun `lapse of a mature card yields positive finite stability`() {
        val card = matureCard(50.0, 5.0, lapses = 2)
        val updated = algo.schedule(card, Rating.AGAIN, daysElapsed = 50)
        assertTrue(updated.stability > 0.0, "lapse stability must stay positive")
        assertTrue(updated.stability.isFinite(), "lapse stability must be finite")
        assertTrue(updated.stability < card.stability)
    }

    // endregion

    // region Difficulty bounds

    @Test
    fun `difficulty stays bounded across many easy reviews`() {
        var card = matureCard(10.0, 5.0)
        repeat(20) { card = algo.schedule(card, Rating.EASY, daysElapsed = card.scheduledDays) }
        assertTrue(card.difficulty >= 1.0, "difficulty must not drop below 1")
        assertTrue(card.difficulty <= 10.0, "difficulty must not exceed 10")
    }

    @Test
    fun `difficulty stays bounded across many again reviews`() {
        var card = matureCard(10.0, 5.0)
        repeat(20) { card = algo.schedule(card, Rating.AGAIN, daysElapsed = card.scheduledDays) }
        assertTrue(card.difficulty >= 1.0, "difficulty must not drop below 1")
        assertTrue(card.difficulty <= 10.0, "difficulty must not exceed 10")
        assertEquals(20, card.lapses, "every AGAIN on a mature card is a lapse")
    }

    // endregion

    // region Same-day review (FSRS-6 w17/w18/w19 growth term)

    @Test
    fun `same day review grows stability via w17-w19 term`() {
        // NEW -> AGAIN enters LEARNING with S0(Again) = 0.212
        val learning = algo.schedule(algo.initCard(), Rating.AGAIN)
        assertEquals(CardState.LEARNING, learning.state)
        assertEquals(0.212, learning.stability, 1e-9)
        // Reviewing again the same day (daysElapsed = 0) with GOOD must grow stability.
        val updated = algo.schedule(learning, Rating.GOOD, daysElapsed = 0)
        assertTrue(updated.stability > learning.stability, "same-day GOOD must grow stability via w17-w19")
        assertEquals(CardState.REVIEW, updated.state)
    }

    // endregion

    // region Relearning graduation

    @Test
    fun `relearning GOOD graduates back to review`() {
        val card = matureCard(10.0, 5.0).copy(state = CardState.RELEARNING, lapses = 1)
        val updated = algo.schedule(card, Rating.GOOD, daysElapsed = 1)
        assertEquals(CardState.REVIEW, updated.state)
        assertTrue(updated.stability > 0.0)
    }

    // endregion

    // region Adapter round-trip

    @Test
    fun `adapter preserves stability and difficulty through AlgorithmState`() {
        val adapter = FSRSAlgorithmAdapter(algo)
        val state = AlgorithmState(
            intervalDays = 10,
            nextReviewAt = Clock.System.now(),
            repetitions = 3,
            lapses = 0,
            easeFactor = 5f,
            stage = CardState.REVIEW.ordinal,
            stability = 10.0,
            difficulty = 5.0
        )
        val updated = adapter.schedule(state, Rating.GOOD, daysElapsed = 10)
        val direct = algo.schedule(matureCard(10.0, 5.0), Rating.GOOD, daysElapsed = 10)
        assertEquals(direct.stability, updated.stability, 1e-9, "adapter must not alter the FSRS math")
        assertEquals(direct.difficulty, updated.difficulty, 1e-9)
        assertEquals(direct.scheduledDays, updated.intervalDays)
    }

    // endregion
}
