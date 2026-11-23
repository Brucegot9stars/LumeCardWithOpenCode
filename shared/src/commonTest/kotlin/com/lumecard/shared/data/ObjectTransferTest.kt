package com.lumecard.shared.data

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.model.Media
import com.lumecard.shared.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ObjectTransferTest {
    private val t = Instant.parse("2026-01-01T00:00:00Z")
    private val date = "2026-01-01T00:00:00Z"

    private fun kb(id: String, name: String) =
        KnowledgeBase(id = id, name = name, createdAt = t, updatedAt = t)

    private fun deck(id: String, kbId: String, name: String, parentId: String? = null) =
        Deck(id = id, knowledgeBaseId = kbId, name = name, parentId = parentId, createdAt = t, updatedAt = t)

    private fun card(
        id: String,
        deckId: String,
        front: String,
        back: String,
        type: CardType = CardType.BASIC,
    ) = Card(id = id, deckId = deckId, type = type, front = front, back = back, createdAt = t, updatedAt = t)

    private val kb1 = kb("kb1", "Alpha")
    private val kb2 = kb("kb2", "Beta")
    private val d1 = deck("d1", "kb1", "Root")
    private val d2 = deck("d2", "kb1", "Child", parentId = "d1")
    private val d3 = deck("d3", "kb2", "Other")
    private val c1 = card("c1", "d1", "Q1", "A1")
    private val c2 = card("c2", "d2", "Q2", "A2")
    private val c3 = card("c3", "d3", "Q3", "A3")

    private val allKbs = listOf(kb1, kb2)
    private val allDecks = listOf(d1, d2, d3)
    private val allCards = listOf(c1, c2, c3)

    private fun build(type: ObjectType, roots: List<String>): ObjectExport =
        (ObjectExportBuilder.build(type, roots, allKbs, allDecks, allCards, date)
            as ObjectBuildResult.Success).data.export

    private fun plan(
        export: ObjectExport,
        request: ObjectImportRequest,
        context: ObjectImportContext = ObjectImportContext(),
    ): ObjectImportPlan =
        (ObjectImportPlanner.buildPlan(export, request, context, t)
            as ObjectImportPlanResult.Success).plan

    private fun failure(
        export: ObjectExport,
        request: ObjectImportRequest,
        context: ObjectImportContext = ObjectImportContext(),
    ): ObjectTransferError =
        (ObjectImportPlanner.buildPlan(export, request, context, t)
            as ObjectImportPlanResult.Failure).error

    // ── Export sub-graph ──────────────────────────────────────────────────────────────────

    @Test
    fun staleRootIsDroppedSoTheExportedFileStaysImportable() {
        // c3's deck no longer exists, so the card cannot be represented with a valid FK and is
        // dropped from the payload. It used to stay in `rootIds`, which produced a file that
        // reported success on export and INVALID_ID on every device that tried to import it.
        val built = ObjectExportBuilder.build(
            ObjectType.CARD, listOf("c1", "c3"), allKbs, listOf(d1, d2), allCards, date,
        ) as ObjectBuildResult.Success
        val export = built.data.export
        val scope = requireNotNull(export.objectScope)

        assertEquals(listOf("c1"), scope.rootIds, "a dropped root must not stay in rootIds")
        assertEquals(listOf("c1"), scope.payloadCardIds)
        assertNull(ObjectExportBuilder.validate(export), "the builder must not emit a file it rejects")
    }

    @Test
    fun danglingParentIsDroppedSoTheExportedFileStaysImportable() {
        // The child survives a soft delete of its parent, so the live tree has a root with a
        // parentId pointing at a row that is not exported. Leaving it would make validate() reject
        // the file everywhere; exporting it as a root keeps the data movable.
        val built = ObjectExportBuilder.build(
            ObjectType.DECK, listOf("d2"), allKbs, listOf(d2), listOf(c2), date,
        ) as ObjectBuildResult.Success
        val export = built.data.export

        assertEquals(listOf("d2"), export.decks.map { it.id })
        assertNull(export.decks.single().parentId, "the deleted parent must not survive in the file")
        assertNull(ObjectExportBuilder.validate(export), "the builder must not emit a file it rejects")
    }

    @Test
    fun knowledgeBaseExportContainsOnlyItsOwnSubgraph() {
        val export = build(ObjectType.KNOWLEDGE_BASE, listOf("kb1"))
        assertEquals(listOf("kb1"), export.knowledgeBases.map { it.id })
        assertEquals(setOf("d1", "d2"), export.decks.map { it.id }.toSet())
        assertEquals(setOf("c1", "c2"), export.cards.map { it.id }.toSet())
        val scope = requireNotNull(export.objectScope)
        assertEquals(setOf("d1", "d2"), scope.payloadDeckIds.toSet())
        assertEquals(setOf("c1", "c2"), scope.payloadCardIds.toSet())
    }

    @Test
    fun deckExportExpandsDescendantsAndAddsAncestorContainers() {
        val export = build(ObjectType.DECK, listOf("d2"))
        val scope = requireNotNull(export.objectScope)
        assertEquals(setOf("d2"), scope.payloadDeckIds.toSet())
        assertEquals(setOf("c2"), export.cards.map { it.id }.toSet())
        // d1 is a container: needed so d2.parent_id resolves under the legacy importer.
        assertTrue(export.decks.any { it.id == "d1" })
        assertTrue(export.knowledgeBases.any { it.id == "kb1" })
        assertEquals(emptyList<String>(), scope.payloadKnowledgeBaseIds)
    }

    @Test
    fun cardExportDropsCardsWhoseDeckIsMissing() {
        val orphan = card("cX", "no-such-deck", "Q", "A")
        val result = ObjectExportBuilder.build(
            ObjectType.CARD, listOf("c1", "cX"), allKbs, allDecks, allCards + orphan, date
        ) as ObjectBuildResult.Success
        assertEquals(listOf("c1"), result.data.export.cards.map { it.id })
        // The card's deck and its knowledge base travel along as containers.
        assertTrue(result.data.export.decks.any { it.id == "d1" })
        assertTrue(result.data.export.knowledgeBases.any { it.id == "kb1" })
    }

    @Test
    fun cardExportWithNoSurvivingCardFails() {
        val orphan = card("cX", "no-such-deck", "Q", "A")
        val result = ObjectExportBuilder.build(
            ObjectType.CARD, listOf("cX"), allKbs, allDecks, listOf(orphan), date
        )
        assertTrue(result is ObjectBuildResult.Failure)
        assertEquals(ObjectTransferError.EMPTY_ROOT, result.error)
    }

    @Test
    fun emptyRootAndFullTypeAreRejected() {
        val empty = ObjectExportBuilder.build(ObjectType.DECK, emptyList(), allKbs, allDecks, allCards, date)
        assertEquals(ObjectTransferError.EMPTY_ROOT, (empty as ObjectBuildResult.Failure).error)
        val full = ObjectExportBuilder.build(ObjectType.FULL, listOf("d1"), allKbs, allDecks, allCards, date)
        assertEquals(ObjectTransferError.UNKNOWN_OBJECT_TYPE, (full as ObjectBuildResult.Failure).error)
    }

    // ── Wire compatibility ───────────────────────────────────────────────────────────────

    @Test
    fun scopedExportIsAcceptedByTheUnchangedLegacyImporter() {
        val export = build(ObjectType.KNOWLEDGE_BASE, listOf("kb1"))
        val json = ObjectExportBuilder.encode(export)
        val decoded = ExportManager().importData(json)
        assertNotNull(decoded)
        assertEquals("share", decoded.exportType)
        assertEquals(export.knowledgeBases.size, decoded.knowledgeBases.size)
        assertEquals(export.decks.size, decoded.decks.size)
        assertEquals(export.cards.size, decoded.cards.size)
    }

    @Test
    fun objectTypeOfDistinguishesScopedFromLegacyFiles() {
        val json = ObjectExportBuilder.encode(build(ObjectType.DECK, listOf("d1")))
        assertEquals(ObjectType.DECK, ObjectExportBuilder.objectTypeOf(json))
        assertEquals(ObjectType.FULL, ObjectExportBuilder.objectTypeOf("{\"exportType\":\"share\",\"decks\":[],\"cards\":[]}"))
        assertEquals(ObjectType.FULL, ObjectExportBuilder.objectTypeOf("not json"))
    }

    @Test
    fun rewriteMediaPathsAppliesRenameMapping() {
        val withMedia = c1.copy(media = listOf(Media("m1", MediaType.IMAGE, "img.png")))
        val result = ObjectExportBuilder.build(
            ObjectType.CARD, listOf("c1"), allKbs, allDecks, listOf(withMedia), date
        ) as ObjectBuildResult.Success
        val rewritten = ObjectExportBuilder.rewriteMediaPaths(
            result.data.export, mapOf("img.png" to "img_ab12.png")
        )
        assertEquals("img_ab12.png", rewritten.cards.single().media.single().filePath)
    }

    @Test
    fun rewriteMediaPathsUsesCaseFallbackOnlyForUniqueCandidates() {
        // The real import path calls this, not resolveRenamedPath. A case-only fallback must
        // refuse to guess when the map contains two different files that differ only in case.
        val cards = listOf(
            c1.copy(id = "c1", media = listOf(Media("m1", MediaType.IMAGE, "A.PNG"))),
            c1.copy(id = "c2", media = listOf(Media("m2", MediaType.IMAGE, "a.png"))),
            c1.copy(id = "c3", media = listOf(Media("m3", MediaType.IMAGE, "a.PNG"))),
        )
        val built = ObjectExportBuilder.build(
            ObjectType.CARD, listOf("c1", "c2", "c3"), allKbs, allDecks, cards, date,
        ) as ObjectBuildResult.Success
        // A single case-only candidate is a safe fallback; two are not.
        val unique = ObjectExportBuilder.rewriteMediaPaths(
            built.data.export, mapOf("A.png" to "upper_xx.png"),
        ).cards.associateBy { it.id }
        assertEquals("upper_xx.png", unique["c1"]!!.media.single().filePath)
        assertEquals("upper_xx.png", unique["c2"]!!.media.single().filePath, "one case-folded candidate is safe")

        val ambiguous = ObjectExportBuilder.rewriteMediaPaths(
            built.data.export,
            mapOf("A.png" to "upper_xx.png", "a.png" to "lower_xx.png"),
        ).cards.associateBy { it.id }
        assertEquals("A.PNG", ambiguous["c1"]!!.media.single().filePath)
        assertEquals("lower_xx.png", ambiguous["c2"]!!.media.single().filePath, "exact match still wins")
        assertEquals("a.PNG", ambiguous["c3"]!!.media.single().filePath, "ambiguous case must stay untouched")
    }

    @Test
    fun validateRejectsDuplicateCardIdsFromAHandEditedManifest() {
        val export = build(ObjectType.DECK, listOf("d1"))
        val first = export.cards.first()
        val tampered = export.copy(cards = listOf(first, first.copy(front = "another")))
        assertEquals(ObjectTransferError.DUPLICATE_ID, ObjectExportBuilder.validate(tampered))
    }

    @Test
    fun validateRejectsGarbageTimestampsAndOversizedFields() {
        val export = build(ObjectType.DECK, listOf("d1"))
        assertEquals(
            ObjectTransferError.INVALID_TIMESTAMP,
            ObjectExportBuilder.validate(
                export.copy(cards = export.cards.map {
                    if (it.id == "c1") it.copy(createdAt = "not-a-time") else it
                }),
            ),
        )
        assertEquals(
            ObjectTransferError.PAYLOAD_TOO_LARGE,
            ObjectExportBuilder.validate(
                export.copy(cards = export.cards.map {
                    if (it.id == "c1") it.copy(front = "x".repeat(200_001)) else it
                }),
            ),
        )
    }

    // ── Validation guards ────────────────────────────────────────────────────────────────

    @Test
    fun validateRejectsBrokenForeignKey() {
        val export = build(ObjectType.KNOWLEDGE_BASE, listOf("kb1"))
        val broken = export.copy(decks = export.decks.map { it.copy(knowledgeBaseId = "missing") })
        assertEquals(ObjectTransferError.INVALID_ID, ObjectExportBuilder.validate(broken))
    }

    @Test
    fun validateRejectsDeckParentCycle() {
        val a = deck("a", "kb1", "A", parentId = "b")
        val b = deck("b", "kb1", "B", parentId = "a")
        val export = (ObjectExportBuilder.build(
            ObjectType.KNOWLEDGE_BASE, listOf("kb1"), listOf(kb1), listOf(a, b), emptyList(), date
        ) as ObjectBuildResult.Success).data.export
        assertEquals(ObjectTransferError.DECK_CYCLE, ObjectExportBuilder.validate(export))
    }

    @Test
    fun validateRejectsLegacyFullExport() {
        val export = build(ObjectType.KNOWLEDGE_BASE, listOf("kb1")).copy(objectScope = null)
        assertEquals(ObjectTransferError.UNKNOWN_OBJECT_TYPE, ObjectExportBuilder.validate(export))
    }

    // ── Import planning ──────────────────────────────────────────────────────────────────

    @Test
    fun keepBothRenamesKbAndRegeneratesEveryId() {
        val export = build(ObjectType.KNOWLEDGE_BASE, listOf("kb1"))
        val context = ObjectImportContext(listOf(kb("local", "Alpha")), emptyList(), emptyList())
        val p = plan(export, ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.KEEP_BOTH), context)
        assertEquals(1, p.knowledgeBases.size)
        assertTrue(p.knowledgeBases.single().name.contains("(1)"))
        assertTrue(p.deleteKnowledgeBaseIds.isEmpty())
        assertTrue(p.knowledgeBases.none { it.id == "kb1" })
        assertTrue(p.decks.none { it.id in setOf("d1", "d2") })
        assertTrue(p.cards.none { it.id in setOf("c1", "c2") })
    }

    @Test
    fun overwriteRecordsTheExistingKbAsDeletionSeed() {
        val export = build(ObjectType.KNOWLEDGE_BASE, listOf("kb1"))
        val context = ObjectImportContext(listOf(kb("local", "Alpha")), emptyList(), emptyList())
        val p = plan(export, ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.OVERWRITE), context)
        assertEquals(listOf("local"), p.deleteKnowledgeBaseIds)
        assertEquals("Alpha", p.knowledgeBases.single().name)
        assertTrue(p.overwritten)
    }

    @Test
    fun deckImportRehangsPayloadUnderChosenKnowledgeBase() {
        val export = build(ObjectType.DECK, listOf("d1"))
        val context = ObjectImportContext(listOf(kb("local", "Local")), emptyList(), emptyList())
        val p = plan(export, ObjectImportRequest(targetKnowledgeBaseId = "local"), context)
        assertTrue(p.decks.all { it.knowledgeBaseId == "local" })
        assertNull(p.decks.first().parentId)
        assertEquals(p.decks.first().id, p.decks.last().parentId)
        assertEquals(setOf("Q1", "Q2"), p.cards.map { it.front }.toSet())
    }

    @Test
    fun deckImportRequiresOrCreatesATargetKnowledgeBase() {
        val export = build(ObjectType.DECK, listOf("d1"))
        val withKb = ObjectImportContext(listOf(kb("local", "Local")), emptyList(), emptyList())
        assertEquals(ObjectTransferError.TARGET_REQUIRED, failure(export, ObjectImportRequest(), withKb))
        assertEquals(ObjectTransferError.NO_KNOWLEDGE_BASE, failure(export, ObjectImportRequest()))
        assertEquals(
            ObjectTransferError.TARGET_NOT_FOUND,
            failure(export, ObjectImportRequest(targetKnowledgeBaseId = "gone"), withKb),
        )
        val created = plan(export, ObjectImportRequest(newKnowledgeBaseName = "Fresh"), withKb)
        assertEquals(1, created.knowledgeBases.size)
        assertEquals("Fresh", created.knowledgeBases.single().name)
    }

    @Test
    fun duplicateCardsAreMatchedByTypeAndTrimmedContent() {
        val export = build(ObjectType.CARD, listOf("c1"))
        val context = ObjectImportContext(
            knowledgeBases = listOf(kb("local", "Local")),
            decks = listOf(deck("localDeck", "local", "Target")),
            cards = listOf(card("dup", "localDeck", " Q1 ", "A1")),
        )
        val skipped = plan(
            export,
            ObjectImportRequest(targetDeckId = "localDeck", duplicatePolicy = ObjectDuplicatePolicy.SKIP),
            context,
        )
        assertEquals(1, skipped.skippedCards)
        assertEquals(0, skipped.cards.size)

        val overwrite = plan(
            export,
            ObjectImportRequest(targetDeckId = "localDeck", duplicatePolicy = ObjectDuplicatePolicy.OVERWRITE),
            context,
        )
        assertEquals(listOf("dup"), overwrite.deleteCardIds)
        assertEquals(1, overwrite.cards.size)

        val keepBoth = plan(
            export,
            ObjectImportRequest(targetDeckId = "localDeck", duplicatePolicy = ObjectDuplicatePolicy.KEEP_BOTH),
            context,
        )
        assertTrue(keepBoth.deleteCardIds.isEmpty())
        assertEquals(1, keepBoth.cards.size)
    }

    @Test
    fun differentCardTypeIsNotADuplicate() {
        val export = build(ObjectType.CARD, listOf("c1"))
        val context = ObjectImportContext(
            knowledgeBases = listOf(kb("local", "Local")),
            decks = listOf(deck("localDeck", "local", "Target")),
            cards = listOf(card("other", "localDeck", "Q1", "A1", type = CardType.CLOZE)),
        )
        val p = plan(
            export,
            ObjectImportRequest(targetDeckId = "localDeck", duplicatePolicy = ObjectDuplicatePolicy.SKIP),
            context,
        )
        assertEquals(0, p.skippedCards)
        assertEquals(1, p.cards.size)
    }

    @Test
    fun cardImportCanCreateBothDeckAndKnowledgeBase() {
        val export = build(ObjectType.CARD, listOf("c1"))
        val p = plan(
            export,
            ObjectImportRequest(
                newDeckName = "New Deck",
                newKnowledgeBaseName = "New KB",
            ),
        )
        assertEquals(1, p.knowledgeBases.size)
        assertEquals(1, p.decks.size)
        assertEquals(p.decks.single().id, p.targetDeckId)
        assertEquals(1, p.cards.size)
        assertEquals(p.decks.single().id, p.cards.single().deckId)
    }

    @Test
    fun cardImportRejectsMissingTargetDeck() {
        val export = build(ObjectType.CARD, listOf("c1"))
        assertEquals(ObjectTransferError.TARGET_REQUIRED, failure(export, ObjectImportRequest()))
        assertEquals(
            ObjectTransferError.TARGET_NOT_FOUND,
            failure(export, ObjectImportRequest(targetDeckId = "gone")),
        )
    }

    // ── Round-1 review regressions ──────────────────────────────────────────────────────

    @Test
    fun overwriteKeepsTheOriginalNameInsteadOfSuffixing() {
        val export = build(ObjectType.KNOWLEDGE_BASE, listOf("kb1"))
        val context = ObjectImportContext(listOf(kb("local", "Alpha")), emptyList(), emptyList())
        val p = plan(export, ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.OVERWRITE), context)
        // The name is freed by the delete, so re-adding "(1)" would be a visible regression.
        assertEquals("Alpha", p.knowledgeBases.single().name)
        assertTrue(p.renamed.isEmpty())
    }

    @Test
    fun duplicateNamesInsideOneExportAreDisambiguated() {
        val sameNameA = kb("dupA", "Alpha")
        val sameNameB = kb("dupB", "Alpha")
        val export = (ObjectExportBuilder.build(
            ObjectType.KNOWLEDGE_BASE, listOf("dupA", "dupB"),
            listOf(sameNameA, sameNameB), emptyList(), emptyList(), date,
        ) as ObjectBuildResult.Success).data.export
        val p = plan(export, ObjectImportRequest(), ObjectImportContext())
        // Two live rows with the same name break the unique-name lookups downstream.
        assertEquals(listOf("Alpha", "Alpha (1)"), p.knowledgeBases.map { it.name })
    }

    @Test
    fun overwriteOnlyMatchesDecksAtTheSameLevel() {
        val export = build(ObjectType.DECK, listOf("d2"))
        val context = ObjectImportContext(
            knowledgeBases = listOf(kb("local", "Local")),
            decks = listOf(
                deck("topChild", "local", "Child"),
                deck("root", "local", "Root"),
                deck("nestedChild", "local", "Child", parentId = "root"),
            ),
            cards = emptyList(),
        )
        val p = plan(
            export,
            ObjectImportRequest(
                targetKnowledgeBaseId = "local",
                conflictPolicy = ObjectConflictPolicy.OVERWRITE,
            ),
            context,
        )
        // Only the deck under the re-attached "Root" is replaced; the unrelated top-level
        // "Child" must survive.
        assertEquals(listOf("nestedChild"), p.deleteDeckIds)
        assertEquals("root", p.decks.single().parentId)
    }

    @Test
    fun freshParentWithoutATwinDoesNotOverwriteATopLevelName() {
        // Importing Physics/Maths into a library that has only a top-level Maths must create a new
        // tree. The child's fresh parent has no local twin, so the lookup must not fall back to
        // the root level and delete the unrelated Maths.
        // Export the whole Root → Child tree, so Child's parent is a fresh id with no local twin.
        val export = build(ObjectType.DECK, listOf("d1"))
        val context = ObjectImportContext(
            knowledgeBases = listOf(kb("local", "Local")),
            decks = listOf(deck("topChild", "local", "Child")),
            cards = emptyList(),
        )
        val p = plan(
            export,
            ObjectImportRequest(
                targetKnowledgeBaseId = "local",
                conflictPolicy = ObjectConflictPolicy.OVERWRITE,
            ),
            context,
        )
        assertTrue(p.deleteDeckIds.isEmpty(), "an unrelated top-level deck must survive: ${p.deleteDeckIds}")
        // Names are claimed per knowledge base, so the unrelated top-level "Child" still forces a
        // suffix — but crucially it must not be deleted.
        assertEquals(listOf("Root", "Child (1)"), p.decks.map { it.name })
    }

    @Test
    fun batchDuplicateCardsAreInsertedOnce() {
        val twin = card("c1b", "d1", "Q1", "A1")
        val export = (ObjectExportBuilder.build(
            ObjectType.CARD, listOf("c1", "c1b"), allKbs, allDecks, allCards + twin, date,
        ) as ObjectBuildResult.Success).data.export
        val p = plan(export, ObjectImportRequest(newDeckName = "D", newKnowledgeBaseName = "K"))
        assertEquals(1, p.cards.size)
        assertEquals(1, p.skippedCards)
    }

    @Test
    fun overwriteReplacesAtMostOneLocalDuplicate() {
        val export = build(ObjectType.CARD, listOf("c1"))
        val context = ObjectImportContext(
            knowledgeBases = listOf(kb("local", "Local")),
            decks = listOf(deck("localDeck", "local", "Target")),
            cards = listOf(
                card("dup1", "localDeck", "Q1", "A1"),
                card("dup2", "localDeck", "Q1", "A1"),
            ),
        )
        val p = plan(
            export,
            ObjectImportRequest(targetDeckId = "localDeck", duplicatePolicy = ObjectDuplicatePolicy.OVERWRITE),
            context,
        )
        assertEquals(1, p.deleteCardIds.size)
        assertEquals(1, p.cards.size)
        assertEquals(1, p.cardIdMap.size)
    }

    @Test
    fun cardImportOverwriteReusesTheNamedDeck() {
        val export = build(ObjectType.CARD, listOf("c1"))
        val context = ObjectImportContext(
            knowledgeBases = listOf(kb("local", "Local")),
            decks = listOf(deck("study", "local", "Study")),
            cards = emptyList(),
        )
        val p = plan(
            export,
            ObjectImportRequest(
                conflictPolicy = ObjectConflictPolicy.OVERWRITE,
                targetKnowledgeBaseId = "local",
                newDeckName = "Study",
            ),
            context,
        )
        assertEquals("study", p.targetDeckId)
        assertTrue(p.decks.isEmpty())
    }

    @Test
    fun planCarriesIdMapsForStudyPlanTransfer() {
        val p = plan(
            build(ObjectType.KNOWLEDGE_BASE, listOf("kb1")),
            ObjectImportRequest(newKnowledgeBaseName = "Fresh"),
        )
        assertEquals(setOf("kb1"), p.knowledgeBaseIdMap.keys)
        assertEquals(setOf("d1", "d2"), p.deckIdMap.keys)
        assertTrue(p.knowledgeBaseIdMap.values.none { it == "kb1" })
    }

    @Test
    fun cardIdentityIgnoresLineEndings() {
        assertEquals(
            ObjectImportPlanner.cardIdentity("BASIC", "A\r\nB", "C"),
            ObjectImportPlanner.cardIdentity("BASIC", "A\nB", "C"),
        )
    }

    @Test
    fun detectSeparatesUnreadableFilesFromFullExports() {
        val scoped = ObjectExportBuilder.encode(build(ObjectType.DECK, listOf("d1")))
        assertEquals(ObjectFileKind.SCOPED, ObjectExportBuilder.detect(scoped))
        assertEquals(
            ObjectFileKind.FULL,
            ObjectExportBuilder.detect("{\"exportType\":\"share\",\"decks\":[],\"cards\":[]}"),
        )
        assertEquals(ObjectFileKind.UNREADABLE, ObjectExportBuilder.detect("not json"))
    }

    @Test
    fun renameRecordsBothNames() {
        val context = ObjectImportContext(listOf(kb("local", "Alpha")), emptyList(), emptyList())
        val p = plan(
            build(ObjectType.KNOWLEDGE_BASE, listOf("kb1")),
            ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.KEEP_BOTH),
            context,
        )
        assertEquals(listOf(ObjectRename("Alpha", "Alpha (1)")), p.renamed)
    }

    @Test
    fun overwriteKeepsIdenticalCardsInDifferentDecksApart() {
        // Two decks may hold cards with the same normalized front/back. A card selection must
        // remain in its own deck after an overwrite; a global identity index would point every
        // such selection at the first copy found in plan order.
        val localKbs = listOf(kb("kb-old", "Alpha"))
        val localDecks = listOf(deck("d1", "kb-old", "Root"), deck("d2", "kb-old", "Other"))
        val localCards = listOf(
            card("c1", "d1", "Same", "A"),
            card("c2", "d2", "Same", "A"),
        )
        val exportKbs = listOf(kb("k1", "Alpha"))
        val exportDecks = listOf(deck("f1", "k1", "Root"), deck("f2", "k1", "Other"))
        val exportCards = listOf(
            card("fc1", "f1", "Same", "A"),
            card("fc2", "f2", "Same", "A"),
        )
        val built = (ObjectExportBuilder.build(
            ObjectType.KNOWLEDGE_BASE, listOf("k1"), exportKbs, exportDecks, exportCards, date,
        ) as ObjectBuildResult.Success).data.export

        val p = plan(
            built,
            ObjectImportRequest(conflictPolicy = ObjectConflictPolicy.OVERWRITE),
            ObjectImportContext(localKbs, localDecks, localCards),
        )
        val first = requireNotNull(p.cardIdMap["c1"])
        val second = requireNotNull(p.cardIdMap["c2"])
        assertNotEquals(first, second, "two local cards must not collapse onto one copy")
        assertEquals(p.deckIdMap["d1"], p.cards.first { it.id == first }.deckId)
        assertEquals(p.deckIdMap["d2"], p.cards.first { it.id == second }.deckId)
    }
}
