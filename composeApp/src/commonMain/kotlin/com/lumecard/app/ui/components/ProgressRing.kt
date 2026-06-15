package com.lumecard.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumecard.app.ui.theme.LumeCardTheme

/**
 * A circular progress ring with animated fill and optional center label.
 */
@Composable
fun ProgressRing(
    progress: Float, // 0f to 1f
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    strokeWidth: Dp = 6.dp,
    trackColor: Color = LumeCardTheme.semanticColors.progressTrack,
    progressColor: Color = LumeCardTheme.semanticColors.progressFill,
    startAngle: Float = -90f,
    showPercentage: Boolean = true,
    label: String? = null,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "progress",
    )

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val canvasSize = this.size
            val stroke = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
            )
            val arcSize = Size(
                canvasSize.width - strokeWidth.toPx(),
                canvasSize.height - strokeWidth.toPx(),
            )
            val topLeft = Offset(
                strokeWidth.toPx() / 2,
                strokeWidth.toPx() / 2,
            )

            // Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = strokeWidth.toPx() * 0.6f,
                    cap = StrokeCap.Butt,
                ),
            )

            // Progress arc
            drawArc(
                color = progressColor,
                startAngle = startAngle,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }

        if (showPercentage) {
            val percentText = "${(progress * 100).toInt()}%"
            val textStyle = TextStyle(
                fontSize = (size.value / 4.5f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val textLayout = textMeasurer.measure(
                text = percentText,
                style = textStyle,
            )
            Canvas(modifier = Modifier.size(size)) {
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(
                        x = (size.toPx() - textLayout.size.width) / 2f,
                        y = (size.toPx() - textLayout.size.height) / 2f,
                    ),
                )
            }
        }
    }
}

/**
 * A simplified progress bar with rounded ends, suitable for lists and compact layouts.
 */
@Composable
fun CompactProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    trackColor: Color = LumeCardTheme.semanticColors.progressTrack,
    progressColor: Color = LumeCardTheme.semanticColors.progressFill,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "barProgress",
    )

    Canvas(modifier = modifier) {
        val barHeight = height.toPx()
        val barY = (size.height - barHeight) / 2f

        // Track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, barY),
            size = Size(size.width, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2),
        )

        // Fill
        drawRoundRect(
            color = progressColor,
            topLeft = Offset(0f, barY),
            size = Size(size.width * animatedProgress, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2),
        )
    }
}
