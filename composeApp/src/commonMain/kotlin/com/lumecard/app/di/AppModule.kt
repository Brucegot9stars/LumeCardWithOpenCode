package com.lumecard.app.di

import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.OperationConfirmationManager
import com.lumecard.app.data.AiCardGenerationManager
import com.lumecard.app.ui.screens.aicard.AiCardViewModel
import com.lumecard.shared.data.EntityMergeManager
import com.lumecard.app.ui.screens.card.CardViewModel
import com.lumecard.app.ui.screens.dashboard.DashboardViewModel
import com.lumecard.app.ui.screens.deck.DeckViewModel
import com.lumecard.app.ui.screens.knowledgebase.KnowledgeBaseViewModel
import com.lumecard.app.ui.screens.learningplan.LearningPlanViewModel
import com.lumecard.app.ui.screens.settings.SettingsStateHolder
import com.lumecard.app.ui.screens.settings.SettingsViewModel
import com.lumecard.app.ui.screens.stats.StatsViewModel
import com.lumecard.app.ui.screens.study.StudyViewModel
import com.lumecard.app.ui.screens.warehouse.WarehouseViewModel
import com.lumecard.shared.data.ExportManager
import com.lumecard.shared.di.sharedModule
import org.koin.dsl.module

val appModule = module {
    includes(sharedModule)
    includes(platformModule)

    single { SettingsStateHolder() }
    single { ExportManager() }
    single { I18nManager() }
    single { EntityMergeManager(get(), get(), get()) }
    single { OperationConfirmationManager() }
    single { AiCardGenerationManager(get(), get(), get(), get(), get()) }

    factory { AiCardViewModel(get()) }
    factory { DashboardViewModel(get(), get(), get(), get(), get(), get()) }
    factory { DeckViewModel(get(), get(), get(), get()) }
    factory { StudyViewModel(get(), get(), get(), get(), get(), get()) }
    factory { CardViewModel(get(), get()) }
    factory { StatsViewModel(get(), get(), get(), get()) }
    factory { SettingsViewModel(get(), get(), get()) }
    factory { KnowledgeBaseViewModel(get(), get()) }
    factory { LearningPlanViewModel(get(), get(), get()) }
    factory { WarehouseViewModel(get(), get(), get(), get()) }
}

