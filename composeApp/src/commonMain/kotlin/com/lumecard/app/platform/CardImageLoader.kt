package com.lumecard.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Media
import com.lumecard.shared.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Where a card image comes from. */
enum class ImageSourceKind { LOCAL_RELATIVE, LOCAL_URI, NETWORK }

/** Which card face an image belongs to (card-level, from metadata["mediaTarget"]). */
enum class MediaTarget { FRONT, BACK, BOTH }

/** A resolved, renderable reference to one card image. */
data class ImageRef(
    val mediaId: String,
    val source: String,
    val kind: ImageSourceKind,
    val target: MediaTarget,
    val caption: String,
    /** Stable key for remember()/caching; equal for the same source. */
    val cacheKey: String = source
)

/** Typed reason for an image load failure; each maps to a localised string in the UI. */
enum class FailureReason { HTTPS_ONLY, LOAD_FAILED, DECODE_FAILED }

sealed interface ImageLoadState {
    data object Loading : ImageLoadState
    data class Success(val bitmap: ImageBitmap) : ImageLoadState
    data class Failure(val reason: FailureReason) : ImageLoadState
}

/** Metadata key for the card-level image target. */
const val MEDIA_TARGET_KEY = "mediaTarget"

/** Max bytes accepted from a remote image (guards against OOM). */
private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024

/** Image URLs may only use https; http is rejected with a stable failure placeholder. */
fun isAllowedImageSource(source: String): Boolean =
    !source.startsWith("http://", ignoreCase = true)

/**
 * Turn a card's media list into image references.
 * Plain-text cards (empty media) produce an empty list and never enter the image rendering path.
 */
fun parseImageRefs(card: Card): List<ImageRef> {
    val target = when (card.metadata[MEDIA_TARGET_KEY]?.lowercase()) {
        "front" -> MediaTarget.FRONT
        "back" -> MediaTarget.BACK
        else -> MediaTarget.BOTH
    }
    return card.media
        .filter { it.type == MediaType.IMAGE && it.filePath.isNotBlank() }
        .map { media -> resolveImageRef(media, target) }
}

/** Classify one Media.filePath into the three supported source kinds. */
fun resolveImageRef(media: Media, target: MediaTarget = MediaTarget.BOTH): ImageRef {
    val source = media.filePath.trim()
    val kind = when {
        source.startsWith("https://", ignoreCase = true) || source.startsWith("http://", ignoreCase = true) ->
            ImageSourceKind.NETWORK
        source.startsWith("file://", ignoreCase = true) ||
            source.startsWith("content://", ignoreCase = true) ||
            source.startsWith("/") ||
            Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(source) ->
            ImageSourceKind.LOCAL_URI
        else -> ImageSourceKind.LOCAL_RELATIVE
    }
    return ImageRef(
        mediaId = media.id,
        source = source,
        kind = kind,
        target = target,
        caption = media.caption
    )
}

/** Whether an image should be shown on the given face. */
fun ImageRef.matchesFace(isFront: Boolean): Boolean = when (target) {
    MediaTarget.BOTH -> true
    MediaTarget.FRONT -> isFront
    MediaTarget.BACK -> !isFront
}

/**
 * Zero-dependency card image loader.
 *
 * - LOCAL_RELATIVE: resolved beneath getMediaBasePath() (resolveMediaFilePath rejects path escape)
 * - LOCAL_URI: platformReadImageSourceBytes handles file://, content:// and absolute paths
 * - NETWORK: https only; disk cache (sha1 of URL) then platformFetchImageBytes
 *
 * Decoding always happens off the UI thread; results are cached in a bounded LRU so a card flip
 * recomposition and the fullscreen viewer reuse the exact same ImageBitmap (no second decode).
 */
object CardImageLoader {

    private const val MAX_ENTRIES = 32
    private const val MAX_CACHE_PIXELS = 64L * 1024 * 1024

    // accessOrder=true makes this a real LRU: getCached() promotes the touched key to the tail.
    private val memoryCache = LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 1f, true)
    private var cachedPixels = 0L
    private val mutex = Mutex()

    suspend fun load(ref: ImageRef): ImageLoadState = withContext(Dispatchers.IO) {
        if (ref.kind == ImageSourceKind.NETWORK && !isAllowedImageSource(ref.source)) {
            return@withContext ImageLoadState.Failure(FailureReason.HTTPS_ONLY)
        }
        getCached(ref.cacheKey)?.let { return@withContext ImageLoadState.Success(it) }

        val bytes = try {
            when (ref.kind) {
                ImageSourceKind.LOCAL_RELATIVE ->
                    platformReadFileBytes(resolveMediaFilePath(getMediaBasePath(), ref.source))
                ImageSourceKind.LOCAL_URI ->
                    platformReadImageSourceBytes(ref.source)
                ImageSourceKind.NETWORK ->
                    loadNetworkBytes(ref.source)
            }
        } catch (_: Exception) {
            null
        }
        if (bytes == null || bytes.isEmpty()) {
            return@withContext ImageLoadState.Failure(FailureReason.LOAD_FAILED)
        }

        val bitmap = try {
            platformDecodeImage(bytes)
        } catch (_: Exception) {
            null
        } ?: return@withContext ImageLoadState.Failure(FailureReason.DECODE_FAILED)

        putCached(ref.cacheKey, bitmap)
        ImageLoadState.Success(bitmap)
    }

    private fun loadNetworkBytes(url: String): ByteArray? {
        val cacheFile = diskCacheFile(url)
        platformReadFileBytes(cacheFile)?.let { cached ->
            if (cached.isNotEmpty() && cached.size <= MAX_IMAGE_BYTES) return cached
            platformDeleteFile(cacheFile) // corrupted/oversized cache: drop it and re-download
        }
        val downloaded = platformFetchImageBytes(url) ?: return null
        if (downloaded.size > MAX_IMAGE_BYTES) return null
        try {
            writeMediaFileAtomically(cacheFile, downloaded)
        } catch (_: Exception) {
            // Disk cache is best-effort; a write failure must not break rendering.
        }
        return downloaded
    }

    private fun diskCacheFile(url: String): String {
        val dir = getMediaBasePath().trimEnd('/', '\\') + "/cache/images"
        try {
            platformMkdirs(dir)
        } catch (_: Exception) {
            // Ignore; write will simply fail and be swallowed.
        }
        return dir + "/" + hashBytesSha1(url.encodeToByteArray()) + ".img"
    }

    private suspend fun getCached(key: String): ImageBitmap? = mutex.withLock {
        memoryCache[key]
    }

    private suspend fun putCached(key: String, bitmap: ImageBitmap) = mutex.withLock {
        val previous = memoryCache.put(key, bitmap)
        cachedPixels += (bitmap.width.toLong() * bitmap.height.toLong()) -
            (previous?.let { it.width.toLong() * it.height.toLong() } ?: 0L)
        // Keep the just-inserted key even when a single image alone exceeds the pixel budget:
        // evicting it would immediately discard what we are trying to cache (self-eviction).
        while (memoryCache.size > 1 && (memoryCache.size > MAX_ENTRIES || cachedPixels > MAX_CACHE_PIXELS)) {
            val oldest = memoryCache.entries.firstOrNull { it.key != key }?.key ?: break
            val removed = memoryCache.remove(oldest) ?: break
            cachedPixels -= removed.width.toLong() * removed.height.toLong()
        }
    }

    /** Clear cached bitmaps (e.g. after a media sync replaces files). */
    suspend fun clearCache() = mutex.withLock {
        memoryCache.clear()
        cachedPixels = 0L
    }
}
