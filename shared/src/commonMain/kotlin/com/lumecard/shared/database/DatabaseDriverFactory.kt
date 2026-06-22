package com.lumecard.shared.database

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

/** Replace the stub CardFTS table with an FTS5 virtual table at runtime. */
expect fun upgradeToFts5(driver: SqlDriver)
