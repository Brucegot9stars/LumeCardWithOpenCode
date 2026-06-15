package com.lumecard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1B5E20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5D6A7),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF4E6352),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E8D2),
    onSecondaryContainer = Color(0xFF0C1F12),
    tertiary = Color(0xFF3C6575),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC0EAFD),
    onTertiaryContainer = Color(0xFF001F29),
    background = Color(0xFFFCFDF7),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFCFDF7),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF717971)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AD68C),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF005315),
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFFB4CCB7),
    onSecondary = Color(0xFF223526),
    secondaryContainer = Color(0xFF384B3C),
    onSecondaryContainer = Color(0xFFD0E8D2),
    tertiary = Color(0xFFA4CEDF),
    onTertiary = Color(0xFF053543),
    tertiaryContainer = Color(0xFF224C5A),
    onTertiaryContainer = Color(0xFFC0EAFD),
    background = Color(0xFF1A1C19),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF1A1C19),
    onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8B9188)
)

@Composable
fun LumeCardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalSpacing provides LumeCardSpacing(),
        LocalRadius provides LumeCardRadius(),
        LocalElevation provides LumeCardElevation(),
        LocalMotion provides LumeCardMotion(),
        LocalSemanticColors provides lumeCardSemanticColors(darkTheme),
        LocalTypography provides LumeCardTypography(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content,
        )
    }
}
