package com.lumecard.shared.data

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

class MediaManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun manifestToJson(manifest: MediaManifest): String {
        return json.encodeToString(MediaManifest.serializer(), manifest)
    }

    fun manifestFromJson(jsonString: String): MediaManifest? {
        return try {
            json.decodeFromString(MediaManifest.serializer(), jsonString)
        } catch (_: Exception) { null }
    }

    /** Which remote files are missing or changed locally. */
    fun diffLocalVsRemote(
        local: MediaManifest,
        remote: MediaManifest
    ): List<String> {
        val remoteMap = remote.entries.associateBy { it.path }
        return local.entries.filter { localEntry ->
            val remoteEntry = remoteMap[localEntry.path]
            remoteEntry == null ||
            remoteEntry.size != localEntry.size ||
            remoteEntry.hash != localEntry.hash
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
}
