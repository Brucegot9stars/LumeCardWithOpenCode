package com.lumecard.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * Scaling benchmark for the planner: the same workload at four library sizes.
 *
 * Property-gated because timing assertions on a shared CI machine are a flake source; run it
 * explicitly with `-PrunScaleBenchmark=true` (wired in `shared/build.gradle.kts`). The default
 * `desktopTest` run no-ops, but `ObjectImportPlannerScaleTest.plannerGrowthRatioStaysLinear` is
 * always on: it compares 2 500 against 20 000 decks by *ratio*, because an absolute ceiling cannot
 * catch the old O(n²) sweep (6–16 s at the cap) without letting CI jitter through.
 *
 * What it proves: doubling the library must roughly double the time (linear), not quadruple it
 * (quadratic). The old implementation needed ~4× per doubling because each collision lookup scanned
 * every live deck.
 */
class ObjectImportPlannerBenchmark {

    private val t = TransferScaleFixtures.t

    private fun runOnce(deckCount: Int): Long {
        val kbId = "kb-bench"
        val decks = TransferScaleFixtures.balancedTree(kbId, deckCount)
        val cards = TransferScaleFixtures.cardsIn(decks.take(50), perDeck = 4)
        val export = TransferScaleFixtures.exportOf(
            ObjectType.KNOWLEDGE_BASE, listOf(kbId),
            listOf(TransferScaleFixtures.kb(kbId, "Bench")), decks, cards,
        )
        val localDecks = decks.map {
            it.copy(
                id = "local-${it.id}",
                knowledgeBaseId = "local-kb",
                parentId = it.parentId?.let { p -> "local-$p" },
            )
        }
        val context = ObjectImportContext(
            knowledgeBases = listOf(TransferScaleFixtures.kb("local-kb", "Bench")),
            decks = localDecks,
            cards = cards.map { it.copy(id = "local-${it.id}", deckId = "local-${it.deckId}") },
        )

        // Warm the JIT once so the first (smallest) sample is not the slowest.
        ObjectImportPlanner.buildPlan(
            export, ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.OVERWRITE), context, t,
        )

        val (result, elapsed) = measureTimedValue {
            ObjectImportPlanner.buildPlan(
                export, ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.OVERWRITE), context, t,
            )
        }
        val plan = (result as ObjectImportPlanResult.Success).plan
        assertEquals(deckCount, plan.decks.size, "each size must import its whole payload")
        return elapsed.inWholeMilliseconds
    }

    @Test
    fun plannerScalesLinearlyNotQuadratically() {
        if (System.getProperty("runScaleBenchmark") != "true") {
            println("[benchmark] skipped: pass -PrunScaleBenchmark=true to run")
            return
        }

        val small = runOnce(2_500)
        val medium = runOnce(5_000)
        val large = runOnce(10_000)
        val cap = runOnce(ObjectExportBuilder.MAX_DECKS)

        println(
            "[benchmark] planning a knowledge-base overwrite: " +
                "2.5k=${small}ms 5k=${medium}ms 10k=${large}ms 20k=${cap}ms"
        )

        // Linear means the ratio tracks the size ratio (2×), quadratic would track its square (4×).
        // A 35% cushion absorbs GC pauses without hiding a genuine complexity regression.
        val growth = cap.toDouble() / small.coerceAtLeast(1).toDouble()
        assertTrue(
            growth <= 8.0 * 1.35,
            "8× the payload must not cost more than ~8× the time (linear would be 8×, quadratic " +
                "64×); measured 2.5k=${small}ms 20k=${cap}ms (${"%.1f".format(growth)}×)",
        )
    }
}