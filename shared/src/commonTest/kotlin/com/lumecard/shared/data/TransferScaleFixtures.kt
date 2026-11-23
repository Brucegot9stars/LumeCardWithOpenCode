package com.lumecard.shared.data

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import kotlin.time.Instant

/**
 * Deterministic fixtures for the scale tests.
 *
 * The documented caps are `MAX_KNOWLEDGE_BASES = 5_000`, `MAX_DECKS = 20_000`,
 * `MAX_CARDS = 100_000`. The planner used to run a full scan per collision lookup, so a library at
 * those caps meant hundreds of millions of trimmed-string comparisons before the first row was
 * written. These builders make that shape cheap to reproduce in a unit test (pure data, no
 * database) so a regression back to a sweep loop shows up as a timeout instead of a shipped freeze.
 */
internal object TransferScaleFixtures {
    val t: Instant = Instant.parse("2026-01-01T00:00:00Z")
    const val DATE: String = "2026-01-01T00:00:00Z"

    fun kb(id: String, name: String) =
        KnowledgeBase(id = id, name = name, createdAt = t, updatedAt = t)

    fun deck(id: String, kbId: String, name: String, parentId: String? = null) =
        Deck(id = id, knowledgeBaseId = kbId, name = name, parentId = parentId, createdAt = t, updatedAt = t)

    fun card(id: String, deckId: String, front: String, back: String) =
        Card(id = id, deckId = deckId, type = CardType.BASIC, front = front, back = back, createdAt = t, updatedAt = t)

    /**
     * `branching ^ depth` decks under one knowledge base, ordered parents-first.
     *
     * Defaults give 20 000 decks in exactly 20 000 rows — the cap — which is the shape the old
     * planner handled worst: every level restarted the sweep, so the cost grew with depth while the
     * row count stayed flat.
     */
    fun balancedTree(
        kbId: String,
        deckCount: Int,
        branching: Int = 10,
        idPrefix: String = "deck",
        namePrefix: String = "Deck",
    ): List<Deck> {
        require(branching > 1) { "branching must be > 1" }
        val decks = ArrayList<Deck>(deckCount)
        decks += deck("$idPrefix-0", kbId, "$namePrefix 0")
        var next = 1
        var parentCursor = 0
        while (next < deckCount) {
            val parent = decks[parentCursor]
            parentCursor++
            repeat(branching) {
                if (next >= deckCount) return@repeat
                decks += deck(
                    id = "$idPrefix-$next",
                    kbId = kbId,
                    name = "${parent.name}/$namePrefix $next",
                    parentId = parent.id,
                )
                next++
            }
        }
        return decks
    }

    /** A single chain of [depth] decks; worst case for "walk to the root" helpers. */
    fun chain(kbId: String, depth: Int, idPrefix: String = "deep"): List<Deck> {
        val decks = ArrayList<Deck>(depth)
        var parentId: String? = null
        for (index in 0 until depth) {
            val deck = deck("$idPrefix-$index", kbId, "Level $index", parentId)
            decks += deck
            parentId = deck.id
        }
        return decks
    }

    fun cardsIn(decks: List<Deck>, perDeck: Int, idPrefix: String = "card"): List<Card> {
        val cards = ArrayList<Card>(decks.size * perDeck)
        decks.forEachIndexed { deckIndex, deck ->
            repeat(perDeck) { index ->
                val serial = deckIndex * perDeck + index
                cards += card(
                    id = "$idPrefix-$serial",
                    deckId = deck.id,
                    front = "Q$serial",
                    back = "A$serial",
                )
            }
        }
        return cards
    }

    fun exportOf(
        type: ObjectType,
        roots: List<String>,
        kbs: List<KnowledgeBase>,
        decks: List<Deck>,
        cards: List<Card>,
    ): ObjectExport = (ObjectExportBuilder.build(type, roots, kbs, decks, cards, DATE)
        as ObjectBuildResult.Success).data.export
}