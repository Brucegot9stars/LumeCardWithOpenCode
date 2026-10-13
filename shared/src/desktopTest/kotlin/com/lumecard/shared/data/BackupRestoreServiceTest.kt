package com.lumecard.shared.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.database.initializeDesktopSchema
import com.lumecard.shared.domain.scheduler.AlgorithmState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupRestoreServiceTest {
    private val date = "2026-01-01T00:00:00Z"
    private val state = Json.encodeToString(AlgorithmState(stability = 7.5, difficulty = 4.0, repetitions = 12))

    private fun backup(cardId: String = "card"): DataExport = DataExport(
        exportDate = date, backupFormatVersion = 2,
        knowledgeBases = listOf(ExportKnowledgeBase("kb", "知识库", createdAt = date, updatedAt = date)),
        decks = listOf(ExportDeck("deck", "kb", "卡组", null, "#4CAF50", "📚", createdAt = date, updatedAt = date)),
        cards = listOf(ExportCard(cardId, "deck", "BASIC", "正面", "背面", tags = listOf("测试"),
            createdAt = date, updatedAt = date)),
        reviewLogs = listOf(ExportReviewLog("log", cardId, 3, 5, 7, 2.5f, 12, 0, date)),
        learningPlans = listOf(ExportLearningPlan("plan", "计划", cardIds = listOf(cardId), createdAt = date, updatedAt = date)),
        algorithmStates = listOf(ExportAlgorithmState(cardId, "FSRS", state)),
        splashQuotes = ExportSplashQuotes(version = 2, hiddenDefaultIndices = "1,3"),
    )

    private fun withDatabase(block: (JdbcSqliteDriver, LumeCardDatabase, BackupRestoreService) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            initializeDesktopSchema(driver)
            val database = LumeCardDatabase(driver)
            block(driver, database, BackupRestoreService(database))
        } finally { driver.close() }
    }

    @Test
    fun snapshotRoundTripIncludesStateAndTombstones() = withDatabase { _, database, service ->
        val original = backup().copy(cards = backup().cards.map { it.copy(deletedAt = date) })
        service.replace(original)
        val export = ExportManager()
        val decoded = assertNotNull(export.importBackup(export.encodeBackup(service.snapshot())))
        service.replace(decoded)
        assertEquals(state, database.lumeCardDatabaseQueries.selectAlgorithmState("card").executeAsOne().state_json)
        assertEquals(date, database.lumeCardDatabaseQueries.selectCardById("card").executeAsOne().deleted_at)
        assertEquals(original.reviewLogs, service.snapshot().reviewLogs)
        assertEquals(original.learningPlans, service.snapshot().learningPlans)
        assertEquals("1,3", service.snapshot().splashQuotes?.hiddenDefaultIndices)
    }

    @Test
    fun childBeforeParentRestoresWithForeignKeysEnabled() = withDatabase { driver, _, service ->
        val parent = backup().decks.single()
        val child = parent.copy(id = "child", parentId = parent.id)
        service.replace(backup().copy(decks = listOf(child, parent)))
        assertEquals(2, service.snapshot().decks.size)
        val foreignKeys = driver.executeQuery(null, "PRAGMA foreign_keys", {
            check(it.next().value)
            QueryResult.Value(it.getLong(0))
        }, 0, null).value
        assertEquals(1L, foreignKeys)
    }

    @Test
    fun invalidReferencesDuplicatesCyclesAndValuesDoNotDeleteLocalData() = withDatabase { _, _, service ->
        val original = backup()
        service.replace(original)
        val expected = service.snapshot()
        val invalid = listOf(
            backup().copy(cards = original.cards.map { it.copy(deckId = "missing") }),
            backup().copy(decks = original.decks.map { it.copy(parentId = it.id) }),
            backup().copy(cards = original.cards + original.cards),
            backup().copy(cards = original.cards.map { it.copy(type = "UNKNOWN") }),
            backup().copy(cards = original.cards.map { it.copy(createdAt = "not-a-date") }),
            backup().copy(algorithmStates = listOf(ExportAlgorithmState("card", "FSRS", "invalid-json"))),
            backup().copy(algorithmStates = null),
            backup().copy(exportType = "share"),
        )
        for (data in invalid) {
            assertFailsWith<Exception> { service.replace(data) }
            assertEquals(expected.copy(exportDate = date), service.snapshot().copy(exportDate = date))
        }
    }

    @Test
    fun failureAfterDeletionRollsBackDataStateSettingsAndIndexWithoutNotifications() = withDatabase { driver, database, service ->
        service.replace(backup())
        val expected = service.snapshot()
        val query = database.lumeCardDatabaseQueries.selectBackupCards()
        var notifications = 0
        val listener = object : Query.Listener {
            override fun queryResultsChanged() { notifications++ }
        }
        query.addListener(listener)
        try {
            driver.execute(null, "CREATE TRIGGER reject_new_card BEFORE INSERT ON Card WHEN NEW.id = 'new' BEGIN SELECT RAISE(ABORT, 'test failure'); END", 0, null)
            assertFailsWith<Exception> { service.replace(backup("new")) }
            assertEquals(expected.copy(exportDate = date), service.snapshot().copy(exportDate = date))
            assertEquals(0, notifications)
            val count = driver.executeQuery(null, "SELECT COUNT(*) FROM CardFTS WHERE card_id = 'card'", {
                check(it.next().value)
                QueryResult.Value(it.getLong(0))
            }, 0, null).value
            assertEquals(1L, count)
            // Restore succeeds once the injected failure is removed; FK enforcement remains active.
            driver.execute(null, "DROP TRIGGER reject_new_card", 0, null)
            service.replace(backup("new"))
            assertEquals("new", service.snapshot().cards.single().id)
            assertTrue(notifications > 0)
        } finally { query.removeListener(listener) }
    }

    @Test
    fun destructiveImportRejectsShareDeltaAndUnknownVersionsButAcceptsLegacy() {
        val manager = ExportManager()
        assertNull(manager.importBackup(manager.encodeBackup(backup().copy(exportType = "share"))))
        assertNull(manager.importBackup(manager.encodeBackup(backup().copy(exportType = "incremental"))))
        assertNull(manager.importBackup(manager.encodeBackup(backup().copy(schemaVersion = Int.MAX_VALUE))))
        assertNull(manager.importBackup(manager.encodeBackup(backup().copy(backupFormatVersion = 3))))
        assertNotNull(manager.importData(manager.encodeBackup(backup().copy(exportType = "share"))))
        val legacy = backup().copy(algorithmStates = null, backupFormatVersion = 1)
        assertNull(assertNotNull(manager.importBackup(manager.encodeBackup(legacy))).algorithmStates)
    }

    @Test
    fun mergePreservesLocalOnlyRowsAndRestoresStateWithoutReplacingParents() = withDatabase { _, database, service ->
        service.replace(backup())
        val extra = backup().cards.single().copy(id = "extra")
        service.merge(backup().copy(cards = backup().cards + extra))
        val updated = backup().copy(cards = backup().cards.map { it.copy(front = "updated") })
        service.merge(updated)
        assertEquals(setOf("card", "extra"), service.snapshot().cards.map { it.id }.toSet())
        assertEquals("updated", database.lumeCardDatabaseQueries.selectCardById("card").executeAsOne().front)
        assertEquals(state, database.lumeCardDatabaseQueries.selectAlgorithmState("card").executeAsOne().state_json)
        assertEquals(1, service.snapshot().reviewLogs.size)
    }

    @Test
    fun mergeFailureRollsBackEarlierParentUpdates() = withDatabase { driver, _, service ->
        service.replace(backup())
        val original = service.snapshot()
        driver.execute(null, "CREATE TRIGGER reject_new_card BEFORE INSERT ON Card WHEN NEW.id = 'new' BEGIN SELECT RAISE(ABORT, 'test failure'); END", 0, null)
        assertFailsWith<Exception> { service.merge(backup("new")) }
        assertEquals(original.copy(exportDate = date), service.snapshot().copy(exportDate = date))
    }

    @Test
    fun settingsReplaceFailureRollsBackDeletedSettings() = withDatabase { driver, database, service ->
        val queries = database.lumeCardDatabaseQueries
        queries.insertSetting("sentinel", "keep")
        driver.execute(null, "CREATE TRIGGER reject_setting BEFORE INSERT ON AppSettings WHEN NEW.key = 'bad' BEGIN SELECT RAISE(ABORT, 'test failure'); END", 0, null)
        assertFailsWith<Exception> { service.replaceSettings(mapOf("first" to "new", "bad" to "failure")) }
        assertEquals("keep", queries.selectSetting("sentinel").executeAsOne().setting_value)
        assertNull(queries.selectSetting("first").executeAsOneOrNull())
    }
}
