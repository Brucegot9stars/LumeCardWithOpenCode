package com.lumecard.app.data

import com.lumecard.app.platform.getApkCacheDir
import com.lumecard.app.platform.getMediaBasePath
import com.lumecard.app.platform.hashBytesSha1
import com.lumecard.app.platform.platformDeleteFile
import com.lumecard.app.platform.platformIsFile
import com.lumecard.app.platform.platformMkdirs
import com.lumecard.app.platform.platformJoinPath
import com.lumecard.app.platform.platformReadFileBytes
import com.lumecard.app.platform.platformWriteFileText
import com.lumecard.app.platform.readZipArchiveEntries
import com.lumecard.app.platform.resolveMediaFilePath
import com.lumecard.app.platform.writeMediaFileAtomically
import com.lumecard.shared.model.Card
import kotlin.random.Random
import kotlin.time.Clock

/** What an export produced, so the UI can report it honestly. */
data class ExportOutcome(
    /** Absolute path or content URI that was written. */
    val path: String,
    /** True when the payload became a `.lumecard` archive rather than a bare `.json`. */
    val isArchive: Boolean,
    /** Local media files successfully stored inside the archive (0 for plain JSON). */
    val mediaIncluded: Int,
    /** Referenced media that no longer exist on disk and therefore could not be archived. */
    val mediaMissing: Int,
    /**
     * True when the archive write was interrupted midway: the half-written file was deleted and
     * the export fell back to a plain JSON. The UI must surface this rather than a plain success.
     */
    val writeFailed: Boolean = false,
)

/** Result of unpacking an archive's media into the media root. */
data class MediaImportOutcome(
    /** Media entries that were stored on disk (new files, or renamed copies on content conflict). */
    val written: Int,
    /** Media entries skipped because an identical file already existed at the target path. */
    val skipped: Int,
    /**
     * Media paths the manifest references that the archive did not contain at all.
     * Counts only "referenced but absent" — extra archive entries never land here.
     */
    val missing: Int,
    /** Media entries that were present in the archive but failed to land on disk. */
    val failed: Int = 0,
    /**
     * Rewrites to apply to `Card.media.filePath` when a name collision forced a rename.
     * Key is the path recorded in the archive, value is the path the file was actually stored as.
     */
    val renamed: Map<String, String> = emptyMap(),
    /**
     * Media paths this import created (final on-disk names, after any rename).
     *
     * Lets a caller that fails later — e.g. while inserting the cards that reference these files —
     * delete exactly what this call added without touching media that already existed.
     */
    val newlyWrittenPaths: List<String> = emptyList(),
)

/**
 * True for Android document URIs (`content://…`, `file://…`), which are resolver handles rather
 * than filesystem paths and cannot be manipulated with string concatenation.
 */
internal fun isDocumentUri(path: String): Boolean {
    val lower = path.lowercase()
    return lower.startsWith("content://") || lower.startsWith("file://")
}

/**
 * Derive the plain-JSON fallback path for a user-chosen export path.
 *
 * Only the **file name** extension is replaced: `substringBeforeLast('.')` would also match a dot
 * in a directory name (`/a.b/export` must become `/a.b/export.json`, not `/a.json`). A leading dot
 * (`.hidden`) is treated as part of the name, never as an extension separator.
 */
fun jsonSiblingPath(chosenPath: String): String {
    if (chosenPath.endsWith(".json", ignoreCase = true)) return chosenPath
    // A `content://` document URI is not a path: appending ".json" to the document-id segment
    // yields a URI no provider resolves. Return it untouched and let the archive branch (which
    // writes through the resolver) be the only Android outcome.
    if (isDocumentUri(chosenPath)) return chosenPath
    val separator = maxOf(chosenPath.lastIndexOf('/'), chosenPath.lastIndexOf('\\'))
    val directory = if (separator >= 0) chosenPath.substring(0, separator + 1) else ""
    val fileName = if (separator >= 0) chosenPath.substring(separator + 1) else chosenPath
    val dot = fileName.lastIndexOf('.')
    val stem = if (dot > 0) fileName.substring(0, dot) else fileName
    return directory + stem + ".json"
}

/**
 * Upper bound on media entries unpacked from one archive.
 *
 * A real export is bounded by the user's own media usage; a hand-built archive can hold hundreds of
 * thousands of tiny entries, each of which is read into memory. Ten thousand is far above any
 * legitimate knowledge base and low enough that the work stays bounded.
 */
private const val MAX_ARCHIVE_ENTRIES = 10_000

/**
 * Unique-enough name for a staging file. Two exports running at once must not overwrite each
 * other's manifest, and a plain timestamp can collide within the same millisecond.
 */
private fun stagingFileName(): String =
    "manifest-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(0, 1_000_000)}.json"

/**
 * Collect the local media files referenced by [cards] and package them with [manifestJson].
 *
 * Returns a plain JSON export (no archive) when nothing local is referenced, which keeps text-only
 * knowledge bases as a single readable, diff-friendly file.
 *
 * Media files that are referenced but missing on disk are counted in [ExportOutcome.mediaMissing]
 * and simply omitted — an export must never fail because one file was deleted.
 */
suspend fun packageExport(
    manifestJson: String,
    cards: List<Card>,
    outputPath: String,
    jsonOutputPath: String,
): ExportOutcome {
    val referenced = LumecardArchive.referencedLocalMedia(cards)
    if (referenced.isEmpty()) {
        platformWriteFileText(jsonOutputPath, manifestJson)
        return ExportOutcome(jsonOutputPath, isArchive = false, mediaIncluded = 0, mediaMissing = 0)
    }

    val mediaRoot = getMediaBasePath()
    val available = mutableListOf<Pair<String, String>>()
    var missing = 0
    for (relativePath in referenced) {
        val absolute = try {
            resolveMediaFilePath(mediaRoot, relativePath)
        } catch (_: Exception) {
            missing++
            continue
        }
        if (platformIsFile(absolute)) {
            available.add(relativePath to absolute)
        } else {
            missing++
        }
    }

    // Nothing survived: emitting an archive with an empty media/ dir would imply files that are
    // not there, so fall back to the plain JSON and report the misses.
    if (available.isEmpty()) {
        platformWriteFileText(jsonOutputPath, manifestJson)
        return ExportOutcome(jsonOutputPath, isArchive = false, mediaIncluded = 0, mediaMissing = missing)
    }

    // Stage the manifest in the app's own cache directory, under a name unique to this call.
    // Deriving it from [outputPath] breaks on Android, where the picker hands back a `content://`
    // document URI: the "parent" is not a directory, the manifest write is silently swallowed, the
    // zip entry is skipped and the user receives an archive that cannot be imported while the UI
    // claims success. The media root is avoided too, because a leftover `lumecard.json` there would
    // be picked up by the media scan.
    val stagingRoot = platformJoinPath(getApkCacheDir().absolutePath, "export-staging")
    platformMkdirs(stagingRoot)
    val manifestTemp = platformJoinPath(stagingRoot, stagingFileName())
    platformWriteFileText(manifestTemp, manifestJson)
    val entries = LumecardArchive.buildEntries(manifestTemp, available)
    val writtenEntries = LumecardArchive.write(outputPath, entries)
    val manifestWritten = writtenEntries != null &&
        writtenEntries.any { it == LumecardArchive.ENTRY_MANIFEST }
    if (writtenEntries == null || !manifestWritten) {
        // Either the write aborted, or the manifest entry was skipped because the staging file was
        // missing. Both leave an archive without its payload, so treat them identically.
        platformDeleteFile(outputPath)
        platformDeleteFile(manifestTemp)
        platformWriteFileText(jsonOutputPath, manifestJson)
        return ExportOutcome(
            jsonOutputPath,
            isArchive = false,
            mediaIncluded = 0,
            mediaMissing = missing,
            writeFailed = true,
        )
    }
    platformDeleteFile(manifestTemp)

    // Count by entry name: the manifest is always first, so `size - 1` would silently depend on
    // that ordering surviving every write.
    val mediaWritten = writtenEntries.count { it.startsWith(LumecardArchive.MEDIA_DIR) }
    if (mediaWritten == 0) {
        // Media existed a moment ago but none reached the archive (files removed between the
        // availability check and the write). Reporting a plain "success" here would tell the user
        // their images were exported when none were.
        platformDeleteFile(outputPath)
        platformWriteFileText(jsonOutputPath, manifestJson)
        return ExportOutcome(
            jsonOutputPath,
            isArchive = false,
            mediaIncluded = 0,
            mediaMissing = missing,
            writeFailed = true,
        )
    }
    return ExportOutcome(outputPath, isArchive = true, mediaIncluded = mediaWritten, mediaMissing = missing)
}

/**
 * Import archived media into the media root.
 *
 * Collision policy: media file names are content SHA-1, so a name that already exists *should*
 * have identical content. When it does not (corrupt or hand-made archive) the incoming file is
 * stored under a suffixed name and the mapping is returned in [MediaImportOutcome.renamed] so the
 * caller can rewrite the card references instead of silently pointing two cards at one image.
 *
 * Entries are validated with [LumecardArchive.mediaPathFromEntry], so a crafted archive containing
 * `media/../../evil` can never write outside the media root.
 *
 * @param referencedPaths the local media paths the manifest/cards actually reference. [MediaImportOutcome.missing]
 *   is computed against this list ("referenced but absent from the archive"), not against whatever
 *   the archive happens to contain — an extra, unreferenced entry must not inflate the count.
 */
suspend fun importArchiveMedia(
    archivePath: String,
    entryNames: List<String>,
    referencedPaths: List<String> = emptyList(),
): MediaImportOutcome {
    val mediaRoot = getMediaBasePath()
    platformMkdirs(mediaRoot)

    // Deduplicate: a zip may legally contain the same entry name twice (the second one shadows the
    // first for some readers, the first one wins for others). Processing both would double-count the
    // outcome and, worse, make `written`/`skipped` disagree with what actually landed on disk.
    val archived = entryNames.mapNotNull { LumecardArchive.mediaPathFromEntry(it) }.distinct()
    val archivedSet = archived.toSet()
    val missing = referencedPaths.filter { it !in archivedSet }.size

    if (archived.isEmpty()) {
        return MediaImportOutcome(written = 0, skipped = 0, missing = missing)
    }

    // Cap the work an archive can demand. Every entry is read into memory, so an archive with
    // hundreds of thousands of tiny entries would otherwise be a cheap denial of service; the
    // overflow is reported as failed rather than silently ignored.
    val accepted = if (archived.size <= MAX_ARCHIVE_ENTRIES) {
        archived
    } else {
        archived.take(MAX_ARCHIVE_ENTRIES)
    }
    val overflow = archived.size - accepted.size

    var written = 0
    var skipped = 0
    var failed = overflow
    val renamed = mutableMapOf<String, String>()
    val newlyWritten = mutableListOf<String>()

    // One pass over the archive instead of one read per entry: on Android the archive is a
    // `content://` stream with no random access, so per-entry reads would rescan it N times.
    val wanted = accepted.map { LumecardArchive.mediaEntryName(it) }.toSet()
    val payloads = readZipArchiveEntries(archivePath, wanted, LumecardArchive.MAX_MEDIA_BYTES)
        ?: emptyMap()

    for (relativePath in accepted) {
        val bytes = payloads[LumecardArchive.mediaEntryName(relativePath)]
        if (bytes == null) {
            // Present in the entry list but unreadable: the file did not land on disk.
            failed++
            continue
        }
        val target = try {
            resolveMediaFilePath(mediaRoot, relativePath)
        } catch (_: Exception) {
            failed++
            continue
        }

        if (platformIsFile(target)) {
            val existing = platformReadFileBytes(target)
            if (existing != null && hashBytesSha1(existing) == hashBytesSha1(bytes)) {
                skipped++
                continue
            }
            // Same name, different content: keep both rather than overwriting the user's file.
            val uniquePath = uniqueMediaPath(relativePath, bytes, mediaRoot)
            val uniqueTarget = try {
                resolveMediaFilePath(mediaRoot, uniquePath)
            } catch (_: Exception) {
                failed++
                continue
            }
            try {
                writeMediaFileAtomically(uniqueTarget, bytes)
                renamed[relativePath] = uniquePath
                newlyWritten.add(uniquePath)
                written++
            } catch (_: Exception) {
                // Unwritable target: counted as failed, never as "missing from the archive".
                failed++
            }
            continue
        }

        try {
            writeMediaFileAtomically(target, bytes)
            newlyWritten.add(relativePath)
            written++
        } catch (_: Exception) {
            failed++
        }
    }

    return MediaImportOutcome(
        written = written,
        skipped = skipped,
        missing = missing,
        failed = failed,
        renamed = renamed,
        newlyWrittenPaths = newlyWritten,
    )
}

/**
 * Look up the post-import path for [filePath], tolerating the whitespace and case noise a
 * hand-edited export may carry. Returns null when no rename applies.
 */
fun resolveRenamedPath(renamed: Map<String, String>, filePath: String): String? {
    if (renamed.isEmpty()) return null
    renamed[filePath]?.let { return it }
    val trimmed = filePath.trim()
    renamed[trimmed]?.let { return it }
    val lower = trimmed.lowercase()
    return renamed.entries.firstOrNull { it.key.trim().lowercase() == lower }?.value
}

/** Derive a non-colliding media name: `<stem>_<shorthash>.<ext>`. */
private fun uniqueMediaPath(relativePath: String, bytes: ByteArray, mediaRoot: String): String {
    val dot = relativePath.lastIndexOf('.')
    val stem = if (dot > 0) relativePath.substring(0, dot) else relativePath
    val extension = if (dot > 0) relativePath.substring(dot) else ""
    val shortHash = hashBytesSha1(bytes).take(8)
    var candidate = "${stem}_$shortHash$extension"
    var attempt = 2
    while (true) {
        val absolute = try {
            resolveMediaFilePath(mediaRoot, candidate)
        } catch (_: Exception) {
            return candidate
        }
        if (!platformIsFile(absolute)) return candidate
        candidate = "${stem}_${shortHash}_$attempt$extension"
        attempt++
        if (attempt > 100) return candidate
    }
}
