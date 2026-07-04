package com.lumecard.shared.data

import kotlinx.serialization.Serializable

// ── Unified override mechanism ──────────────────────────────
// Every field in QuoteOverrideConfig uses the same pattern:
//   null  → Follow global / default setting
//   value → Use this value (overridden)
//
// This is the single "ConfigOverride<T>" mechanism — there is no
// per-field follow/custom branching logic.

inline fun <T> T?.resolveOverridden(global: T): T = this ?: global

@Serializable
data class SplashQuoteData(
    val text: String,
    val author: String = "",
    val overrideConfig: QuoteOverrideConfig? = null,
)

@Serializable
data class QuoteOverrideConfig(
    val direction: SplashQuoteDirection? = null,
    val font: String? = null,
    val fontSize: Float? = null,
    val showAuthor: Boolean? = null,
    val animationStyle: com.lumecard.shared.feature.quote.config.QuoteAnimationStyle? = null,
    val background: BackgroundOverride? = null,
    val layout: QuoteLayoutOverride? = null,
)

@Serializable
data class BackgroundOverride(
    val type: BackgroundType = BackgroundType.FOLLOW_GLOBAL,
    val color: String? = null,
    val imagePath: String? = null,
)

@Serializable
enum class BackgroundType {
    FOLLOW_GLOBAL,
    SOLID_COLOR,
    IMAGE,
}

@Serializable
data class QuoteLayoutOverride(
    val textAlign: TextAlignOverride? = null,
    val authorAlign: TextAlignOverride? = null,
    val contentSpacing: Float? = null,
    val pagePadding: Float? = null,
    val maxWidth: Float? = null,
    val verticalPosition: VerticalPosition? = null,
)

@Serializable
enum class TextAlignOverride {
    START,
    CENTER,
    END,
}

@Serializable
enum class VerticalPosition {
    TOP,
    CENTER,
    BOTTOM,
}

@Serializable
data class SplashQuotesCollection(
    val quotes: List<SplashQuoteData>,
)

enum class SplashQuoteDirection {
    HORIZONTAL,
    VERTICAL,
}

enum class SplashQuoteStrategy {
    RANDOM,
    SEQUENTIAL,
}

data class SplashQuoteSettings(
    val enabled: Boolean = true,
    val durationSeconds: Int = 3,
    val direction: SplashQuoteDirection = SplashQuoteDirection.HORIZONTAL,
    val font: String = "",
    val fontSize: Float = 0f,
    val backgroundPath: String = "",
    val strategy: SplashQuoteStrategy = SplashQuoteStrategy.RANDOM,
    val sequenceIndex: Int = 0,
    val showAuthor: Boolean = true,
)
