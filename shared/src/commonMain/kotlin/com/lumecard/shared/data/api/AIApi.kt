package com.lumecard.shared.data.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class AIGenerateRequest(
    val content: String,
    val type: String,
    val cardCount: Int = 10
)

@Serializable
data class AIGenerateResponse(
    val cards: List<AIGeneratedCard>,
    val success: Boolean,
    val error: String? = null
)

@Serializable
data class AIGeneratedCard(
    val front: String,
    val back: String,
    val type: String,
    val confidence: Double
)

interface AIApi {
    suspend fun generateCards(request: AIGenerateRequest): AIGenerateResponse
    suspend fun generateCardsStream(request: AIGenerateRequest): Flow<String>
    suspend fun explainConcept(concept: String): String
    suspend fun generateQuiz(topic: String, count: Int = 5): List<AIGeneratedCard>
}

class AIApiImpl(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1"
) : AIApi {

    override suspend fun generateCards(request: AIGenerateRequest): AIGenerateResponse {
        return AIGenerateResponse(
            cards = emptyList(),
            success = true
        )
    }

    override suspend fun generateCardsStream(request: AIGenerateRequest): Flow<String> {
        return kotlinx.coroutines.flow.flow {
            emit("正在生成...")
        }
    }

    override suspend fun explainConcept(concept: String): String {
        return "概念解释: $concept"
    }

    override suspend fun generateQuiz(topic: String, count: Int): List<AIGeneratedCard> {
        return emptyList()
    }
}
