package com.lumecard.app.data

import com.lumecard.app.platform.getApkCacheDir
import com.lumecard.app.platform.MAX_ARCHIVE_ENTRIES
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
import com.lumecard.shared.data.MediaPathRules
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
     * True when the archive write was interrupted midway: the half-written file was removed and
     * the export fell back to a plain JSON. The UI must surface this rather than a plain success.
     */
    val writeFailed: Boolean = false,
    /**
     * True when the plain-JSON fallback could not be written either — [path] then points at a file
     * that does not exist. Kept separate from [writeFailed] so the message can stop claiming "a
     * JSON was written instead" when no file landed at all.
     */
    val plainJsonWriteFailed: Boolean = false,
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

/** Import-side caps: a card image is never legitimately this large, and the sum is the real risk. */
private const val MAX_IMPORT_MEDIA_BYTES = 64L * 1024 * 1024
private const val MAX_IMPORT_MEDIA_TOTAL_BYTES = 1024L * 1024 * 1024

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
        val ok = platformWriteFileText(jsonOutputPath, manifestJson)
        return ExportOutcome(
            jsonOutputPath, isArchive = false, mediaIncluded = 0, mediaMissing = 0,
            plainJsonWriteFailed = !ok,
        )
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
        val ok = platformWriteFileText(jsonOutputPath, manifestJson)
        return ExportOutcome(
            jsonOutputPath, isArchive = false, mediaIncluded = 0, mediaMissing = missing,
            plainJsonWriteFailed = !ok,
        )
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
    // A failed staging write may leave a *partial* file that still looks like a valid source.
    // Only a confirmed write may become the archive's manifest entry.
    val staged = platformWriteFileText(manifestTemp, manifestJson)
    val entries = if (staged) LumecardArchive.buildEntries(manifestTemp, available) else emptyList()
    val writtenEntries = if (staged) LumecardArchive.write(outputPath, entries) else null
    val manifestWritten = writtenEntries != null &&
        writtenEntries.any { it == LumecardArchive.ENTRY_MANIFEST }
    if (writtenEntries == null || !manifestWritten) {
        // Either the write aborted, or the manifest entry was skipped because the staging file was
        // missing. Both leave an archive without its payload, so treat them identically.
        //
        // Delete the partial archive only when it is a real path. On Android [jsonSiblingPath]
        // returns the same `content://` URI, and deleting that document would make the fallback
        // write target a URI that no longer exists — the user would end up with neither file.
        if (!isDocumentUri(outputPath)) platformDeleteFile(outputPath)
        platformDeleteFile(manifestTemp)
        val ok = writeJsonFallback(jsonOutputPath, manifestJson)
        return ExportOutcome(
            jsonOutputPath,
            isArchive = false,
            mediaIncluded = 0,
            mediaMissing = missing,
            writeFailed = true,
            plainJsonWriteFailed = !ok,
        )
    }
    platformDeleteFile(manifestTemp)

    // Count by entry name: the manifest is always first, so `size - 1` would silently depend on
    // that ordering surviving every write.
    val mediaWritten = writtenEntries.count { it.startsWith(LumecardArchive.MEDIA_DIR) }
    // A source can disappear between the availability check and the write; `writeZipArchive`
    // skips it without reporting. Those files are missing from the export just as surely as the
    // ones that were already absent, so do not let them vanish from the count.
    val missingAtWrite = (available.size - mediaWritten).coerceAtLeast(0)
    if (mediaWritten == 0) {
        // Media existed a moment ago but none reached the archive (files removed between the
        // availability check and the write). Reporting a plain "success" here would tell the user
        // their images were exported when none were.
        if (!isDocumentUri(outputPath)) platformDeleteFile(outputPath)
        val ok = writeJsonFallback(jsonOutputPath, manifestJson)
        return ExportOutcome(
            jsonOutputPath,
            isArchive = false,
            mediaIncluded = 0,
            mediaMissing = missing + missingAtWrite,
            writeFailed = true,
            plainJsonWriteFailed = !ok,
        )
    }
    return ExportOutcome(
        outputPath, isArchive = true, mediaIncluded = mediaWritten,
        mediaMissing = missing + missingAtWrite,
    )
}

/**
 * Write the plain-JSON fallback, reporting whether it actually landed.
 *
 * Try the write first: on Android `jsonSiblingPath` hands back the very same `content://` URI and
 * `openOutputStream(uri, "wt")` truncates in place, which is exactly what replacing a partial
 * archive needs. Only a plain path can be deleted and retried, which covers the desktop case where
 * a stale read-only file blocks the write.
 */
private fun writeJsonFallback(jsonOutputPath: String, manifestJson: String): Boolean {
    if (platformWriteFileText(jsonOutputPath, manifestJson)) return true
    if (isDocumentUri(jsonOutputPath)) return false
    if (!platformDeleteFile(jsonOutputPath)) return false
    return platformWriteFileText(jsonOutputPath, manifestJson)
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
 *   Pass `null` only when the caller genuinely does not know what it needs (legacy callers); an
 *   explicit empty list means "nothing is needed", and nothing is extracted.
 */
suspend fun importArchiveMedia(
    archivePath: String,
    entryNames: List<String>,
    referencedPaths: List<String>? = null,
): MediaImportOutcome {
    val mediaRoot = getMediaBasePath()
    platformMkdirs(mediaRoot)

    // Deduplicate: a zip may legally contain the same entry name twice (the second one shadows the
    // first for some readers, the first one wins for others). Processing both would double-count the
    // outcome and, worse, make `written`/`skipped` disagree with what actually landed on disk.
    val allArchived = entryNames.mapNotNull { LumecardArchive.mediaPathFromEntry(it) }.distinct()
    val archivedKeys = allArchived.map { it.trim().lowercase() }.toSet()
    val referencedKeys = referencedPaths?.map { it.trim().lowercase() }?.toSet()
    val missing = referencedPaths.orEmpty().count { it.trim().lowercase() !in archivedKeys }

    // Write to the *manifest* spelling: otherwise the file lands under a name no card references
    // and the image is silently broken on a case-sensitive filesystem. Match the exact spelling
    // first; a case-insensitive fallback is only safe when it identifies exactly one manifest name.
    // Folding every name into one `lowercase` key would silently merge `A.png` and `a.png` (two
    // distinct files on Linux) and make both cards point at whichever bytes happened to arrive last.
    val manifestNames = referencedPaths.orEmpty().map { it.trim() }.distinct()
    val referencedByExact = manifestNames.associateBy { it }
    val referencedByFolded = manifestNames.groupBy { it.lowercase() }

    // Only files the manifest actually references are written. An archive may legally carry extra
    // `media/` entries; extracting them would let a small file fill the disk with data no card
    // can ever use, and nothing would clean it up afterwards.
    val referencedOnly = if (referencedKeys == null) {
        allArchived
    } else {
        allArchived.filter { it.trim().lowercase() in referencedKeys }
    }

    if (referencedOnly.isEmpty()) {
        return MediaImportOutcome(written = 0, skipped = 0, missing = missing)
    }

    // Cap the work an archive can demand. Every entry is read into memory, so an archive with
    // hundreds of thousands of tiny entries would otherwise be a cheap denial of service; the
    // overflow is reported as failed rather than silently ignored.
    val accepted = if (referencedOnly.size <= MAX_ARCHIVE_ENTRIES) {
        referencedOnly
    } else {
        referencedOnly.take(MAX_ARCHIVE_ENTRIES)
    }
    val overflow = referencedOnly.size - accepted.size

    var written = 0
    var skipped = 0
    var failed = overflow
    var bytesWritten = 0L
    val renamed = mutableMapOf<String, String>()
    val newlyWritten = mutableListOf<String>()

    // One pass over the archive instead of one read per entry: on Android the archive is a
    // `content://` stream with no random access, so per-entry reads would rescan it N times.
    //
    // Both budgets go down into the reader. The returned map is fully resident in memory, so
    // checking the total afterwards would mean allocating up to `entries × 64 MB` (10 000 × 64 MB)
    // before rejecting it — the check has to happen while reading, not after.
    val wanted = accepted.map { LumecardArchive.mediaEntryName(it) }.toSet()
    val payloads = readZipArchiveEntries(
        archivePath,
        wanted,
        MAX_IMPORT_MEDIA_BYTES,
        MAX_IMPORT_MEDIA_TOTAL_BYTES,
    ) ?: emptyMap()

    for (archivedPath in accepted) {
        val bytes = payloads[LumecardArchive.mediaEntryName(archivedPath)]
        if (bytes == null) {
            // Present in the entry list but unreadable: the file did not land on disk.
            failed++
            continue
        }
        // A second, independent budget: entry count alone cannot bound total footprint because a
        // single entry may be megabytes. Kept as a belt-and-braces check next to the reader's own.
        if (bytesWritten + bytes.size > MAX_IMPORT_MEDIA_TOTAL_BYTES) {
            failed++
            continue
        }
        // Land the file under the spelling the manifest uses, not the archive's. An ambiguous
        // case-only match cannot be resolved without guessing, so keep the archive's own spelling
        // rather than merging two different files into one.
        val trimmedArchived = archivedPath.trim()
        val relativePath = referencedByExact[trimmedArchived]
            ?: referencedByFolded[trimmedArchived.lowercase()]?.singleOrNull()
            ?: archivedPath
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
            if (uniquePath == null) {
                // Every collision-free candidate is occupied: never fall back to a name that could
                // overwrite the existing file. Report the entry as failed instead.
                failed++
                continue
            }
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
                bytesWritten += bytes.size
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
            bytesWritten += bytes.size
            written++
        } catch (_: Exception) {
            failed++
        }
    }

    // Several manifest spellings may differ only by case. Downstream lookups allow a
    // case-insensitive fallback, which would point the card that kept its own name at a
    // *different* file after its case twin was renamed. Pin an identity entry for every
    // such spelling that was not renamed, so exact matching wins for those cards.
    manifestNames.groupBy { it.lowercase() }
        .values
        .filter { it.size > 1 }
        .flatten()
        .forEach { name -> renamed.putIfAbsent(name, name) }

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
    // Several case-only keys can mean different files; picking the first would silently repoint a
    // card at the wrong image. Match the new export builder's behaviour: only a unique candidate.
    return renamed.entries.groupBy { it.key.trim().lowercase() }[lower]?.singleOrNull()?.value
}

/** Derive a non-colliding media name: `<stem>_<shorthash>.<ext>`, or null when all candidates are taken. */
private fun uniqueMediaPath(relativePath: String, bytes: ByteArray, mediaRoot: String): String? {
    val dot = relativePath.lastIndexOf('.')
    val stem = if (dot > 0) relativePath.substring(0, dot) else relativePath
    val extension = if (dot > 0) relativePath.substring(dot) else ""
    val shortHash = hashBytesSha1(bytes).take(8)

    // The suffix makes the name longer. A stem already at the segment limit therefore produces a
    // path the validator rejects — and that rejection happens *after* the row is written, surfacing
    // as an unrelated INVALID_ID for the whole import. Offer a short flat name as the last resort.
    val candidates = buildList {
        add("${stem}_$shortHash$extension")
        for (attempt in 2..100) add("${stem}_${shortHash}_$attempt$extension")
        add("$shortHash$extension")
    }
    for (candidate in candidates) {
        if (!MediaPathRules.isSafeRelativePath(candidate)) continue
        val absolute = try {
            resolveMediaFilePath(mediaRoot, candidate)
        } catch (_: Exception) {
            continue
        }
        if (!platformIsFile(absolute)) return candidate
    }
    return null
}
