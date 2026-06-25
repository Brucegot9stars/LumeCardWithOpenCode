package com.lumecard.shared.data

import com.lumecard.shared.repository.MediaCacheRepository
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MediaManifestEntry(
    val path: String,
    val size: Long,
    val hash: String
)

@Serializable
data class MediaManifest(
    val version: Long = 1,
    val entries: List<MediaManifestEntry> = emptyList()
)

class MediaManager(
    private val cacheRepository: MediaCacheRepository
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun manifestToJson(manifest: MediaManifest): String {
        return json.encodeToString(MediaManifest.serializer(), manifest)
    }

    fun manifestFromJson(jsonString: String): MediaManifest? {
        return try {
            json.decodeFromString(MediaManifest.serializer(), jsonString)
        } catch (_: Exception) { null }
    }

    /** Check cache: returns cached SHA-1 if mtime matches, null otherwise. */
    suspend fun getCachedHash(path: String, mtime: Long): String? {
        val entry = cacheRepository.get(path) ?: return null
        return if (entry.mtime == mtime) entry.sha1 else null
    }

    /** Update cache with a freshly scanned file. */
    suspend fun updateCache(path: String, mtime: Long, sha1: String) {
        cacheRepository.set(path, mtime, sha1)
    }

    /** Mark files as synced in cache. */
    suspend fun markCachedSynced(paths: List<String>, syncedAt: Instant) {
        paths.forEach { cacheRepository.set(it, 0L, "", syncedAt) }
    }

    /** Which local files are missing or changed vs remote manifest. */
    fun filesToUpload(
        local: List<MediaManifestEntry>,
        remote: MediaManifest?
    ): List<String> {
        val remoteMap = remote?.entries?.associateBy { it.path } ?: emptyMap()
        return local.filter { entry ->
            val remoteEntry = remoteMap[entry.path]
            remoteEntry == null || remoteEntry.size != entry.size || remoteEntry.hash != entry.hash
        }.map { it.path }
    }

    /** Which remote files are not present locally. */
    fun filesMissingLocally(
        local: MediaManifest,
        remote: MediaManifest
    ): List<String> {
        val localPaths = local.entries.map { it.path }.toSet()
        return remote.entries.map { it.path }.filter { it !in localPaths }
    }

    /** Keep backward compat: same as filesToUpload. */
    fun diffLocalVsRemote(
        local: MediaManifest,
        remote: MediaManifest
    ): List<String> = filesToUpload(local.entries, remote)
}
