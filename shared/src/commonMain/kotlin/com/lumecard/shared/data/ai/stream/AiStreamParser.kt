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
