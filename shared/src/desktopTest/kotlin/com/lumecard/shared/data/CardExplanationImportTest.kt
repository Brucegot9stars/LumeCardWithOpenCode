package com.lumecard.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CardExplanationImportTest {
    @Test
    fun explanationTravelsThroughShareImport() {
        val source = """
            {
              "exportType": "share",
              "version": "1.0.0",
              "schemaVersion": 1,
              "exportDate": "2026-11-26T00:00:00Z",
              "knowledgeBases": [
                {"id":"kb","name":"Test","createdAt":"2026-11-26T00:00:00Z","updatedAt":"2026-11-26T00:00:00Z"}
              ],
              "decks": [
                {"id":"deck","knowledgeBaseId":"kb","name":"Deck","description":null,"color":"#4CAF50","icon":"📚","createdAt":"2026-11-26T00:00:00Z","updatedAt":"2026-11-26T00:00:00Z"}
              ],
              "cards": [
                {
                  "id":"card","deckId":"deck","type":"MULTIPLE_CHOICE",
                  "front":"Which option is correct?","back":"+Correct\nWrong",
                  "explanation":"The correct option is the only one that satisfies the rule.",
                  "tags":[],"createdAt":"2026-11-26T00:00:00Z","updatedAt":"2026-11-26T00:00:00Z"
                }
              ]
            }
        """.trimIndent()

        val manager = ExportManager()
        val imported = assertNotNull(manager.importData(source))
        assertEquals(
            "The correct option is the only one that satisfies the rule.",
            imported.cards.single().explanation,
        )
        assertEquals("+Correct\nWrong", imported.cards.single().back)
        assertNotNull(manager.importShareData(source))
    }
}
