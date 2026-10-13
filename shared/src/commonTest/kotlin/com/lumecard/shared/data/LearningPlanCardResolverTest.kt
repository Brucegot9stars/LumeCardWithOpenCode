package com.lumecard.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals

class LearningPlanCardResolverTest {
    private val decks = listOf(PlanDeckRef("d1", "kb"), PlanDeckRef("d2", "other"))
    private val cards = listOf(PlanCardRef("c1", "d1"), PlanCardRef("c2", "d2"), PlanCardRef("orphan", "deleted"))

    @Test
    fun overlappingSelectionsAreCountedOnceAndMissingRowsExcluded() {
        val selection = PlanSelection(listOf("kb"), listOf("d1"), listOf("c1", "c2", "orphan"))
        assertEquals(setOf("c1", "c2"), LearningPlanCardResolver.cardIds(selection, cards, decks))
        assertEquals(setOf("d1", "d2"), LearningPlanCardResolver.deckIds(selection, cards, decks))
    }

    @Test
    fun explicitCardOnlyPlanIncludesItsDeckWithoutExpandingToAllCards() {
        val selection = PlanSelection(emptyList(), emptyList(), listOf("c2"))
        assertEquals(setOf("c2"), LearningPlanCardResolver.cardIds(selection, cards, decks))
        assertEquals(setOf("d2"), LearningPlanCardResolver.deckIds(selection, cards, decks))
    }
}
