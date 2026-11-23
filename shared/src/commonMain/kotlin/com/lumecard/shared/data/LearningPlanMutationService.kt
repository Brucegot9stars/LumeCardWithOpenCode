package com.lumecard.shared.data

import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.model.LearningPlan
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/** Small database commands that must remain atomic across plan and content tables. */
class LearningPlanMutationService(
    private val database: LumeCardDatabase,
    private val reviewService: ReviewService,
) {
    private val queries get() = database.lumeCardDatabaseQueries
    private val json = Json { ignoreUnknownKeys = true }

    fun create(plan: LearningPlan) {
        database.transaction(noEnclosing = true) {
            if (plan.isDefault) queries.clearOtherDefaultLearningPlans(Clock.System.now().toString(), plan.id)
            queries.insertLearningPlan(plan.id, plan.name, plan.description, plan.status.name,
                if (plan.isDefault) 1L else 0L, json.encodeToString(plan.knowledgeBaseIds),
                json.encodeToString(plan.deckIds), json.encodeToString(plan.cardIds),
                plan.totalCards.toLong(), plan.completedCards.toLong(), plan.createdAt.toString(),
                plan.updatedAt.toString(), plan.version, plan.deletedAt?.toString(), null)
            reviewService.reconcilePlanInCurrentTransaction(plan.id)
        }
    }

    fun update(plan: LearningPlan) {
        database.transaction(noEnclosing = true) {
            queries.selectLearningPlanById(plan.id).executeAsOneOrNull()
                ?.takeIf { it.deleted_at == null } ?: return@transaction
            if (plan.isDefault) queries.clearOtherDefaultLearningPlans(Clock.System.now().toString(), plan.id)
            updateRow(plan)
            reviewService.reconcilePlanInCurrentTransaction(plan.id)
        }
    }

    fun setDefault(planId: String) {
        database.transaction(noEnclosing = true) {
            val plan = queries.selectLearningPlanById(planId).executeAsOneOrNull()
                ?.takeIf { it.deleted_at == null } ?: return@transaction
            val now = Clock.System.now().toString()
            queries.clearOtherDefaultLearningPlans(now, plan.id)
            if (plan.is_default != 1L) queries.setLearningPlanDefault(1L, now, plan.id)
        }
    }

    fun unsetDefault(planId: String) {
        database.transaction(noEnclosing = true) {
            val plan = queries.selectLearningPlanById(planId).executeAsOneOrNull()
                ?.takeIf { it.deleted_at == null } ?: return@transaction
            if (plan.is_default == 1L) queries.setLearningPlanDefault(0L, Clock.System.now().toString(), planId)
        }
    }

    fun start(planId: String) {
        queries.startLearningPlan(Clock.System.now().toString(), planId)
    }

    fun delete(planId: String) {
        database.transaction(noEnclosing = true) {
            val plan = queries.selectLearningPlanById(planId).executeAsOneOrNull()
                ?.takeIf { it.deleted_at == null } ?: return@transaction
            val now = Clock.System.now().toString()
            queries.softDeleteLearningPlan(now, now, planId)
            if (plan.is_default == 1L) {
                val replacement = queries.selectAllLearningPlans().executeAsList().firstOrNull()
                replacement?.let { queries.setLearningPlanDefault(1L, now, it.id) }
            }
        }
    }

    /** Invoked by content repositories before their enclosing transaction commits. */
    internal fun contentChanged() = reviewService.reconcileAllInCurrentTransaction()

    /**
     * Re-point learning plans at the freshly copied objects, then recompute every plan.
     *
     * Learning plans store member ids as JSON arrays with no foreign keys, so a scoped import that
     * overwrites a knowledge base / deck / card must **transfer** membership, not just drop it:
     * filtering alone would empty a user's plan on every overwrite. Ids with a replacement are
     * rewritten to the new id; ids that genuinely disappeared are scrubbed.
     *
     * Runs as a joinable transaction, so it works both standalone and inside the importer's
     * transaction, and the recompute always sees the final rows.
     *
     * @return number of plans whose id arrays were rewritten.
     */
    internal fun contentReplaced(
        knowledgeBaseIdMap: Map<String, String> = emptyMap(),
        deckIdMap: Map<String, String> = emptyMap(),
        cardIdMap: Map<String, String> = emptyMap(),
        deletedKnowledgeBaseIds: Set<String> = emptySet(),
        deletedDeckIds: Set<String> = emptySet(),
        deletedCardIds: Set<String> = emptySet(),
    ): Int = database.transactionWithResult(noEnclosing = false) {
        val now = Clock.System.now().toString()
        var scrubbed = 0
        queries.selectAllLearningPlans().executeAsList().forEach { plan ->
            val kbIds = decodeIds(plan.knowledge_base_ids)
            val deckIds = decodeIds(plan.deck_ids)
            val cardIds = decodeIds(plan.card_ids)
            val newKbIds = remap(kbIds, knowledgeBaseIdMap, deletedKnowledgeBaseIds)
            val newDeckIds = remap(deckIds, deckIdMap, deletedDeckIds)
            val newCardIds = remap(cardIds, cardIdMap, deletedCardIds)
            if (newKbIds != kbIds || newDeckIds != deckIds || newCardIds != cardIds) {
                queries.updateLearningPlan(
                    plan.name, plan.description, plan.status, plan.is_default,
                    json.encodeToString(newKbIds), json.encodeToString(newDeckIds),
                    json.encodeToString(newCardIds), plan.total_cards, plan.completed_cards,
                    now, plan.deleted_at, null, plan.id,
                )
                scrubbed++
            }
        }
        contentChanged()
        scrubbed
    }

    /** A corrupt array must not abort an import: treat it as empty and leave the row untouched. */
    private fun decodeIds(raw: String?): List<String> = try {
        json.decodeFromString<List<String>>(raw ?: "[]")
    } catch (_: Exception) {
        emptyList()
    }

    /** Replace-then-scrub, preserving order and dropping duplicates introduced by a merge. */
    private fun remap(
        ids: List<String>,
        replacements: Map<String, String>,
        deleted: Set<String>,
    ): List<String> {
        if (replacements.isEmpty() && deleted.isEmpty()) return ids
        val result = LinkedHashSet<String>(ids.size)
        for (id in ids) {
            val replacement = replacements[id]
            when {
                replacement != null -> result += replacement
                id in deleted -> Unit
                else -> result += id
            }
        }
        return result.toList()
    }

    internal fun deckDeleted(deckId: String) {
        val now = Clock.System.now()
        queries.selectAllLearningPlans().executeAsList().forEach { plan ->
            val ids = decodeIds(plan.deck_ids)
            if (deckId in ids) updateRawPlan(plan, deckIds = ids - deckId, updatedAt = now.toString())
        }
        contentChanged()
    }

    internal fun knowledgeBaseDeleted(kbId: String) {
        val now = Clock.System.now()
        val deletedDeckIds = queries.selectBackupDecks().executeAsList()
            .filter { it.knowledge_base_id == kbId }.map { it.id }.toSet()
        queries.selectAllLearningPlans().executeAsList().forEach { plan ->
            // decodeIds(), not a bare decode: one corrupt row must not make every subsequent deck
            // or knowledge-base deletion throw, which is what a raw decodeFromString does here.
            val ids = decodeIds(plan.knowledge_base_ids)
            val deckIds = decodeIds(plan.deck_ids)
            if (kbId in ids || deckIds.any { it in deletedDeckIds }) {
                updateRawPlan(plan, knowledgeBaseIds = ids - kbId,
                    deckIds = deckIds.filterNot { it in deletedDeckIds }, updatedAt = now.toString())
            }
        }
        contentChanged()
    }

    private fun updateRow(plan: LearningPlan) {
        queries.updateLearningPlan(plan.name, plan.description, plan.status.name,
            if (plan.isDefault) 1L else 0L, json.encodeToString(plan.knowledgeBaseIds),
            json.encodeToString(plan.deckIds), json.encodeToString(plan.cardIds),
            plan.totalCards.toLong(), plan.completedCards.toLong(), plan.updatedAt.toString(),
            plan.deletedAt?.toString(), null, plan.id)
    }

    private fun updateRawPlan(
        plan: com.lumecard.shared.database.LearningPlan,
        knowledgeBaseIds: List<String> = json.decodeFromString(plan.knowledge_base_ids ?: "[]"),
        deckIds: List<String> = json.decodeFromString(plan.deck_ids ?: "[]"),
        updatedAt: String,
    ) {
        queries.updateLearningPlan(plan.name, plan.description, plan.status, plan.is_default,
            json.encodeToString(knowledgeBaseIds), json.encodeToString(deckIds), plan.card_ids ?: "[]",
            plan.total_cards, plan.completed_cards, updatedAt, plan.deleted_at, null, plan.id)
    }
}
