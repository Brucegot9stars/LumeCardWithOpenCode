package com.lumecard.shared.data

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import kotlin.time.Instant

/**
 * Pure, database-free resolution of one scoped import.
 *
 * The planner turns a validated [ObjectExport] plus the user's [ObjectImportRequest] into an
 * immutable [ObjectImportPlan]: every imported id is freshly generated, every name collision is
 * already resolved, and the objects to overwrite have been recorded as deletion seeds. Nothing
 * here touches the database, so all conflict rules are covered by `commonTest`.
 *
 * Design invariant: a scoped import is always a **copy**. Even when the user chooses OVERWRITE,
 * the service soft-deletes the old rows and inserts new rows under new ids. This avoids the
 * `INSERT OR REPLACE` parent-row deletion semantics (see `LumeCardDatabase.sq`) and keeps the
 * operation a single atomic, replay-safe transaction. The `*IdMap`s therefore describe a rename
 * of identity, which the service replays into learning plans.
 */
object ObjectImportPlanner {

    /** Keeps auto-suffixed names inside what the lists can render without wrapping. */
    private const val MAX_NAME_LENGTH = 120
    private const val FALLBACK_NAME = "Untitled"

    /** A corrupt parent chain must not spin while rebuilding a container path. */
    private const val MAX_DECK_DEPTH = 64

    /**
     * Duplicate-card identity chosen by the user: card type + front/back content.
     * Whitespace-only and line-ending-only differences are ignored; a NUL separator keeps a
     * literal `|` in the content from producing a false match.
     */
    fun cardIdentity(type: String, front: String, back: String): String =
        listOf(type, normalizeText(front), normalizeText(back)).joinToString("\u0000")

    private fun normalizeText(value: String): String =
        value.replace("\r\n", "\n").replace('\r', '\n').trim()

    private fun Card.identity(): String = cardIdentity(type.name, front, back)

    fun buildPlan(
        export: ObjectExport,
        request: ObjectImportRequest,
        context: ObjectImportContext,
        now: Instant,
    ): ObjectImportPlanResult {
        val validationError = ObjectExportBuilder.validate(export)
        if (validationError != null) return ObjectImportPlanResult.Failure(validationError)

        val scope = export.objectScope
            ?: return ObjectImportPlanResult.Failure(ObjectTransferError.UNKNOWN_OBJECT_TYPE)
        val payloadKbIds = scope.payloadKnowledgeBaseIds.toSet()
        val payloadDeckIds = scope.payloadDeckIds.toSet()
        val payloadCardIds = scope.payloadCardIds.toSet()

        val payloadKbs = export.knowledgeBases.filter { it.id in payloadKbIds }
        val payloadDecks = orderDecks(export.decks.filter { it.id in payloadDeckIds })
        val payloadCards = export.cards.filter { it.id in payloadCardIds }

        // Every collision lookup below is a point query on (knowledge base, level, name). Doing
        // them by filtering `context.decks` made one plan O(payload × library): a 20 000-deck
        // knowledge base (the documented cap) has to compare 4×10⁸ trimmed names, which is the
        // "several minutes" a large import used to spend before writing a single row. The index
        // is built once, in one pass, and keeps the exact iteration order of `context.decks` so
        // `collisions.map { it.id }` records the same deletion order as before.
        val local = LocalIndex(context)

        val newKbs = mutableListOf<KnowledgeBase>()
        val newDecks = mutableListOf<Deck>()
        val newCards = mutableListOf<Card>()
        val deleteKbIds = mutableListOf<String>()
        val deleteDeckIds = mutableListOf<String>()
        val deleteCardIds = mutableListOf<String>()
        val renamed = mutableListOf<ObjectRename>()
        val kbIdMap = LinkedHashMap<String, String>()
        val deckIdMap = LinkedHashMap<String, String>()
        val cardIdMap = LinkedHashMap<String, String>()
        var skippedCards = 0

        // Live names only, trimmed so trailing whitespace cannot create two "identical" rows.
        val takenKbNames = context.knowledgeBases.map { it.name.trim() }.toMutableSet()
        val claimedKbNames = mutableSetOf<String>()
        val takenDeckKeys = context.decks
            .map { deckKey(it.knowledgeBaseId, it.name) }
            .toMutableSet()
        val claimedDeckKeys = mutableSetOf<String>()

        /**
         * Where to resume suffix probing for a base name, so a bulk import of N identical names
         * costs O(N) instead of O(N²).
         *
         * `uniqueName` walks ` (1)`, ` (2)`, … until it finds a free slot. Without a hint, the
         * k-th copy of "Maths" re-probes all k−1 names its predecessors just took: 5 000 decks
         * named "Maths" is ~12.5M probes, each one a string build plus two set lookups. The hint
         * records the suffix the previous claim landed on for that base; because suffixes are only
         * ever handed out upward within one plan, resuming from there cannot skip a free slot.
         *
         * Only a hint — correctness still comes from the `isTaken` predicate, so a stale or
         * conservative value just costs a few extra probes.
         */
        val kbClaimHint = mutableMapOf<String, Int>()
        val deckClaimHint = mutableMapOf<String, Int>()

        /**
         * Claims a live name for this plan. A collision is only "freed" when the user asked to
         * overwrite it; otherwise the name stays reserved and the copy is suffixed. Claimed names
         * always participate, so an export that itself contains duplicates cannot write two live
         * rows with the same name (the name lookups use `executeAsOneOrNull`).
         */
        fun claimKbName(base: String, freedByOverwrite: Boolean): String {
            if (freedByOverwrite && base !in claimedKbNames) takenKbNames.remove(base)
            val clip = trimTo(base, MAX_NAME_LENGTH)
            val name = uniqueName(base, kbClaimHint[clip] ?: 1) { it in takenKbNames || it in claimedKbNames }
            kbClaimHint[clip] = suffixAttempt(name, base) ?: 1
            claimedKbNames += name
            return name
        }

        fun claimDeckName(kbId: String, base: String, freedByOverwrite: Boolean): String {
            val baseKey = deckKey(kbId, base)
            if (freedByOverwrite && baseKey !in claimedDeckKeys) takenDeckKeys.remove(baseKey)
            val name = uniqueName(base, deckClaimHint[baseKey] ?: 1) { candidate ->
                val key = deckKey(kbId, candidate)
                key in takenDeckKeys || key in claimedDeckKeys
            }
            deckClaimHint[baseKey] = suffixAttempt(name, base) ?: 1
            claimedDeckKeys += deckKey(kbId, name)
            return name
        }

        fun resolveTargetKb(): Pair<String?, ObjectTransferError?> {
            request.targetKnowledgeBaseId?.let { id ->
                return if (local.hasKnowledgeBase(id)) {
                    id to null
                } else {
                    null to ObjectTransferError.TARGET_NOT_FOUND
                }
            }
            val requestedName = normalizeName(request.newKnowledgeBaseName)
                ?: return null to if (local.knowledgeBases.isEmpty()) {
                    ObjectTransferError.NO_KNOWLEDGE_BASE
                } else {
                    ObjectTransferError.TARGET_REQUIRED
                }
            // "Overwrite" on a *new* target name means "land in the knowledge base that already
            // has this name" — deleting it wholesale would destroy unrelated content the user
            // never pointed at.
            val existing = local.knowledgeBasesNamed(requestedName).firstOrNull()
            if (existing != null && request.conflictPolicy == ObjectConflictPolicy.OVERWRITE) {
                return existing.id to null
            }
            val name = claimKbName(requestedName, freedByOverwrite = false)
            if (name != requestedName) renamed += ObjectRename(requestedName, name)
            val id = generateId("kb")
            newKbs += KnowledgeBase(
                id = id, name = name, createdAt = now, updatedAt = now,
                version = 1, deletedAt = null, syncedAt = null,
            )
            return id to null
        }

        when (scope.type) {
            ObjectType.KNOWLEDGE_BASE -> {
                for (kb in payloadKbs) {
                    val base = normalizeName(kb.name) ?: FALLBACK_NAME
                    val collisions = local.knowledgeBasesNamed(base)
                    val overwrite = collisions.isNotEmpty() &&
                        request.conflictPolicy == ObjectConflictPolicy.OVERWRITE
                    val newId = generateId("kb")
                    kbIdMap[kb.id] = newId
                    if (overwrite) {
                        deleteKbIds += collisions.map { it.id }
                        // `kbIdMap` is keyed by the *file's* ids because that is what resolves
                        // deck parents. Learning plans, however, store *local* ids — without these
                        // extra entries `contentReplaced` finds no replacement for the row it is
                        // about to delete and simply drops it, emptying every plan that pointed at
                        // the overwritten knowledge base.
                        collisions.forEach { kbIdMap[it.id] = newId }
                    }
                    val name = claimKbName(base, overwrite)
                    if (name != base) renamed += ObjectRename(base, name)
                    newKbs += kb.toDomain(id = newId, name = name, now = now)
                }
                if (kbIdMap.isEmpty()) return ObjectImportPlanResult.Failure(ObjectTransferError.EMPTY_PAYLOAD)
                for (deck in payloadDecks) {
                    val newId = generateId("deck")
                    deckIdMap[deck.id] = newId
                    // A payload deck must belong to a payload knowledge base; a mismatch means the
                    // file is corrupt, and silently re-homing it would hide that.
                    val kbId = kbIdMap[deck.knowledgeBaseId]
                        ?: return ObjectImportPlanResult.Failure(ObjectTransferError.INVALID_ID)
                    val parentId = deck.parentId?.let { deckIdMap[it] }
                    val base = normalizeName(deck.name) ?: FALLBACK_NAME
                    // Bucketed by rendered parent id, which is what the copy will carry.
                    val collisions = local.decksAtLevel(kbId, parentId, base)
                    val overwrite = collisions.isNotEmpty() &&
                        request.conflictPolicy == ObjectConflictPolicy.OVERWRITE
                    if (overwrite) {
                        deleteDeckIds += collisions.map { it.id }
                        // Same reason as the knowledge-base map above: plans hold local ids.
                        collisions.forEach { deckIdMap[it.id] = newId }
                    }
                    val name = claimDeckName(kbId, base, overwrite)
                    if (name != base) renamed += ObjectRename(base, name)
                    newDecks += deck.toDomain(
                        id = newId,
                        knowledgeBaseId = kbId,
                        // Resolved inside the payload: this whole knowledge base is copied, so every
                        // ancestor of a payload deck is itself a payload deck.
                        parentId = parentId,
                        name = name,
                        now = now,
                    )
                }
                for (card in payloadCards) {
                    val deckId = deckIdMap[card.deckId]
                        ?: return ObjectImportPlanResult.Failure(ObjectTransferError.INVALID_ID)
                    newCards += card.toDomain(id = generateId("card"), deckId = deckId, now = now)
                }
                // A plan can select individual decks or cards of the knowledge base being replaced.
                // `deckIdMap` / `cardIdMap` above are keyed by the *file's* ids, so those local
                // selections would simply be pruned — emptying the plan even though the content is
                // still there under a new id. Match them to the copies by their stable identity:
                // deck = name path (its position in the tree), card = type + front/back.
                if (deleteKbIds.isNotEmpty()) {
                    val overwrittenLocalDecks = local.decks
                        .filter { it.knowledgeBaseId in deleteKbIds }
                        .mapTo(HashSet()) { it.id }
                    remapLocalDeckSelections(overwrittenLocalDecks, newDecks, deckIdMap, local)
                    // Snapshot taken *after* the call above, which adds `overwrittenLocalId` entries
                    // to the same map. The `filter` this replaced read the map once it was final;
                    // taking the snapshot earlier would silently drop every local deck's card
                    // selection (no exception, just an emptied learning plan).
                    val deckKeys = deckIdMap.keys.toSet()
                    remapLocalCardSelections(deckKeys, newCards, cardIdMap, deckIdMap, local)
                }
                return ObjectImportPlanResult.Success(
                    planFor(
                        scope.type, newKbs, newDecks, newCards, deleteKbIds, deleteDeckIds,
                        deleteCardIds, skippedCards, renamed,
                        targetKnowledgeBaseId = kbIdMap.values.firstOrNull(),
                        targetDeckId = null,
                        knowledgeBaseIdMap = kbIdMap,
                        deckIdMap = deckIdMap,
                        cardIdMap = cardIdMap,
                    )
                )
            }

            ObjectType.DECK -> {
                val (targetKbId, error) = resolveTargetKb()
                if (targetKbId == null) {
                    return ObjectImportPlanResult.Failure(error ?: ObjectTransferError.TARGET_REQUIRED)
                }
                if (payloadDecks.isEmpty()) {
                    return ObjectImportPlanResult.Failure(ObjectTransferError.EMPTY_PAYLOAD)
                }
                val exportDeckById = export.decks.associateBy { it.id }
                // For a fresh parent id, the local deck it replaces (if any). Used to find a
                // payload child's twin at the corresponding *local* level, so a twin that this
                // overwrite is about to delete stops reserving its name.
                val localIdByFreshId = HashMap<String, MutableList<String>>()
                val newDeckIds = HashSet<String>()

                for (deck in payloadDecks) {
                    val newId = generateId("deck")
                    newDeckIds += newId
                    deckIdMap[deck.id] = newId
                    val parentId = resolveParent(
                        deck = deck,
                        exportDeckById = exportDeckById,
                        targetKbId = targetKbId,
                        local = local,
                        deckIdMap = deckIdMap,
                    )
                    val base = normalizeName(deck.name) ?: FALLBACK_NAME
                    // Match at the corresponding *local* level: parentId above is the freshly
                    // generated parent, so querying with it would never find the local twin of a
                    // child deck and would suffix the copy "Child (1)". A suffixed name then breaks
                    // the name-path remap that carries learning-plan selections across the
                    // overwrite. A top-level "Maths" still cannot overwrite "Physics/Maths".
                    // resolveParent may return a fresh id (parent inside the payload) or a live
                    // id (matched container). Only the former needs translating back to its twin.
                    // When a fresh parent has no twin, keep the fresh id as the level key: it can
                    // never match a local deck, which is exactly right — a new "Physics/Maths"
                    // must not overwrite an unrelated top-level "Maths".
                    val localParentIds = parentId?.let { p ->
                        if (p in newDeckIds) localIdByFreshId[p].orEmpty() else listOf(p)
                    }.orEmpty()
                    val collisions = if (parentId == null) {
                        local.decksAtLevel(targetKbId, null, base)
                    } else {
                        // A dirty library may have several same-named parents; check each level.
                        localParentIds.flatMap { local.decksAtLevel(targetKbId, it, base) }
                    }
                    val overwrite = collisions.isNotEmpty() &&
                        request.conflictPolicy == ObjectConflictPolicy.OVERWRITE
                    if (overwrite) {
                        deleteDeckIds += collisions.map { it.id }
                        // Same reason as the knowledge-base map: plans hold local ids, so the row
                        // about to be soft-deleted needs a replacement entry of its own.
                        collisions.forEach {
                            deckIdMap[it.id] = newId
                            localIdByFreshId.getOrPut(newId) { mutableListOf() } += it.id
                        }
                    }
                    val name = claimDeckName(targetKbId, base, overwrite)
                    if (name != base) renamed += ObjectRename(base, name)
                    newDecks += deck.toDomain(
                        id = newId, knowledgeBaseId = targetKbId,
                        parentId = parentId,
                        name = name, now = now,
                    )
                }
                for (card in payloadCards) {
                    val deckId = deckIdMap[card.deckId]
                        ?: return ObjectImportPlanResult.Failure(ObjectTransferError.INVALID_ID)
                    newCards += card.toDomain(id = generateId("card"), deckId = deckId, now = now)
                }
                if (deleteDeckIds.isNotEmpty()) {
                    // execute() soft-deletes the whole subtree under every collision seed, not just
                    // the seeds themselves. A plan may select any of those descendants by id or by
                    // card, so carry them over too — otherwise the selections are silently scrubbed.
                    val overwrittenLocalDecks = HashSet(deleteDeckIds)
                    DeckTree.of(local.decks).expandDescendants(overwrittenLocalDecks)
                    remapLocalDeckSelections(overwrittenLocalDecks, newDecks, deckIdMap, local)
                    val deckKeys = deckIdMap.keys.toSet()
                    remapLocalCardSelections(deckKeys, newCards, cardIdMap, deckIdMap, local)
                }
                return ObjectImportPlanResult.Success(
                    planFor(
                        scope.type, newKbs, newDecks, newCards, deleteKbIds, deleteDeckIds,
                        deleteCardIds, skippedCards, renamed,
                        targetKnowledgeBaseId = targetKbId,
                        targetDeckId = null,
                        knowledgeBaseIdMap = kbIdMap,
                        deckIdMap = deckIdMap,
                        cardIdMap = cardIdMap,
                    )
                )
            }

            ObjectType.CARD -> {
                if (payloadCards.isEmpty()) {
                    return ObjectImportPlanResult.Failure(ObjectTransferError.EMPTY_PAYLOAD)
                }
                var targetDeckId: String
                val targetKbId: String
                val existingDeck = request.targetDeckId?.let { local.deck(it) }
                if (request.targetDeckId != null && existingDeck == null) {
                    return ObjectImportPlanResult.Failure(ObjectTransferError.TARGET_NOT_FOUND)
                }
                if (existingDeck != null) {
                    // Both targets may be supplied by a non-UI caller; refuse a contradictory
                    // pair instead of silently preferring one.
                    request.targetKnowledgeBaseId?.let { declaredKb ->
                        if (declaredKb != existingDeck.knowledgeBaseId) {
                            return ObjectImportPlanResult.Failure(ObjectTransferError.TARGET_DECK_MISMATCH)
                        }
                    }
                    targetDeckId = existingDeck.id
                    targetKbId = existingDeck.knowledgeBaseId
                } else {
                    // A card import must name a destination deck. Report that before looking for
                    // a knowledge base, otherwise an empty library yields a misleading
                    // NO_KNOWLEDGE_BASE instead of the actual missing decision.
                    val requestedDeckName = normalizeName(request.newDeckName)
                        ?: return ObjectImportPlanResult.Failure(ObjectTransferError.TARGET_REQUIRED)
                    val (kbId, kbError) = resolveTargetKb()
                    if (kbId == null) {
                        return ObjectImportPlanResult.Failure(
                            kbError ?: ObjectTransferError.TARGET_REQUIRED
                        )
                    }
                    targetKbId = kbId
                    val localDeck = local.decksAtLevel(kbId, null, requestedDeckName).firstOrNull()
                    if (localDeck != null &&
                        request.conflictPolicy == ObjectConflictPolicy.OVERWRITE
                    ) {
                        // Overwrite semantics for the destination deck: reuse it rather than
                        // creating a suffixed twin the user did not ask for.
                        targetDeckId = localDeck.id
                    } else {
                        val deckName = claimDeckName(
                            kbId, requestedDeckName,
                            freedByOverwrite = false,
                        )
                        if (deckName != requestedDeckName) {
                            renamed += ObjectRename(requestedDeckName, deckName)
                        }
                        targetDeckId = generateId("deck")
                        newDecks += Deck(
                            id = targetDeckId, knowledgeBaseId = targetKbId, name = deckName,
                            createdAt = now, updatedAt = now, version = 1,
                            deletedAt = null, syncedAt = null,
                        )
                    }
                }

                val duplicatesByIdentity = local.cardsInDeckByIdentity(targetDeckId)
                val seenInBatch = mutableSetOf<String>()
                for (card in payloadCards) {
                    val identity = cardIdentity(card.type, card.front, card.back)
                    // The same card twice in one file adds nothing new; never delete twice for it.
                    if (!seenInBatch.add(identity)) {
                        skippedCards++
                        continue
                    }
                    val duplicates = duplicatesByIdentity[identity].orEmpty()
                    if (duplicates.isNotEmpty()) {
                        when (request.duplicatePolicy) {
                            // One-for-one replacement: deleting every local twin would remove
                            // more cards than this import puts back.
                            ObjectDuplicatePolicy.OVERWRITE -> {
                                val replaced = duplicates.first()
                                deleteCardIds += replaced.id
                                val newId = generateId("card")
                                cardIdMap[replaced.id] = newId
                                newCards += card.toDomain(id = newId, deckId = targetDeckId, now = now)
                                continue
                            }
                            ObjectDuplicatePolicy.SKIP -> {
                                skippedCards++
                                continue
                            }
                            ObjectDuplicatePolicy.KEEP_BOTH -> Unit
                        }
                    }
                    newCards += card.toDomain(
                        id = generateId("card"), deckId = targetDeckId, now = now,
                    )
                }
                return ObjectImportPlanResult.Success(
                    planFor(
                        scope.type, newKbs, newDecks, newCards, deleteKbIds, deleteDeckIds,
                        deleteCardIds, skippedCards, renamed,
                        targetKnowledgeBaseId = targetKbId,
                        targetDeckId = targetDeckId,
                        knowledgeBaseIdMap = kbIdMap,
                        deckIdMap = deckIdMap,
                        cardIdMap = cardIdMap,
                    )
                )
            }

            ObjectType.FULL -> return ObjectImportPlanResult.Failure(ObjectTransferError.UNKNOWN_OBJECT_TYPE)
        }
    }

    /**
     * Parent/child index over the live deck list plus the name-scoped collision buckets every
     * branch of [buildPlan] needs, built once per plan.
     *
     * **Thread-confined.** An instance is created and consumed entirely inside one non-suspending
     * `buildPlan` call, so the `LazyThreadSafetyMode.NONE` fields below are safe *only* because the
     * instance never escapes to another thread. Do not cache it, do not key it by context, and do
     * not capture it in a coroutine: `buildPlan` itself is called from `Dispatchers.IO`, and several
     * imports can be in flight at once.
     *
     * Ordering guarantee: buckets preserve `context.decks` order, so the `delete*Ids` lists keep the
     * exact order they had when they were produced by a `filter` over the same list.
     */
    private class LocalIndex(val context: ObjectImportContext) {

        /**
         * Deck level key -> live decks at that level. `null` from [levelKey] means "unindexable id";
         * those decks are simply never matched, which is the same outcome a `filter` produced for a
         * blank knowledge-base id.
         *
         * Sized up front: at the 20 000-deck cap the default 16-bucket table rehashes 11 times and
         * copies ~24 500 entries on the way, which is the same order of magnitude as the linear
         * pass this index was built to achieve. Same reasoning as `DeckTree.build`.
         */
        private val decksByLevel = HashMap<String, MutableList<Deck>>(context.decks.size * 2)

        /** Knowledge base name bucket, keyed by [kbLookupKey] — the exact function callers query with. */
        private val kbsByName = HashMap<String, MutableList<KnowledgeBase>>(context.knowledgeBases.size * 2)

        val knowledgeBases: List<KnowledgeBase> get() = context.knowledgeBases
        val decks: List<Deck> get() = context.decks
        val cards: List<Card> get() = context.cards

        private val kbIds: Set<String> = context.knowledgeBases.mapTo(HashSet(context.knowledgeBases.size)) { it.id }
        private val tree: DeckTree by lazy(LazyThreadSafetyMode.NONE) { DeckTree.of(context.decks) }

        init {
            // Same key function as the query side (`normalizeName`), not a bare `trim()`: names can
            // exceed MAX_NAME_LENGTH (a file may carry up to 200 chars), and bucketing the full
            // string while querying the clipped one made the two never meet — an OVERWRITE of a
            // long-named knowledge base silently degraded into "create another one".
            context.knowledgeBases.forEach {
                kbsByName.getOrPut(kbLookupKey(it.name)) { ArrayList(1) } += it
            }
            context.decks.forEach { deck ->
                val key = levelKey(deck.knowledgeBaseId, deck.parentId, deck.name) ?: return@forEach
                decksByLevel.getOrPut(key) { ArrayList(1) } += deck
            }
        }

        fun hasKnowledgeBase(id: String): Boolean = id in kbIds

        /**
         * One deck by id, or null.
         *
         * A linear scan on purpose: the only caller resolves a single user-supplied `targetDeckId`,
         * and an `associateBy` index would build a 20 000-entry map on every knowledge-base and
         * deck plan to answer one question. `O(decks)` once is cheaper than that map.
         */
        fun deck(id: String): Deck? = context.decks.firstOrNull { it.id == id }

        fun knowledgeBasesNamed(name: String): List<KnowledgeBase> = kbsByName[kbLookupKey(name)].orEmpty()

        /** Every live deck at the same knowledge base / level / name as the prospective copy. */
        fun decksAtLevel(kbId: String, parentId: String?, name: String): List<Deck> {
            val key = levelKey(kbId, parentId, name) ?: return emptyList()
            return decksByLevel[key].orEmpty()
        }

        fun tree(): DeckTree = tree

        /**
         * Cards of **one** deck, grouped by content identity.
         *
         * Deliberately filtered before grouping. Grouping the whole library (`groupBy { it.deckId }`)
         * would allocate a bucket per deck and an entry per card — 100 000 entries at the cap — to
         * answer a question about a single deck. The version this index replaced filtered first,
         * and keeping the same one-deck scope is what keeps the CARD branch linear in the target
         * deck rather than in the library.
         *
         * Called once per plan (before the payload loop), so a plain recompute is cheaper than a
         * cache would be.
         */
        fun cardsInDeckByIdentity(deckId: String): Map<String, List<Card>> =
            context.cards.asSequence()
                .filter { it.deckId == deckId }
                .groupBy { it.identity() }

        /**
         * Bucket key for one deck level, or null when the id is unusable.
         *
         * Blank names are bucketed like any other name on purpose. Returning "no bucket" for them
         * would make `decksAtLevel("")` miss a stored blank-named deck, whereas the `filter` this
         * replaced compared trimmed strings and *would* match `"" == ""`. The main collision loops
         * never pass a blank base (`normalizeName(...) ?: FALLBACK_NAME`), but `resolveParent`
         * passes the file's raw ancestor name, so the difference was reachable from a crafted file.
         */
        private fun levelKey(kbId: String, parentId: String?, name: String): String? {
            if (kbId.isEmpty()) return null
            // Unambiguous by construction, not by assumption: a length-prefixed prefix code cannot
            // collide even when a component contains the separator, and "no parent" is its own
            // tag rather than a sentinel id. A sentinel (`"\u0001root"`) was wrong: ids come from a
            // decoded `.json`, and nothing rejects `\u0001`, so a crafted file could put a real
            // parent id in the same bucket as "top level" and an OVERWRITE would then delete a deck
            // at the wrong level.
            return buildString(kbId.length + (parentId?.length ?: 0) + name.length + 16) {
                appendKeyPart(kbId)
                append(if (parentId == null) ROOT_TAG else CHILD_TAG)
                if (parentId != null) appendKeyPart(parentId)
                appendKeyPart(name.trim())
            }
        }

        private companion object {
            /** "This deck has no parent", encoded as a tag so no id string can imitate it. */
            const val ROOT_TAG = 'R'
            const val CHILD_TAG = 'C'
        }
    }

    private fun planFor(
        type: ObjectType,
        kbs: List<KnowledgeBase>,
        decks: List<Deck>,
        cards: List<Card>,
        deleteKbIds: List<String>,
        deleteDeckIds: List<String>,
        deleteCardIds: List<String>,
        skippedCards: Int,
        renamed: List<ObjectRename>,
        targetKnowledgeBaseId: String?,
        targetDeckId: String?,
        knowledgeBaseIdMap: Map<String, String>,
        deckIdMap: Map<String, String>,
        cardIdMap: Map<String, String>,
    ) = ObjectImportPlan(
        objectType = type,
        knowledgeBases = kbs,
        decks = decks,
        cards = cards,
        deleteKnowledgeBaseIds = deleteKbIds.distinct(),
        deleteDeckIds = deleteDeckIds.distinct(),
        deleteCardIds = deleteCardIds.distinct(),
        skippedCards = skippedCards,
        renamed = renamed,
        overwritten = deleteKbIds.isNotEmpty() || deleteDeckIds.isNotEmpty() || deleteCardIds.isNotEmpty(),
        targetKnowledgeBaseId = targetKnowledgeBaseId,
        targetDeckId = targetDeckId,
        knowledgeBaseIdMap = knowledgeBaseIdMap,
        deckIdMap = deckIdMap,
        cardIdMap = cardIdMap,
    )

    /**
     * Carries a plan's **deck** selection across an overwrite of the whole knowledge base.
     *
     * The copies get new ids, so the only stable handle is the deck's position in the tree: its
     * trimmed name plus every ancestor's. Both sides of the comparison are indexed once, which
     * replaces the O(local decks × path length) rebuild the previous inline version performed for
     * every row (each `deckPath` call walked to the root from scratch, twice per deck).
     *
     * Order: the copies are visited parents-first and later duplicates win, then the local decks in
     * `context.decks` order — identical to the previous map/loop sequence, so `deckIdMap` keeps its
     * insertion order.
     */
    private fun remapLocalDeckSelections(
        overwrittenLocalDeckIds: Set<String>,
        newDecks: List<Deck>,
        deckIdMap: MutableMap<String, String>,
        local: LocalIndex,
    ) {
        if (newDecks.isEmpty()) return
        val freshNameById = HashMap<String, String>(newDecks.size * 2)
        newDecks.forEach { freshNameById[it.id] = it.name }
        val freshTree = DeckTree.of(newDecks)
        val freshIdByPath = HashMap<String, String>(newDecks.size * 2)
        newDecks.forEach { fresh ->
            val path = freshTree.pathOf(fresh.id) { freshNameById[it] } ?: return@forEach
            freshIdByPath[path] = fresh.id
        }
        if (freshIdByPath.isEmpty()) return

        val localTree = local.tree()
        val candidates = local.decks.filter { it.id in overwrittenLocalDeckIds }
        if (candidates.isEmpty()) return
        // `pathOf` walks to the root, and a dirty cross-knowledge-base parent id can lead out of
        // `candidates`. The map therefore has to cover every ancestor the tree knows, i.e. the whole
        // library — a partial map would silently truncate those paths and drop the selection.
        val localNameById = HashMap<String, String>(local.decks.size * 2)
        local.decks.forEach { localNameById[it.id] = it.name }
        candidates.forEach { localDeck ->
            val path = localTree.pathOf(localDeck.id) { localNameById[it] } ?: return@forEach
            freshIdByPath[path]?.let { deckIdMap[localDeck.id] = it }
        }
    }

    /**
     * Carries a plan's **card** selection across an overwrite, matching on content identity
     * (type + trimmed front/back) inside the decks that were just remapped.
     *
     * [deckKeys] is the deck-id snapshot taken when the filter used to run, deliberately including
     * the local ids [remapLocalDeckSelections] just registered.
     */
    private fun remapLocalCardSelections(
        deckKeys: Set<String>,
        newCards: List<Card>,
        cardIdMap: MutableMap<String, String>,
        deckIdMap: Map<String, String>,
        local: LocalIndex,
    ) {
        if (deckKeys.isEmpty() || newCards.isEmpty()) return
        // Identity alone is not enough: two decks can legitimately hold cards with the same
        // front/back. Key on the destination deck as well, or every such selection would point at
        // one arbitrary copy — possibly in a different deck.
        fun key(deckId: String, card: Card): String =
            deckId + "\u0001" + cardIdentity(card.type.name, card.front, card.back)

        val freshIdByIdentity = HashMap<String, String>(newCards.size * 2)
        newCards.forEach { fresh ->
            // putIfAbsent preserves the old "first copy in plan order" behaviour within a deck.
            freshIdByIdentity.putIfAbsent(key(fresh.deckId, fresh), fresh.id)
        }
        if (freshIdByIdentity.isEmpty()) return
        local.cards.forEach { card ->
            if (card.deckId !in deckKeys) return@forEach
            val freshDeckId = deckIdMap[card.deckId] ?: return@forEach
            freshIdByIdentity[key(freshDeckId, card)]
                ?.let { cardIdMap[card.id] = it }
        }
    }

    /**
     * Resolves the local parent for an imported deck.
     *
     * A parent inside the payload maps to its freshly generated id. A parent that only exists as
     * a container (an ancestor added for foreign-key completeness) is matched against the target
     * knowledge base level by level, so importing `Physics/Maths` into a library that already has
     * `Physics` re-attaches the copy instead of dumping it at the root.
     */
    private fun resolveParent(
        deck: ExportDeck,
        exportDeckById: Map<String, ExportDeck>,
        targetKbId: String,
        local: LocalIndex,
        deckIdMap: Map<String, String>,
    ): String? {
        val rawParent = deck.parentId ?: return null
        deckIdMap[rawParent]?.let { return it }

        // Walk root-wards to rebuild the container chain, then match it top-down.
        val chain = mutableListOf<ExportDeck>()
        var cursor: ExportDeck? = exportDeckById[rawParent]
        while (cursor != null && chain.size < MAX_DECK_DEPTH) {
            chain += cursor
            cursor = cursor.parentId?.let { exportDeckById[it] }
        }
        chain.reverse()

        var resolved: String? = null
        for (ancestor in chain) {
            // A point query per level: this used to scan the whole live deck list, once per
            // ancestor of every imported deck, which made re-attaching a deep payload quadratic.
            val hit = local.decksAtLevel(targetKbId, resolved, ancestor.name).firstOrNull()
            // Stop at the deepest level that exists; deeper ancestors are simply not there yet.
            if (hit == null) break
            resolved = hit.id
        }
        return resolved
    }

    /** Parents-first ordering; a parent outside the payload (a container) becomes a root. */
    private fun orderDecks(decks: List<ExportDeck>): List<ExportDeck> {
        val byId = decks.associateBy { it.id }
        val children = decks.filter { it.parentId != null }.groupBy { it.parentId }
        val pending = ArrayDeque(decks.filter { it.parentId == null || it.parentId !in byId })
        val ordered = mutableListOf<ExportDeck>()
        val seen = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val deck = pending.removeFirst()
            if (!seen.add(deck.id)) continue
            ordered += deck
            children[deck.id].orEmpty().forEach { pending.addLast(it) }
        }
        // validate() rejects cycles, but never silently lose a row if a caller skipped it.
        if (ordered.size != decks.size) ordered += decks.filter { it.id !in seen }
        return ordered
    }

    private fun normalizeName(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { trimTo(it, MAX_NAME_LENGTH) }

    /**
     * The one key function for "is this knowledge-base name taken / already present".
     *
     * Callers pass either a user-typed name (already normalized) or a name read from a file. Both
     * sides must clip identically or a long name creates a bucket nothing can ever query.
     */
    private fun kbLookupKey(name: String): String = trimTo(name.trim(), MAX_NAME_LENGTH)

    private fun trimTo(value: String, max: Int): String =
        if (value.length <= max) value else value.take(max)

    private fun uniqueName(base: String, startAt: Int = 1, isTaken: (String) -> Boolean): String {
        val clipped = trimTo(base, MAX_NAME_LENGTH)
        if (!isTaken(clipped)) return clipped
        var attempt = startAt.coerceAtLeast(1)
        while (attempt <= 10_000) {
            val suffix = " ($attempt)"
            val candidate = trimTo(clipped, MAX_NAME_LENGTH - suffix.length) + suffix
            if (!isTaken(candidate)) return candidate
            attempt++
        }
        val fallbackSuffix = " (${generateId().takeLast(8)})"
        return trimTo(clipped, MAX_NAME_LENGTH - fallbackSuffix.length) + fallbackSuffix
    }

    /**
     * The `n` in a trailing ` (n)`, or null when [name] carries no numeric suffix.
     *
     * Used only to seed the next probe position, so a name that happens to *end* in something like
     * `(2024)` must not be mistaken for a generated suffix. [base] is therefore required: the
     * suffix only counts when [name] is the clipped base followed by ` (n)`. The synthetic
     * fallback (` (ab12cd34)`) maps to null and leaves probing at the start.
     */
    private fun suffixAttempt(name: String, base: String): Int? {
        val clipped = trimTo(base, MAX_NAME_LENGTH)
        if (!name.startsWith(clipped) || !name.endsWith(")")) return null
        val suffixStart = name.length - 1
        val open = name.lastIndexOf(" (", startIndex = suffixStart - 1)
        if (open < clipped.length) return null
        return name.substring(open + 2, name.length - 1).toIntOrNull()
    }

    private fun deckKey(kbId: String, name: String): String =
        buildString(kbId.length + name.length + 16) {
            appendKeyPart(kbId)
            appendKeyPart(name.trim())
        }

    /**
     * Prefix-code one component: `length` + separator + value.
     *
     * The length prefix is what makes concatenation unambiguous. Joining with a bare separator is
     * not enough — a value containing that separator could shift the boundary and produce the same
     * string for two different tuples, silently merging two name scopes.
     */
    private fun StringBuilder.appendKeyPart(value: String): StringBuilder {
        append(value.length).append('\u0000').append(value)
        return this
    }

    private fun ExportKnowledgeBase.toDomain(id: String, name: String, now: Instant) = KnowledgeBase(
        id = id, name = name, description = description, icon = icon,
        createdAt = now, updatedAt = now, version = 1,
        deletedAt = null, syncedAt = null,
    )

    private fun ExportDeck.toDomain(
        id: String,
        knowledgeBaseId: String,
        parentId: String?,
        name: String,
        now: Instant,
    ) = Deck(
        id = id, knowledgeBaseId = knowledgeBaseId, name = name,
        description = description, color = color, icon = icon, parentId = parentId,
        createdAt = now, updatedAt = now, version = 1,
        deletedAt = null, syncedAt = null,
    )

    private fun ExportCard.toDomain(id: String, deckId: String, now: Instant) = Card(
        id = id, deckId = deckId,
        type = CardType.entries.firstOrNull { it.name == type } ?: CardType.BASIC,
        front = front, back = back, explanation = explanation, title = title, tags = tags,
        media = media, metadata = metadata,
        // A copied card starts unscheduled: the original review logs / algorithm state are not
        // part of a share export, so carrying due dates would surface a card with no state.
        createdAt = now, updatedAt = now,
        lastReviewedAt = null, nextReviewAt = null,
        version = 1, deletedAt = null, syncedAt = null,
    )
}
