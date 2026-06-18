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
        AndroidContextHolder.context = this
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
        if (resultCode == RESULT_OK) {
            val uri = data?.data?.toString()
            FilePickerState.onResult(uri)
        } else {
            FilePickerState.onResult(null)
        }
    }
}

