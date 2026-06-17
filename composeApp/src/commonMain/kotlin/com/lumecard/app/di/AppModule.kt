package com.lumecard.app.di

import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.screens.card.CardViewModel
import com.lumecard.app.ui.screens.dashboard.DashboardViewModel
import com.lumecard.app.ui.screens.deck.DeckViewModel
import com.lumecard.app.ui.screens.knowledgebase.KnowledgeBaseViewModel
import com.lumecard.app.ui.screens.learningplan.LearningPlanViewModel
import com.lumecard.app.ui.screens.settings.SettingsStateHolder
import com.lumecard.app.ui.screens.settings.SettingsViewModel
import com.lumecard.app.ui.screens.stats.StatsViewModel
import com.lumecard.app.ui.screens.study.StudyViewModel
import com.lumecard.shared.data.ExportManager
import com.lumecard.shared.di.sharedModule
import org.koin.dsl.module

val appModule = module {
    includes(sharedModule)
    includes(platformModule)

    single { SettingsStateHolder() }
    single { ExportManager() }
    single { I18nManager() }

    factory { DashboardViewModel(get(), get(), get(), get(), get(), get()) }
    factory { DeckViewModel(get(), get(), get()) }
    factory { StudyViewModel(get(), get(), get(), get()) }
    factory { CardViewModel(get(), get()) }
    factory { StatsViewModel(get(), get(), get()) }
    factory { SettingsViewModel(get(), get()) }
    factory { KnowledgeBaseViewModel(get()) }
    factory { LearningPlanViewModel(get(), get(), get()) }
}

