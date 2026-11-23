package com.lumecard.shared.data

import com.lumecard.shared.model.Deck

/**
 * Linear-time parent/child lookups over a flat deck list.
 *
 * Every "grow this seed set until nothing changes" loop in the transfer code used to be a
 * `while (changed) { for (deck in allDecks) ... }` sweep. That is O(n²) in the number of decks and
 * O(n) restarts per level: a 20 000-deck library (the documented cap, see
 * `ObjectExportBuilder.MAX_DECKS`) turned a confirmation count, an export with a re-attached
 * ancestor chain, or the post-commit deletes into seconds-to-minutes of work — and inside
 * `ObjectImportService.execute()` it ran while holding the SQLite write lock.
 *
 * Building the child index once makes every expansion a breadth-first walk: O(n + e).
 *
 * Missing parents are tolerated on purpose. Both the importer and the exporter run against live
 * rows where a parent can be a soft-deleted or foreign id; such a deck simply acts as a root for
 * [expandDescendants] and terminates the chain for [expandAncestors], which is exactly what the
 * sweep loops did too.
 *
 * Instances are immutable after construction and safe to share, but see [DeckTree] users for the
 * mutation contract: [expandDescendants] / [expandAncestors] write into the caller's seed set.
 */
internal class DeckTree private constructor(
    private val parentById: Map<String, String?>,
    private val childrenByParent: Map<String, List<String>>,
) {
    val size: Int get() = parentById.size

    fun contains(id: String): Boolean = parentById.containsKey(id)

    fun parentOf(id: String): String? = parentById[id]

    /**
     * Adds every descendant of [seeds] to it, breadth first, and returns how many were added.
     *
     * The seed set doubles as the visited set, so a corrupt parent cycle cannot spin here and a
     * deck already present (an explicit root that is also somebody's child) is never re-walked.
     */
    fun expandDescendants(seeds: MutableSet<String>): Int {
        var added = 0
        val pending = ArrayDeque<String>()
        // Pure drain: nothing below may write to `seeds` until this iteration has finished, or the
        // fail-fast iterator would throw. All writes happen inside the `while`.
        seeds.forEach { pending.addLast(it) }
        while (pending.isNotEmpty()) {
            childrenByParent[pending.removeFirst()]?.forEach { child ->
                if (seeds.add(child)) {
                    added++
                    pending.addLast(child)
                }
            }
        }
        return added
    }

    /**
     * Adds every **known** ancestor of [seeds] to it and returns how many were added.
     *
     * A parent that is not itself in the index stays out: `ObjectExportBuilder` relies on that to
     * report the dangling id as `INVALID_ID` instead of inventing a container row.
     */
    fun expandAncestors(seeds: MutableSet<String>): Int {
        var added = 0
        val pending = ArrayDeque<String>()
        seeds.forEach { pending.addLast(it) }
        while (pending.isNotEmpty()) {
            val parent = parentById[pending.removeFirst()] ?: continue
            if (parentById.containsKey(parent) && seeds.add(parent)) {
                added++
                pending.addLast(parent)
            }
        }
        return added
    }

    /**
     * Stable identity of a deck inside its knowledge base: the trimmed names of itself and every
     * ancestor, root first.
     *
     * Used to carry a learning plan's deck selection across an overwrite, where the copies get new
     * ids. Two decks at different levels can share a name, so the whole chain is part of the key.
     *
     * [nameOf] **must** resolve every id in this index. The chain ends at the first id this tree
     * does not know (a soft-deleted or foreign parent), which is the behaviour the inline walk had;
     * but a `nameOf` that is missing a key the tree *does* contain also ends the chain, silently
     * shortening the path and breaking an overwrite match. Both current call sites build the map
     * from the same list they built the tree from, so they cannot diverge.
     *
     * The chain is capped at [MAX_PATH_DEPTH] ancestor hops so a corrupt cycle cannot spin. When the
     * cap is actually hit the result is *marked*, not silently shortened: [TRUNCATED] is appended as
     * the first component. Without the marker, two decks whose chains only diverge above the cap
     * would produce the same string, and an overwrite would map both onto one copy — a learning plan
     * would then point at the wrong deck (or two plans at the same one). With it, truncated paths
     * can never equal a full path, so the worst case degrades to "no match" (the pre-indexing
     * behaviour for an unknown ancestor) instead of "wrong match".
     */
    fun pathOf(id: String, nameOf: (String) -> String?): String? {
        val ownName = nameOf(id) ?: return null
        val parts = arrayListOf(ownName.trim())
        var cursor = parentOf(id)
        var depth = 0
        var truncated = false
        while (cursor != null) {
            // Only ancestors that are actually present participate: a soft-deleted or foreign
            // parent id truncates the path here, exactly like `byId[parentId]` returning null in
            // the walk this replaced. Asking `nameOf` for an id we do not index would be a guess.
            if (!contains(cursor)) break
            if (depth >= MAX_PATH_DEPTH) {
                // Still have a real, indexed ancestor we are refusing to walk: the path is
                // incomplete, and saying so is what keeps it from colliding with another one.
                truncated = true
                break
            }
            val name = nameOf(cursor) ?: break
            parts += name.trim()
            cursor = parentOf(cursor)
            depth++
        }
        parts.reverse()
        if (truncated) {
            // A bare "was truncated" flag is not enough: two chains that diverge above the cap
            // still produce the same 65 names, and `remapLocalDeckSelections` would then map two
            // local decks onto one copy. The stopped-at id is what keeps them apart — it is the
            // nearest ancestor the walk refused to visit, which is by definition different for two
            // chains that only meet further up. Kept as one component (no separator inside) so the
            // component layout stays "names, with a marker prefix".
            parts.add(0, TRUNCATED_MARKER + (cursor ?: ""))
        }
        return parts.joinToString("\u0000")
    }

    companion object {
        /** Same cap the previous inline walks used; a cycle must not walk forever. */
        internal const val MAX_PATH_DEPTH = 64

        /**
         * Prefix of the first component of a path that hit [MAX_PATH_DEPTH]; the rest of that
         * component is the ancestor id the walk stopped at.
         *
         * Built from a control character so a deck name cannot imitate it: names are compared after
         * `trim()`, and a name containing this character is not something any UI produces. Even if
         * one did, the marker only ever prefixes *truncated* paths, so the collision it would cause
         * is with another truncated path — which the appended stopped-at id already separates.
         */
        internal const val TRUNCATED_MARKER = "\u0002truncated"

        fun of(decks: List<Deck>): DeckTree = build(decks.size) { index, sink ->
            val deck = decks[index]
            sink(deck.id, deck.parentId)
        }

        fun ofExport(decks: List<ExportDeck>): DeckTree = build(decks.size) { index, sink ->
            val deck = decks[index]
            sink(deck.id, deck.parentId)
        }

        /**
         * From raw id/parent pairs read off any row type, without materialising a parallel list.
         *
         * The service path needs this because its rows are SQLDelight projections; mapping them to
         * `Pair`s first would allocate 20 000 short-lived objects at the cap purely to be copied
         * into the index.
         */
        fun <T> ofRows(rows: List<T>, id: (T) -> String, parentId: (T) -> String?): DeckTree =
            build(rows.size) { index, sink -> sink(id(rows[index]), parentId(rows[index])) }

        private inline fun build(count: Int, each: (Int, (String, String?) -> Unit) -> Unit): DeckTree {
            val parents = HashMap<String, String?>(count * 2)
            val children = HashMap<String, MutableList<String>>()
            for (index in 0 until count) {
                each(index) { id, parentId ->
                    parents[id] = parentId
                    if (parentId != null) children.getOrPut(parentId) { ArrayList(2) } += id
                }
            }
            return DeckTree(parents, children)
        }
    }
}