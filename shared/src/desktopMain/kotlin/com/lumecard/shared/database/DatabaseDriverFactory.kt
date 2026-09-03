package com.lumecard.shared.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.lumecard.shared.database.LumeCardDatabase
import java.io.File

private fun log(msg: String) = System.err.println("[LumeCard] $msg")

private fun resolveAppDir(): File {
    // Primary: user home ~/.lumecard
    val userHome = System.getenv("USERPROFILE") ?: System.getenv("HOME") ?: System.getProperty("user.home")
    if (userHome != null) {
        val primary = File(userHome, ".lumecard")
        try {
            if (!primary.exists()) primary.mkdirs()
            // Write-test: create and delete a temp file to confirm the path is usable
            val probe = File(primary, ".probe")
            probe.createNewFile()
            probe.delete()
            return primary
        } catch (e: Exception) {
            log("WARNING: Cannot use $primary (${e.message}), falling back to app-local directory")
        }
    }
    // Fallback: directory next to the running JAR (portable mode)
    val codeLocation = DatabaseDriverFactory::class.java.protectionDomain?.codeSource?.location?.toURI()
    val appDir = if (codeLocation != null) {
        File(File(codeLocation).parentFile, ".lumecard")
    } else {
        File(System.getProperty("java.io.tmpdir"), "lumecard")
    }
    if (!appDir.exists()) appDir.mkdirs()
    log("Using fallback app directory: ${appDir.absolutePath}")
    return appDir
}

actual class DatabaseDriverFactory {
    actual fun createDriver(): app.cash.sqldelight.db.SqlDriver {
        val appDir = resolveAppDir()
        val dbFile = File(appDir, "lumecard.db")
        log("Database path: ${dbFile.absolutePath}")

        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        DatabaseDriverHolder.driver = driver
        driver.execute(null, "PRAGMA foreign_keys = ON", 0, null)
        val targetVersion = LumeCardDatabase.Schema.version
        if (dbFile.exists()) {
            val rawVersion = driver.execute(null, "PRAGMA user_version", 0, null).value as Long
            val currentVersion = if (rawVersion == 0L) 1L else rawVersion
            if (currentVersion < targetVersion) {
                var migrated = false
                try {
                    LumeCardDatabase.Schema.migrate(driver, currentVersion, targetVersion)
                    migrated = true
                } catch (e: Exception) {
                    log("WARNING: Schema.migrate($currentVersion → $targetVersion) failed: ${e.message}")
                    // Manual fallback: apply known migrations one-by-one
                    migrated = applyManualMigrations(driver, currentVersion, targetVersion)
                }
                if (migrated) {
                    driver.execute(null, "PRAGMA user_version = $targetVersion", 0, null)
                    log("Schema migrated to version $targetVersion")
                } else {
                    log("ERROR: Migration incomplete, user_version NOT updated — will retry next launch")
                }
            }
        } else {
            LumeCardDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA user_version = $targetVersion", 0, null)
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

/**
 * Manual migration fallback when SQLDelight Schema.migrate() fails.
 * Applies known migrations one-by-one, skipping columns that already exist.
 */
private fun applyManualMigrations(driver: JdbcSqliteDriver, from: Long, to: Long): Boolean {
    if (from < 2 && to >= 2) {
        if (!columnExists(driver, "Card", "title")) {
            try {
                driver.execute(null, "ALTER TABLE Card ADD COLUMN title TEXT", 0, null)
                log("Manual migration 1→2: added Card.title")
            } catch (e: Exception) {
                log("WARNING: migration 1→2 failed: ${e.message}")
                return false
            }
        }
    }
    if (from < 3 && to >= 3) {
        if (!columnExists(driver, "KnowledgeBase", "icon")) {
            try {
                driver.execute(null, "ALTER TABLE KnowledgeBase ADD COLUMN icon TEXT DEFAULT '📁'", 0, null)
                log("Manual migration 2→3: added KnowledgeBase.icon")
            } catch (e: Exception) {
                log("WARNING: migration 2→3 failed: ${e.message}")
                return false
            }
        }
    }
    return true
}

private fun columnExists(driver: JdbcSqliteDriver, table: String, column: String): Boolean {
    val rs = driver.execute(null, "PRAGMA table_info($table)", 0, null).value as java.sql.ResultSet
    while (rs.next()) {
        if (rs.getString("name") == column) {
            rs.close()
            return true
        }
    }
    rs.close()
    return false
}
