package com.lumecard.app.data

import com.lumecard.app.platform.listZipArchive
import com.lumecard.app.platform.readZipArchiveEntry
import com.lumecard.app.platform.writeZipArchive
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.MediaType

/**
 * LumeCard archive format (`.lumecard`), version 1.
 *
 * ```
 * lumecard.json          the exact same JSON a plain .json export would contain
 * media/<filePath>       one entry per referenced local media file, path identical to the app's
 *                        media root so importing is an identity mapping
 * ```
 *
 * Design notes:
 * - Media uses a **flat directory** (`media/a1b2c3.png`), matching the media root. File names are
 *   content SHA-1 already, so classification subdirectories would add migration cost (rewriting
 *   every `Card.media.filePath` and invalidating the WebDAV manifest) for no real benefit.
 * - The reader nevertheless accepts **any** sub-path under `media/`, so a future layout change
 *   (e.g. `media/images/…`) will not need a format version bump.
 * - [ENTRY_MANIFEST] holds the unchanged export JSON, so an archive can be opened by any zip tool
 *   and the payload inspected or re-imported as a plain JSON file.
 */
object LumecardArchive {
    const val EXTENSION = "lumecard"
    const val MIME_TYPE = "application/zip"
    const val ENTRY_MANIFEST = "lumecard.json"
    const val MEDIA_DIR = "media/"

    /**
     * Upper bound for the manifest entry.
     *
     * `readManifest` decodes the bytes into a `String`, which doubles them in memory (UTF-16), so a
     * generous cap would be an easy out-of-memory lever. A share export of a very large knowledge
     * base is single-digit megabytes; 64 MB leaves two orders of magnitude of headroom while
     * keeping the decoded string bounded.
     */
    private const val MAX_MANIFEST_BYTES = 64L * 1024 * 1024

    /** Per-file cap for archived media, matching the loader's own download guard. */
    const val MAX_MEDIA_BYTES = 512L * 1024 * 1024

    /**
     * Local media referenced by [cards], as media-root-relative paths.
     *
     * Only `LOCAL_RELATIVE` entries are archived: absolute paths, `file://`/`content://` URIs and
     * https URLs are not files this app owns, so they are left untouched in the JSON.
     */
    fun referencedLocalMedia(cards: List<Card>): List<String> =
        cards.asSequence()
            .flatMap { it.media.asSequence() }
            .filter { it.type == MediaType.IMAGE }
            .map { it.filePath.trim() }
            .filter { isArchivableMediaPath(it) }
            .distinct()
            .toList()

    /**
     * Same classification, for export-model cards whose media list is already deserialized.
     *
     * [mediaTypes] must mirror the filter [referencedLocalMedia] applies. Callers that pass every
     * media path regardless of type will count non-image files as "missing", because the writer
     * never archived them in the first place.
     */
    fun referencedLocalMediaFrom(
        entries: List<Pair<String, String>>,
        mediaTypes: Set<String> = setOf(MediaType.IMAGE.name),
    ): List<String> =
        entries.asSequence()
            .filter { it.second.uppercase() in mediaTypes }
            .map { it.first.trim() }
            .filter { isArchivableMediaPath(it) }
            .distinct()
            .toList()

    /** Convenience overload for callers that only carry paths (assumed already filtered). */
    fun referencedLocalMediaFrom(paths: List<String>): List<String> =
        paths.asSequence().map { it.trim() }.filter { isArchivableMediaPath(it) }.distinct().toList()

    /**
     * Whether [path] denotes a file inside the media root rather than an external source.
     * Mirrors `resolveImageRef`'s classification so the archived set matches what the app loads
     * locally, and rejects traversal so an archive can never be pointed outside the root.
     */
    fun isArchivableMediaPath(path: String): Boolean {
        if (path.isBlank()) return false
        val lower = path.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return false
        if (lower.startsWith("file://") || lower.startsWith("content://")) return false
        if (lower.startsWith("data:")) return false
        if (path.startsWith("/") || Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(path)) return false
        return isSafeMediaRelativePath(path)
    }

    /** Reject any path that could escape the media root once joined to it. */
    fun isSafeMediaRelativePath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.any { it.code < 32 }) return false
        if (path.contains('\\')) return false
        // A drive prefix makes the name absolute on Windows, and `File(parent, child)` silently
        // drops the parent for an absolute child. `resolveMediaFilePath` would still catch it via
        // the canonical-path check, but rejecting it here keeps the guarantee local to the format
        // rules instead of depending on a downstream call staying in place.
        if (Regex("^[A-Za-z]:").containsMatchIn(path)) return false
        return path.split('/').all { segment ->
            segment.isNotEmpty() && segment != "." && segment != ".."
        }
    }

    /** Media entry name for an archive-relative media path. */
    fun mediaEntryName(relativePath: String): String = MEDIA_DIR + relativePath

    /** Recover the media-root-relative path from an archive entry name, or null if malformed. */
    fun mediaPathFromEntry(entryName: String): String? {
        if (!entryName.startsWith(MEDIA_DIR)) return null
        val relative = entryName.removePrefix(MEDIA_DIR)
        return relative.takeIf { it.isNotBlank() && isSafeMediaRelativePath(it) }
    }

    /**
     * Build the zip entry list for [outputPath]:
     * the manifest first, then every media file that still exists on disk.
     *
     * The manifest is always read from [manifestTempPath] — never from a separately passed string —
     * so the bytes embedded in the archive are exactly the bytes that were staged.
     *
     * Missing files are dropped here instead of failing the export — a user who deleted a media
     * file should still be able to export the rest of their knowledge base.
     */
    fun buildEntries(
        manifestTempPath: String,
        mediaFiles: List<Pair<String, String>>,
    ): List<Pair<String, String>> = buildList {
        add(ENTRY_MANIFEST to manifestTempPath)
        // A duplicate source is harmless (same content hash) but a duplicate entry name is not.
        val seen = mutableSetOf<String>()
        for ((relativePath, absolutePath) in mediaFiles) {
            if (!isSafeMediaRelativePath(relativePath)) continue
            val name = mediaEntryName(relativePath)
            if (!seen.add(name)) continue
            add(name to absolutePath)
        }
    }

    /**
     * Write [entries] to [outputPath].
     *
     * Returns the entry names actually written (write order), or `null` when the write was
     * interrupted — in which case the produced file is partial and must not be used.
     */
    suspend fun write(
        outputPath: String,
        entries: List<Pair<String, String>>,
    ): List<String>? = writeZipArchive(outputPath, entries)

    suspend fun listEntries(archivePath: String): List<String>? = listZipArchive(archivePath)

    /** Read the embedded JSON payload. Returns null when absent, oversized or unreadable. */
    suspend fun readManifest(archivePath: String): String? {
        val bytes = readZipArchiveEntry(archivePath, ENTRY_MANIFEST, MAX_MANIFEST_BYTES) ?: return null
        return try {
            bytes.decodeToString()
        } catch (_: Exception) {
            null
        }
    }

    /** Read one archived media file by its media-root-relative path. */
    suspend fun readMedia(archivePath: String, relativePath: String): ByteArray? {
        if (!isSafeMediaRelativePath(relativePath)) return null
        return readZipArchiveEntry(archivePath, mediaEntryName(relativePath), MAX_MEDIA_BYTES)
    }

    /** Cheap content sniff: a zip starts with "PK\u0003\u0004" (or an empty-archive signature). */
    fun looksLikeZip(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        if (bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return false
        // 03 04 = local file header, 05 06 = empty archive, 07 08 = spanned marker.
        return (bytes[2].toInt() and 0xFF) in 3..7
    }
}
