package com.lumecard.shared.di

import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.domain.scheduler.*
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
    single<SettingsRepository> { SqlDelightSettingsRepository(get()) }
    single<AlgorithmStateRepository> { SqlDelightAlgorithmStateRepository(get()) }

    // Algorithm implementations
    single { FSRSAlgorithm() }

    // Factory to create the right algorithm based on mode
    fun createAlgorithm(mode: ReviewMode): ReviewAlgorithm {
        return when (mode) {
            ReviewMode.FSRS -> FSRSAlgorithmAdapter(FSRSAlgorithm())
            ReviewMode.SM2 -> SM2Algorithm()
            ReviewMode.LEITNER -> LeitnerAlgorithm()
            ReviewMode.SIMPLE -> SimpleAlgorithm()
        }
    }
    factory<ReviewAlgorithm> { createAlgorithm(ReviewMode.SM2) } // default fallback
}
