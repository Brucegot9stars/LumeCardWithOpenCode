package com.lumecard.app.ui.navigation

sealed class LumeCardScreen(val title: String) {
    data object Dashboard : LumeCardScreen("首页")
    data object DeckList : LumeCardScreen("牌组")
    data object Settings : LumeCardScreen("设置")
    data object Stats : LumeCardScreen("统计")
}

// 动态参数屏幕 - 这些 Screen 类已在各自的文件中定义
// StudyScreen -> com.lumecard.app.ui.screens.study.StudyScreen
// CreateCardScreen -> com.lumecard.app.ui.screens.card.CreateCardScreen
// CardListScreen -> com.lumecard.app.ui.screens.card.CardListScreen
