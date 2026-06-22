package com.lumecard.shared.database

import android.content.Context
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
        return driver
    }
}

actual fun upgradeToFts5(driver: SqlDriver) {
    driver.execute(null, "DROP TABLE IF EXISTS CardFTS", 0, null)
    driver.execute(null, "CREATE VIRTUAL TABLE IF NOT EXISTS CardFTS USING fts5(card_id UNINDEXED, front, back, tags, tokenize='unicode61')", 0, null)
    driver.execute(null, "INSERT INTO CardFTS(card_id, front, back, tags) SELECT id, front, back, tags FROM Card WHERE deleted_at IS NULL", 0, null)
}
