package com.lumecard.shared.database

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.lumecard.shared.database.LumeCardDatabase

actual class DatabaseDriverFactory {
    companion object {
        lateinit var appContext: Context
    }

    actual fun createDriver(): app.cash.sqldelight.db.SqlDriver {
        val driver = AndroidSqliteDriver(
            schema = LumeCardDatabase.Schema,
            context = appContext,
            name = "lumecard.db"
        )
        return driver
    }
}
