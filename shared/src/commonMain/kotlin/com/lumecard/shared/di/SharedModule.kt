package com.lumecard.shared.di

import com.lumecard.shared.data.SyncManager
import com.lumecard.shared.data.WebDavConfigManager
import com.lumecard.shared.database.LumeCardDatabase
import com.lumecard.shared.domain.scheduler.*
import com.lumecard.shared.repository.*
import io.ktor.client.HttpClient
import org.koin.dsl.module

val sharedModule = module {
    // Database (DatabaseDriverFactory must be provided by platform module)
    single { LumeCardDatabase(get<com.lumecard.shared.database.DatabaseDriverFactory>().createDriver()) }

    // HTTP client (auto-detects platform engine: OkHttp on JVM/Android)
    single { HttpClient() }

    // Repositories
    single<KnowledgeBaseRepository> { SqlDelightKnowledgeBaseRepository(get()) }
    single<DeckRepository> { SqlDelightDeckRepository(get()) }
    single<CardRepository> { SqlDelightCardRepository(get()) }
    single<ReviewLogRepository> { SqlDelightReviewLogRepository(get()) }
    single<SettingsRepository> { SqlDelightSettingsRepository(get()) }
    single<AlgorithmStateRepository> { SqlDelightAlgorithmStateRepository(get()) }

    // Data services
    single { SyncManager(get()) }
    single { WebDavConfigManager(get(), get()) }

    // Algorithm implementations
    single { FSRSAlgorithm() }

    // Default algorithm (FSRS) — uses the FSRSAlgorithm singleton via get()
    factory<ReviewAlgorithm> { FSRSAlgorithmAdapter(get()) }
}
