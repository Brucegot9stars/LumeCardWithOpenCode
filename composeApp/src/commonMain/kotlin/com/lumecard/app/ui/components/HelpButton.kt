package com.lumecard.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveHelp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.ui.screens.help.HelpScreen

@Composable
fun ContextHelpButton(
    articleId: String,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.currentOrThrow
    IconButton(
        onClick = { navigator.push(HelpScreen(initialArticleId = articleId)) },
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            Icons.Default.LiveHelp,
            contentDescription = "Help",
            modifier = Modifier.size(18.dp),
        )
    }
}
