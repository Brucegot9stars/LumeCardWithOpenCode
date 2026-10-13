package com.lumecard.app.di

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import org.koin.core.context.GlobalContext

/** Lets Voyager own Koin-created ScreenModels until their screen leaves the navigator. */
@Composable
inline fun <reified T : ScreenModel> Screen.rememberInjectedScreenModel(tag: String? = null): T =
    rememberScreenModel(tag) { GlobalContext.get().get<T>() }
