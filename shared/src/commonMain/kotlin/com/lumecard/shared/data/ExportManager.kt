package com.lumecard.shared.data

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Deck
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LumeCardExport(
    val version: String = "1.0.0",
    val exportDate: String,
    val decks: List<ExportDeck>,
    val cards: List<ExportCard>
)

@Serializable
data class ExportDeck(
    val id: String,
    val knowledgeBaseId: String = "default",
    val name: String,
    val description: String?,
    val color: String,
    val icon: String,
    val parentId: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class ExportCard(
    val id: String,
    val deckId: String,
    val type: String,
    val front: String,
    val back: String,
    val tags: List<String>,
    val createdAt: String,
    val updatedAt: String
)

class ExportManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun exportToJson(
        decks: List<Deck>,
        cards: List<Card>
    ): String {
        val export = LumeCardExport(
            exportDate = kotlinx.datetime.Clock.System.now().toString(),
            decks = decks.map { deck ->
                ExportDeck(
                    id = deck.id,
                    knowledgeBaseId = deck.knowledgeBaseId,
                    name = deck.name,
                    description = deck.description,
                    color = deck.color,
                    icon = deck.icon,
                    parentId = deck.parentId,
                    createdAt = deck.createdAt.toString(),
                    updatedAt = deck.updatedAt.toString()
                )
            },
            cards = cards.map { card ->
                ExportCard(
                    id = card.id,
                    deckId = card.deckId,
                    type = card.type.name,
                    front = card.front,
                    back = card.back,
                    tags = card.tags,
                    createdAt = card.createdAt.toString(),
                    updatedAt = card.updatedAt.toString()
                )
            }
        )
        return json.encodeToString(LumeCardExport.serializer(), export)
    }

    fun importFromJson(jsonString: String): LumeCardExport? {
        return try {
            json.decodeFromString(LumeCardExport.serializer(), jsonString)
        } catch (e: Exception) {
            null
        }
    }

    fun exportToCsv(cards: List<Card>): String {
        val header = "Front,Back,Tags,Type"
        val rows = cards.map { card ->
            val front = card.front.replace(",", ";").replace("\n", " ")
            val back = card.back.replace(",", ";").replace("\n", " ")
            val tags = card.tags.joinToString(";")
            "$front,$back,$tags,${card.type.name}"
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun importFromCsv(csvString: String): List<Pair<String, String>> {
        val lines = csvString.lines().drop(1) // Skip header
        return lines.mapNotNull { line ->
            val parts = line.split(",")
            if (parts.size >= 2) {
                Pair(parts[0].trim(), parts[1].trim())
            } else {
                null
            }
        }
    }
}

