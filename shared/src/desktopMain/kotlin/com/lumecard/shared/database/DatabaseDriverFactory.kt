package com.lumecard.shared.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.lumecard.shared.database.LumeCardDatabase
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): app.cash.sqldelight.db.SqlDriver {
        val appDir = File(System.getProperty("user.home"), ".lumecard")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        val dbFile = File(appDir, "lumecard.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (!dbFile.exists()) {
            LumeCardDatabase.Schema.create(driver)
        }
        upgradeToFts5(driver)
        return driver
    }
}

actual fun upgradeToFts5(driver: app.cash.sqldelight.db.SqlDriver) {
    try {
        driver.execute(null, "DROP TABLE IF EXISTS CardFTS", 0, null)
        driver.execute(null, "CREATE VIRTUAL TABLE IF NOT EXISTS CardFTS USING fts5(card_id UNINDEXED, front, back, tags, tokenize='unicode61')", 0, null)
        driver.execute(null, "INSERT INTO CardFTS(card_id, front, back, tags) SELECT id, front, back, tags FROM Card WHERE deleted_at IS NULL", 0, null)
    } catch (e: Exception) {
        System.err.println("[LumeCard] WARNING: FTS5 not available, falling back to LIKE search: ${e.message}")
    }
}
