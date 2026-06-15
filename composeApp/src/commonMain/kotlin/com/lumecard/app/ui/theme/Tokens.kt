package com.lumecard.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Spacing ────────────────────────────────────────────────────────────────

data class LumeCardSpacing(
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val sm: androidx.compose.ui.unit.Dp = 8.dp,
    val md: androidx.compose.ui.unit.Dp = 16.dp,
    val lg: androidx.compose.ui.unit.Dp = 24.dp,
    val xl: androidx.compose.ui.unit.Dp = 32.dp,
    val xxl: androidx.compose.ui.unit.Dp = 48.dp,
    val section: androidx.compose.ui.unit.Dp = 12.dp,
)

val LocalSpacing = staticCompositionLocalOf { LumeCardSpacing() }

// ─── Radius ─────────────────────────────────────────────────────────────────

data class LumeCardRadius(
    val sm: androidx.compose.ui.unit.Dp = 8.dp,
    val md: androidx.compose.ui.unit.Dp = 12.dp,
    val lg: androidx.compose.ui.unit.Dp = 16.dp,
    val xl: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val card: RoundedCornerShape get() = RoundedCornerShape(md)
    val button: RoundedCornerShape get() = RoundedCornerShape(sm)
    val dialog: RoundedCornerShape get() = RoundedCornerShape(lg)
    val pill: RoundedCornerShape get() = RoundedCornerShape(xl)
}

val LocalRadius = staticCompositionLocalOf { LumeCardRadius() }

// ─── Elevation ───────────────────────────────────────────────────────────────

data class LumeCardElevation(
    val level0: androidx.compose.ui.unit.Dp = 0.dp,
    val level1: androidx.compose.ui.unit.Dp = 1.dp,
    val level2: androidx.compose.ui.unit.Dp = 4.dp,
    val level3: androidx.compose.ui.unit.Dp = 8.dp,
)

val LocalElevation = staticCompositionLocalOf { LumeCardElevation() }

// ─── Motion ──────────────────────────────────────────────────────────────────

data class LumeCardMotion(
    val quick: Int = 200,
    val normal: Int = 350,
    val slow: Int = 500,
)

val LocalMotion = staticCompositionLocalOf { LumeCardMotion() }

// ─── Semantic Colors ─────────────────────────────────────────────────────────

data class LumeCardSemanticColors(
    val ratingAgain: Color,
    val ratingHard: Color,
    val ratingGood: Color,
    val ratingEasy: Color,
    val progressTrack: Color,
    val progressFill: Color,
    val streakActive: Color,
    val streakInactive: Color,
    val skeleton: Color,
)

fun lumeCardSemanticColors(isDark: Boolean): LumeCardSemanticColors {
    return if (isDark) {
        LumeCardSemanticColors(
            ratingAgain = Color(0xFFF87171),
            ratingHard = Color(0xFFFBBF24),
            ratingGood = Color(0xFF34D399),
            ratingEasy = Color(0xFF60A5FA),
            progressTrack = Color(0xFF2D2D2D),
            progressFill = Color(0xFF8AD68C),
            streakActive = Color(0xFF8AD68C),
            streakInactive = Color(0xFF3A3A3A),
            skeleton = Color(0xFF2A2A2A),
        )
    } else {
        LumeCardSemanticColors(
            ratingAgain = Color(0xFFEF4444),
            ratingHard = Color(0xFFF59E0B),
            ratingGood = Color(0xFF22C55E),
            ratingEasy = Color(0xFF3B82F6),
            progressTrack = Color(0xFFE5E7EB),
            progressFill = Color(0xFF1B5E20),
            streakActive = Color(0xFF1B5E20),
            streakInactive = Color(0xFFD1D5DB),
            skeleton = Color(0xFFF3F4F6),
        )
    }
}

val LocalSemanticColors = staticCompositionLocalOf { lumeCardSemanticColors(false) }

// ─── Typography ──────────────────────────────────────────────────────────────

data class LumeCardTypography(
    val display: TextStyle = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 32.sp,
        lineHeight = 40.sp, letterSpacing = (-0.5).sp,
    ),
    val heading1: TextStyle = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 24.sp,
        lineHeight = 32.sp, letterSpacing = (-0.3).sp,
    ),
    val heading2: TextStyle = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
        lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    val heading3: TextStyle = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    val body: TextStyle = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 15.sp,
        lineHeight = 22.sp, letterSpacing = 0.1.sp,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 13.sp,
        lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    val caption: TextStyle = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 11.sp,
        lineHeight = 16.sp, letterSpacing = 0.3.sp,
    ),
    val label: TextStyle = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
        lineHeight = 18.sp, letterSpacing = 0.2.sp,
    ),
    val button: TextStyle = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    val numeric: TextStyle = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 28.sp,
        lineHeight = 32.sp, letterSpacing = (-0.5).sp,
    ),
)

val LocalTypography = staticCompositionLocalOf { LumeCardTypography() }

// ─── Convenience accessor ────────────────────────────────────────────────────

object LumeCardTheme {
    val spacing: LumeCardSpacing
        @Composable get() = LocalSpacing.current

    val radius: LumeCardRadius
        @Composable get() = LocalRadius.current

    val elevation: LumeCardElevation
        @Composable get() = LocalElevation.current

    val motion: LumeCardMotion
        @Composable get() = LocalMotion.current

    val semanticColors: LumeCardSemanticColors
        @Composable get() = LocalSemanticColors.current

    val typography: LumeCardTypography
        @Composable get() = LocalTypography.current
}
