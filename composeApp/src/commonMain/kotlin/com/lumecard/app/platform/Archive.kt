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

/** Read the entry names of a zip archive. Returns null when the file is missing or not a zip. */
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
 * Entries are only held in the returned map when they exist and fit under [maxBytes]; everything
 * else is simply absent. This exists because Android archives arrive through a `content://` URI,
 * which has no random access — one call here replaces N full scans of the same stream.
 */
expect suspend fun readZipArchiveEntries(
    archivePath: String,
    entryPaths: Set<String>,
    maxBytes: Long = 512L * 1024 * 1024,
): Map<String, ByteArray>?

/**
 * Read a file's leading bytes without loading the whole thing.
 *
 * Used to sniff whether an import target is a zip archive or plain JSON, which must work for both
 * absolute paths and Android `content://` URIs.
 */
expect suspend fun readFileHeader(filePath: String, byteCount: Int): ByteArray?

