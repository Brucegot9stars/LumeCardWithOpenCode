package com.lumecard.app.ui.screens.study

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class StudyCardSelectorTest {
    private val now = Instant.parse("2026-01-02T00:00:00Z")
    private fun card(id: String, last: Instant? = null, next: Instant? = null) = Card(
        id = id, deckId = "deck", type = CardType.BASIC, front = id, back = id,
        lastReviewedAt = last, nextReviewAt = next,
    )

    @Test
    fun newModeDoesNotFallBackToReviewedCards() {
        val reviewed = card("reviewed", last = now, next = now)
        assertTrue(selectStudyCards(listOf(reviewed), CardsStudyMode.NEW_CARDS, now, 20).isEmpty())
        assertTrue(selectStudyCards(listOf(card("new")), CardsStudyMode.NEW_CARDS, now, 0).isEmpty())
    }

    @Test
    fun dueOnlyAndDueFirstHaveDifferentEmptyFallbacks() {
        val future = card("future", last = now, next = Instant.parse("2026-01-03T00:00:00Z"))
        assertTrue(selectStudyCards(listOf(future), CardsStudyMode.DUE_ONLY, now, 20).isEmpty())
        assertEquals(listOf(future), selectStudyCards(listOf(future), CardsStudyMode.DUE_FIRST, now, 20))

        val new = card("new")
        val due = card("due", last = now, next = now)
        assertEquals(listOf(due), selectStudyCards(listOf(new, due, future), CardsStudyMode.DUE_ONLY, now, 20))
        assertEquals(listOf(new, due), selectStudyCards(listOf(new, due, future), CardsStudyMode.DUE_FIRST, now, 20))
    }

    @Test
    fun randomModeRespectsLimit() {
        val cards = listOf(card("a"), card("b"), card("c"))
        val selected = selectStudyCards(cards, CardsStudyMode.RANDOM, now, 2)
        assertEquals(2, selected.size)
        assertTrue(selected.all { it in cards })
    }
}
