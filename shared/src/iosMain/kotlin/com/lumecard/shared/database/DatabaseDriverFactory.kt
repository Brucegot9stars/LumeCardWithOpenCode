package com.lumecard.shared.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.lumecard.shared.database.LumeCardDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = NativeSqliteDriver(
            schema = LumeCardDatabase.Schema,
            name = "lumecard.db"
        )
        upgradeToFts5(driver)
        return driver
    }
}

actual fun upgradeToFts5(driver: SqlDriver) {
    try {
        driver.execute(null, "DROP TABLE IF EXISTS CardFTS", 0, null)
        driver.execute(null, "CREATE VIRTUAL TABLE IF NOT EXISTS CardFTS USING fts5(card_id UNINDEXED, front, back, tags, tokenize='unicode61')", 0, null)
        driver.execute(null, "INSERT INTO CardFTS(card_id, front, back, tags) SELECT id, front, back, tags FROM Card WHERE deleted_at IS NULL", 0, null)
    } catch (e: Exception) {
        println("[LumeCard] WARNING: FTS5 not available, falling back to LIKE search: ${e.message}")
        driver.execute(null, "CREATE TABLE IF NOT EXISTS CardFTS(card_id TEXT NOT NULL, front TEXT NOT NULL, back TEXT NOT NULL, tags TEXT NOT NULL)", 0, null)
        driver.execute(null, "INSERT OR IGNORE INTO CardFTS(card_id, front, back, tags) SELECT id, front, back, tags FROM Card WHERE deleted_at IS NULL", 0, null)
    }
}
