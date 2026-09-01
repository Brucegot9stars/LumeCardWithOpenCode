package com.lumecard.shared.database

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

/** Replace the stub CardFTS table with an FTS5 virtual table at runtime. */
expect fun upgradeToFts5(driver: SqlDriver)

/**
 * Holds a reference to the active SqlDriver so that raw PRAGMA statements
 * can be executed (e.g. PRAGMA foreign_keys = OFF during force-download).
 * Set by [DatabaseDriverFactory.createDriver] on each platform.
 */
object DatabaseDriverHolder {
    @Volatile
    var driver: SqlDriver? = null
        internal set
}
