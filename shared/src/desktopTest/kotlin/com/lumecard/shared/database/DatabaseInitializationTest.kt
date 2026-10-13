package com.lumecard.shared.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseInitializationTest {
    private fun inMemory(block: (JdbcSqliteDriver) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try { block(driver) } finally { driver.close() }
    }

    private fun version(driver: JdbcSqliteDriver): Long = driver.executeQuery(null, "PRAGMA user_version", {
        check(it.next().value)
        QueryResult.Value(checkNotNull(it.getLong(0)))
    }, 0, null).value

    private fun columns(driver: JdbcSqliteDriver, table: String): Set<String> = driver.executeQuery(null,
        "PRAGMA table_info($table)", { cursor ->
            val names = mutableSetOf<String>()
            while (cursor.next().value) names.add(checkNotNull(cursor.getString(1)))
            QueryResult.Value(names)
        }, 0, null).value

    @Test
    fun emptyDatabaseIsCreatedAndCanBeReopened() = inMemory { driver ->
        initializeDesktopSchema(driver)
        val queries = LumeCardDatabase(driver).lumeCardDatabaseQueries
        queries.insertSetting("sentinel", "keep")
        initializeDesktopSchema(driver)
        assertEquals(LumeCardDatabase.Schema.version, version(driver))
        assertEquals("keep", queries.selectSetting("sentinel").executeAsOne().setting_value)
    }

    @Test
    fun partialDatabaseIsNotRebuiltAndFailedOpenClosesDriver() {
        val directory = Files.createTempDirectory("lumecard-db-init-test-")
        val dbFile = directory.resolve("partial.db").toFile()
        try {
            val initial = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
            try {
                initial.execute(null, "CREATE TABLE KnowledgeBase(id TEXT PRIMARY KEY, name TEXT)", 0, null)
                initial.execute(null, "INSERT INTO KnowledgeBase VALUES ('sentinel', 'keep')", 0, null)
            } finally { initial.close() }
            assertFailsWith<IllegalStateException> { openDesktopDatabase(dbFile) }
            val reopened = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
            try {
                val value = reopened.executeQuery(null, "SELECT name FROM KnowledgeBase WHERE id='sentinel'", {
                    check(it.next().value)
                    QueryResult.Value(it.getString(0))
                }, 0, null).value
                assertEquals("keep", value)
                assertEquals(0L, version(reopened))
            } finally { reopened.close() }
        } finally {
            // Only this test's isolated files; no application database is accessed.
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
    }

    @Test
    fun newerDatabaseVersionIsRejectedWithoutChangingData() = inMemory { driver ->
        LumeCardDatabase.Schema.create(driver)
        val future = LumeCardDatabase.Schema.version + 1
        driver.execute(null, "PRAGMA user_version = $future", 0, null)
        val queries = LumeCardDatabase(driver).lumeCardDatabaseQueries
        queries.insertSetting("sentinel", "keep")
        assertFailsWith<IllegalStateException> { initializeDesktopSchema(driver) }
        assertEquals(future, version(driver))
        assertEquals("keep", queries.selectSetting("sentinel").executeAsOne().setting_value)
    }

    @Test
    fun legacyVersionZeroWithAlreadyAppliedColumnsIsRecovered() = inMemory { driver ->
        LumeCardDatabase.Schema.create(driver)
        initializeDesktopSchema(driver)
        assertEquals(LumeCardDatabase.Schema.version, version(driver))
    }

    @Test
    fun versionOneMigrationsPreserveRows() = inMemory { driver ->
        LumeCardDatabase.Schema.create(driver)
        driver.execute(null, "ALTER TABLE Card DROP COLUMN title", 0, null)
        driver.execute(null, "ALTER TABLE KnowledgeBase DROP COLUMN icon", 0, null)
        driver.execute(null, "PRAGMA user_version = 1", 0, null)
        val queries = LumeCardDatabase(driver).lumeCardDatabaseQueries
        queries.insertSetting("sentinel", "keep")
        initializeDesktopSchema(driver)
        assertTrue("title" in columns(driver, "Card"))
        assertTrue("icon" in columns(driver, "KnowledgeBase"))
        assertEquals(LumeCardDatabase.Schema.version, version(driver))
        assertEquals("keep", queries.selectSetting("sentinel").executeAsOne().setting_value)
    }

    @Test
    fun failedMigrationRollsBackColumnsAndVersion() = inMemory { driver ->
        LumeCardDatabase.Schema.create(driver)
        driver.execute(null, "ALTER TABLE Card DROP COLUMN title", 0, null)
        driver.execute(null, "ALTER TABLE KnowledgeBase DROP COLUMN icon", 0, null)
        driver.execute(null, "ALTER TABLE ReviewLog DROP COLUMN ease_factor", 0, null)
        driver.execute(null, "PRAGMA user_version = 1", 0, null)
        assertFailsWith<Exception> { initializeDesktopSchema(driver) }
        assertFalse("title" in columns(driver, "Card"))
        assertFalse("icon" in columns(driver, "KnowledgeBase"))
        assertEquals(1L, version(driver))
    }

    @Test
    fun fileDatabaseEnforcesForeignKeysOnNewConnectionsAndOtherThreads() {
        val directory = Files.createTempDirectory("lumecard-db-fk-test-")
        val driver = openDesktopDatabase(directory.resolve("test.db").toFile())
        try {
            val queries = LumeCardDatabase(driver).lumeCardDatabaseQueries
            queries.insertSetting("sentinel", "keep") // This connection is closed by the driver.
            fun insertOrphan() = driver.execute(null,
                "INSERT INTO AlgorithmState VALUES ('missing-card', 'FSRS', '{}')", 0, null)
            assertFailsWith<Exception> { insertOrphan() }
            val onOtherThread = java.util.concurrent.CompletableFuture.supplyAsync {
                runCatching { insertOrphan() }.isFailure
            }.get()
            assertTrue(onOtherThread)
            assertEquals("keep", queries.selectSetting("sentinel").executeAsOne().setting_value)
        } finally {
            driver.close()
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
    }
}
