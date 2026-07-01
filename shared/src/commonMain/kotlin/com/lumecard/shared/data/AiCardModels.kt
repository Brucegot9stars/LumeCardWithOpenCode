package com.lumecard.shared.data

import com.lumecard.shared.model.CardType
import kotlinx.serialization.Serializable

enum class AiCardMode {
    AUTO,
    SPECIFY_KB,
    SPECIFY_BOTH,
}

data class AiCardRequest(
    val config: AiConfig,
    val mode: AiCardMode,
    val knowledgeBaseId: String? = null,
    val deckId: String? = null,
    val knowledgeBaseName: String? = null,
    val deckName: String? = null,
    val topic: String,
    val referenceMaterials: String,
    val additionalRequirements: String = "",
    val cardCount: Int,
    val systemPrompt: String,
    val appLanguage: String = "中文",
)

data class AiCardResult(
    val knowledgeBaseName: String,
    val deckName: String,
    val knowledgeBaseId: String,
    val deckId: String,
    val cardsCreated: Int,
    val cardIds: List<String>,
)

enum class AiCardError {
    NO_CONFIG,
    CONNECTION_FAILED,
    AUTH_FAILED,
    API_ERROR,
    PARSE_ERROR,
    RATE_LIMITED,
    TIMEOUT,
    NO_CONTENT,
    UNKNOWN,
}

data class AiCardException(
    val error: AiCardError,
    override val message: String,
) : Exception(message)

@Serializable
data class AiCardResponseJson(
    val knowledge_base_name: String = "",
    val deck_name: String = "",
    val cards: List<AiCardItemJson> = emptyList(),
)

@Serializable
data class AiCardItemJson(
    val front: String = "",
    val back: String = "",
    val type: String = "BASIC",
    val tags: List<String> = emptyList(),
)
