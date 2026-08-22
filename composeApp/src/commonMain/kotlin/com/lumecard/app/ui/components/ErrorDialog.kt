package com.lumecard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.lumecard.app.i18n.I18nManager
import org.koin.compose.koinInject

@Composable
fun ErrorDialog(
    error: String?,
    title: String,
    description: String,
    onDismiss: () -> Unit,
    showCancel: Boolean = false,
    onCancel: (() -> Unit)? = null,
) {
    if (error == null) return
    val strings = koinInject<I18nManager>().strings
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showCancel) {
                    TextButton(onClick = { onCancel?.invoke() ?: onDismiss() }, interactionSource = null) {
                        Text(strings.actionCancel)
                    }
                }
                Button(onClick = {
                    clipboardManager.setText(AnnotatedString(error))
                }, interactionSource = null) {
                    Text(strings.actionCopy)
                }
                Button(onClick = onDismiss, interactionSource = null) {
                    Text(strings.actionOk)
                }
            }
        },
    )
}
