package com.lumecard.app

import android.os.Bundle
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
        if (GlobalContext.getOrNull() == null) {
            startKoin { modules(appModule) }
        }
        enableEdgeToEdge()
        setContent {
            App()
        }
    }

    @Deprecated("Use registerForActivityResult instead", ReplaceWith(""))
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            1001, 1002 -> {
                val uri = if (resultCode == RESULT_OK) data?.data?.toString() else null
                FilePickerState.onResult(uri)
            }
        }
    }
}

