package com.lumecard.shared.di

import com.lumecard.shared.data.AiCardGenerator
import com.lumecard.shared.data.AiCardPromptManager
import com.lumecard.shared.data.AiClient
import com.lumecard.shared.data.AiConfigManager
import com.lumecard.shared.data.MediaManager
import com.lumecard.shared.data.SyncManager
import com.lumecard.shared.data.UpdateManager
import com.lumecard.shared.data.WebDavConfigManager
import com.lumecard.shared.data.ai.AiClientAdapter
import com.lumecard.shared.data.ai.AiFallbackManager
import com.lumecard.shared.data.ai.AiModelListFetcher
import com.lumecard.shared.data.ai.ProviderAdapter
import com.lumecard.shared.data.ai.event.AiEventBus
import com.lumecard.shared.data.ai.task.AiBatchGenerator
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
                requestTimeoutMillis = 3_600_000
                socketTimeoutMillis = 3_600_000
                connectTimeoutMillis = 30_000
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
    single<MediaCacheRepository> { SqlDelightMediaCacheRepository(get()) }

    // Data services
    single { MediaManager(get()) }
    single { SyncManager(get()) }
    single { WebDavConfigManager(get(), get()) }
    single { UpdateManager(get()) }
    single<AiClient> { AiClient(get()) }
    single<ProviderAdapter> { AiClientAdapter(get()) }
    single<AiConfigManager> { AiConfigManager(get(), get()) }
    single { AiCardPromptManager(get()) }
    single { AiFallbackManager(get(), get()) }
    single { AiModelListFetcher(get(), get()) }
    single { AiCardGenerator(get(), get(), get(), get(), get(), get()) }
    single { AiEventBus() }
    single { AiBatchGenerator(get(), get()) }

    // Algorithm implementations
    single { FSRSAlgorithm() }

    // Default algorithm (FSRS) — uses the FSRSAlgorithm singleton via get()
    factory<ReviewAlgorithm> { FSRSAlgorithmAdapter(get()) }
}
