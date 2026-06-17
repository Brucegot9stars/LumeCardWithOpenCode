package com.lumecard.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumecard.app.ui.theme.LumeCardTheme

@Composable
fun AnimatedDonutChart(
    progress: Float,
    completed: Int,
    target: Int,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    strokeWidth: Dp = 14.dp,
    trackColor: Color = LumeCardTheme.semanticColors.progressTrack,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(progress) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    val textMeasurer = rememberTextMeasurer()
    val displayPercentage = (animatedProgress.value * 100).toInt()

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val canvasSize = this.size
            val stroke = strokeWidth.toPx()
            val arcSize = Size(
                canvasSize.width - stroke,
                canvasSize.height - stroke,
            )
            val topLeft = Offset(stroke / 2f, stroke / 2f)

            // Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Butt,
                ),
            )

            // Progress arc
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = animatedProgress.value * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                ),
            )
        }

        // Center text
        val centerTextStyle = TextStyle(
            fontSize = (size.value / 5f).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        val subTextStyle = TextStyle(
            fontSize = (size.value / 8f).sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        val centerText = "$displayPercentage%"
        val centerLayout = textMeasurer.measure(centerText, centerTextStyle)
        val subLayout = textMeasurer.measure(label, subTextStyle)

        Canvas(modifier = Modifier.size(size)) {
            val totalHeight = centerLayout.size.height + subLayout.size.height + 4.dp.toPx()
            val startY = (size.toPx() - totalHeight) / 2f

            drawText(
                textLayoutResult = centerLayout,
                topLeft = Offset(
                    x = (size.toPx() - centerLayout.size.width) / 2f,
                    y = startY,
                ),
            )
            drawText(
                textLayoutResult = subLayout,
                topLeft = Offset(
                    x = (size.toPx() - subLayout.size.width) / 2f,
                    y = startY + centerLayout.size.height + 4.dp.toPx(),
                ),
            )
        }
    }
}
