package com.lumecard.shared.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.database.initializeDesktopSchema
import com.lumecard.shared.domain.scheduler.FSRSAlgorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * Scale guards for the two database paths that used to share the planner's quadratic pattern:
 * [ObjectImportService.expandedDeleteCounts] (runs when the confirmation dialog opens) and
 * [ObjectImportService.execute] (runs inside the write transaction).
 *
 * Both re-swept the live deck list until a round added nothing, so a deep tree cost O(n²) twice —
 * once on the UI thread's caller and once while holding the SQLite write lock, which is the shape
 * that produces an ANR rather than a slow spinner.
 *
 * The library built here is 6 000 decks × 2 cards. Deliberately smaller than the 20 000 cap: the
 * quadratic version already needs ~36M comparisons at this size, and an in-memory JDBC insert of
 * 12 000 cards dominates the runtime long before the interesting part. The assertions are on
 * *counts* (correctness) plus a wall-clock ceiling that is a hang detector — see the note below.
 *
 * **The ceilings are not complexity guards.** `6 000` decks is small enough that the old
 * round-until-nothing-changes sweep finishes in about a second, so a 20 s (or 60 s) ceiling would
 * pass either way. The verifiable property here is the *indexed* result: the counts must be the
 * closed sub-tree, which a sweep that gives up early would get wrong. Complexity is pinned in the
 * pure-data layer instead, by `ObjectImportPlannerScaleTest.plannerGrowthRatioStaysLinear`.
 */
class ObjectImportServiceScaleTest {

    private val date = TransferScaleFixtures.DATE
    private val t = TransferScaleFixtures.t

    private fun buildLibrary(deckCount: Int, cardsPerDeck: Int): Triple<LumeCardDatabase, ObjectImportService, List<ExportDeck>> {
        val decks = TransferScaleFixtures.balancedTree("kb-big", deckCount)
        val cards = TransferScaleFixtures.cardsIn(decks, cardsPerDeck)
        val export = DataExport(
            exportDate = date,
            backupFormatVersion = 2,
            algorithmStates = emptyList(),
            knowledgeBases = listOf(
                ExportKnowledgeBase("kb-big", "Big", createdAt = date, updatedAt = date),
            ),
            decks = decks.map {
                ExportDeck(
                    id = it.id, knowledgeBaseId = it.knowledgeBaseId, name = it.name,
                    description = null, color = "#4CAF50", icon = "📚",
                    parentId = it.parentId, createdAt = date, updatedAt = date,
                )
            },
            cards = cards.map {
                ExportCard(it.id, it.deckId, "BASIC", it.front, it.back, tags = emptyList(),
                    createdAt = date, updatedAt = date)
            },
            learningPlans = emptyList(),
        )
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        initializeDesktopSchema(driver)
        val db = LumeCardDatabase(driver)
        BackupRestoreService(db).replace(export)
        val service = ObjectImportService(db, LearningPlanMutationService(db, ReviewService(db, FSRSAlgorithm())))
        return Triple(db, service, export.decks)
    }

    /**
     * Imports a deck named exactly like the library's root, so OVERWRITE seeds that root and the
     * transaction has to expand + soft-delete its whole 6 000-deck sub-tree.
     */
    private fun importOfLibraryRoot(): ObjectExport {
        val kbId = "kb-src"
        val deck = TransferScaleFixtures.deck("src-deck", kbId, "Deck 0")
        val cards = listOf(
            TransferScaleFixtures.card("src-c1", deck.id, "Q1", "A1"),
            TransferScaleFixtures.card("src-c2", deck.id, "Q2", "A2"),
        )
        return TransferScaleFixtures.exportOf(
            ObjectType.DECK, listOf(deck.id),
            listOf(TransferScaleFixtures.kb(kbId, "Src")), listOf(deck), cards,
        )
    }

    @Test
    fun previewCountsOverwriteOfADeepLibraryByIndexedWalk() {
        val deckCount = 6_000
        val (_, service, _) = buildLibrary(deckCount, cardsPerDeck = 2)
            val plan = (service.plan(
                importOfLibraryRoot(),
                ObjectImportRequest(
                    conflictPolicy = ObjectConflictPolicy.OVERWRITE,
                    targetKnowledgeBaseId = "kb-big",
                ),
            ) as ObjectImportPlanResult.Success).plan
            // The seed is the one existing top-level deck we collide with, not the whole library.
            assertEquals(1, plan.deleteDeckIds.size + plan.deleteKnowledgeBaseIds.size)

            val (counts, elapsed) = measureTimedValue { service.expandedDeleteCounts(plan) }
            println("[benchmark] expandedDeleteCounts over $deckCount decks: ${elapsed.inWholeMilliseconds}ms")

            // expandedDeleteCounts must report what actually disappears, sub-tree included. The
            // seed is one deck; a count that returned the seed alone (no expansion) or the whole
            // knowledge base (no descent, just a kb sweep) both fail here, so this pins the walk
            // in both directions.
            assertEquals(deckCount, counts.decks, "the seeded sub-tree must expand to every deck")
            assertEquals(deckCount * 2, counts.cards, "the cards move with the decks")
            assertTrue(
                elapsed.inWholeMilliseconds < 20_000,
                "counting an overwrite over $deckCount decks took $elapsed",
            )
    }

    @Test
    fun executeDeletesTheWholeSubtreeInsideOneTransaction() {
        val deckCount = 6_000
        val (db, service, _) = buildLibrary(deckCount, cardsPerDeck = 2)
        val queries = db.lumeCardDatabaseQueries

        val plan = (service.plan(
            importOfLibraryRoot(),
            ObjectImportRequest(
                conflictPolicy = ObjectConflictPolicy.OVERWRITE,
                targetKnowledgeBaseId = "kb-big",
            ),
        ) as ObjectImportPlanResult.Success).plan

        val (result, elapsed) = measureTimedValue { service.execute(plan) }
        println("[benchmark] execute() deleting $deckCount decks: ${elapsed.inWholeMilliseconds}ms")

        assertEquals(deckCount, result.deletedDecks, "every descendant deck must be soft-deleted")
        assertEquals(deckCount * 2, result.deletedCards)
        assertEquals(1, result.importedDecks)
        assertEquals(2, result.importedCards)
        // The copy is the only live deck left; the tombstones stay for sync.
        assertEquals(1, queries.selectAllDecks().executeAsList().size)
        assertTrue(
            elapsed.inWholeMilliseconds < 60_000,
            "committing a $deckCount-deck overwrite took $elapsed",
        )
    }
}