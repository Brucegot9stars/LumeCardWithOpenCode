package com.lumecard.app.di

import com.lumecard.app.ui.screens.card.CardViewModel
import com.lumecard.app.ui.screens.dashboard.DashboardViewModel
import com.lumecard.app.ui.screens.study.StudyViewModel
import com.lumecard.shared.di.sharedModule
import org.koin.dsl.module

val appModule = module {
    includes(sharedModule)
    includes(platformModule)

    // ViewModels
    factory { DashboardViewModel(get()) }
    factory { StudyViewModel(get(), get()) }
    factory { CardViewModel(get()) }
}
