package com.lumecard.shared.di

import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.domain.scheduler.FSRSAlgorithm
import com.lumecard.shared.repository.*
import org.koin.dsl.module

val sharedModule = module {
    // Database (DatabaseDriverFactory must be provided by platform module)
    single { LumeCardDatabase(get<com.lumecard.shared.database.DatabaseDriverFactory>().createDriver()) }

    // Repositories
    single<KnowledgeBaseRepository> { SqlDelightKnowledgeBaseRepository(get()) }
    single<DeckRepository> { SqlDelightDeckRepository(get()) }
    single<CardRepository> { SqlDelightCardRepository(get()) }
    single<ReviewLogRepository> { SqlDelightReviewLogRepository(get()) }

    // Algorithms
    single { FSRSAlgorithm() }
}
