package com.lumecard.app.ui.screens.study

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumecard.app.i18n.I18nStrings
import com.lumecard.app.platform.CardImageLoader
import com.lumecard.app.platform.FailureReason
import com.lumecard.app.platform.ImageLoadState
import com.lumecard.app.platform.ImageRef
import com.lumecard.app.platform.MediaAlignment
import com.lumecard.app.platform.matchesFace
import com.lumecard.app.platform.parseImageRefs
import com.lumecard.app.ui.screens.settings.AnswerDisplayMode
import com.lumecard.shared.model.Card
import kotlinx.coroutines.delay

private const val VIEWER_TRANSITION_MS = 180
private const val IMAGE_MAX_HEIGHT_FRACTION = 0.45f

/**
 * Independent image block rendered at CardContent level (outside FlipCard).
 *
 * This is deliberate: FlipCard applies a 3D rotation and mirrors the back face with
 * scaleX = -1f, and key(card.id) rebuilds the subtree. Keeping images here means no
 * mirroring, no 3D layer, and no subtree rebuild on flip — the decoded bitmap stays
 * alive in remember() and is shared with the fullscreen viewer.
 *
 * Plain-text cards (no IMAGE media) return immediately: zero extra nodes.
 */
@Composable
internal fun CardImageBlock(
    card: Card,
    isFlipped: Boolean,
    displayMode: AnswerDisplayMode,
    strings: I18nStrings,
    onOpenViewer: (ImageRef, ImageBitmap) -> Unit,
) {
    val refs = remember(card.id, card.media, card.metadata) { parseImageRefs(card) }
    val visible = refs.filter { ref ->
        when (displayMode) {
            AnswerDisplayMode.SPLIT -> if (isFlipped) true else ref.matchesFace(isFront = true)
            AnswerDisplayMode.FLIP -> ref.matchesFace(isFront = !isFlipped)
        }
    }
    if (visible.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        visible.forEach { ref ->
            CardImageThumbnail(ref = ref, strings = strings, onOpenViewer = onOpenViewer)
        }
    }
}

@Composable
private fun CardImageThumbnail(
    ref: ImageRef,
    strings: I18nStrings,
    onOpenViewer: (ImageRef, ImageBitmap) -> Unit,
) {
    var state by remember(ref.cacheKey) { mutableStateOf<ImageLoadState>(ImageLoadState.Loading) }
    LaunchedEffect(ref.cacheKey) {
        state = CardImageLoader.load(ref)
    }

    val boxAlignment = when (ref.alignment) {
        MediaAlignment.START -> Alignment.CenterStart
        MediaAlignment.CENTER -> Alignment.Center
        MediaAlignment.END -> Alignment.CenterEnd
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = boxAlignment) {
            // maxHeight may be unbounded (e.g. inside a scrollable column); fall back to a fixed cap.
            val maxHeight = if (maxHeight.value.isFinite()) {
                (maxHeight * IMAGE_MAX_HEIGHT_FRACTION).coerceAtMost(320.dp)
            } else {
                320.dp
            }
            when (val current = state) {
                is ImageLoadState.Loading -> ImagePlaceholder(maxHeight, strings.cardImageLoading, showProgress = true)
                is ImageLoadState.Failure -> {
                    val label = when (current.reason) {
                        FailureReason.HTTPS_ONLY -> strings.cardImageHttpsOnly
                        FailureReason.LOAD_FAILED -> strings.cardImageLoadFailed
                        FailureReason.DECODE_FAILED -> strings.cardImageDecodeFailed
                    }
                    ImagePlaceholder(maxHeight, label, showProgress = false)
                }
                is ImageLoadState.Success -> {
                    val bitmap = current.bitmap
                    val size = naturalOrHalfSize(
                        widthPx = bitmap.width,
                        heightPx = bitmap.height,
                        containerWidth = maxWidth,
                        heightCap = maxHeight
                    )
                    Image(
                        painter = BitmapPainter(bitmap),
                        contentDescription = ref.caption.ifBlank { strings.cardImageViewer },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(width = size.width, height = size.height)
                            .clip(RoundedCornerShape(8.dp))
                            // Consume the tap so StudyScreen's detectTapGestures { flipCard() } is not triggered.
                            .clickable { onOpenViewer(ref, bitmap) }
                    )
                }
            }
        }
        if (ref.caption.isNotBlank()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = boxAlignment) {
                Text(
                    ref.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = when (ref.alignment) {
                        MediaAlignment.START -> TextAlign.Start
                        MediaAlignment.CENTER -> TextAlign.Center
                        MediaAlignment.END -> TextAlign.End
                    }
                )
            }
        }
    }
}

/**
 * Display size for one image inside the card body.
 *
 * Policy (user-confirmed): map source pixels to physical screen pixels 1:1. If the image is
 * narrower than half the available width it keeps its natural pixel size; otherwise it is capped
 * at half the width. The height cap only kicks in for very tall images and shrinks both axes
 * proportionally so the aspect ratio is never distorted.
 */
@Composable
private fun naturalOrHalfSize(
    widthPx: Int,
    heightPx: Int,
    containerWidth: Dp,
    heightCap: Dp,
): DpSize {
    val density = LocalDensity.current.density
    if (widthPx <= 0 || heightPx <= 0 || density <= 0f) return DpSize(containerWidth, heightCap)

    val ratio = widthPx.toFloat() / heightPx.toFloat()
    val naturalWidth = (widthPx / density).dp
    val halfWidth = containerWidth / 2

    var width = if (naturalWidth <= halfWidth) naturalWidth else halfWidth
    var height = width / ratio
    if (height > heightCap) {
        height = heightCap
        width = height * ratio
    }
    return DpSize(width, height)
}

@Composable
private fun ImagePlaceholder(maxHeight: androidx.compose.ui.unit.Dp, label: String, showProgress: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp, max = maxHeight.coerceAtLeast(96.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showProgress) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Fullscreen viewer in an independent Dialog (separate window/layer).
 * It receives the already-decoded bitmap, so entering never re-downloads or re-decodes.
 */
@Composable
internal fun ImageViewerDialog(
    ref: ImageRef,
    bitmap: ImageBitmap,
    strings: I18nStrings,
    onDismiss: () -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Play the exit animation before actually removing the Dialog node.
    LaunchedEffect(visible) {
        if (!visible) {
            delay(VIEWER_TRANSITION_MS.toLong())
            onDismiss()
        }
    }
    fun requestClose() { visible = false }
    fun applyScale(next: Float) {
        scale = next.coerceIn(1f, 5f)
        if (scale <= 1.01f) { offsetX = 0f; offsetY = 0f }
    }

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(VIEWER_TRANSITION_MS)) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(VIEWER_TRANSITION_MS, easing = FastOutSlowInEasing)
                ),
                exit = fadeOut(tween(VIEWER_TRANSITION_MS)) + scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(VIEWER_TRANSITION_MS)
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = BitmapPainter(bitmap),
                        contentDescription = ref.caption.ifBlank { strings.cardImageViewer },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val next = (scale * zoom).coerceIn(1f, 5f)
                                    scale = next
                                    if (next > 1.01f) {
                                        val limit = 600f * (next - 1f)
                                        offsetX = (offsetX + pan.x).coerceIn(-limit, limit)
                                        offsetY = (offsetY + pan.y).coerceIn(-limit, limit)
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { applyScale(scale / 1.3f) }) {
                                    Icon(Icons.Default.Remove, contentDescription = strings.cardImageZoomOut, tint = Color.White)
                                }
                                IconButton(onClick = { applyScale(1f); offsetX = 0f; offsetY = 0f }) {
                                    Icon(Icons.Default.ZoomOutMap, contentDescription = strings.cardImageFit, tint = Color.White)
                                }
                                IconButton(onClick = { applyScale(scale * 1.3f) }) {
                                    Icon(Icons.Default.Add, contentDescription = strings.cardImageZoomIn, tint = Color.White)
                                }
                                IconButton(onClick = { requestClose() }) {
                                    Icon(Icons.Default.Close, contentDescription = strings.cardImageClose, tint = Color.White)
                                }
                            }
                        }
                    }

                    if (ref.caption.isNotBlank()) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            Text(
                                ref.caption,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
