package com.lumecard.shared.data

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Deck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Scale guards for the import planner.
 *
 * The planner used to answer every "does this name already exist at this level?" question by
 * filtering the whole live deck list, and every descendant/ancestor closure by re-sweeping the list
 * until a round added nothing. Both are quadratic. At the documented caps
 * (`MAX_DECKS = 20_000`, `MAX_CARDS = 100_000`) that is ~4×10⁸ trimmed-string comparisons for a
 * single plan — minutes of CPU, on the UI thread's caller, before one row is written.
 *
 * Correctness is asserted alongside the timing, because an index that returns the wrong bucket is
 * fast and useless. Data lives in `TransferScaleFixtures`.
 *
 * **What the wall-clock ceilings below can and cannot do.** They are hang detectors, not complexity
 * guards: the quadratic planner needs ~4×10⁸ trimmed comparisons at the 20 000-deck cap, which
 * measures roughly 6–16 s on a laptop — under any ceiling loose enough to survive a shared CI box.
 * The discriminating guard is [plannerGrowthRatioStaysLinear], which compares two payload sizes
 * instead of an absolute number (8× payload ≈ 8× time when indexed, ≈ 64× when swept).
 */
class ObjectImportPlannerScaleTest {

    private val t = TransferScaleFixtures.t

    private fun plan(
        export: ObjectExport,
        request: ObjectImportRequest,
        context: ObjectImportContext,
    ): ObjectImportPlan =
        (ObjectImportPlanner.buildPlan(export, request, context, t) as ObjectImportPlanResult.Success).plan

    private fun planFailure(
        export: ObjectExport,
        request: ObjectImportRequest,
        context: ObjectImportContext,
    ): ObjectTransferError =
        (ObjectImportPlanner.buildPlan(export, request, context, t) as ObjectImportPlanResult.Failure).error

    /**
     * The worst shape for the old planner: the payload is the entire library, so every payload deck
     * has a live counterpart with the same level and name. 20 000 × 20 000 comparisons.
     */
    @Test
    fun knowledgeBaseOverwriteAtTheDeckCapStaysLinear() {
        val kbId = "kb-big"
        val decks = TransferScaleFixtures.balancedTree(kbId, ObjectExportBuilder.MAX_DECKS)
        val cards = TransferScaleFixtures.cardsIn(decks.take(200), perDeck = 10)
        val kbs = listOf(TransferScaleFixtures.kb(kbId, "Big"))
        val export = TransferScaleFixtures.exportOf(ObjectType.KNOWLEDGE_BASE, listOf(kbId), kbs, decks, cards)

        // Local library = the same content under the same names, levels and knowledge base name.
        // Ids (and the knowledge base id) differ, which is exactly what the remap has to bridge.
        val localDecks = decks.map {
            it.copy(
                id = "local-${it.id}",
                knowledgeBaseId = "local-kb",
                parentId = it.parentId?.let { p -> "local-$p" },
            )
        }
        val localCards = cards.map { it.copy(id = "local-${it.id}", deckId = "local-${it.deckId}") }
        val context = ObjectImportContext(
            knowledgeBases = listOf(TransferScaleFixtures.kb("local-kb", "Big")),
            decks = localDecks,
            cards = localCards,
        )

        val elapsed = measureTime {
            val plan = plan(
                export,
                ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.OVERWRITE),
                context,
            )

            // Every deck collides at its level, so all of them become deletion seeds for the one
            // knowledge base that is replaced — not 20 000 separate seeds.
            assertEquals(1, plan.deleteKnowledgeBaseIds.size)
            assertEquals(listOf("local-kb"), plan.deleteKnowledgeBaseIds)

            // Knowledge-base-scope overwrite replaces the *container*, so the planner must not
            // also seed deck deletions: the decks disappear with the knowledge base, and counting
            // them twice would double the confirmation numbers and the soft-delete pass. This is
            // the real observable for this branch — note that `decksAtLevel` below the root can
            // never hit here (the copy's knowledge-base id and parent id are freshly generated),
            // so a deck-level seed here would be a bug, not a collision.
            assertTrue(
                plan.deleteDeckIds.isEmpty(),
                "a knowledge-base overwrite seeds the container only, got ${plan.deleteDeckIds.take(3)}",
            )
            assertEquals(decks.size, plan.decks.size, "the whole payload must be copied")
            // Overwrite frees the name, so no copy is suffixed away from its original.
            assertTrue(plan.renamed.isEmpty(), "an overwrite must not suffix names: ${plan.renamed.take(3)}")
            // Deck selection remap: the plan's own (file) ids map onto the copies, and the live
            // rows that are about to be deleted map onto them too (that is what keeps a learning
            // plan alive). Both halves are exercised at full width here.
            assertEquals(
                decks.size + localDecks.size, plan.deckIdMap.size,
                "deckIdMap=${plan.deckIdMap.size} decks=${decks.size} local=${localDecks.size} " +
                    "renamed=${plan.renamed.size}",
            )
            // A size check alone would also pass if two local decks collapsed onto one copy. The
            // path-based remap is the only thing that can keep them apart, so assert the shape:
            // every local deck maps to a distinct copy, and to the copy at its own depth.
            // Indexed once — a `firstOrNull` per local deck would make the assertion itself
            // quadratic (20 000 × 20 000) and dominate the very timing it is meant to guard.
            val copyNameById = HashMap<String, String>(plan.decks.size * 2)
            plan.decks.forEach { copyNameById[it.id] = it.name }
            val copiesByLocalId = localDecks.map { localDeck ->
                val mapped = assertNotNull(
                    plan.deckIdMap[localDeck.id],
                    "local deck ${localDeck.id} lost its selection: a learning plan would be emptied",
                )
                val copyName = assertNotNull(
                    copyNameById[mapped],
                    "remapped id $mapped is not one of the planned copies",
                )
                assertEquals(
                    localDeck.name, copyName,
                    "local ${localDeck.id} was remapped to a copy with a different tree position",
                )
                mapped
            }
            assertEquals(localDecks.size, copiesByLocalId.toSet().size, "remap must be one-for-one")
        }

        assertTrue(
            elapsed.inWholeSeconds < 30,
            "planning a ${decks.size}-deck knowledge-base overwrite took $elapsed; the planner is " +
                "quadratic again",
        )
    }

    /**
     * Deck import that must re-attach a deep ancestor chain into a large library.
     *
     * `resolveParent` used to scan the whole live deck list per ancestor per imported deck, which is
     * the second quadratic path: deep payload × large library.
     */
    @Test
    fun deckImportReattachesADeepChainIntoALargeLibrary() {
        val targetKb = "kb-target"
        // 20 000 unrelated live decks, all top-level and named so they cannot match the payload.
        val liveDecks = ArrayList<Deck>(ObjectExportBuilder.MAX_DECKS)
        liveDecks += TransferScaleFixtures.deck("live-0", targetKb, "Filler 0")
        for (index in 1 until ObjectExportBuilder.MAX_DECKS) {
            liveDecks += TransferScaleFixtures.deck(
                "live-$index", targetKb, "Filler $index", parentId = "live-${index - 1}",
            )
        }
        val context = ObjectImportContext(
            knowledgeBases = listOf(TransferScaleFixtures.kb(targetKb, "Target")),
            decks = liveDecks,
            cards = emptyList(),
        )

        // Payload: a 6-level chain whose ancestors also exist locally, so each level is re-attached.
        val sourceKb = "kb-source"
        val payloadDecks = TransferScaleFixtures.chain(sourceKb, depth = 6, idPrefix = "src")
        val payloadCards = listOf(TransferScaleFixtures.card("src-card", payloadDecks.last().id, "Q", "A"))
        val export = (ObjectExportBuilder.build(
            ObjectType.DECK, listOf(payloadDecks.last().id),
            listOf(TransferScaleFixtures.kb(sourceKb, "Source")),
            payloadDecks.map { it.copy(name = "Level ${payloadDecks.indexOf(it)}") },
            payloadCards,
            TransferScaleFixtures.DATE,
        ) as ObjectBuildResult.Success).data.export

        // Give the target knowledge base the same ancestor chain, so re-attachment has work to do.
        val withAncestors = liveDecks.map { it.copy(id = "anc-${it.id}") } +
            payloadDecks.map { it.copy(id = "local-${it.id}", knowledgeBaseId = targetKb) }
        val deepContext = context.copy(decks = liveDecks + withAncestors)

        val elapsed = measureTime {
            val plan = plan(
                export,
                ObjectImportRequest(
                    targetKnowledgeBaseId = targetKb,
                    conflictPolicy = ObjectConflictPolicy.KEEP_BOTH,
                ),
                deepContext,
            )
            // The payload is a sub-path, so exactly the payload decks are copied.
            assertEquals(1, plan.decks.size, "only the selected sub-tree is imported")
            // Its parent resolves to the existing local ancestor instead of becoming a root.
            assertTrue(
                plan.decks.single().parentId != null,
                "a deck whose ancestors exist locally must be re-attached, got parentId=null",
            )
            assertTrue(plan.decks.single().parentId!!.startsWith("local-"))
        }

        assertTrue(
            elapsed.inWholeSeconds < 30,
            "re-attaching a deep payload into ${deepContext.decks.size} live decks took $elapsed",
        )
    }

    /**
     * Card import against a deck that already holds a lot of cards.
     *
     * Duplicate detection used to call `context.cards.filter { it.deckId == targetDeckId }` once
     * (cheap) but the identity grouping ran on every card; keeping the index lazy means a CARD plan
     * never sorts the decks it does not need. 5 000 local cards is well under the 100k cap but
     * enough that a per-card rescan shows up.
     */
    @Test
    fun cardImportWithManyLocalDuplicatesStaysLinear() {
        val kbId = "kb-cards"
        val deck = TransferScaleFixtures.deck("deck-target", kbId, "Target")
        val localCards = ArrayList<Card>(5_000)
        repeat(5_000) { index ->
            localCards += TransferScaleFixtures.card("local-$index", deck.id, "Q$index", "A$index")
        }
        val context = ObjectImportContext(
            knowledgeBases = listOf(TransferScaleFixtures.kb(kbId, "Cards")),
            decks = listOf(deck),
            cards = localCards,
        )

        // Import 500 of the same cards; each is a duplicate, so each must replace exactly one row.
        val payloadCards = (0 until 500).map {
            TransferScaleFixtures.card("src-$it", "src-deck", "Q$it", "A$it")
        }
        val export = (ObjectExportBuilder.build(
            ObjectType.CARD, payloadCards.map { it.id },
            listOf(TransferScaleFixtures.kb("kb-src", "Src")),
            listOf(TransferScaleFixtures.deck("src-deck", "kb-src", "Src Deck")),
            payloadCards,
            TransferScaleFixtures.DATE,
        ) as ObjectBuildResult.Success).data.export

        val elapsed = measureTime {
            val plan = plan(
                export,
                ObjectImportRequest(
                    targetDeckId = deck.id,
                    duplicatePolicy = ObjectDuplicatePolicy.OVERWRITE,
                ),
                context,
            )
            assertEquals(500, plan.cards.size)
            assertEquals(500, plan.deleteCardIds.size)
            assertEquals(500, plan.cardIdMap.size)
            assertEquals(0, plan.skippedCards)
            // One-for-one: each new id must be distinct, no double replacement of one local card.
            assertEquals(500, plan.cardIdMap.values.toSet().size)
        }

        assertTrue(
            elapsed.inWholeSeconds < 30,
            "planning 500 duplicate replacements in ${localCards.size} local cards took $elapsed",
        )
    }

    /** Acceptance order must not change: the copies keep the file's parents-first order. */
    @Test
    fun largePlanKeepsParentsFirstOrdering() {
        val kbId = "kb-order"
        val decks = TransferScaleFixtures.balancedTree(kbId, 2_000)
        val kbs = listOf(TransferScaleFixtures.kb(kbId, "Ordered"))
        val export = TransferScaleFixtures.exportOf(ObjectType.KNOWLEDGE_BASE, listOf(kbId), kbs, decks, emptyList())

        val plan = plan(export, ObjectImportRequest(newKnowledgeBaseName = "Copy"), ObjectImportContext())

        assertEquals(decks.size, plan.decks.size)
        val seen = HashSet<String>(plan.decks.size)
        plan.decks.forEach { fresh ->
            fresh.parentId?.let {
                assertTrue(it in seen, "deck ${fresh.id} was emitted before its parent $it")
            }
            seen += fresh.id
        }
    }

    /** A corrupt file at scale is still rejected before any planning work happens. */
    @Test
    fun corruptFileAtScaleIsRejectedBeforePlanning() {
        val kbId = "kb-broken"
        val decks = TransferScaleFixtures.balancedTree(kbId, 2_000)
        val export = TransferScaleFixtures.exportOf(
            ObjectType.KNOWLEDGE_BASE, listOf(kbId),
            listOf(TransferScaleFixtures.kb(kbId, "Broken")), decks, emptyList(),
        ).copy(decks = emptyList())

        // Validation runs before the planner touches a single deck, so emptying the array turns
        // the whole payload invalid rather than producing a half-built plan.
        assertEquals(
            ObjectTransferError.INVALID_ID,
            planFailure(export, ObjectImportRequest(), ObjectImportContext()),
        )
    }

    /**
     * The deck-level bucket, at the cap.
     *
     * [knowledgeBaseOverwriteAtTheDeckCapStaysLinear] exercises the knowledge-base branch, where the
     * copy's knowledge-base id and parent id are freshly generated and therefore can never match a
     * live row — the deck bucket is built but never queried there. This test targets the branch that
     * *does* query it: a DECK import into a 20 000-deck library, where each payload deck resolves a
     * live parent and then looks its own level up. That lookup is the second quadratic path in the
     * old code (a full `filter` per imported deck against every live deck).
     */
    @Test
    fun deckLevelLookupAtTheCapStaysLinear() {
        val kbId = "kb-wide"
        val siblingCount = 2_000
        // The live library: the cap-sized tree, plus an anchor path ("Level 0/Level 1") that the
        // payload will re-attach to, plus one live deck per payload name so every level lookup
        // collides instead of missing.
        val liveDecks = ArrayList<Deck>(ObjectExportBuilder.MAX_DECKS + siblingCount + 2)
        liveDecks += TransferScaleFixtures.balancedTree(kbId, ObjectExportBuilder.MAX_DECKS)
        val anchors = TransferScaleFixtures.chain(kbId, depth = 2, idPrefix = "anchor")
        liveDecks += anchors
        val deepestAnchor = anchors.last()
        repeat(siblingCount) { index ->
            liveDecks += TransferScaleFixtures.deck(
                "live-sibling-$index", kbId, "Sibling $index", parentId = deepestAnchor.id,
            )
        }
        val context = ObjectImportContext(
            knowledgeBases = listOf(TransferScaleFixtures.kb(kbId, "Wide")),
            decks = liveDecks,
            cards = emptyList(),
        )

        // Payload: the same anchor path (as containers) plus 2 000 new siblings named exactly like
        // the live ones, so all 2 000 lookups hit a populated bucket.
        val payloadKb = "kb-incoming"
        val payloadAnchors = TransferScaleFixtures.chain(payloadKb, depth = 2, idPrefix = "src-anchor")
        val payloadLeaves = (0 until siblingCount).map { index ->
            TransferScaleFixtures.deck(
                "incoming-$index", payloadKb, "Sibling $index", parentId = payloadAnchors.last().id,
            )
        }
        val export = TransferScaleFixtures.exportOf(
            ObjectType.DECK, payloadLeaves.map { it.id },
            listOf(TransferScaleFixtures.kb(payloadKb, "Incoming")),
            payloadAnchors + payloadLeaves,
            emptyList(),
        )

        val elapsed = measureTime {
            val plan = plan(
                export,
                ObjectImportRequest(
                    targetKnowledgeBaseId = kbId,
                    conflictPolicy = ObjectConflictPolicy.KEEP_BOTH,
                ),
                context,
            )
            assertEquals(siblingCount, plan.decks.size, "every payload deck must be copied")
            // `resolveParent` walked the container chain and matched it in the live library, so the
            // copies hang off the existing anchor rather than becoming roots.
            plan.decks.forEach { fresh ->
                assertEquals(deepestAnchor.id, fresh.parentId, "each copy must re-attach to the live anchor")
            }
            // KEEP_BOTH never deletes, and every name is taken at that level, so every copy is
            // suffixed — the observable that proves the bucket actually matched.
            assertTrue(plan.deleteDeckIds.isEmpty(), "KEEP_BOTH must not delete anything")
            assertTrue(plan.overwritten.not(), "nothing was replaced, so the plan is not destructive")
            assertEquals(siblingCount, plan.renamed.size, "every colliding name must be suffixed")
            assertTrue(
                plan.renamed.all { rename -> rename.newName == "${rename.original} (1)" },
                "expected a \" (1)\" suffix, got ${plan.renamed.take(3).map { it.newName }}",
            )
            // Only the payload ids are registered: the live→copy entries exist so an OVERWRITE can
            // keep a learning plan pointing at the replacement row, and KEEP_BOTH deletes nothing.
            assertEquals(
                siblingCount, plan.deckIdMap.size,
                "KEEP_BOTH registers the payload's own ids only",
            )
        }

        assertTrue(
            elapsed.inWholeSeconds < 30,
            "resolving $siblingCount decks against ${liveDecks.size} live decks took $elapsed",
        )
    }

    /**
     * The complexity guard that a wall-clock ceiling cannot be: two payloads, one ratio.
     *
     * An absolute ceiling is blind here — the quadratic planner needs ~4×10⁸ comparisons at the cap,
     * which is 6–16 s on this hardware, so any threshold loose enough for CI also lets it through.
     * Comparing 2 500 decks against 20 000 removes the machine from the equation: an indexed plan
     * costs ~8× (payload ratio), a swept one costs ~64× (its square). The threshold sits at 24×,
     * far from both, so it tolerates GC pauses and a cold cache yet still fails loudly on a
     * regression.
     *
     * Only this shape (payload == library, all names colliding) produces quadratic work in the old
     * code, because the sweep ran once per payload deck against every live deck.
     */
    @Test
    fun plannerGrowthRatioStaysLinear() {
        fun planWholeOverwrite(deckCount: Int): PlanSample {
            val kbId = "kb-growth"
            val decks = TransferScaleFixtures.balancedTree(kbId, deckCount)
            val export = TransferScaleFixtures.exportOf(
                ObjectType.KNOWLEDGE_BASE, listOf(kbId),
                listOf(TransferScaleFixtures.kb(kbId, "Growth")), decks, emptyList(),
            )
            val localDecks = decks.map {
                it.copy(
                    id = "local-${it.id}",
                    knowledgeBaseId = "local-kb",
                    parentId = it.parentId?.let { p -> "local-$p" },
                )
            }
            val context = ObjectImportContext(
                knowledgeBases = listOf(TransferScaleFixtures.kb("local-kb", "Growth")),
                decks = localDecks,
                cards = emptyList(),
            )
            val request = ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.OVERWRITE)
            // One untimed pass so the first sample never pays for class loading / JIT.
            plan(export, request, context)

            val elapsed = measureTime {
                val warmPlan = plan(export, request, context)
                assertEquals(deckCount, warmPlan.decks.size)
            }
            // Microseconds, not seconds: the small sample would quantise to 0 otherwise and the
            // ratio would divide by the `coerceAtLeast(1)` guard instead of by a measurement.
            return PlanSample(micros = elapsed.inWholeMicroseconds, deckCount = deckCount)
        }

        val small = planWholeOverwrite(2_500)
        val cap = planWholeOverwrite(ObjectExportBuilder.MAX_DECKS)

        val ratio = cap.micros.toDouble() / small.micros.coerceAtLeast(1).toDouble()
        assertTrue(
            ratio <= 24.0,
            "8× the payload cost ${"%.1f".format(ratio)}× the time (${small.micros}µs for " +
                "${small.deckCount} -> ${cap.micros}µs for ${cap.deckCount}); linear is ~8×, a " +
                "reintroduced nested sweep is ~64×",
        )
    }

    private data class PlanSample(val micros: Long, val deckCount: Int)
}