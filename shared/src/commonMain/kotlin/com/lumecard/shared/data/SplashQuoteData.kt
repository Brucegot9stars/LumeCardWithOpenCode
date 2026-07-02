package com.lumecard.shared.data

import kotlinx.serialization.Serializable

@Serializable
data class SplashQuoteData(
    val text: String,
    val author: String = "",
)

@Serializable
data class SplashQuotesCollection(
    val quotes: List<SplashQuoteData>,
)

enum class SplashQuoteDirection {
    HORIZONTAL,
    VERTICAL,
}
