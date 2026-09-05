package com.lumecard.shared.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

@Serializable
data class FontManifestEntry(
    val id: String,
    val fileName: String,
    val version: Long,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)

fun generateUuid(): String {
    val hex = "0123456789abcdef"
    val segments = intArrayOf(8, 4, 4, 4, 12)
    return segments.joinToString("-") { len ->
        (1..len).map { hex[Random.nextInt(hex.length)] }.joinToString("")
    }
}

fun generateId(prefix: String = ""): String {
    val uuid = generateUuid().take(8)
    return if (prefix.isEmpty()) uuid else "${prefix}_$uuid"
}

fun mergeFontEntries(
    local: List<FontManifestEntry>,
    remote: List<FontManifestEntry>,
): List<FontManifestEntry> {
    val localMap = local.associateBy { it.id }
    val remoteMap = remote.associateBy { it.id }
    return (localMap.keys + remoteMap.keys).mapNotNull { id ->
        val l = localMap[id]
        val r = remoteMap[id]
        when {
            l != null && r == null -> l
            l == null && r != null -> r
            l != null && r != null -> if (r.version > l.version) r else l
            else -> null
        }
    }
}

private val manifestJson = Json { ignoreUnknownKeys = true }

suspend fun SyncManager.downloadFontManifestEntries(config: WebDavConfig): Result<List<FontManifestEntry>> {
    val result = downloadFontManifest(config)
    return if (result.isFailure) {
        Result.success(emptyList())
    } else {
        try {
            val json = result.getOrThrow()
            // Try new format first
            val entries = try {
                manifestJson.decodeFromString<List<FontManifestEntry>>(json)
            } catch (_: Exception) {
                // Backward compat: old format was List<String>
                val oldNames = manifestJson.decodeFromString<List<String>>(json)
                oldNames.map { name ->
                    val baseId = name.substringBeforeLast(".")
                        .lowercase().replace(Regex("[^a-z0-9_]"), "_")
                    FontManifestEntry(
                        id = baseId.ifEmpty { generateUuid() },
                        fileName = name,
                        version = 1,
                        createdAt = kotlin.time.Clock.System.now().toString(),
                        updatedAt = kotlin.time.Clock.System.now().toString(),
                    )
                }
            }
            Result.success(entries)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }
}

suspend fun SyncManager.uploadFontManifestEntries(config: WebDavConfig, entries: List<FontManifestEntry>): Result<Unit> {
    return uploadFontManifest(config, manifestJson.encodeToString(entries))
}
