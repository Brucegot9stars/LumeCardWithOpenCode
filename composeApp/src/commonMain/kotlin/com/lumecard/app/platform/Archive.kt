package com.lumecard.app.platform

/**
 * Write a zip archive to [outputPath].
 *
 * [entries] maps an archive-relative path (always '/'-separated) to the local source file that
 * should be stored under it. On Android [outputPath] may be a `content://` URI returned by the
 * system save dialog, on Desktop it is an absolute path.
 *
 * Returns the archive-relative names of the entries actually written, in write order, or `null`
 * when the write was interrupted by an exception. A `null` result means the target is a **partial
 * file that must not be treated as usable** — callers are expected to delete it and fall back.
 * Sources that no longer exist are skipped rather than aborting the whole archive, so one missing
 * media file cannot destroy an export; they simply do not appear in the returned list.
 */
expect suspend fun writeZipArchive(outputPath: String, entries: List<Pair<String, String>>): List<String>?

/**
 * Hard cap on the entry names one archive may expose.
 *
 * Listing happens *before* the importer can filter down to the media it needs, so a hand-built
 * archive with millions of tiny entries would otherwise be fully materialised on both platforms.
 * Ten thousand is the *import-side* budget for referenced media. Listing has its own, larger cap:
 * [MAX_ARCHIVE_LIST_ENTRIES] counts every non-directory entry (manifest and extras included), and
 * `listZipArchive` returns null past it, which the loader reports as a read failure.
 */
const val MAX_ARCHIVE_ENTRIES = 10_000

/**
 * The listing cap counts *all* non-directory entries, while the importer's cap counts only media
 * the manifest references. A legal archive may therefore carry [MAX_ARCHIVE_ENTRIES] referenced
 * media plus the manifest and arbitrary extra files. Allow twice the media budget here: still
 * bounded (a few tens of thousands of names), but no boundary-case rejection of a valid export.
 */
internal const val MAX_ARCHIVE_LIST_ENTRIES = MAX_ARCHIVE_ENTRIES * 2

/** Read the entry names of a zip archive. Returns null when the file is missing, not a zip, or over [MAX_ARCHIVE_LIST_ENTRIES]. */
expect suspend fun listZipArchive(archivePath: String): List<String>?

/**
 * Read one entry from a zip archive.
 *
 * Returns null when the entry is absent, exceeds [maxBytes] (a guard against a malicious or
 * corrupt archive trying to exhaust memory), or cannot be read.
 *
 * Prefer [readZipArchiveEntries] when more than a couple of entries are needed: on Android a
 * `content://` archive has no random access, so every single-entry read rescans the whole stream.
 */
expect suspend fun readZipArchiveEntry(archivePath: String, entryPath: String, maxBytes: Long = 512L * 1024 * 1024): ByteArray?

/**
 * Read several entries in one pass.
 *
 * Entries are only held in the returned map when they exist, fit under [maxBytes] **and** the
 * running total of everything already held stays under [maxTotalBytes]. Both budgets are enforced
 * while reading, not afterwards, because the whole map is resident in memory: `entries × maxBytes`
 * (10 000 × 64 MB) would be allocated in full before any caller could check the total.
 *
 * This exists because Android archives arrive through a `content://` URI, which has no random
 * access — one call here replaces N full scans of the same stream.
 */
expect suspend fun readZipArchiveEntries(
    archivePath: String,
    entryPaths: Set<String>,
    maxBytes: Long = 512L * 1024 * 1024,
    maxTotalBytes: Long = Long.MAX_VALUE,
): Map<String, ByteArray>?

/**
 * Read a file's leading bytes without loading the whole thing.
 *
 * Used to sniff whether an import target is a zip archive or plain JSON, which must work for both
 * absolute paths and Android `content://` URIs.
 */
expect suspend fun readFileHeader(filePath: String, byteCount: Int): ByteArray?

