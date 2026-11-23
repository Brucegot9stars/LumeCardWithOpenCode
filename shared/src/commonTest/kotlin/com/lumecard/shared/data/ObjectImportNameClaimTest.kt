package com.lumecard.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Guards the suffix-probing fast path in [ObjectImportPlanner]'s name claim.
 *
 * `uniqueName` walks ` (1)`, ` (2)`, … until a slot is free. A bulk import of identical names used
 * to restart that walk for every copy, so 5 000 decks all named "Maths" cost ~12.5M probes. The
 * claim now remembers where the previous copy landed and resumes there. A hint must never change
 * *which* name a copy gets — only how fast the walk finds it — because the assigned names are what
 * the user sees and what `executeAsOneOrNull` lookups depend on being unique.
 */
class ObjectImportNameClaimTest {

    private val t = TransferScaleFixtures.t

    private fun importOfDuplicates(count: Int, name: String): ObjectExport {
        val kbId = "kb-src"
        val decks = (0 until count).map {
            TransferScaleFixtures.deck("src-$it", kbId, name)
        }
        // DECK scope on purpose: a KNOWLEDGE_BASE import lands its copies under a *newly generated*
        // knowledge-base id, so their level keys could never match a live row and the deck-level
        // name collision this class is about would never be exercised.
        return TransferScaleFixtures.exportOf(
            ObjectType.DECK, decks.map { it.id },
            listOf(TransferScaleFixtures.kb(kbId, "Src")), decks, emptyList(),
        )
    }

        /**
     * A DECK import ignores `newKnowledgeBaseName` and needs an explicit destination, so the
     * default request creates a fresh knowledge base and the collision cases override it with
     * `targetKnowledgeBaseId` pointing at their fixture library.
     */
    private fun planDuplicates(
        count: Int,
        name: String,
        context: ObjectImportContext,
        request: ObjectImportRequest = ObjectImportRequest(newKnowledgeBaseName = "Target"),
    ): ObjectImportPlan =
        (ObjectImportPlanner.buildPlan(
            importOfDuplicates(count, name),
            request,
            context,
            t,
        ) as ObjectImportPlanResult.Success).plan

    /**
     * The exact sequence: first copy keeps the bare name, then ` (1)`, ` (2)`, … in payload order.
     * Resuming the probe from a hint that was one too high would silently skip a suffix, and two
     * rows would then be identical in the UI.
     */
    @Test
    fun bulkDuplicatesStillReceiveConsecutiveSuffixes() {
        val plan = planDuplicates(60, "Maths", ObjectImportContext())

        val names = plan.decks.map { it.name }
        assertEquals("Maths", names.first())
        assertEquals(
            (1 until 60).map { "Maths ($it)" }, names.drop(1),
            "suffixes must be consecutive and start at 1",
        )
        // The rename log must match the copies one-for-one — it drives the "renamed N items"
        // summary, and `original` is the base the user gave.
        assertEquals(59, plan.renamed.size, "the first copy is not a rename")
        assertTrue(plan.renamed.all { it.original == "Maths" }, "every rename quotes the base name")
        assertEquals(names.drop(1), plan.renamed.map { it.newName })
    }

    /**
     * A live library that already holds some of the suffixes must not confuse the hint: the claim
     * has to skip past them and land on the first genuinely free slot.
     */
    @Test
    fun hintSkipsOverNamesThatAreAlreadyTaken() {
        val kbId = "local-kb"
        // "Maths", "Maths (1)", "Maths (2)" already exist locally.
        val existing = (0..2).map {
            TransferScaleFixtures.deck(
                "local-$it", kbId, if (it == 0) "Maths" else "Maths ($it)",
            )
        }
        val context = ObjectImportContext(
            knowledgeBases = listOf(TransferScaleFixtures.kb(kbId, "Target")),
            decks = existing,
            cards = emptyList(),
        )

        val plan = planDuplicates(
            4, "Maths", context,
            // Import into the existing knowledge base, so the deck names actually collide.
            ObjectImportRequest(targetKnowledgeBaseId = kbId),
        )

        assertEquals(
            listOf("Maths (3)", "Maths (4)", "Maths (5)", "Maths (6)"),
            plan.decks.map { it.name },
            "the walk must continue past every taken suffix, in order",
        )
    }

    /**
     * A base name that merely *ends* in something like `(2024)` is not a generated suffix; the
     * probe hint must not read it as one and jump to `(2025)`.
     */
    @Test
    fun baseNameEndingInParenthesesIsNotMistakenForASuffix() {
        val plan = planDuplicates(3, "Budget (2024)", ObjectImportContext())

        assertEquals(
            listOf("Budget (2024)", "Budget (2024) (1)", "Budget (2024) (2)"),
            plan.decks.map { it.name },
        )
    }

    /**
     * The reason the hint exists: the same fixture that used to probe ~N²/2 times must stay
     * linear. 5 000 identical names is ~12.5M probes without the hint, which is several seconds of
     * pure string building; with it, each claim is one or two probes.
     */
    @Test
    fun bulkDuplicatesAtScaleStayLinear() {
        val count = 5_000
        val elapsed = measureTime {
            val plan = planDuplicates(count, "Maths", ObjectImportContext())
            assertEquals(count, plan.decks.size)
            // Spot-check the tail instead of mapping every name: the point is the runtime, and the
            // exact sequence is covered above.
            assertEquals("Maths (${count - 1})", plan.decks.last().name)
            assertEquals(count, plan.decks.map { it.name }.toSet().size, "names must stay unique")
        }

        assertTrue(
            elapsed.inWholeSeconds < 10,
            "claiming $count identical names took $elapsed; the probe hint is not being used",
        )
    }
}
