package com.lumecard.shared.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.database.initializeDesktopSchema
import com.lumecard.shared.domain.scheduler.FSRSAlgorithm
import com.lumecard.shared.model.Rating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewServiceTest {
    private val date = "2026-01-01T00:00:00Z"

    private fun withDatabase(block: (JdbcSqliteDriver, LumeCardDatabase, ReviewService) -> Unit) {
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
                learningPlans = listOf(ExportLearningPlan("plan", "Plan", deckIds = listOf("deck"),
                    totalCards = 1, createdAt = date, updatedAt = date)),
            ))
            block(driver, db, ReviewService(db, FSRSAlgorithm()))
        } finally { driver.close() }
    }

    @Test
    fun failedStateWriteRollsBackLogCardAndPlan() = withDatabase { driver, db, service ->
        val queries = db.lumeCardDatabaseQueries
        driver.execute(null, "CREATE TRIGGER reject_state BEFORE INSERT ON AlgorithmState BEGIN SELECT RAISE(ABORT, 'injected failure'); END", 0, null)
        assertFailsWith<Exception> { service.review("card", Rating.GOOD, 1000, "review-1") }
        assertTrue(queries.selectAllReviewLogs().executeAsList().isEmpty())
        assertNull(queries.selectCardById("card").executeAsOne().last_reviewed_at)
        assertEquals(0L, queries.selectLearningPlanById("plan").executeAsOne().completed_cards)
    }

    @Test
    fun repeatReviewsCountOneCardAndResetStartsFreshWindow() = withDatabase { _, db, service ->
        val queries = db.lumeCardDatabaseQueries
        service.review("card", Rating.GOOD, 1000, "review-1")
        service.review("card", Rating.HARD, 1000, "review-2")
        assertEquals(2, queries.selectAllReviewLogs().executeAsList().size)
        assertEquals(1L, queries.selectLearningPlanById("plan").executeAsOne().completed_cards)
        service.resetPlanProgress("plan")
        assertEquals(0L, queries.selectLearningPlanById("plan").executeAsOne().completed_cards)
        service.reconcilePlan("plan")
        assertEquals(0L, queries.selectLearningPlanById("plan").executeAsOne().completed_cards)
        val boundary = queries.selectSetting(PlanProgressKeys.forPlan("plan")).executeAsOne().setting_value
        assertNotNull(boundary)
        val snapshot = BackupRestoreService(db).snapshot()
        assertEquals(boundary, snapshot.learningPlans.single().progressSince)
        assertTrue(PlanProgressKeys.forPlan("plan") !in ExportManager().importConfig(
            ExportManager().exportConfig(mapOf(PlanProgressKeys.forPlan("plan") to boundary, "theme" to "dark"))
        )!!.settings)
        BackupRestoreService(db).replaceSettings(mapOf("theme" to "light"))
        assertEquals(boundary, queries.selectSetting(PlanProgressKeys.forPlan("plan")).executeAsOne().setting_value)
        BackupRestoreService(db).replace(snapshot)
        assertEquals(boundary, queries.selectSetting(PlanProgressKeys.forPlan("plan")).executeAsOne().setting_value)
    }

    @Test
    fun duplicateReviewIdCannotReplaceExistingLogOrScheduling() = withDatabase { _, db, service ->
        val queries = db.lumeCardDatabaseQueries
        service.review("card", Rating.GOOD, 1000, "same-id")
        val card = queries.selectCardById("card").executeAsOne()
        val state = queries.selectAlgorithmState("card").executeAsOne()
        assertFailsWith<Exception> { service.review("card", Rating.AGAIN, 1000, "same-id") }
        assertEquals(1, queries.selectAllReviewLogs().executeAsList().size)
        assertEquals(3L, queries.selectAllReviewLogs().executeAsOne().rating)
        assertEquals(card.last_reviewed_at, queries.selectCardById("card").executeAsOne().last_reviewed_at)
        assertEquals(state.state_json, queries.selectAlgorithmState("card").executeAsOne().state_json)
    }

    @Test
    fun automaticCompletionReopensWhenPlanGrowsButManualCompletionPersists() = withDatabase { _, db, service ->
        val queries = db.lumeCardDatabaseQueries
        fun addCard(id: String) {
            queries.insertCard(id, "deck", "BASIC", null, id, "back", "[]", "[]", "{}",
                date, date, null, null, 1L, null, null)
        }
        service.review("card", Rating.GOOD, 1000, "review-1")
        assertEquals("COMPLETED", queries.selectLearningPlanById("plan").executeAsOne().status)
        addCard("new-card")
        service.reconcilePlan("plan")
        val reopened = queries.selectLearningPlanById("plan").executeAsOne()
        assertEquals(2L, reopened.total_cards)
        assertEquals(1L, reopened.completed_cards)
        assertEquals("IN_PROGRESS", reopened.status)
        service.completePlan("plan")
        assertTrue(BackupRestoreService(db).snapshot().learningPlans.single().manuallyCompleted)
        addCard("another-card")
        service.reconcilePlan("plan")
        val manual = queries.selectLearningPlanById("plan").executeAsOne()
        assertEquals(3L, manual.total_cards)
        assertEquals(3L, manual.completed_cards)
        assertEquals("COMPLETED", manual.status)
        service.resetPlanProgress("plan")
        assertNull(queries.selectSetting(PlanProgressKeys.manualComplete("plan")).executeAsOneOrNull())
    }
}
