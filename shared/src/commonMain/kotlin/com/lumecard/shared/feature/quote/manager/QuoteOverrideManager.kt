package com.lumecard.shared.feature.quote.manager

import com.lumecard.shared.feature.quote.config.QuoteAnimationStyle
import com.lumecard.shared.feature.quote.config.QuoteDisplayConfig
import com.lumecard.shared.feature.quote.config.QuoteDisplayMode
import com.lumecard.shared.data.QuoteOverrideConfig
import com.lumecard.shared.data.SplashQuoteData

class QuoteOverrideManager {

    fun merge(
        quote: SplashQuoteData,
        baseConfig: QuoteDisplayConfig,
        globalConfig: QuoteDisplayConfig? = null,
    ): QuoteDisplayConfig {
        val override = quote.overrideConfig ?: return baseConfig
        val effectiveGlobal = globalConfig ?: baseConfig

        val animationStyle = when {
            override.animationStyle != null -> override.animationStyle
            else -> effectiveGlobal.animationStyle
        }

        val showAuthor = when {
            override.showAuthor != null -> override.showAuthor
            else -> effectiveGlobal.showAuthor
        }

        val enableBackground = true
        val useGlobalBackground = override.background?.let { it.type == com.lumecard.shared.data.BackgroundType.FOLLOW_GLOBAL } ?: true

        return baseConfig.copy(
            animationStyle = animationStyle ?: baseConfig.animationStyle,
            showAuthor = showAuthor ?: baseConfig.showAuthor,
            enableBackground = enableBackground,
            useGlobalBackground = useGlobalBackground,
        )
    }

    fun shouldOverrideBackground(override: com.lumecard.shared.data.BackgroundOverride?): Boolean {
        return override != null && override.type != com.lumecard.shared.data.BackgroundType.FOLLOW_GLOBAL
    }
}
