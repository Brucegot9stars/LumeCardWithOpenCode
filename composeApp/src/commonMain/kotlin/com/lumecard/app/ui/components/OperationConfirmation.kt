package com.lumecard.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumecard.app.i18n.I18nManager
import com.lumecard.shared.data.EntityOperationType
import kotlin.time.Clock
import org.koin.compose.koinInject

class OperationConfirmationManager {
    private val snoozeUntil = mutableMapOf<EntityOperationType, Long>()

    fun isConfirmationNeeded(type: EntityOperationType): Boolean {
        val snoozedUntil = snoozeUntil[type] ?: return true
        return Clock.System.now().toEpochMilliseconds() > snoozedUntil
    }

    fun snooze(type: EntityOperationType) {
        snoozeUntil[type] = Clock.System.now().toEpochMilliseconds() + 60_000L
    }
}

@Composable
fun ConfirmOperationDialog(
    title: String,
    text: String,
    operationType: EntityOperationType,
    confirmationManager: OperationConfirmationManager,
    snoozeLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = koinInject<I18nManager>().strings
    var skipFor60s by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = skipFor60s,
                        onCheckedChange = { skipFor60s = it },
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(snoozeLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (skipFor60s) confirmationManager.snooze(operationType)
                onConfirm()
            }) {
                Text(strings.actionConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.actionCancel)
            }
        },
    )
}
