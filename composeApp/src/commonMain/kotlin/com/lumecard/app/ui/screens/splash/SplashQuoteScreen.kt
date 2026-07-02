package com.lumecard.app.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumecard.app.font.FontRegistry
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.SplashQuoteData
import com.lumecard.shared.data.SplashQuoteDirection
import kotlinx.coroutines.delay

@Composable
fun SplashQuoteScreen(
    quote: SplashQuoteData,
    direction: SplashQuoteDirection,
    splashFontId: String,
    splashFontSize: Float,
    onDismiss: () -> Unit,
) {
    val autoDismiss = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(3000L)
        if (!autoDismiss.value) {
            autoDismiss.value = true
            onDismiss()
        }
    }

    val fontFamily = remember(splashFontId) {
        if (splashFontId.isNotBlank()) {
            FontRegistry.resolveFontFamily(splashFontId)
        } else {
            FontFamily.Default
        }
    }

    val fontSize: TextUnit = remember(splashFontSize) {
        if (splashFontSize > 0f) splashFontSize.sp else 24.sp
    }

    val spacing = LumeCardTheme.spacing

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable {
                autoDismiss.value = true
                onDismiss()
            },
        contentAlignment = Alignment.Center,
    ) {
        when (direction) {
            SplashQuoteDirection.HORIZONTAL -> {
                HorizontalLayout(quote = quote, fontFamily = fontFamily, fontSize = fontSize)
            }
            SplashQuoteDirection.VERTICAL -> {
                VerticalLayout(quote = quote, fontFamily = fontFamily, fontSize = fontSize)
            }
        }
    }
}

@Composable
private fun HorizontalLayout(
    quote: SplashQuoteData,
    fontFamily: FontFamily,
    fontSize: TextUnit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        Text(
            text = quote.text,
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = fontSize,
                lineHeight = fontSize * 1.6f,
                textAlign = TextAlign.Center,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (quote.author.isNotBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "—— ${quote.author}",
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSize * 0.75f,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VerticalLayout(
    quote: SplashQuoteData,
    fontFamily: FontFamily,
    fontSize: TextUnit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (ch in quote.text) {
            if (ch == ' ') continue
            Text(
                text = ch.toString(),
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    lineHeight = fontSize * 0.95f,
                    textAlign = TextAlign.Center,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (quote.author.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            for (ch in "——") {
                Text(
                    text = ch.toString(),
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = fontSize * 0.75f,
                        lineHeight = fontSize * 0.95f,
                        textAlign = TextAlign.Center,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            for (ch in quote.author) {
                if (ch == ' ') continue
                Text(
                    text = ch.toString(),
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = fontSize,
                        lineHeight = fontSize * 0.95f,
                        textAlign = TextAlign.Center,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
