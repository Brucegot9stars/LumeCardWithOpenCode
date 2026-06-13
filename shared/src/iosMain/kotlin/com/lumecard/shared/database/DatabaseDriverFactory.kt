package com.lumecard.shared.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.lumecard.shared.database.LumeCardDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = LumeCardDatabase.Schema,
            name = "lumecard.db"
        )
    }
}
