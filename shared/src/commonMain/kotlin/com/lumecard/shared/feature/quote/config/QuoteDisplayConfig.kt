package com.lumecard.shared.feature.quote.config

import com.lumecard.shared.data.SplashQuoteDirection

data class QuoteDisplayConfig(
    val autoDismiss: Boolean = true,
    val dismissDurationMs: Long = 3000L,
    val allowTapToSkip: Boolean = true,
    val autoRotate: Boolean = false,
    val rotationIntervalMs: Long = 5000L,
    val showPreviewBadge: Boolean = false,
    val respondToIdle: Boolean = false,
    val enableAnimation: Boolean = true,
    val animationStyle: QuoteAnimationStyle = QuoteAnimationStyle.FADE_IN,
    val fullScreen: Boolean = false,
    val hideSystemUi: Boolean = false,
    val showAuthor: Boolean = true,
    val enableBackground: Boolean = true,
    val useGlobalBackground: Boolean = true,
    val mode: QuoteDisplayMode = QuoteDisplayMode.STARTUP,
    val defaultDirection: SplashQuoteDirection = SplashQuoteDirection.HORIZONTAL,
    val defaultFont: String = "",
    val defaultFontSize: Float = 0f,
) {
    companion object {
        val STARTUP_DEFAULT = QuoteDisplayConfig(
            mode = QuoteDisplayMode.STARTUP,
            autoDismiss = true,
            dismissDurationMs = 3000L,
            allowTapToSkip = true,
            autoRotate = false,
            enableAnimation = true,
            animationStyle = QuoteAnimationStyle.FADE_IN,
            fullScreen = false,
            showAuthor = true,
            enableBackground = true,
        )

        val SCREEN_SAVER_DEFAULT = QuoteDisplayConfig(
            mode = QuoteDisplayMode.SCREEN_SAVER,
            autoDismiss = false,
            allowTapToSkip = true,
            autoRotate = true,
            rotationIntervalMs = 3000L,
            respondToIdle = true,
            enableAnimation = true,
            animationStyle = QuoteAnimationStyle.FADE_IN,
            fullScreen = true,
            hideSystemUi = true,
            showAuthor = true,
            enableBackground = true,
        )

        val PREVIEW_DEFAULT = QuoteDisplayConfig(
            mode = QuoteDisplayMode.PREVIEW,
            autoDismiss = false,
            allowTapToSkip = true,
            autoRotate = false,
            showPreviewBadge = true,
            enableAnimation = true,
            animationStyle = QuoteAnimationStyle.NONE,
            fullScreen = false,
            showAuthor = true,
            enableBackground = true,
        )
    }
}
