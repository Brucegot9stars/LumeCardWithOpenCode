package com.lumecard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lumecard.app.di.appModule
import com.lumecard.shared.database.AndroidContextHolder
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidContextHolder.context = this
        startKoin { modules(appModule) }
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
