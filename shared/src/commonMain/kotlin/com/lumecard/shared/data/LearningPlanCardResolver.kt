package com.lumecard.shared.data

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.LearningPlan

/** A plan is the union of its explicit cards, decks and knowledge bases. */
data class PlanSelection(
    val knowledgeBaseIds: List<String>,
    val deckIds: List<String>,
    val cardIds: List<String>,
)

data class PlanCardRef(val id: String, val deckId: String)
data class PlanDeckRef(val id: String, val knowledgeBaseId: String)

object LearningPlanCardResolver {
    /**
     * The deck lookup shared by [cardIds] and [deckIds], built **once** per call site.
     *
     * `refreshPlans` recalculates every plan after each review write, so rebuilding
     * `decks.associateBy` per plan (and again inside `deckIds`, which delegates to `cardIds`) made
     * that path O(plans × decks). At the 20 000-deck cap that is 20 000 map insertions per plan,
     * per keystroke-free review — pure allocation for a lookup that is identical for every plan in
     * the batch.
     *
     * Thread-confined: built and consumed within one synchronous call.
     */
    class Scoped(private val decks: List<PlanDeckRef>) {

        private val deckById: Map<String, PlanDeckRef> = decks.associateBy { it.id }

        fun cardIds(selection: PlanSelection, cards: List<PlanCardRef>): Set<String> {
            val explicitCards = selection.cardIds.toSet()
            val selectedDecks = selection.deckIds.toSet()
            val selectedKnowledgeBases = selection.knowledgeBaseIds.toSet()
            return cards.asSequence()
                .filter { card ->
                    val deck = deckById[card.deckId] ?: return@filter false
                    card.id in explicitCards || card.deckId in selectedDecks ||
                        deck.knowledgeBaseId in selectedKnowledgeBases
                }
                .map { it.id }
                .toSet()
        }

        fun deckIds(selection: PlanSelection, cards: List<PlanCardRef>): Set<String> {
            val eligibleCards = cardIds(selection, cards)
            val selectedDecks = selection.deckIds.toSet()
            val selectedKnowledgeBases = selection.knowledgeBaseIds.toSet()
            // Iterates the caller's list (not `deckById.values`) so the result keeps the same
            // encounter order it had before this class existed.
            return decks.asSequence().filter {
                it.id in selectedDecks || it.knowledgeBaseId in selectedKnowledgeBases
            }.map { it.id }.toMutableSet().apply {
                cards.filter { it.id in eligibleCards && it.deckId in deckById }.forEach { add(it.deckId) }
            }
        }
    }

    fun cardIds(selection: PlanSelection, cards: List<PlanCardRef>, decks: List<PlanDeckRef>): Set<String> =
        Scoped(decks).cardIds(selection, cards)

    fun deckIds(selection: PlanSelection, cards: List<PlanCardRef>, decks: List<PlanDeckRef>): Set<String> =
        Scoped(decks).deckIds(selection, cards)

    fun selection(plan: LearningPlan) = PlanSelection(plan.knowledgeBaseIds, plan.deckIds, plan.cardIds)
    fun cardRefs(cards: List<Card>) = cards.filter { it.deletedAt == null }.map { PlanCardRef(it.id, it.deckId) }
    fun deckRefs(decks: List<Deck>) = decks.filter { it.deletedAt == null }.map { PlanDeckRef(it.id, it.knowledgeBaseId) }
}

/** Stored in AppSettings, but owned by data backup and excluded from config sync. */
object PlanProgressKeys {
    const val PREFIX = "learning_plan_progress_since:"
    const val MANUAL_COMPLETE_PREFIX = "learning_plan_manual_complete:"
    fun forPlan(id: String) = PREFIX + id
    fun manualComplete(id: String) = MANUAL_COMPLETE_PREFIX + id
    fun isPlanMetadata(key: String) = key.startsWith(PREFIX) || key.startsWith(MANUAL_COMPLETE_PREFIX)
}
