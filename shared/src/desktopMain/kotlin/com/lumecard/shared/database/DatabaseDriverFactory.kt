package com.lumecard.shared.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

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
    actual fun createDriver(): SqlDriver {
        val appDir = resolveAppDir()
        val dbFile = File(appDir, "lumecard.db")
        log("Database path: ${dbFile.absolutePath}")
        val driver = openDesktopDatabase(dbFile)
        DatabaseDriverHolder.driver = driver
        return driver
    }
}

/** Open failures must preserve the existing database, not silently rebuild it. */
internal fun openDesktopDatabase(dbFile: File): JdbcSqliteDriver {
    // File-backed SQLDelight opens connections per thread/operation. Configure
    // every connection, not just the one used by an initialization PRAGMA.
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", Properties().apply {
        setProperty("foreign_keys", "true")
    })
    try {
        initializeDesktopSchema(driver)
        upgradeToFts5(driver)
        driver.execute(null, "CREATE TABLE IF NOT EXISTS MediaCache(path TEXT PRIMARY KEY NOT NULL, mtime INTEGER NOT NULL, sha1 TEXT NOT NULL, synced_at TEXT)", 0, null)
        return driver
    } catch (failure: Throwable) {
        try {
            driver.close()
        } catch (closeFailure: Throwable) {
            failure.addSuppressed(closeFailure)
        }
        throw IllegalStateException("无法安全打开数据库 ${dbFile.absolutePath}，原有数据已保留；请备份后检查数据库结构。", failure)
    }
}

internal fun initializeDesktopSchema(driver: SqlDriver) {
    driver.execute(null, "PRAGMA foreign_keys = ON", 0, null)
    val tables = driver.executeQuery(null,
        "SELECT name FROM sqlite_master WHERE type IN ('table', 'view') AND name NOT LIKE 'sqlite_%'",
        { cursor ->
            val names = mutableSetOf<String>()
            while (cursor.next().value) names.add(checkNotNull(cursor.getString(0)))
            QueryResult.Value(names)
        }, 0, null).value
    val rawVersion = readUserVersion(driver)
    val targetVersion = LumeCardDatabase.Schema.version
    check(rawVersion <= targetVersion) { "Database version $rawVersion is newer than supported version $targetVersion" }
    val database = LumeCardDatabase(driver)
    if (tables.isEmpty()) {
        check(rawVersion == 0L) { "Versioned database has no tables" }
        database.transaction {
            LumeCardDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA user_version = $targetVersion", 0, null)
        }
        return
    }
    val required = setOf("KnowledgeBase", "Deck", "Card", "ReviewLog", "AppSettings", "AlgorithmState", "LearningPlan")
    check(tables.containsAll(required)) { "Incomplete schema; missing ${required - tables}" }
    val currentVersion = rawVersion.takeIf { it > 0L } ?: 1L
    if (currentVersion < targetVersion) {
        try {
            database.transaction {
                LumeCardDatabase.Schema.migrate(driver, currentVersion, targetVersion)
                verifyBusinessColumns(driver)
                driver.execute(null, "PRAGMA user_version = $targetVersion", 0, null)
            }
        } catch (migrationFailure: Exception) {
            // Older releases may have added a column without updating user_version.
            // The failed generated migration has already rolled back in full.
            try {
                database.transaction {
                    applyKnownMigrations(driver, currentVersion, targetVersion)
                    verifyBusinessColumns(driver)
                    driver.execute(null, "PRAGMA user_version = $targetVersion", 0, null)
                }
            } catch (fallbackFailure: Exception) {
                fallbackFailure.addSuppressed(migrationFailure)
                throw fallbackFailure
            }
        }
    } else {
        verifyBusinessColumns(driver)
    }
}

private fun readUserVersion(driver: SqlDriver): Long = driver.executeQuery(
    null, "PRAGMA user_version",
    { cursor -> QueryResult.Value(if (cursor.next().value) checkNotNull(cursor.getLong(0)) else error("Missing user_version")) },
    0, null,
).value

private fun verifyBusinessColumns(driver: SqlDriver) {
    val columns = mapOf(
        "KnowledgeBase" to "id,name,description,icon,created_at,updated_at,version,deleted_at,synced_at",
        "Deck" to "id,knowledge_base_id,name,description,color,icon,parent_id,created_at,updated_at,version,deleted_at,synced_at",
        "Card" to "id,deck_id,type,title,front,back,tags,media,metadata,created_at,updated_at,last_reviewed_at,next_review_at,version,deleted_at,synced_at",
        "ReviewLog" to "id,card_id,rating,review_time,interval,ease_factor,repetitions,lapse_count,reviewed_at,version,deleted_at,synced_at",
        "AppSettings" to "key,setting_value",
        "AlgorithmState" to "card_id,mode,state_json",
        "LearningPlan" to "id,name,description,status,is_default,knowledge_base_ids,deck_ids,card_ids,total_cards,completed_cards,created_at,updated_at,version,deleted_at,synced_at",
    )
    for ((table, fields) in columns) {
        driver.executeQuery(null, "SELECT $fields FROM $table LIMIT 0", { QueryResult.Value(Unit) }, 0, null).value
    }
}

actual fun upgradeToFts5(driver: app.cash.sqldelight.db.SqlDriver) {
    try {
        val tableExists = driver.execute(null, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='CardFTS'", 0, null)
        if (tableExists.value == 0L) {
            driver.execute(null, "CREATE VIRTUAL TABLE IF NOT EXISTS CardFTS USING fts5(card_id UNINDEXED, front, back, tags, tokenize='unicode61')", 0, null)
            try {
                driver.execute(null, "INSERT INTO CardFTS(card_id, front, back, tags) SELECT id, front, back, tags FROM Card WHERE deleted_at IS NULL", 0, null)
            } catch (e2: Exception) {
                log("WARNING: FTS5 backfill skipped: ${e2.message}")
            }
        }
    } catch (e: Exception) {
        System.err.println("[LumeCard] WARNING: FTS5 not available, falling back to LIKE search: ${e.message}")
        try {
            driver.execute(null, "CREATE TABLE IF NOT EXISTS CardFTS(card_id TEXT NOT NULL, front TEXT NOT NULL, back TEXT NOT NULL, tags TEXT NOT NULL)", 0, null)
            driver.execute(null, "INSERT OR IGNORE INTO CardFTS(card_id, front, back, tags) SELECT id, front, back, tags FROM Card WHERE deleted_at IS NULL", 0, null)
        } catch (e2: Exception) {
            log("WARNING: FTS5 LIKE-search fallback failed, search may be unavailable: ${e2.message}")
        }
    }
}

/**
 * Manual migration fallback when SQLDelight Schema.migrate() fails.
 * Tries each ALTER TABLE; "duplicate column name" means already applied (skip).
 */
private fun applyKnownMigrations(driver: SqlDriver, from: Long, to: Long) {
    check(to <= 3L) { "No manual migration available for schema version $to" }
    val migrations = mutableListOf<Pair<Long, String>>()
    if (from < 2 && to >= 2) {
        migrations.add(2L to "ALTER TABLE Card ADD COLUMN title TEXT")
    }
    if (from < 3 && to >= 3) {
        migrations.add(3L to "ALTER TABLE KnowledgeBase ADD COLUMN icon TEXT DEFAULT '📁'")
    }
    for ((targetVer, sql) in migrations) {
        val table = if (targetVer == 2L) "Card" else "KnowledgeBase"
        val column = if (targetVer == 2L) "title" else "icon"
        val columns = driver.executeQuery(null, "PRAGMA table_info($table)", { cursor ->
            val names = mutableSetOf<String>()
            while (cursor.next().value) names.add(checkNotNull(cursor.getString(1)))
            QueryResult.Value(names)
        }, 0, null).value
        if (column !in columns) {
            driver.execute(null, sql, 0, null)
            log("Manual migration → v$targetVer: applied")
        }
    }
}
