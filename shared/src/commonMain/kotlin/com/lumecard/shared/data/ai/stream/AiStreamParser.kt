package com.lumecard.shared.data.ai.stream

import com.lumecard.shared.data.AiCardItemJson

sealed class ParseResult {
    data class CardExtracted(val card: AiCardItemJson, val rawJson: String, val index: Int) : ParseResult()
    data class MetadataExtracted(val knowledgeBaseName: String?, val deckName: String?) : ParseResult()
    data object Incomplete : ParseResult()
    data class ParseError(val message: String) : ParseResult()
}

interface AiStreamParser {
    fun feed(chunk: String): List<ParseResult>
    fun reset()
    val accumulatedText: String
    val parsedCardCount: Int
}

class IncrementalJsonParser : AiStreamParser {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val buffer = StringBuilder()
    private var depth = 0
    private var inCardsArray = false
    private var cardStartPos = -1
    private var cardBraceDepth = 0
    private var cardIndex = 0
    private var _parsedCardCount = 0
    private var insideCardObject = false

    override val accumulatedText: String get() = buffer.toString()
    override val parsedCardCount: Int get() = _parsedCardCount

    override fun reset() {
        buffer.clear()
        depth = 0
        inCardsArray = false
        cardStartPos = -1
        cardBraceDepth = 0
        cardIndex = 0
        _parsedCardCount = 0
        insideCardObject = false
    }

    override fun feed(chunk: String): List<ParseResult> {
        val results = mutableListOf<ParseResult>()
        buffer.append(chunk)
        val text = buffer.toString()
        var i = 0

        while (i < text.length) {
            when (text[i]) {
                '{' -> {
                    depth++
                    if (inCardsArray && !insideCardObject && depth == cardBraceDepth + 1) {
                        insideCardObject = true
                        cardStartPos = i
                    }
                    i++
                }
                '}' -> {
                    depth--
                    if (inCardsArray && insideCardObject && depth == cardBraceDepth) {
                        insideCardObject = false
                        val cardJson = text.substring(cardStartPos, i + 1)
                        try {
                            val card = json.decodeFromString<AiCardItemJson>(cardJson)
                            results.add(ParseResult.CardExtracted(card, cardJson, cardIndex++))
                            _parsedCardCount++
                        } catch (_: Exception) {
                        }
                        cardStartPos = -1
                    }
                    i++
                }
                '"' -> {
                    val end = findStringEnd(text, i)
                    if (end > i) {
                        val segment = text.substring(i, end + 1)
                        if (segment.contains("cards", ignoreCase = true) && !inCardsArray) {
                            val afterStr = text.substring(end + 1).trimStart()
                            if (afterStr.startsWith(":")) {
                                val afterColon = afterStr.substring(1).trimStart()
                                if (afterColon.startsWith("[")) {
                                    inCardsArray = true
                                    cardBraceDepth = depth
                                    cardStartPos = -1
                                }
                            }
                        }
                        if (segment.contains("knowledge_base_name", ignoreCase = true) && !inCardsArray) {
                            val colonPos = text.indexOf(':', end)
                            if (colonPos > end) {
                                val afterColon = text.substring(colonPos + 1).trimStart()
                                val name = extractStringValue(afterColon)
                                if (name != null) {
                                    val deckName = tryExtractDeckName(text)
                                    results.add(ParseResult.MetadataExtracted(name, deckName))
                                }
                            }
                        }
                        i = end + 1
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }

        trimBuffer()
        return results
    }

    private fun tryExtractDeckName(text: String): String? {
        val deckPattern = Regex("\"deck_name\"\\s*:\\s*\"([^\"]*)\"")
        val match = deckPattern.find(text)
        return match?.groupValues?.get(1)
    }

    private fun findStringEnd(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            if (text[i] == '\\') i += 2
            else if (text[i] == '"') return i
            else i++
        }
        return -1
    }

    private fun extractStringValue(afterColon: String): String? {
        val trimmed = afterColon.trimStart()
        if (trimmed.startsWith("\"")) {
            val end = findStringEnd(trimmed, 0)
            return if (end > 0) trimmed.substring(1, end) else null
        }
        return null
    }

    private fun trimBuffer() {
        val lastBrace = buffer.lastIndexOf("}")
        val lastBracket = buffer.lastIndexOf("]")
        val cutPos = maxOf(lastBrace, lastBracket)
        if (cutPos > 0 && cutPos > buffer.length / 2) {
            val newStart = (cutPos - 8192).coerceAtLeast(0)
            if (newStart > 4096) {
                val removed = buffer.substring(0, newStart)
                buffer.delete(0, newStart)
            }
        }
    }
}

class SseStreamParser(private val jsonParser: AiStreamParser = IncrementalJsonParser()) : AiStreamParser {
    private val sseBuffer = StringBuilder()
    private var dataLine = StringBuilder()

    override val accumulatedText: String get() = jsonParser.accumulatedText
    override val parsedCardCount: Int get() = jsonParser.parsedCardCount

    override fun reset() {
        sseBuffer.clear()
        dataLine.clear()
        jsonParser.reset()
    }

    override fun feed(chunk: String): List<ParseResult> {
        sseBuffer.append(chunk)
        val results = mutableListOf<ParseResult>()
        val text = sseBuffer.toString()
        val lines = text.split("\n")
        sseBuffer.clear()

        for (line in lines) {
            when {
                line.startsWith("data: ") -> {
                    val content = line.removePrefix("data: ").trim()
                    if (content == "[DONE]") continue
                    dataLine.append(content)
                }
                line.isBlank() && dataLine.isNotEmpty() -> {
                    val jsonData = dataLine.toString().trim()
                    dataLine.clear()
                    if (jsonData.isNotBlank()) {
                        results.addAll(jsonParser.feed(jsonData))
                    }
                }
                line.isNotBlank() -> {
                    if (!line.startsWith(":")) {
                        results.addAll(jsonParser.feed(line + "\n"))
                    }
                }
            }
        }

        return results
    }
}
