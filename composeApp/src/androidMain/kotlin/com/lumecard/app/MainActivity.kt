package com.lumecard.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lumecard.app.di.appModule
import com.lumecard.app.platform.FilePickerState
import com.lumecard.shared.database.AndroidContextHolder
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidContextHolder.context = applicationContext
        FilePickerState.activity = this
        FilePickerState.launcher = FilePickerState.createLauncher(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = throwable.stackTraceToString()
            val msg = "[UncaughtException] ${throwable.message}\n$stackTrace"
            Log.e("LumeCard", "CRASH", throwable)
            try {
                CrashLogHolder.lastCrashLog = msg
                val prefs = getSharedPreferences("lumecard_crash", MODE_PRIVATE)
                prefs.edit().putString("last_crash", msg).commit()
                // Also write to file as backup
                try {
                    val file = java.io.File(filesDir, "lumecard_crash.txt")
                    file.writeText(msg)
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e("LumeCard", "Failed to save crash log", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Read crash log from file or prefs
        try {
            val file = java.io.File(filesDir, "lumecard_crash.txt")
            if (file.exists()) {
                val msg = file.readText()
                CrashLogHolder.lastCrashLog = msg
                file.delete()
                Log.i("LumeCard", "Restored crash log from file")
            } else {
                val prefs = getSharedPreferences("lumecard_crash", MODE_PRIVATE)
                val savedCrash = prefs.getString("last_crash", null)
                if (savedCrash != null) {
                    CrashLogHolder.lastCrashLog = savedCrash
                    prefs.edit().remove("last_crash").commit()
                    Log.i("LumeCard", "Restored crash log from prefs")
                }
            }
        } catch (e: Exception) {
            Log.e("LumeCard", "Failed to read crash log", e)
        }

        if (GlobalContext.getOrNull() == null) {
            startKoin { modules(appModule) }
        }
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}

