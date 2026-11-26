package com.lumecard.shared.data

import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.model.Media
import com.lumecard.shared.model.MediaType
import kotlin.time.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.lumecard.shared.database.Card as CardRow

/**
 * Database side of a scoped import.
 *
 * [plan] reads a live snapshot and delegates all decisions to the pure [ObjectImportPlanner].
 * [execute] applies the resulting plan inside **one** transaction: soft-delete the overwritten
 * sub-trees, insert the freshly-identified copy, repair FTS / algorithm state / learning plans,
 * and report only the newly written paths no card ended up referencing. Tombstones still count as
 * references, so their files are never reported. Any exception rolls the whole transaction back,
 * so a partial import is impossible at the database level.
 *
 * Deletions are soft (tombstones), matching every other delete path in the app and letting WebDAV
 * sync propagate them. Hard deletes would also break `ReviewLog.card_id` foreign keys.
 */
class ObjectImportService(
    private val database: LumeCardDatabase,
    private val planMutations: LearningPlanMutationService,
) {
    private val queries get() = database.lumeCardDatabaseQueries
    private val lenientJson = Json { ignoreUnknownKeys = true }

    /** Live-only snapshot used by the conflict resolver. */
    fun snapshot(): ObjectImportContext {
        val kbs = queries.selectAllKnowledgeBases().executeAsList().map {
            KnowledgeBase(
                id = it.id, name = it.name, description = it.description,
                icon = it.icon ?: "\uD83D\uDCC1",
            )
        }
        val decks = queries.selectAllDecks().executeAsList().map {
            Deck(
                id = it.id, knowledgeBaseId = it.knowledge_base_id, name = it.name,
                description = it.description, color = it.color ?: "#4CAF50",
                icon = it.icon ?: "\uD83D\uDCDA", parentId = it.parent_id,
            )
        }
        val cards = queries.selectAllCards().executeAsList().map {
            Card(
                id = it.id, deckId = it.deck_id,
                type = CardType.entries.firstOrNull { t -> t.name == it.type } ?: CardType.BASIC,
                front = it.front, back = it.back, title = it.title ?: "",
            )
        }
        return ObjectImportContext(kbs, decks, cards)
    }

    fun plan(export: ObjectExport, request: ObjectImportRequest): ObjectImportPlanResult =
        ObjectImportPlanner.buildPlan(export, request, snapshot(), Clock.System.now())

    /**
     * Delete counts as the user will experience them: the plan's seeds **plus the sub-trees**
     * [execute] soft-deletes for them.
     *
     * The plan only stores seeds (one knowledge base, one deck), so showing those numbers in the
     * confirmation dialog would tell a user "removes 1 knowledge base" right before the import
     * deletes that knowledge base's dozens of decks and thousands of cards.
     */
    fun expandedDeleteCounts(plan: ObjectImportPlan): ObjectDeleteCounts {
        val context = snapshot()
        val kbIds = LinkedHashSet(plan.deleteKnowledgeBaseIds)
        val deckIds = LinkedHashSet(plan.deleteDeckIds)

        if (kbIds.isNotEmpty()) {
            // One pass over the snapshot instead of one pass per knowledge base.
            context.decks.forEach { if (it.knowledgeBaseId in kbIds) deckIds += it.id }
        }
        // Decks recursively: a seed deck brings its descendants. Indexed walk, not a
        // "restart the whole list until a round adds nothing" sweep — the latter was O(n²) and
        // made this preview call slow on a 20 000-deck library every time the dialog opened.
        // Skipped entirely when there is no deck seed: a non-destructive preview must stay O(n).
        if (deckIds.isNotEmpty()) DeckTree.of(context.decks).expandDescendants(deckIds)

        val cardIds = LinkedHashSet(plan.deleteCardIds)
        context.cards.forEach { if (it.deckId in deckIds) cardIds += it.id }

        return ObjectDeleteCounts(
            knowledgeBases = kbIds.size,
            decks = deckIds.size,
            cards = cardIds.size,
        )
    }

    fun execute(
        plan: ObjectImportPlan,
        newlyWrittenPaths: List<String> = emptyList(),
    ): ObjectImportResult =
        database.transactionWithResult(noEnclosing = true) {
            val liveDecks = queries.selectAllDecks().executeAsList()
            val liveCards = queries.selectAllCards().executeAsList()
            val now = Clock.System.now().toString()
            val deletedCardIds = LinkedHashSet<String>()
            val deletedDeckIds = LinkedHashSet<String>()
            val deletedKbIds = LinkedHashSet<String>()

            // The plan comes from a snapshot taken before the media unpack, which for a large
            // archive can be tens of seconds. If the target was deleted meanwhile (WebDAV pull,
            // another window), inserting into it still succeeds at the SQL level — the FK row is
            // only soft-deleted — and the import reports "N cards" for rows the UI will never show.
            // Re-check here, inside the transaction, so the failure is atomic.
            //
            // Targets created *by this plan* (a new knowledge base / deck) are obviously absent
            // from the live snapshot, so they must be exempt: checking only against `live*` would
            // make every knowledge-base import, and every "create a new deck" import, fail with
            // TARGET_NOT_FOUND.
            val newKbIds = plan.knowledgeBases.mapTo(mutableSetOf()) { it.id }
            val newDeckIds = plan.decks.mapTo(mutableSetOf()) { it.id }
            val liveKbIdSet = queries.selectAllKnowledgeBases().executeAsList().mapTo(mutableSetOf()) { it.id }
            val liveDeckIdSet = liveDecks.mapTo(mutableSetOf()) { it.id }
            plan.targetKnowledgeBaseId?.let {
                if (it !in liveKbIdSet && it !in newKbIds) {
                    throw ObjectTransferException(ObjectTransferError.TARGET_NOT_FOUND)
                }
            }
            plan.targetDeckId?.let {
                if (it !in liveDeckIdSet && it !in newDeckIds) {
                    throw ObjectTransferException(ObjectTransferError.TARGET_NOT_FOUND)
                }
            }

            // Knowledge-base seeds: the whole live sub-tree goes away. Both indexes are built only
            // when there is something to delete — a plain "keep both" import must not pay for a
            // 20 000-deck index it will never read.
            if (plan.deleteKnowledgeBaseIds.isNotEmpty()) {
                val liveDeckIdsByKb = liveDecks.groupBy({ it.knowledge_base_id }, { it.id })
                for (kbId in plan.deleteKnowledgeBaseIds) {
                    deletedKbIds += kbId
                    deletedDeckIds += liveDeckIdsByKb[kbId].orEmpty()
                }
            }

            // Deck seeds: softDeleteCardsByDeck is not recursive, so expand the parent chain first.
            val deckSeeds = LinkedHashSet(plan.deleteDeckIds)
            if (deckSeeds.isNotEmpty()) {
                DeckTree.ofRows(liveDecks, { it.id }, { it.parent_id }).expandDescendants(deckSeeds)
            }
            deletedDeckIds += deckSeeds

            if (deletedDeckIds.isNotEmpty()) {
                liveCards.forEach { if (it.deck_id in deletedDeckIds) deletedCardIds += it.id }
            }

            // Explicit duplicate-card seeds (OVERWRITE policy).
            deletedCardIds += plan.deleteCardIds

            // Cards first, then decks, then knowledge bases. Soft deletes are UPDATEs, so the
            // foreign keys stay resolvable throughout; the order only keeps the log readable.
            for (cardId in deletedCardIds) {
                queries.softDeleteCard(now, now, cardId)
                queries.deleteCardFts(cardId)
                queries.deleteAlgorithmState(cardId)
            }
            for (deckId in deletedDeckIds) queries.softDeleteDeck(now, now, deckId)
            for (kbId in deletedKbIds) queries.softDeleteKnowledgeBase(now, now, kbId)

            // Insert the copy. Ids are fresh, so INSERT OR REPLACE can never touch a parent row.
            plan.knowledgeBases.forEach { kb ->
                queries.insertKnowledgeBase(
                    kb.id, kb.name, kb.description, kb.icon,
                    kb.createdAt.toString(), kb.updatedAt.toString(), kb.version,
                    kb.deletedAt?.toString(), kb.syncedAt?.toString(),
                )
            }
            // Planner guarantees parents-first ordering.
            plan.decks.forEach { deck ->
                queries.insertDeck(
                    deck.id, deck.knowledgeBaseId, deck.name, deck.description,
                    deck.color, deck.icon, deck.parentId,
                    deck.createdAt.toString(), deck.updatedAt.toString(), deck.version,
                    deck.deletedAt?.toString(), deck.syncedAt?.toString(),
                )
            }
            plan.cards.forEach { card ->
                queries.insertCard(
                    card.id, card.deckId, card.type.name, card.title, card.front, card.back, card.explanation,
                    lenientJson.encodeToString(card.tags), lenientJson.encodeToString(card.media),
                    lenientJson.encodeToString(card.metadata),
                    card.createdAt.toString(), card.updatedAt.toString(),
                    card.lastReviewedAt?.toString(), card.nextReviewAt?.toString(),
                    card.version, card.deletedAt?.toString(), card.syncedAt?.toString(),
                )
                queries.deleteCardFts(card.id)
                queries.insertCardFts(card.id, card.front, card.back, card.tags.joinToString(" "))
            }

            // Re-point plans at the copies, then recompute. Membership transfer is what makes
            // "overwrite" preserve the user's study plan instead of emptying it.
            val scrubbedPlans = planMutations.contentReplaced(
                knowledgeBaseIdMap = plan.knowledgeBaseIdMap,
                deckIdMap = plan.deckIdMap,
                cardIdMap = plan.cardIdMap,
                deletedKnowledgeBaseIds = deletedKbIds,
                deletedDeckIds = deletedDeckIds,
                deletedCardIds = deletedCardIds,
            )

            // Paths still referenced once the import is in place. Used to decide which files this
            // import wrote but nothing can reach (every card was a skipped duplicate).
            //
            // There is deliberately no "orphaned media" set: every card this import removes is
            // *soft* deleted, and a tombstone still stores its `media` array, so a file that lost
            // its live owner is still referenced. Nothing becomes an orphan here, and the files
            // therefore stay on disk — which is what sync needs to restore the row later.
            //
            // Only computed when the caller actually wrote files. `refsOfRows` JSON-decodes the
            // `media` column of every live card, so an import that unpacked nothing (the common
            // case: a scoped import with no attachments) would pay an O(100 000) decode to produce
            // an empty answer.
            val unusedNewMediaPaths = if (newlyWrittenPaths.isEmpty()) {
                emptyList()
            } else {
                // Include tombstones, including the rows soft-deleted by this very import: their
                // media column is deliberately retained so a later sync can restore the card, and
                // deleting the file now would break that restore. Only rows with no reference in
                // any live *or* deleted card are truly ours to clean up.
                val afterRefs = LinkedHashSet(refsOfRows(queries.selectBackupCards().executeAsList()))
                afterRefs += refsOfCards(plan.cards)
                // Compared case-insensitively: `abc.png` and `ABC.png` are one file on Windows.
                val afterKeys = afterRefs.mapTo(mutableSetOf()) { it.lowercase() }
                newlyWrittenPaths.filterNot { it.lowercase() in afterKeys }
            }

            ObjectImportResult(
                importedKnowledgeBases = plan.knowledgeBases.size,
                importedDecks = plan.decks.size,
                importedCards = plan.cards.size,
                deletedKnowledgeBases = deletedKbIds.size,
                deletedDecks = deletedDeckIds.size,
                deletedCards = deletedCardIds.size,
                unusedNewMediaPaths = unusedNewMediaPaths,
                scrubbedPlans = scrubbedPlans,
            )
        }

    /**
     * Media-root-relative paths referenced by the given rows.
     * Mirrors `LumecardArchive.isArchivableMediaPath` (shared cannot depend on composeApp).
     */
    private fun refsOfRows(rows: List<CardRow>): Set<String> {
        val paths = LinkedHashSet<String>()
        rows.forEach { row ->
            val media: List<Media> = try {
                lenientJson.decodeFromString(row.media ?: "[]")
            } catch (_: Exception) {
                emptyList()
            }
            media.forEach { addMediaPath(paths, it) }
        }
        return paths
    }

    private fun refsOfCards(cards: List<Card>): Set<String> {
        val paths = LinkedHashSet<String>()
        cards.forEach { card -> card.media.forEach { addMediaPath(paths, it) } }
        return paths
    }

    private fun addMediaPath(target: MutableSet<String>, item: Media) {
        if (item.type != MediaType.IMAGE) return
        val path = item.filePath.trim()
        if (isArchivableMediaPath(path)) target += path
    }

    private fun isArchivableMediaPath(path: String): Boolean =
        MediaPathRules.isSafeRelativePath(path)
}
