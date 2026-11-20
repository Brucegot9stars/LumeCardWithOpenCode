package com.lumecard.app.ui.screens.study

import com.lumecard.app.ui.screens.card.HTTPS_IMAGE_URL
import kotlin.test.Test
import kotlin.test.assertEquals

class CardImageEditorRegexTest {
    @Test
    fun httpsImageUrlAcceptsValidUrls() {
        val accepted = listOf(
            "https://example.com",
            "https://localhost",
            "https://127.0.0.1:8080",
            "https://host:9000/p?q=1",
            "https://host?q=abc",
        )
        accepted.forEach { url ->
            assertEquals(true, HTTPS_IMAGE_URL.matches(url), "expected accept: $url")
        }
    }

    @Test
    fun httpsImageUrlRejectsInvalidUrls() {
        val rejected = listOf(
            "https://",
            "http://example.com",
            "https://exa mple.com",
            "https://trusted.com@evil.com",
        )
        rejected.forEach { url ->
            assertEquals(false, HTTPS_IMAGE_URL.matches(url), "expected reject: $url")
        }
    }
}
