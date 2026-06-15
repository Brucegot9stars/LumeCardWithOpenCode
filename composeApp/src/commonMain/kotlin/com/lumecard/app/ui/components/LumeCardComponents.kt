package com.lumecard.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumecard.app.ui.theme.LumeCardTheme

// ─── LumeCardTopBar ───────────────────────────────────────────────────────

/**
 * Standardized top bar for all LumeCard screens.
 * Uses subtle surface-based styling instead of primaryContainer,
 * with an optional bottom divider for visual separation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumeCardTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = LumeCardTheme.spacing

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                ),
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        actions = {
            if (action != null) {
                Box(modifier = Modifier.padding(end = spacing.sm)) {
                    action()
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    )
}

// ─── LumeCardRatingBar ─────────────────────────────────────────────────────

/**
 * Refined 4-button rating bar for study mode.
 * Uses neutral tonal styling with subtle color accents, not solid filled buttons.
 */
@Composable
fun LumeCardRatingBar(
    onAgain: () -> Unit,
    onHard: () -> Unit,
    onGood: () -> Unit,
    onEasy: () -> Unit,
    modifier: Modifier = Modifier,
    showShortcuts: Boolean = true,
) {
    val colors = LumeCardTheme.semanticColors
    val spacing = LumeCardTheme.spacing
    val radius = LumeCardTheme.radius

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        RatingChip(
            label = "Again",
            shortcut = if (showShortcuts) "1" else null,
            accentColor = colors.ratingAgain,
            modifier = Modifier.weight(1f),
            onClick = onAgain,
        )
        RatingChip(
            label = "Hard",
            shortcut = if (showShortcuts) "2" else null,
            accentColor = colors.ratingHard,
            modifier = Modifier.weight(1f),
            onClick = onHard,
        )
        RatingChip(
            label = "Good",
            shortcut = if (showShortcuts) "3" else null,
            accentColor = colors.ratingGood,
            modifier = Modifier.weight(1f),
            onClick = onGood,
        )
        RatingChip(
            label = "Easy",
            shortcut = if (showShortcuts) "4" else null,
            accentColor = colors.ratingEasy,
            modifier = Modifier.weight(1f),
            onClick = onEasy,
        )
    }
}

/**
 * Compact version for mobile / narrow layouts.
 * Shows only 1-2 letters per button.
 */
@Composable
fun LumeCardRatingBarCompact(
    onAgain: () -> Unit,
    onHard: () -> Unit,
    onGood: () -> Unit,
    onEasy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LumeCardTheme.semanticColors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MiniRatingChip(label = "1", accentColor = colors.ratingAgain, onClick = onAgain)
        MiniRatingChip(label = "2", accentColor = colors.ratingHard, onClick = onHard)
        MiniRatingChip(label = "3", accentColor = colors.ratingGood, onClick = onGood)
        MiniRatingChip(label = "4", accentColor = colors.ratingEasy, onClick = onEasy)
    }
}

// ─── Internal components ─────────────────────────────────────────────────────

@Composable
private fun RatingChip(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    shortcut: String? = null,
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = surfaceColor.copy(alpha = 0.7f),
        tonalElevation = 0.dp,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Row: shortcut badge + accent dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (shortcut != null) {
                    Text(
                        text = shortcut,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                }
                Surface(
                    modifier = Modifier.size(6.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = accentColor,
                ) {}
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MiniRatingChip(
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        modifier = Modifier
            .size(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = surfaceColor.copy(alpha = 0.7f),
        tonalElevation = 0.dp,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = accentColor,
                ) {}
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

