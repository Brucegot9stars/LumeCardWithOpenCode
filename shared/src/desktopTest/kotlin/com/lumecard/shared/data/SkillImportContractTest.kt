package com.lumecard.shared.data

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillImportContractTest {
    @Test
    fun generatedSkillFilesMatchTheApplicationImporter() {
        val directory = System.getenv("LUMECARD_SKILL_CONTRACT_DIR")
        assumeTrue("Generate Skill fixtures and set LUMECARD_SKILL_CONTRACT_DIR to run this integration test", directory != null)
        val root = File(requireNotNull(directory))
        val cases = Json.parseToJsonElement(File(root, "manifest.json").readText()).jsonObject.getValue("cases").jsonArray
        assertTrue(cases.isNotEmpty())
        val manager = ExportManager()
        for (element in cases) {
            val case = element.jsonObject
            val name = case.getValue("file").jsonPrimitive.content
            val accepted = case.getValue("accepted").jsonPrimitive.boolean
            val source = File(root, name).readText(Charsets.UTF_8)
            val imported = manager.importData(source)
            if (accepted) {
                val data = assertNotNull(imported, name)
                assertNotNull(manager.importShareData(source), name)
                assertEquals("share", data.exportType, name)
                assertEquals(1, data.knowledgeBases.size, name)
                assertEquals(1, data.decks.size, name)
                assertEquals(5, data.cards.size, name)
                assertEquals(setOf("BASIC", "CLOZE", "MULTIPLE_CHOICE", "MARKDOWN", "REVERSED"), data.cards.map { it.type }.toSet(), name)
                assertTrue(data.cards.all { card -> data.decks.any { it.id == card.deckId } }, name)
                assertTrue(data.reviewLogs.isEmpty() && data.learningPlans.isEmpty(), name)
                val choice = data.cards.single { it.type == "MULTIPLE_CHOICE" }
                val options = choice.back.split("\n").filter { it.isNotBlank() }
                assertTrue(options.size >= 2 && options.any { it.startsWith("+") }, name)
            } else {
                assertNull(imported, name)
            }
        }
    }
}
