package com.lumecard.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.AppVersion
import com.lumecard.shared.data.UpdateInfo
import org.koin.compose.koinInject

@Composable
fun UpdateCheckDialog(
    updateInfo: UpdateInfo?,
    isChecking: Boolean,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
) {
    val strings = koinInject<I18nManager>().strings
    val spacing = LumeCardTheme.spacing

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                when {
                    isChecking -> strings.updateChecking
                    updateInfo?.hasUpdate == true -> strings.updateAvailable
                    updateInfo != null -> strings.updateUpToDate
                    else -> strings.settingsCheckUpdate
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (isChecking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(spacing.sm))
                    Text(strings.updateCheckingDesc, style = MaterialTheme.typography.bodyMedium)
                } else if (updateInfo?.hasUpdate == true) {
                    Text(
                        "${strings.updateCurrentVersion}: ${AppVersion.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${strings.updateLatestVersion}: ${updateInfo.version}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (updateInfo.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(spacing.sm))
                        Text(strings.updateReleaseNotes, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            updateInfo.releaseNotes.take(500),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (updateInfo != null) {
                    Text(strings.updateUpToDateDesc, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${strings.updateCurrentVersion}: ${AppVersion.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            if (updateInfo?.hasUpdate == true) {
                Button(onClick = onUpdate) {
                    Text(strings.updateDownload)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.actionClose)
            }
        },
    )
}
