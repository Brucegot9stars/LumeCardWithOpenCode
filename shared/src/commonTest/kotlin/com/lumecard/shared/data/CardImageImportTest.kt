package com.lumecard.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 带图 share JSON 的导入契约。
 *
 * 与 `SettingsScreen` UI 导入补 `media/metadata/title` 的修复配套：验证数据源头
 * （ExportManager.importShareData）解析带图卡片时不丢字段。UI 层暂无单测框架，
 * 其修复由编译与静态自审保证。
 */
class CardImageImportTest {

    private fun shareJson(cardJson: String) = """
        {
          "exportDate": "2026-11-19T00:00:00Z",
          "decks": [{
            "id": "d1", "knowledgeBaseId": "kb1", "name": "Deck",
            "description": "", "color": "#4CAF50", "icon": "book",
            "createdAt": "2026-11-19T00:00:00Z", "updatedAt": "2026-11-19T00:00:00Z"
          }],
          "cards": [$cardJson]
        }
    """.trimIndent()

    private fun importSingle(cardJson: String) =
        assertNotNull(ExportManager().importShareData(shareJson(cardJson))).cards.single()

    @Test
    fun `share import keeps media metadata and title`() {
        val card = """
            {
              "id": "c1", "deckId": "d1", "type": "BASIC",
              "front": "正面", "back": "反面", "title": "带图卡",
              "tags": [],
              "media": [{"id":"m1","type":"IMAGE","filePath":"a1b2.png","mimeType":"image/png","size":1024}],
              "metadata": {"mediaTarget":"front"},
              "createdAt": "2026-11-19T00:00:00Z", "updatedAt": "2026-11-19T00:00:00Z", "version": 1
            }
        """.trimIndent()
        val ec = importSingle(card)
        assertEquals("带图卡", ec.title)
        assertEquals(1, ec.media.size)
        assertEquals("a1b2.png", ec.media.single().filePath)
        assertEquals("", ec.media.single().caption)
        assertEquals("front", ec.metadata["mediaTarget"])
    }

    @Test
    fun `share import keeps network image with minimal fields`() {
        val card = """
            {
              "id": "c2", "deckId": "d1", "type": "BASIC",
              "front": "f", "back": "b", "title": "",
              "tags": [],
              "media": [{"id":"m2","type":"IMAGE","filePath":"https://cdn.example.com/x.png","caption":"示意图"}],
              "metadata": {},
              "createdAt": "2026-11-19T00:00:00Z", "updatedAt": "2026-11-19T00:00:00Z", "version": 1
            }
        """.trimIndent()
        val ec = importSingle(card)
        assertEquals(1, ec.media.size)
        assertEquals("https://cdn.example.com/x.png", ec.media.single().filePath)
        assertEquals("", ec.media.single().mimeType)
        assertEquals(0L, ec.media.single().size)
        assertEquals("示意图", ec.media.single().caption)
    }

    @Test
    fun `legacy plain text card import stays media free`() {
        val card = """
            {
              "id": "c3", "deckId": "d1", "type": "BASIC",
              "front": "f", "back": "b", "tags": [],
              "createdAt": "2026-11-19T00:00:00Z", "updatedAt": "2026-11-19T00:00:00Z", "version": 1
            }
        """.trimIndent()
        val ec = importSingle(card)
        assertTrue(ec.media.isEmpty())
        assertTrue(ec.metadata.isEmpty())
        assertEquals("", ec.title)
    }
}
