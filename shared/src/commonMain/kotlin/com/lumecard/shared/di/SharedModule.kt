package com.lumecard.shared.di

import com.lumecard.shared.data.SyncManager
import com.lumecard.shared.data.WebDavConfigManager
import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.domain.scheduler.*
import com.lumecard.shared.repository.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.koin.dsl.module

val sharedModule = module {
    // Database (DatabaseDriverFactory must be provided by platform module)
    single { LumeCardDatabase(get<com.lumecard.shared.database.DatabaseDriverFactory>().createDriver()) }

    // HTTP client with timeout configuration
    single {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }

    // Repositories
    single<KnowledgeBaseRepository> { SqlDelightKnowledgeBaseRepository(get()) }
    single<DeckRepository> { SqlDelightDeckRepository(get()) }
    single<CardRepository> { SqlDelightCardRepository(get()) }
    single<ReviewLogRepository> { SqlDelightReviewLogRepository(get()) }
    single<SettingsRepository> { SqlDelightSettingsRepository(get()) }
    single<AlgorithmStateRepository> { SqlDelightAlgorithmStateRepository(get()) }
    single<LearningPlanRepository> { SqlDelightLearningPlanRepository(get()) }

    // Data services
    single { SyncManager(get()) }
    single { WebDavConfigManager(get(), get()) }

    // Algorithm implementations
    single { FSRSAlgorithm() }

    // Default algorithm (FSRS) — uses the FSRSAlgorithm singleton via get()
    factory<ReviewAlgorithm> { FSRSAlgorithmAdapter(get()) }
}
