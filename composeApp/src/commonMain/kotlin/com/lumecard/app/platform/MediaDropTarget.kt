package com.lumecard.app.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
expect fun MediaDropTarget(
    onMediaDropped: (markdownRef: String) -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
)
