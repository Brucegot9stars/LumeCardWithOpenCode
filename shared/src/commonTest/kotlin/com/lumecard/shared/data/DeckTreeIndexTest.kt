package com.lumecard.shared.data

import com.lumecard.shared.model.Deck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the silent failure modes an index introduces where a table scan could not fail that way.
 *
 * A wrong bucket does not throw — it overwrites (or spares) the wrong deck. A wrong path comparison
 * does not throw either — it silently empties a learning plan. Both were correct *by construction*
 * with a `filter`/walk, so indexing them needs explicit coverage.
 */
class DeckTreeIndexTest {

    private val t = TransferScaleFixtures.t

    /**
     * Knowledge base id, parent id and name are joined into one lookup key. Without length
     * prefixes, `("kb", "parent", "Same")` and `("kb\u0000parent", null, "Same")` would hash to the
     * same key and an import could delete a deck at the wrong level.
     */
    @Test
    fun namesContainingTheKeySeparatorDoNotShareABucket() {
        val tricky = Deck(
            id = "d-tricky", knowledgeBaseId = "kb\u0000parent", name = "Same",
            parentId = null, createdAt = t, updatedAt = t,
        )
        val plain = Deck(
            id = "d-plain", knowledgeBaseId = "kb", name = "Same",
            parentId = "parent", createdAt = t, updatedAt = t,
        )
        val context = ObjectImportContext(
            knowledgeBases = listOf(
                TransferScaleFixtures.kb("kb", "KB"),
                TransferScaleFixtures.kb("kb\u0000parent", "Tricky"),
            ),
            decks = listOf(tricky, plain),
            cards = emptyList(),
        )

        val source = TransferScaleFixtures.deck("src", "kb-src", "Same")
        val export = TransferScaleFixtures.exportOf(
            ObjectType.DECK, listOf(source.id),
            listOf(TransferScaleFixtures.kb("kb-src", "Src")), listOf(source), emptyList(),
        )

        val plan = (ObjectImportPlanner.buildPlan(
            export,
            ObjectImportRequest(
                targetKnowledgeBaseId = "kb",
                conflictPolicy = ObjectConflictPolicy.OVERWRITE,
            ),
            context,
            t,
        ) as ObjectImportPlanResult.Success).plan

        // Neither local deck is at the top level of knowledge base "kb":
        // `tricky` is top-level but belongs to a different knowledge base, `plain` is nested.
        assertTrue(
            plan.deleteDeckIds.isEmpty(),
            "no deck is at this level, so nothing may be deleted, got ${plan.deleteDeckIds}",
        )
        assertNull(plan.decks.single().parentId)
    }

    /**
     * The inline walk this replaced ended the chain at an unknown parent id, producing a
     * *truncated* path. Returning null instead would drop a rename match and empty a plan — the
     * exact class of bug the id maps exist to prevent.
     */
    @Test
    fun pathTruncatesAtAnUnknownParentInsteadOfFailing() {
        val orphaned = Deck(
            id = "d-orphan", knowledgeBaseId = "kb", name = "Child",
            parentId = "gone", createdAt = t, updatedAt = t,
        )
        val tree = DeckTree.of(listOf(orphaned))
        val names = mapOf("d-orphan" to "Child", "gone" to "Missing")

        assertEquals("Child", tree.pathOf("d-orphan") { names[it] })
    }

    /** A cycle is capped, not walked forever, and still yields a usable path. */
    @Test
    fun pathIsCappedOnACorruptCycle() {
        val a = Deck(id = "a", knowledgeBaseId = "kb", name = "A", parentId = "b", createdAt = t, updatedAt = t)
        val b = Deck(id = "b", knowledgeBaseId = "kb", name = "B", parentId = "a", createdAt = t, updatedAt = t)
        val tree = DeckTree.of(listOf(a, b))
        val names = mapOf("a" to "A", "b" to "B")

        val path = requireNotNull(tree.pathOf("a") { names[it] })
        // `size <= MAX_PATH_DEPTH + 1` would also pass for a 1-component path, i.e. for an
        // implementation that bailed out after the first hop. Pin the real property instead: the
        // walk keeps stepping through the cycle until the cap stops it, so it must come out
        // *exactly* at the cap — the truncation marker, the own name, and MAX_PATH_DEPTH ancestors.
        val parts = path.split("\u0000")
        assertTrue(
            parts.first().startsWith(DeckTree.TRUNCATED_MARKER),
            "a capped walk must say it was capped, got ${parts.first()}",
        )
        assertEquals(
            DeckTree.MAX_PATH_DEPTH + 2, parts.size,
            "one marker + own name + MAX_PATH_DEPTH ancestors",
        )
    }

    /**
     * Two chains that only diverge above the cap must not produce the same path.
     *
     * This is the failure the truncation marker exists for. Both decks sit at depth 70 under
     * different roots; unmarked, both paths would be "the leaf plus its 64 nearest ancestors" — the
     * same 65 names — and `remapLocalDeckSelections` would map both local decks onto one copy.
     */
    @Test
    fun deeplyNestedChainsDoNotCollapseOntoOneAnother() {
        fun deepChain(prefix: String, depth: Int): List<Deck> = (0 until depth).map { level ->
            Deck(
                id = "$prefix-$level", knowledgeBaseId = "kb",
                // Names repeat across the two chains on purpose: only the roots differ.
                name = if (level == 0) "Root $prefix" else "L$level",
                parentId = if (level == 0) null else "$prefix-${level - 1}",
                createdAt = t, updatedAt = t,
            )
        }
        val decks = deepChain("a", 70) + deepChain("b", 70)
        val tree = DeckTree.of(decks)
        val names = decks.associate { it.id to it.name }

        val pathA = requireNotNull(tree.pathOf("a-69") { names[it] })
        val pathB = requireNotNull(tree.pathOf("b-69") { names[it] })

        assertTrue(DeckTree.TRUNCATED_MARKER in pathA, "a 70-deep chain is over the cap")
        assertNotEquals(pathA, pathB, "truncated chains from different roots must stay distinct")
    }

    /**
     * `resolveParent` passes the file's raw ancestor name, so a blank-named container is reachable
     * from a crafted file. The trimmed-string `filter` this replaced matched `""` against a stored
     * blank-named deck; a "blank ids have no bucket" shortcut would silently re-root the copy.
     */
    @Test
    fun blankNamedAncestorStillMatchesAStoredBlankNamedDeck() {
        val kbId = "kb-blank"
        val storedBlank = Deck(
            id = "local-blank", knowledgeBaseId = kbId, name = "",
            parentId = null, createdAt = t, updatedAt = t,
        )
        val context = ObjectImportContext(
            knowledgeBases = listOf(TransferScaleFixtures.kb(kbId, "KB")),
            decks = listOf(storedBlank),
            cards = emptyList(),
        )

        // Container `src-parent` is named " " (whitespace only) and owns the imported deck.
        val container = TransferScaleFixtures.deck("src-parent", "kb-src", " ")
        val child = TransferScaleFixtures.deck("src-child", "kb-src", "Child", parentId = container.id)
        val export = TransferScaleFixtures.exportOf(
            ObjectType.DECK, listOf(child.id),
            listOf(TransferScaleFixtures.kb("kb-src", "Src")),
            listOf(container, child),
            emptyList(),
        )

        val plan = (ObjectImportPlanner.buildPlan(
            export,
            ObjectImportRequest(targetKnowledgeBaseId = kbId),
            context,
            t,
        ) as ObjectImportPlanResult.Success).plan

        assertEquals("local-blank", plan.decks.single().parentId, "the blank-named ancestor must match")
    }

    /**
     * A crafted file can carry any string as a parent id. The "no parent" case must therefore be a
     * distinct tag, not a sentinel id: with a sentinel, a deck whose parent id *is* that sentinel
     * shares a bucket with top-level decks, and an OVERWRITE deletes the wrong one.
     */
    @Test
    fun forgedSentinelParentIdDoesNotAliasTheTopLevel() {
        val kbId = "kb-alias"
        // A real, nested child whose parent id is the sentinel-looking string.
        val sentinelId = "\u0001root"
        val nestedParent = Deck(
            id = sentinelId, knowledgeBaseId = kbId, name = "Parent",
            parentId = null, createdAt = t, updatedAt = t,
        )
        val nestedChild = Deck(
            id = "child", knowledgeBaseId = kbId, name = "Same",
            parentId = sentinelId, createdAt = t, updatedAt = t,
        )
        val topLevel = Deck(
            id = "top", knowledgeBaseId = kbId, name = "Same",
            parentId = null, createdAt = t, updatedAt = t,
        )
        val context = ObjectImportContext(
            knowledgeBases = listOf(TransferScaleFixtures.kb(kbId, "KB")),
            decks = listOf(nestedParent, nestedChild, topLevel),
            cards = emptyList(),
        )

        // Import a top-level "Same"; only `top` is at that level.
        val source = TransferScaleFixtures.deck("src", "kb-src", "Same")
        val export = TransferScaleFixtures.exportOf(
            ObjectType.DECK, listOf(source.id),
            listOf(TransferScaleFixtures.kb("kb-src", "Src")), listOf(source), emptyList(),
        )

        val plan = (ObjectImportPlanner.buildPlan(
            export,
            ObjectImportRequest(
                targetKnowledgeBaseId = kbId,
                conflictPolicy = ObjectConflictPolicy.OVERWRITE,
            ),
            context,
            t,
        ) as ObjectImportPlanResult.Success).plan

        assertEquals(
            listOf("top"), plan.deleteDeckIds,
            "the nested deck under the sentinel id must not be treated as top level",
        )
    }

    /**
     * A knowledge-base name longer than `MAX_NAME_LENGTH` is legal in a file (`MAX_NAME_FIELD` is
     * 200). The bucket key and the query key must clip identically, or an OVERWRITE of that name
     * silently creates a second knowledge base instead of replacing the first.
     */
    @Test
    fun overlongKnowledgeBaseNameStillOverwrites() {
        val longName = "K".repeat(150)
        val context = ObjectImportContext(
            knowledgeBases = listOf(TransferScaleFixtures.kb("local-kb", longName)),
            decks = emptyList(),
            cards = emptyList(),
        )
        val sourceKb = TransferScaleFixtures.kb("src-kb", longName)
        val export = TransferScaleFixtures.exportOf(
            ObjectType.KNOWLEDGE_BASE, listOf("src-kb"), listOf(sourceKb), emptyList(), emptyList(),
        )

        val plan = (ObjectImportPlanner.buildPlan(
            export,
            ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.OVERWRITE),
            context,
            t,
        ) as ObjectImportPlanResult.Success).plan

        assertEquals(
            listOf("local-kb"), plan.deleteKnowledgeBaseIds,
            "the clipped name must still find the existing knowledge base",
        )
    }

    /**
     * A parent id that is literally the level tag (`"R"` / `"C"`) must still be a *child* level.
     *
     * The bucket key encodes "no parent" as a tag byte rather than as a sentinel id, precisely so no
     * id string can impersonate it. This is the companion case to
     * [forgedSentinelParentIdDoesNotAliasTheTopLevel]: the tag is now a single character, and ids
     * come from a decoded file, so `"R"` is a perfectly legal id.
     */
    @Test
    fun idEqualToTheLevelTagIsStillAChildLevel() {
        val kbId = "kb-tag"
        val tagIdParent = Deck(
            id = "R", knowledgeBaseId = kbId, name = "Parent",
            parentId = null, createdAt = t, updatedAt = t,
        )
        val childUnderTag = Deck(
            id = "kid", knowledgeBaseId = kbId, name = "Same",
            parentId = "R", createdAt = t, updatedAt = t,
        )
        val topLevel = Deck(
            id = "top", knowledgeBaseId = kbId, name = "Same",
            parentId = null, createdAt = t, updatedAt = t,
        )
        val context = ObjectImportContext(
            knowledgeBases = listOf(TransferScaleFixtures.kb(kbId, "KB")),
            decks = listOf(tagIdParent, childUnderTag, topLevel),
            cards = emptyList(),
        )

        val source = TransferScaleFixtures.deck("src", "kb-src", "Same")
        val export = TransferScaleFixtures.exportOf(
            ObjectType.DECK, listOf(source.id),
            listOf(TransferScaleFixtures.kb("kb-src", "Src")), listOf(source), emptyList(),
        )

        val plan = (ObjectImportPlanner.buildPlan(
            export,
            ObjectImportRequest(
                targetKnowledgeBaseId = kbId,
                conflictPolicy = ObjectConflictPolicy.OVERWRITE,
            ),
            context,
            t,
        ) as ObjectImportPlanResult.Success).plan

        assertEquals(
            listOf("top"), plan.deleteDeckIds,
            "a deck whose parent id is \"R\" is nested, not top level",
        )
    }

    /**
     * A dirty cross-knowledge-base parent chain must not truncate an overwrite match.
     *
     * `pathOf` walks to the root through *any* indexed deck, including one that belongs to another
     * knowledge base (a stale row left by a failed sync). The name map therefore has to cover the
     * whole library, not just the decks being replaced — a "candidates only" map would end the chain
     * early, produce a shorter path, miss the copy, and silently empty the learning plan.
     */
    @Test
    fun pathWalksThroughADirtyForeignParent() {
        val decks = listOf(
            Deck(id = "foreign-root", knowledgeBaseId = "kb-other", name = "Root",
                parentId = null, createdAt = t, updatedAt = t),
            Deck(id = "mine", knowledgeBaseId = "kb", name = "Child",
                parentId = "foreign-root", createdAt = t, updatedAt = t),
        )
        val tree = DeckTree.of(decks)
        val names = decks.associate { it.id to it.name }

        assertEquals(
            "Root\u0000Child", tree.pathOf("mine") { names[it] },
            "the foreign parent must contribute to the path, not truncate it",
        )
    }

    @Test
    fun descendantsAndAncestorsWalkTheWholeChain() {        val decks = TransferScaleFixtures.chain("kb", depth = 5)
        val tree = DeckTree.of(decks)

        val down = linkedSetOf(decks.first().id)
        tree.expandDescendants(down)
        assertEquals(decks.size, down.size)

        val up = linkedSetOf(decks.last().id)
        tree.expandAncestors(up)
        assertEquals(decks.size, up.size)

        // A dangling parent id is never invented.
        val dangling = linkedSetOf("not-a-deck")
        tree.expandAncestors(dangling)
        assertEquals(setOf("not-a-deck"), dangling)
    }

    /** The exporter walks its own `ExportDeck` rows; keep that adapter honest at a real size. */
    @Test
    fun exportAdapterFindsEveryDescendantAtTheCap() {
        val kbId = "kb-export"
        val decks = TransferScaleFixtures.balancedTree(kbId, ObjectExportBuilder.MAX_DECKS)
        val tree = DeckTree.ofExport(
            decks.map {
                ExportDeck(
                    id = it.id, knowledgeBaseId = it.knowledgeBaseId, name = it.name,
                    description = null, color = "#4CAF50", icon = "D",
                    parentId = it.parentId, createdAt = TransferScaleFixtures.DATE,
                    updatedAt = TransferScaleFixtures.DATE,
                )
            }
        )

        assertEquals(ObjectExportBuilder.MAX_DECKS, tree.size)
        val seeds = linkedSetOf(decks.first().id)
        tree.expandDescendants(seeds)
        assertEquals(ObjectExportBuilder.MAX_DECKS, seeds.size, "every deck hangs off the root")
    }
}