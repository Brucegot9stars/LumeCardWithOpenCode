package com.lumecard.app.ui.navigation

import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import com.lumecard.app.ui.screens.card.CreateCardScreen
import com.lumecard.app.ui.screens.dashboard.DashboardScreen
import com.lumecard.app.ui.screens.deck.DeckListScreen
import com.lumecard.app.ui.screens.settings.SettingsScreen
import com.lumecard.app.ui.screens.stats.StatsScreen
import com.lumecard.app.ui.screens.study.StudyScreen

sealed class LumeCardScreen(val title: String) : Screen {
    data object Dashboard : LumeCardScreen("首页")
    data object DeckList : LumeCardScreen("牌组")
    data object Settings : LumeCardScreen("设置")
    data object Stats : LumeCardScreen("统计")

    override val key: ScreenKey
        get() = this::class.simpleName ?: "Screen"
}

// 动态参数屏幕
data class StudyScreen(val deckId: String, val deckName: String) : Screen {
    override val key: ScreenKey = "Study_$deckId"
}

data class CreateCardScreen(val deckId: String, val deckName: String) : Screen {
    override val key: ScreenKey = "CreateCard_$deckId"
}

data class CardListScreen(val deckId: String, val deckName: String) : Screen {
    override val key: ScreenKey = "CardList_$deckId"
}
