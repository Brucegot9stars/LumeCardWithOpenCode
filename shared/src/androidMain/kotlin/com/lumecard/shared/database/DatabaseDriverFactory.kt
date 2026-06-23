package com.lumecard.shared.database

import android.content.Context
import android.util.Log
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

object AndroidContextHolder {
    lateinit var context: Context
}

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = AndroidSqliteDriver(
            schema = LumeCardDatabase.Schema,
            context = AndroidContextHolder.context,
            name = "lumecard.db"
        )
        upgradeToFts5(driver)
        ensureMediaCacheTable(driver)
        return driver
    }
}

/** CREATE TABLE IF NOT EXISTS for the MediaCache table — safe to call every launch. */
private fun ensureMediaCacheTable(driver: SqlDriver) {
    driver.execute(null, "CREATE TABLE IF NOT EXISTS MediaCache(path TEXT PRIMARY KEY NOT NULL, mtime INTEGER NOT NULL, sha1 TEXT NOT NULL, synced_at TEXT)", 0, null)
}

actual fun upgradeToFts5(driver: SqlDriver) {
    try {
        val tableExists = driver.execute(null, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='CardFTS'", 0, null)
        if (tableExists.value == 0L) {
            driver.execute(null, "CREATE VIRTUAL TABLE IF NOT EXISTS CardFTS USING fts5(card_id UNINDEXED, front, back, tags, tokenize='unicode61')", 0, null)
            driver.execute(null, "INSERT INTO CardFTS(card_id, front, back, tags) SELECT id, front, back, tags FROM Card WHERE deleted_at IS NULL", 0, null)
        }
    } catch (e: Exception) {
        Log.w("LumeCard", "FTS5 not available, falling back to LIKE search", e)
        // Recreate plain stub table so compiled insertCardFts/deleteCardFts queries don't fail
        driver.execute(null, "CREATE TABLE IF NOT EXISTS CardFTS(card_id TEXT NOT NULL, front TEXT NOT NULL, back TEXT NOT NULL, tags TEXT NOT NULL)", 0, null)
        driver.execute(null, "INSERT OR IGNORE INTO CardFTS(card_id, front, back, tags) SELECT id, front, back, tags FROM Card WHERE deleted_at IS NULL", 0, null)
    }
}
