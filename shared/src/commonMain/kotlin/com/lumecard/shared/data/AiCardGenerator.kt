package com.lumecard.shared.data

import com.lumecard.shared.data.ai.AiFallbackManager
import com.lumecard.shared.data.ai.ProviderAdapter
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.KnowledgeBaseRepository
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AiCardGenerator(
    private val adapter: ProviderAdapter,
    private val fallbackManager: AiFallbackManager,
    private val promptManager: AiCardPromptManager,
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun generate(
        request: AiCardRequest,
        onProgress: ((received: Long, total: Long?) -> Unit)? = null,
        onLog: ((LogEntry) -> Unit)? = null,
    ): Result<AiCardResult> {
        return try {
            val promptTemplate = promptManager.getActivePrompt()
            val systemPrompt = promptTemplate
                .replace("{card_count}", request.cardCount.toString())
                .replace("{app_language}", request.appLanguage)
            val userMessage = buildUserMessage(request)

            val now = Clock.System.now().toEpochMilliseconds()
            onLog?.invoke(LogEntry(now, LogEntryType.SYSTEM_PROMPT, "System Prompt", systemPrompt))
            onLog?.invoke(LogEntry(now, LogEntryType.USER_MESSAGE, "User Message", userMessage))
            onLog?.invoke(LogEntry(now, LogEntryType.INFO, "Request", "Sending to ${request.config.model} (${request.config.baseUrl})"))

            val response = fallbackManager.sendWithFallback(
                config = request.config,
                systemPrompt = systemPrompt,
                userMessage = userMessage,
                onProgress = onProgress,
            )

            val content = response.getOrElse { e ->
                val msg = e.message ?: ""
                val aiErr = when {
                    msg.startsWith("Server error (5") && msg.contains("timeout", ignoreCase = true) ->
                        AiCardException(AiCardError.TIMEOUT, "AI 服务端超时，请减少单次制卡数量或分批生成")
                    msg.startsWith("Server error (") ->
                        AiCardException(AiCardError.API_ERROR, msg)
                    msg.contains("401") -> AiCardException(AiCardError.AUTH_FAILED, msg)
                    msg.contains("429") -> AiCardException(AiCardError.RATE_LIMITED, msg)
                    msg.contains("Timeout", ignoreCase = true) -> AiCardException(AiCardError.TIMEOUT, msg)
                    msg.contains("Connection") -> AiCardException(AiCardError.CONNECTION_FAILED, msg)
                    msg.contains("Parse error", ignoreCase = true) -> AiCardException(AiCardError.PARSE_ERROR, msg)
                    else -> AiCardException(AiCardError.API_ERROR, msg)
                }
                return Result.failure(aiErr)
            }

            val cleaned = content
                .trim()
                .let { s ->
                    if (s.startsWith("```")) {
                        s.substringAfter('\n').trim().removeSuffix("```").trim()
                    } else s
                }

            onLog?.invoke(LogEntry(now, LogEntryType.API_RESPONSE, "Raw Response", cleaned.take(2000)))

            val parsed = try {
                json.decodeFromString<AiCardResponseJson>(cleaned)
            } catch (e: Exception) {
                val fallback = extractCardsFallback(cleaned)
                if (fallback != null) {
                    onLog?.invoke(LogEntry(now, LogEntryType.WARNING, "Parse Warning", "Full JSON parse failed, extracted ${fallback.cards.size} cards via fallback"))
                    fallback
                } else {
                    val msg = e.message ?: ""
                    val shortMsg = when {
                        msg.contains("EOF", ignoreCase = true) ->
                            "AI 返回内容被截断，请检查模型的 max_tokens 设置或减少每批制卡数量"
                        else -> "解析 AI 返回的 JSON 失败"
                    }
                    val fullMsg = "$shortMsg|||详细错误：${e.message}\n\nJSON input:\n$cleaned"
                    return Result.failure(
                        AiCardException(AiCardError.PARSE_ERROR, fullMsg)
                    )
                }
            }

            if (parsed.cards.isEmpty()) {
                return Result.failure(
                    AiCardException(AiCardError.NO_CONTENT, "AI returned no cards")
                )
            }

            val (kbId, deckId) = resolveEntities(request, parsed)
            val cardIds = createCards(deckId, parsed)

            val cardTypes = parsed.cards.groupBy { it.type }.map { (t, c) -> "${c.size}x $t" }.joinToString(", ")
            onLog?.invoke(LogEntry(now, LogEntryType.PARSE_RESULT, "Parse Result", "${cardIds.size} cards parsed ($cardTypes) → deck: ${parsed.deck_name}"))

            Result.success(
                AiCardResult(
                    knowledgeBaseName = parsed.knowledge_base_name.ifBlank { request.knowledgeBaseName ?: "AI Cards" },
                    deckName = parsed.deck_name.ifBlank { request.deckName ?: "AI Cards" },
                    knowledgeBaseId = kbId,
                    deckId = deckId,
                    cardsCreated = cardIds.size,
                    cardIds = cardIds,
                )
            )
        } catch (e: AiCardException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(
                AiCardException(AiCardError.UNKNOWN, "Unexpected error: ${e.message}")
            )
        }
    }

    private fun buildUserMessage(request: AiCardRequest): String {
        val sb = StringBuilder()
        sb.appendLine("Application Language: ${request.appLanguage}")
        sb.appendLine("Target Card Count: ${request.cardCount}")
        when (request.mode) {
            AiCardMode.AUTO, AiCardMode.SPECIFY_KB -> {
                sb.appendLine("Create a knowledge base and deck with appropriate names.")
            }
            AiCardMode.SPECIFY_BOTH -> {}
        }
        sb.appendLine()
        sb.appendLine("Learning Topic:")
        sb.appendLine(request.topic)
        if (request.additionalRequirements.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("Additional Requirements:")
            sb.appendLine(request.additionalRequirements)
        }
        if (request.referenceMaterials.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("Reference Materials:")
            sb.appendLine(request.referenceMaterials)
        }
        return sb.toString()
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun resolveEntities(request: AiCardRequest, parsed: AiCardResponseJson): Pair<String, String> {
        if (request.knowledgeBaseId != null && request.deckId != null) {
            return Pair(request.knowledgeBaseId, request.deckId)
        }
        if (request.knowledgeBaseId != null) {
            val kbId = request.knowledgeBaseId
            val deckName = makeUniqueDeckName(kbId, parsed.deck_name.ifBlank { "Cards" })
            val deck = Deck(
                id = "deck_${Uuid.random().toString().take(8)}",
                knowledgeBaseId = kbId,
                name = deckName,
                description = "Auto-generated by AI",
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
            )
            deckRepository.insert(deck)
            return Pair(kbId, deck.id)
        }
        return when (request.mode) {
            AiCardMode.AUTO -> {
                val kbName = makeUniqueKbName(parsed.knowledge_base_name.ifBlank { "AI Cards" })
                val kb = KnowledgeBase(
                    id = "kb_${Uuid.random().toString().take(8)}",
                    name = kbName,
                    description = "Auto-generated by AI",
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                )
                knowledgeBaseRepository.insert(kb)

                val deckName = makeUniqueDeckName(kb.id, parsed.deck_name.ifBlank { "Cards" })
                val deck = Deck(
                    id = "deck_${Uuid.random().toString().take(8)}",
                    knowledgeBaseId = kb.id,
                    name = deckName,
                    description = "Auto-generated by AI",
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                )
                deckRepository.insert(deck)
                Pair(kb.id, deck.id)
            }

            AiCardMode.SPECIFY_KB -> {
                val kbId = request.knowledgeBaseId
                    ?: throw AiCardException(AiCardError.UNKNOWN, "KnowledgeBase ID required")
                val deckName = makeUniqueDeckName(kbId, parsed.deck_name.ifBlank { "Cards" })
                val deck = Deck(
                    id = "deck_${Uuid.random().toString().take(8)}",
                    knowledgeBaseId = kbId,
                    name = deckName,
                    description = "Auto-generated by AI",
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                )
                deckRepository.insert(deck)
                Pair(kbId, deck.id)
            }

            AiCardMode.SPECIFY_BOTH -> {
                val deckId = request.deckId
                    ?: throw AiCardException(AiCardError.UNKNOWN, "Deck ID required")
                val kbId = request.knowledgeBaseId
                    ?: throw AiCardException(AiCardError.UNKNOWN, "KnowledgeBase ID required")
                Pair(kbId, deckId)
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun createCards(deckId: String, parsed: AiCardResponseJson): List<String> {
        val ids = mutableListOf<String>()
        val now = Clock.System.now()
        for (item in parsed.cards) {
            val cardType = parseCardType(item.type)
            val card = Card(
                id = Uuid.random().toString(),
                deckId = deckId,
                type = cardType,
                front = item.front,
                back = item.back,
                tags = item.tags,
                createdAt = now,
                updatedAt = now,
            )
            cardRepository.insert(card)
            ids.add(card.id)
        }
        return ids
    }

    private fun extractCardsFallback(text: String): AiCardResponseJson? {
        val trimmed = text.trim()

        val kbName = Regex("""knowledge_base_name["\s]*:["\s]*"([^"]*)"""").find(trimmed)?.groupValues?.getOrNull(1) ?: ""
        val deckName = Regex("""deck_name["\s]*:["\s]*"([^"]*)"""").find(trimmed)?.groupValues?.getOrNull(1) ?: ""

        val cardsStart = trimmed.indexOf("\"cards\"")
        if (cardsStart == -1) return null
        val arrayStart = trimmed.indexOf('[', cardsStart)
        if (arrayStart == -1) return null

        val cards = mutableListOf<AiCardItemJson>()
        var i = arrayStart + 1
        while (i < trimmed.length) {
            while (i < trimmed.length && (trimmed[i] == ' ' || trimmed[i] == '\n' || trimmed[i] == '\r' || trimmed[i] == '\t' || trimmed[i] == ',')) i++
            if (i >= trimmed.length || trimmed[i] == ']') break

            if (trimmed[i] == '{') {
                val braceStart = i
                var depth = 0
                var inString = false
                var escaped = false
                while (i < trimmed.length) {
                    val c = trimmed[i]
                    if (escaped) { escaped = false; i++; continue }
                    if (c == '\\' && inString) { escaped = true; i++; continue }
                    if (c == '"') inString = !inString
                    if (!inString) {
                        if (c == '{') depth++
                        else if (c == '}') {
                            depth--
                            if (depth == 0) { i++; break }
                        }
                    }
                    i++
                }
                val cardJson = trimmed.substring(braceStart, i)
                try {
                    val card = json.decodeFromString<AiCardItemJson>(cardJson)
                    cards.add(card)
                } catch (_: Exception) { }
            } else {
                i++
            }
        }
        if (cards.isEmpty()) return null
        return AiCardResponseJson(knowledge_base_name = kbName, deck_name = deckName, cards = cards)
    }

    private val allowedAiTypes = setOf(
        CardType.BASIC,
        CardType.REVERSED,
        CardType.CLOZE,
        CardType.MULTIPLE_CHOICE,
        CardType.MARKDOWN,
        CardType.RICH_TEXT,
    )

    private fun parseCardType(type: String): CardType {
        return try {
            val parsed = CardType.valueOf(type.uppercase())
            if (parsed in allowedAiTypes) parsed else CardType.BASIC
        } catch (_: IllegalArgumentException) {
            CardType.BASIC
        }
    }

    private suspend fun makeUniqueKbName(baseName: String): String {
        val existing = knowledgeBaseRepository.getByName(baseName)
        if (existing == null) return baseName
        var suffix = 1
        while (true) {
            val candidate = "$baseName ($suffix)"
            if (knowledgeBaseRepository.getByName(candidate) == null) return candidate
            suffix++
        }
    }

    private suspend fun makeUniqueDeckName(kbId: String, baseName: String): String {
        val existing = deckRepository.getByNameInKnowledgeBase(kbId, baseName)
        if (existing == null) return baseName
        var suffix = 1
        while (true) {
            val candidate = "$baseName ($suffix)"
            if (deckRepository.getByNameInKnowledgeBase(kbId, candidate) == null) return candidate
            suffix++
        }
    }
}
