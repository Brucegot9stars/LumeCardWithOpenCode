package com.lumecard.shared.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.lumecard.shared.database.LumeCardDatabase
import java.io.File

private fun userHome(): String = System.getenv("USERPROFILE") ?: System.getenv("HOME") ?: System.getProperty("user.home")

actual class DatabaseDriverFactory {
    actual fun createDriver(): app.cash.sqldelight.db.SqlDriver {
        val appDir = File(userHome(), ".lumecard")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        val dbFile = File(appDir, "lumecard.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        driver.execute(null, "PRAGMA foreign_keys = ON", 0, null)
        if (dbFile.exists()) {
            val rawVersion = driver.execute(null, "PRAGMA user_version", 0, null).value as Long
            val currentVersion = if (rawVersion == 0L) 1L else rawVersion
            if (currentVersion < LumeCardDatabase.Schema.version) {
                LumeCardDatabase.Schema.migrate(driver, currentVersion, LumeCardDatabase.Schema.version)
            }
        } else {
            LumeCardDatabase.Schema.create(driver)
        }
        upgradeToFts5(driver)
        driver.execute(null, "CREATE TABLE IF NOT EXISTS MediaCache(path TEXT PRIMARY KEY NOT NULL, mtime INTEGER NOT NULL, sha1 TEXT NOT NULL, synced_at TEXT)", 0, null)
        return driver
    }
}

actual fun upgradeToFts5(driver: app.cash.sqldelight.db.SqlDriver) {
    try {
        val tableExists = driver.execute(null, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='CardFTS'", 0, null)
        if (tableExists.value == 0L) {
            driver.execute(null, "CREATE VIRTUAL TABLE IF NOT EXISTS CardFTS USING fts5(card_id UNINDEXED, front, back, tags, tokenize='unicode61')", 0, null)
            driver.execute(null, "INSERT INTO CardFTS(card_id, front, back, tags) SELECT id, front, back, tags FROM Card WHERE deleted_at IS NULL", 0, null)
        }
    } catch (e: Exception) {
        System.err.println("[LumeCard] WARNING: FTS5 not available, falling back to LIKE search: ${e.message}")
        driver.execute(null, "CREATE TABLE IF NOT EXISTS CardFTS(card_id TEXT NOT NULL, front TEXT NOT NULL, back TEXT NOT NULL, tags TEXT NOT NULL)", 0, null)
        driver.execute(null, "INSERT OR IGNORE INTO CardFTS(card_id, front, back, tags) SELECT id, front, back, tags FROM Card WHERE deleted_at IS NULL", 0, null)
    }
}
