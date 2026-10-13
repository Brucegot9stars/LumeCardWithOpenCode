package com.lumecard.app.ui.screens.study

import com.lumecard.shared.model.Card
import kotlin.time.Instant

/** Session selection, kept pure so mode edge cases are covered without a UI runtime. */
internal fun selectStudyCards(cards: List<Card>, mode: CardsStudyMode, now: Instant, limit: Int): List<Card> =
    when (mode) {
        CardsStudyMode.DUE_FIRST -> cards.filter { it.nextReviewAt == null || it.nextReviewAt <= now }
            .ifEmpty { cards }
        CardsStudyMode.DUE_ONLY -> cards.filter { it.nextReviewAt != null && it.nextReviewAt <= now }
        CardsStudyMode.ALL_CARDS -> cards
        CardsStudyMode.NEW_CARDS -> cards.filter { it.lastReviewedAt == null }.shuffled().take(limit.coerceAtLeast(0))
        CardsStudyMode.RANDOM -> cards.shuffled().take(limit.coerceAtLeast(0))
    }
