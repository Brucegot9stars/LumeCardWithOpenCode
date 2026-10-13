package com.lumecard.shared.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.database.initializeDesktopSchema
import com.lumecard.shared.domain.scheduler.FSRSAlgorithm
import com.lumecard.shared.model.LearningPlan
import com.lumecard.shared.model.Rating
import com.lumecard.shared.repository.SqlDelightCardRepository
import com.lumecard.shared.repository.SqlDelightDeckRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LearningPlanMutationServiceTest {
    private val date = "2026-01-01T00:00:00Z"

    private fun withDatabase(block: (JdbcSqliteDriver, LumeCardDatabase, ReviewService, LearningPlanMutationService) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            initializeDesktopSchema(driver)
            val db = LumeCardDatabase(driver)
            BackupRestoreService(db).replace(DataExport(
                exportDate = date, backupFormatVersion = 2, algorithmStates = emptyList(),
                knowledgeBases = listOf(ExportKnowledgeBase("kb", "KB", createdAt = date, updatedAt = date)),
                decks = listOf(ExportDeck("deck", "kb", "Deck", null, "#4CAF50", "📚", createdAt = date, updatedAt = date)),
                cards = listOf(ExportCard("card", "deck", "BASIC", "front", "back", tags = emptyList(),
                    createdAt = date, updatedAt = date)),
                learningPlans = listOf(
                    ExportLearningPlan("first", "First", isDefault = true, deckIds = listOf("deck"),
                        totalCards = 1, createdAt = date, updatedAt = date),
                    ExportLearningPlan("second", "Second", deckIds = listOf("deck"),
                        totalCards = 1, createdAt = date, updatedAt = date),
                ),
            ))
            val review = ReviewService(db, FSRSAlgorithm())
            block(driver, db, review, LearningPlanMutationService(db, review))
        } finally { driver.close() }
    }

    @Test
    fun failedDefaultSwitchLeavesPreviousDefault() = withDatabase { driver, db, _, mutations ->
        val queries = db.lumeCardDatabaseQueries
        driver.execute(null, "CREATE TRIGGER reject_default BEFORE UPDATE OF is_default ON LearningPlan WHEN NEW.id = 'second' AND NEW.is_default = 1 BEGIN SELECT RAISE(ABORT, 'injected failure'); END", 0, null)
        assertFailsWith<Exception> { mutations.setDefault("second") }
        assertEquals(1L, queries.selectLearningPlanById("first").executeAsOne().is_default)
        assertEquals(0L, queries.selectLearningPlanById("second").executeAsOne().is_default)
    }

    @Test
    fun createReconcilesStaleTotalAndStartChangesOnlyStatus() = withDatabase { _, db, _, mutations ->
        val queries = db.lumeCardDatabaseQueries
        mutations.create(LearningPlan(id = "third", name = "Third", deckIds = listOf("deck"), totalCards = 0))
        assertEquals(1L, queries.selectLearningPlanById("third").executeAsOne().total_cards)
        mutations.start("third")
        mutations.start("third")
        val started = queries.selectLearningPlanById("third").executeAsOne()
        assertEquals("IN_PROGRESS", started.status)
        assertEquals("[\"deck\"]", started.deck_ids)
        assertEquals(1L, started.total_cards)
        assertEquals(3L, started.version)
    }

    @Test
    fun deletingDeckReconcilesAllPlansInSameRepositoryTransaction() = withDatabase { _, db, review, mutations ->
        review.review("card", Rating.GOOD, 1000, "review")
        runBlocking { SqlDelightDeckRepository(db, mutations).delete("deck") }
        val queries = db.lumeCardDatabaseQueries
        val first = queries.selectLearningPlanById("first").executeAsOne()
        val second = queries.selectLearningPlanById("second").executeAsOne()
        assertEquals(0L, first.total_cards)
        assertEquals(0L, first.completed_cards)
        assertEquals("NOT_STARTED", first.status)
        assertEquals(0L, second.total_cards)
        assertEquals("[]", first.deck_ids)
        assertEquals("[]", second.deck_ids)
    }

    @Test
    fun failedPlanUpdateRollsBackDeckAndCardDeletion() = withDatabase { driver, db, _, mutations ->
        driver.execute(null, "CREATE TRIGGER reject_plan BEFORE UPDATE OF deck_ids ON LearningPlan WHEN NEW.id = 'first' BEGIN SELECT RAISE(ABORT, 'injected failure'); END", 0, null)
        assertFailsWith<Exception> { runBlocking { SqlDelightDeckRepository(db, mutations).delete("deck") } }
        val queries = db.lumeCardDatabaseQueries
        assertNull(queries.selectDeckById("deck").executeAsOne().deleted_at)
        assertNull(queries.selectCardById("card").executeAsOne().deleted_at)
        assertEquals("[\"deck\"]", queries.selectLearningPlanById("first").executeAsOne().deck_ids)
    }

    @Test
    fun insertingCardReopensAutomaticallyCompletedPlan() = withDatabase { _, db, review, mutations ->
        review.review("card", Rating.GOOD, 1000, "review")
        runBlocking {
            SqlDelightCardRepository(db, mutations).insert(com.lumecard.shared.model.Card(
                id = "new-card", deckId = "deck", type = com.lumecard.shared.model.CardType.BASIC,
                front = "new", back = "back",
            ))
        }
        val first = db.lumeCardDatabaseQueries.selectLearningPlanById("first").executeAsOne()
        assertEquals(2L, first.total_cards)
        assertEquals(1L, first.completed_cards)
        assertEquals("IN_PROGRESS", first.status)
    }
}
