package com.lumecard.shared.database

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

/** Replace the stub CardFTS table with an FTS5 virtual table at runtime. */
expect fun upgradeToFts5(driver: SqlDriver)

/**
 * Holds a reference to the successfully initialized SqlDriver for diagnostics.
 * Set by [DatabaseDriverFactory.createDriver] on each platform.
 */
object DatabaseDriverHolder {
    @Volatile
    var driver: SqlDriver? = null
        internal set
}
