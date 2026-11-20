package com.lumecard.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter

/** Legacy sync loader used by the splash background. Card images use [platformDecodeImage] instead. */
expect fun platformLoadImage(path: String): Painter?

/** Decode encoded image bytes. Android applies inSampleSize downsampling; Desktop uses Skia. */
expect fun platformDecodeImage(bytes: ByteArray): ImageBitmap?

/** Read bytes for a local image source: file://, content://, or an absolute path. */
expect fun platformReadImageSourceBytes(source: String): ByteArray?

/** HTTPS GET for a remote image. Returns null on non-2xx, oversize, or any I/O error. */
expect fun platformFetchImageBytes(url: String): ByteArray?

/**
 * Re-encode [bytes] so its longest edge is at most [maxEdge] pixels, keeping the original format
 * (PNG stays PNG so transparency survives; JPEG keeps a high quality setting).
 *
 * Returns null when the source is already within [maxEdge], cannot be decoded, or re-encoding fails —
 * callers then keep the original bytes, so a compression failure never loses the image.
 */
expect fun platformCompressImage(bytes: ByteArray, maxEdge: Int, sourceExtension: String): ByteArray?
