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
    fun cardIds(selection: PlanSelection, cards: List<PlanCardRef>, decks: List<PlanDeckRef>): Set<String> {
        val activeDecks = decks.associateBy { it.id }
        val explicitCards = selection.cardIds.toSet()
        val selectedDecks = selection.deckIds.toSet()
        val selectedKnowledgeBases = selection.knowledgeBaseIds.toSet()
        return cards.asSequence()
            .filter { card ->
                val deck = activeDecks[card.deckId] ?: return@filter false
                card.id in explicitCards || card.deckId in selectedDecks ||
                    deck.knowledgeBaseId in selectedKnowledgeBases
            }
            .map { it.id }
            .toSet()
    }

    fun deckIds(selection: PlanSelection, cards: List<PlanCardRef>, decks: List<PlanDeckRef>): Set<String> {
        val eligibleCards = cardIds(selection, cards, decks)
        val activeDecks = decks.associateBy { it.id }
        val selectedDecks = selection.deckIds.toSet()
        val selectedKnowledgeBases = selection.knowledgeBaseIds.toSet()
        return decks.asSequence().filter {
            it.id in selectedDecks || it.knowledgeBaseId in selectedKnowledgeBases
        }.map { it.id }.toMutableSet().apply {
            cards.filter { it.id in eligibleCards && it.deckId in activeDecks }.forEach { add(it.deckId) }
        }
    }

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
