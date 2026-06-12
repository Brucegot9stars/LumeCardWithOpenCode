package com.lumecard.shared.di

import com.lumecard.shared.domain.scheduler.FSRSAlgorithm
import com.lumecard.shared.repository.*
import org.koin.dsl.module

val sharedModule = module {
    // Repositories
    single<KnowledgeBaseRepository> { InMemoryKnowledgeBaseRepository() }
    single<DeckRepository> { InMemoryDeckRepository() }
    single<CardRepository> { InMemoryCardRepository() }
    single<ReviewLogRepository> { InMemoryReviewLogRepository() }

    // Algorithms
    single { FSRSAlgorithm() }
}
