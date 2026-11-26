package com.lumecard.shared.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.database.initializeDesktopSchema
import com.lumecard.shared.domain.scheduler.FSRSAlgorithm
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Integration tests for the transaction that actually applies a scoped import.
 *
 * These exist because the planner is unit-tested in `commonTest` but `execute()` was not: a target
 * check that ignored plan-created ids, and learning-plan id maps keyed only by the *file's* ids,
 * both passed planning and then broke at commit time. Both regressions are covered here.
 */
class ObjectImportServiceTest {
    private val date = "2026-01-01T00:00:00Z"
    private val t = Instant.parse(date)
    private val json = Json { ignoreUnknownKeys = true }

    private fun kb(id: String, name: String) =
        KnowledgeBase(id = id, name = name, createdAt = t, updatedAt = t)

    private fun deck(id: String, kbId: String, name: String, parentId: String? = null) =
        Deck(id = id, knowledgeBaseId = kbId, name = name, parentId = parentId, createdAt = t, updatedAt = t)

    private fun card(id: String, deckId: String, front: String, back: String) =
        Card(id = id, deckId = deckId, type = CardType.BASIC, front = front, back = back, createdAt = t, updatedAt = t)

    private fun export(
        type: ObjectType,
        roots: List<String>,
        kbs: List<KnowledgeBase>,
        decks: List<Deck>,
        cards: List<Card>,
    ): ObjectExport = (ObjectExportBuilder.build(type, roots, kbs, decks, cards, date)
        as ObjectBuildResult.Success).data.export

    private fun withDatabase(
        plans: List<ExportLearningPlan> = emptyList(),
        block: (LumeCardDatabase, ObjectImportService) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            initializeDesktopSchema(driver)
            val db = LumeCardDatabase(driver)
            BackupRestoreService(db).replace(
                DataExport(
                    exportDate = date,
                    backupFormatVersion = 2,
                    algorithmStates = emptyList(),
                    knowledgeBases = listOf(
                        ExportKnowledgeBase("kb-old", "Alpha", createdAt = date, updatedAt = date),
                    ),
                    decks = listOf(
                        // All named on purpose: `ExportDeck`'s 4th positional parameter is
                        // `description`, not `parentId`, and getting that wrong silently flattens
                        // the tree this fixture exists to provide.
                        ExportDeck(
                            id = "deck-old", knowledgeBaseId = "kb-old", name = "Root",
                            description = null, color = "#4CAF50", icon = "📚",
                            createdAt = date, updatedAt = date,
                        ),
                        ExportDeck(
                            id = "deck-child", knowledgeBaseId = "kb-old", name = "Child",
                            description = null, color = "#4CAF50", icon = "📚",
                            parentId = "deck-old", createdAt = date, updatedAt = date,
                        ),
                    ),
                    cards = listOf(
                        ExportCard("card-old", "deck-old", "BASIC", "Q", "A", tags = emptyList(),
                            createdAt = date, updatedAt = date),
                        ExportCard("card-child", "deck-child", "BASIC", "Q2", "A2", tags = emptyList(),
                            createdAt = date, updatedAt = date),
                    ),
                    learningPlans = plans,
                )
            )
            val review = ReviewService(db, FSRSAlgorithm())
            val service = ObjectImportService(db, LearningPlanMutationService(db, review))
            block(db, service)
        } finally {
            driver.close()
        }
    }

    private fun knowledgeBaseIdsOf(db: LumeCardDatabase, planId: String): List<String> =
        json.decodeFromString(
            db.lumeCardDatabaseQueries.selectLearningPlanById(planId).executeAsOne().knowledge_base_ids
                ?: "[]"
        )

    @Test
    fun knowledgeBaseImportIntoAnEmptyLibraryCreatesTheWholeSubtree() = withDatabase { db, service ->
        // Guards the "target must still exist" check: a knowledge-base import wants to land in the
        // knowledge base it is creating, which by definition is not in the pre-insert snapshot.
        val kbs = listOf(kb("k1", "Imported"))
        val decks = listOf(deck("d1", "k1", "Root"), deck("d2", "k1", "Child", "d1"))
        val cards = listOf(card("c1", "d1", "Q1", "A1"), card("c2", "d2", "Q2", "A2"))
        val built = export(ObjectType.KNOWLEDGE_BASE, listOf("k1"), kbs, decks, cards)

        val plan = (service.plan(built, ObjectImportRequest()) as ObjectImportPlanResult.Success).plan
        val result = service.execute(plan)

        assertEquals(1, result.importedKnowledgeBases)
        assertEquals(2, result.importedDecks)
        assertEquals(2, result.importedCards)
        val queries = db.lumeCardDatabaseQueries
        assertTrue(queries.selectAllKnowledgeBases().executeAsList().any { it.name == "Imported" })
        val fixtureCards = setOf("card-old", "card-child")
        assertEquals(2, queries.selectAllCards().executeAsList().count { it.id !in fixtureCards })
    }

    @Test
    fun cardImportIntoANewDeckUsesTheDeckItJustCreated() = withDatabase { _, service ->
        // Same regression on the deck side: `targetDeckId` is a fresh id, not a live row.
        val kbs = listOf(kb("k1", "Source"))
        val decks = listOf(deck("d1", "k1", "Source Deck"))
        val cards = listOf(card("c1", "d1", "Q", "A"))
        val built = export(ObjectType.CARD, listOf("c1"), kbs, decks, cards)

        val request = ObjectImportRequest(newKnowledgeBaseName = "Fresh", newDeckName = "Fresh Deck")
        val plan = (service.plan(built, request) as ObjectImportPlanResult.Success).plan
        val result = service.execute(plan)

        assertEquals(1, result.importedCards)
        assertEquals(1, result.importedDecks)
    }

    @Test
    fun overwriteKeepsTheKnowledgeBaseSelectionOfALearningPlan() = withDatabase(
        plans = listOf(
            ExportLearningPlan("plan", "Plan", isDefault = true, knowledgeBaseIds = listOf("kb-old"),
                totalCards = 1, createdAt = date, updatedAt = date),
        ),
    ) { db, service ->
        // `kbIdMap` is keyed by the ids inside the file. A plan stores *local* ids, so without a
        // local→new entry the overwrite simply pruned the selection and emptied the plan.
        val kbs = listOf(kb("k1", "Alpha"))
        val decks = listOf(deck("d1", "k1", "Root"))
        val cards = listOf(card("c1", "d1", "Q", "A"))
        val built = export(ObjectType.KNOWLEDGE_BASE, listOf("k1"), kbs, decks, cards)

        val request = ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.OVERWRITE)
        val plan = (service.plan(built, request) as ObjectImportPlanResult.Success).plan
        service.execute(plan)

        val ids = knowledgeBaseIdsOf(db, "plan")
        assertEquals(1, ids.size, "the overwritten knowledge base must be remapped, not pruned")
        assertNotEquals("kb-old", ids.single())
        assertTrue(
            db.lumeCardDatabaseQueries.selectAllKnowledgeBases().executeAsList().any { it.id == ids.single() },
            "the remapped id must point at the copy that was just inserted",
        )
    }

    @Test
    fun overwriteKeepsTheDeckSelectionOfALearningPlan() = withDatabase(
        plans = listOf(
            ExportLearningPlan("plan", "Plan", deckIds = listOf("deck-old"),
                totalCards = 1, createdAt = date, updatedAt = date),
        ),
    ) { db, service ->
        val kbs = listOf(kb("k1", "Alpha"))
        val decks = listOf(deck("d1", "k1", "Root"))
        val cards = listOf(card("c1", "d1", "Q", "A"))
        val built = export(ObjectType.DECK, listOf("d1"), kbs, decks, cards)

        val request = ObjectImportRequest(
            conflictPolicy = ObjectConflictPolicy.OVERWRITE,
            targetKnowledgeBaseId = "kb-old",
        )
        val plan = (service.plan(built, request) as ObjectImportPlanResult.Success).plan
        service.execute(plan)

        val row = db.lumeCardDatabaseQueries.selectLearningPlanById("plan").executeAsOne()
        val deckIds = json.decodeFromString<List<String>>(row.deck_ids ?: "[]")
        assertEquals(1, deckIds.size, "the replaced deck must be remapped to its copy")
        assertNotEquals("deck-old", deckIds.single())
        assertEquals(1L, row.total_cards, "progress must be recomputed for the new deck")
    }

    @Test
    fun targetDeletedAfterPlanningFailsAtomically() = withDatabase { db, service ->
        val kbs = listOf(kb("k1", "Source"))
        val decks = listOf(deck("d1", "k1", "Source Deck"))
        val cards = listOf(card("c1", "d1", "Q", "A"))
        val built = export(ObjectType.CARD, listOf("c1"), kbs, decks, cards)

        val plan = (service.plan(
            built,
            ObjectImportRequest(targetKnowledgeBaseId = "kb-old", targetDeckId = "deck-old"),
        ) as ObjectImportPlanResult.Success).plan
        // Another screen (or a sync pull) removes the destination while the file is being unpacked.
        db.lumeCardDatabaseQueries.softDeleteDeck(date, date, "deck-old")

        val error = assertFailsWith<ObjectTransferException> { service.execute(plan) }

        assertEquals(ObjectTransferError.TARGET_NOT_FOUND, error.error)
        assertEquals(2, db.lumeCardDatabaseQueries.selectAllCards().executeAsList().size,
            "nothing may be inserted when the target is gone")
    }

    @Test
    fun overwriteOfAKnowledgeBaseKeepsDeckAndCardSelections() = withDatabase(
        plans = listOf(
            ExportLearningPlan(
                "plan", "Plan",
                deckIds = listOf("deck-old", "deck-child"),
                cardIds = listOf("card-old", "card-child"),
                totalCards = 2, createdAt = date, updatedAt = date,
            ),
        ),
    ) { db, service ->
        // Replacing a whole knowledge base gives every deck and card a new id. A plan that selected
        // them explicitly must follow the copies — by deck name path and by card content identity —
        // or the overwrite silently wipes the selection.
        val kbs = listOf(kb("k1", "Alpha"))
        val decks = listOf(deck("d1", "k1", "Root"), deck("d2", "k1", "Child", "d1"))
        val cards = listOf(
            card("c1", "d1", "Q", "A"),
            card("c2", "d2", "Q2", "A2"),
        )
        val built = export(ObjectType.KNOWLEDGE_BASE, listOf("k1"), kbs, decks, cards)

        val plan = (service.plan(
            built,
            ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.OVERWRITE),
        ) as ObjectImportPlanResult.Success).plan
        service.execute(plan)

        val row = db.lumeCardDatabaseQueries.selectLearningPlanById("plan").executeAsOne()
        val deckIds = json.decodeFromString<List<String>>(row.deck_ids ?: "[]")
        val cardIds = json.decodeFromString<List<String>>(row.card_ids ?: "[]")

        assertEquals(2, deckIds.size, "both decks must be remapped, got deckIds=$deckIds kbIds=${knowledgeBaseIdsOf(db, "plan")}")
        assertEquals(2, cardIds.size, "both cards must be remapped, got cardIds=$cardIds")
        assertEquals(emptySet(), deckIds.filter { it in setOf("deck-old", "deck-child") }.toSet())
        assertEquals(emptySet(), cardIds.filter { it in setOf("card-old", "card-child") }.toSet())
        assertEquals(2L, row.total_cards)
    }

    @Test
    fun deckOverwriteKeepsDescendantDeckAndCardSelections() = withDatabase(
        plans = listOf(
            ExportLearningPlan(
                "plan", "Plan",
                deckIds = listOf("deck-child"), cardIds = listOf("card-child"),
                totalCards = 1, createdAt = date, updatedAt = date,
            ),
        ),
    ) { db, service ->
        // Overwriting the root deck soft-deletes its whole subtree. A plan selecting a child deck
        // or a card inside it must follow the copy; previously only the root collision was mapped.
        val kbs = listOf(kb("k1", "Alpha"))
        val decks = listOf(deck("d1", "k1", "Root"), deck("d2", "k1", "Child", "d1"))
        val cards = listOf(card("c1", "d1", "Q", "A"), card("c2", "d2", "Q2", "A2"))
        val built = export(ObjectType.DECK, listOf("d1"), kbs, decks, cards)

        val plan = (service.plan(
            built,
            ObjectImportRequest(
                conflictPolicy = ObjectConflictPolicy.OVERWRITE,
                targetKnowledgeBaseId = "kb-old",
            ),
        ) as ObjectImportPlanResult.Success).plan
        service.execute(plan)

        val row = db.lumeCardDatabaseQueries.selectLearningPlanById("plan").executeAsOne()
        val deckIds = json.decodeFromString<List<String>>(row.deck_ids ?: "[]")
        val cardIds = json.decodeFromString<List<String>>(row.card_ids ?: "[]")

        assertEquals(1, deckIds.size, "the child deck selection must be remapped: $deckIds")
        assertNotEquals("deck-child", deckIds.single())
        assertEquals(1, cardIds.size, "the child card selection must be remapped: $cardIds")
        assertNotEquals("card-child", cardIds.single())
    }

    @Test
    fun tombstoneMediaIsNeverReportedAsUnused() = withDatabase { db, service ->
        // A soft-deleted card keeps its media JSON so a later sync can restore it. If the cleanup
        // only looks at live rows, this file is deleted although a tombstone still names it.
        db.lumeCardDatabaseQueries.updateCard(
            deck_id = "deck-child", type = "BASIC", title = null, front = "Q2", back = "A2",
            explanation = "", tags = "[]", media = """[{"id":"m","type":"IMAGE","filePath":"tombstone.png"}]""",
            metadata = "{}", updated_at = date, last_reviewed_at = null, next_review_at = null,
            id = "card-child",
        )
        db.lumeCardDatabaseQueries.softDeleteCard(date, date, "card-child")

        val built = export(
            ObjectType.DECK, listOf("d1"),
            listOf(kb("k1", "Alpha")), listOf(deck("d1", "k1", "Root")),
            listOf(card("c1", "d1", "Q", "A")),
        )
        val plan = (service.plan(
            built,
            ObjectImportRequest(
                conflictPolicy = ObjectConflictPolicy.KEEP_BOTH,
                targetKnowledgeBaseId = "kb-old",
            ),
        ) as ObjectImportPlanResult.Success).plan

        val result = service.execute(plan, newlyWrittenPaths = listOf("tombstone.png", "orphan.png"))
        assertTrue("tombstone.png" !in result.unusedNewMediaPaths, "a tombstone still references it")
        assertTrue("orphan.png" in result.unusedNewMediaPaths, "nobody references this one")
    }

    @Test
    fun previewCountsIncludeTheSubtreeAnOverwriteRemoves() = withDatabase { _, service ->
        // The plan stores one knowledge-base *seed*; the confirmation dialog must not claim that
        // only one object disappears when the transaction also deletes its decks and cards.
        val kbs = listOf(kb("k1", "Alpha"))
        val decks = listOf(deck("d1", "k1", "Root"), deck("d2", "k1", "Child", "d1"))
        val cards = listOf(card("c1", "d1", "Q1", "A1"), card("c2", "d2", "Q2", "A2"))
        val built = export(ObjectType.KNOWLEDGE_BASE, listOf("k1"), kbs, decks, cards)
        val plan = (service.plan(
            built,
            ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.OVERWRITE),
        ) as ObjectImportPlanResult.Success).plan

        assertEquals(1, plan.deleteKnowledgeBaseIds.size, "the plan itself only carries the seed")
        val counts = service.expandedDeleteCounts(plan)

        assertEquals(1, counts.knowledgeBases)
        assertEquals(2, counts.decks, "the child deck is removed with its parent, so it must count")
        assertEquals(2, counts.cards)
    }
}
