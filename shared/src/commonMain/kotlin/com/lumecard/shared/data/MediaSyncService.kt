package com.lumecard.shared.data

/** Keep file transfer order testable and independent of platform filesystem APIs. */
class MediaSyncService(private val syncManager: SyncManager, private val mediaManager: MediaManager) {
    private suspend fun remoteManifest(config: WebDavConfig): MediaManifest {
        val content = syncManager.downloadManifest(config).getOrNullIfNotFound()
            ?: return MediaManifest()
        return checkNotNull(mediaManager.manifestFromJson(content)) { "Invalid remote media manifest" }
            .also { validate(it) }
    }

    private fun validate(manifest: MediaManifest) {
        require(manifest.version == 1L) { "Unsupported media manifest version" }
        require(manifest.entries.map { it.path }.toSet().size == manifest.entries.size) { "Duplicate media paths" }
        manifest.entries.forEach {
            validateMediaPath(it.path)
            require(it.size >= 0L && it.hash.matches(Regex("[0-9a-fA-F]{40}"))) { "Invalid media metadata" }
        }
    }

    suspend fun upload(
        config: WebDavConfig,
        local: MediaManifest,
        readFile: suspend (String) -> ByteArray,
        hashBytes: (ByteArray) -> String,
    ) {
        validate(local)
        // Always compare against the OLD remote manifest, before publishing any replacement.
        val remote = remoteManifest(config)
        val toUpload = mediaManager.filesToUpload(local.entries, remote).toSet()
        local.entries.filter { it.path in toUpload }.forEach { entry ->
            val bytes = readFile(entry.path)
            check(bytes.size.toLong() == entry.size && hashBytes(bytes).equals(entry.hash, ignoreCase = true)) {
                "Media changed during upload: ${entry.path}"
            }
            syncManager.uploadMedia(config, entry.path, bytes).getOrThrow()
        }
        // Keep remote-only files: older cloud backups may still reference them.
        val merged = remote.entries.associateBy { it.path }.toMutableMap()
        local.entries.forEach { merged[it.path] = it }
        syncManager.uploadManifest(config, mediaManager.manifestToJson(MediaManifest(entries = merged.values.toList()))).getOrThrow()
    }

    suspend fun download(
        config: WebDavConfig,
        local: MediaManifest,
        writeFile: suspend (String, ByteArray) -> Unit,
        hashBytes: (ByteArray) -> String,
    ) {
        validate(local)
        val remote = remoteManifest(config)
        val toDownload = mediaManager.filesToUpload(remote.entries, local).toSet()
        remote.entries.filter { it.path in toDownload }.forEach { entry ->
            val bytes = syncManager.downloadMedia(config, entry.path).getOrThrow()
            check(bytes.size.toLong() == entry.size && hashBytes(bytes).equals(entry.hash, ignoreCase = true)) {
                "Corrupt downloaded media: ${entry.path}"
            }
            writeFile(entry.path, bytes)
        }
        // Download-only operations must never write the cloud manifest.
    }
}

internal fun validateMediaPath(path: String) {
    require(path.isNotBlank() && path.none { it.code < 32 || it in "\\:*?\"<>|#%" }) { "Unsafe media path" }
    require(path.split('/').all { it.isNotEmpty() && it != "." && it != ".." && !it.endsWith('.') && !it.endsWith(' ') }) {
        "Unsafe media path"
    }
}
