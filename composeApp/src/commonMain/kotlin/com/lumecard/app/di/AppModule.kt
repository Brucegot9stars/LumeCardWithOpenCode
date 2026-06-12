package com.lumecard.app.di

import com.lumecard.shared.di.sharedModule
import org.koin.dsl.module

val appModule = module {
    includes(sharedModule)

    // ViewModels and Screen Models will be added here
}
