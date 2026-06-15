package com.lumecard.app.ui.navigation

sealed class LumeCardScreen {
    data object Dashboard : LumeCardScreen()
    data object DeckList : LumeCardScreen()
    data object Settings : LumeCardScreen()
    data object Stats : LumeCardScreen()
}
