package com.lumecard.shared.util

/** Load a text resource by its classpath path (e.g. "/config/prompt_ai_card.txt"). */
expect fun loadTextResource(path: String): String?
