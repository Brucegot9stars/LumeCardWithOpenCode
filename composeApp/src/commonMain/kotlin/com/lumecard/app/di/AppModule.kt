package com.lumecard.app.di

import com.lumecard.app.ui.screens.card.CardViewModel
import com.lumecard.app.ui.screens.dashboard.DashboardViewModel
import com.lumecard.app.ui.screens.deck.DeckViewModel
import com.lumecard.app.ui.screens.settings.ThemeStateHolder
import com.lumecard.app.ui.screens.stats.StatsViewModel
import com.lumecard.app.ui.screens.study.StudyViewModel
import com.lumecard.shared.data.ExportManager
import com.lumecard.shared.di.sharedModule
import org.koin.dsl.module

val appModule = module {
    includes(sharedModule)
    includes(platformModule)

    single { ThemeStateHolder() }
    single { ExportManager() }

    factory { DashboardViewModel(get()) }
    factory { DeckViewModel(get()) }
    factory { StudyViewModel(get(), get(), get()) }
    factory { CardViewModel(get()) }
    factory { StatsViewModel(get(), get()) }
}
