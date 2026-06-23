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
import com.lumecard.app.platform.getAppVersion
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.UpdateInfo
import com.lumecard.shared.data.UpdateState
import org.koin.compose.koinInject

@Composable
fun UpdateCheckDialog(
    updateState: UpdateState,
    onDismiss: () -> Unit,
    onCheckUpdate: () -> Unit,
    onUpdate: () -> Unit,
    onCancel: () -> Unit = {},
    onCopyError: (String) -> Unit = {},
) {
    val strings = koinInject<I18nManager>().strings
    val spacing = LumeCardTheme.spacing

    AlertDialog(
        onDismissRequest = { if (updateState !is UpdateState.Downloading) onDismiss() },
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
                when (updateState) {
                    is UpdateState.Checking -> strings.updateChecking
                    is UpdateState.UpdateAvailable -> strings.updateAvailable
                    is UpdateState.UpToDate -> strings.updateUpToDate
                    is UpdateState.Error -> strings.updateError
                    is UpdateState.Downloading -> strings.updateDownloading
                    is UpdateState.Installing -> strings.updateInstalling
                    is UpdateState.Complete -> strings.updateComplete
                    is UpdateState.Idle -> strings.settingsCheckUpdate
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                when (updateState) {
                    is UpdateState.Checking -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(spacing.sm))
                        Text(strings.updateCheckingDesc, style = MaterialTheme.typography.bodyMedium)
                    }
                    is UpdateState.UpdateAvailable -> {
                        Text(
                            "${strings.updateCurrentVersion}: ${getAppVersion()}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${strings.updateLatestVersion}: ${updateState.info.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (updateState.info.publishedAt.isNotBlank()) {
                            Text(
                                "${strings.updatePublishedAt}: ${updateState.info.publishedAt.take(10)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (updateState.info.releaseNotes.isNotBlank()) {
                            Spacer(Modifier.height(spacing.sm))
                            Text(strings.updateReleaseNotes, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                updateState.info.releaseNotes.take(500),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is UpdateState.UpToDate -> {
                        Text(strings.updateUpToDateDesc, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${strings.updateCurrentVersion}: ${getAppVersion()}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is UpdateState.Error -> {
                        Text(updateState.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            "${strings.updateCurrentVersion}: ${getAppVersion()}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is UpdateState.Downloading -> {
                        val progress = if (updateState.total > 0) {
                            updateState.downloaded.toFloat() / updateState.total
                        } else {
                            0f
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(spacing.sm))
                        Text(
                            if (updateState.total > 0) {
                                "${strings.updateDownloading} ${(progress * 100).toInt()}% (${formatBytes(updateState.downloaded)} / ${formatBytes(updateState.total)})"
                            } else {
                                "${strings.updateDownloading} ${formatBytes(updateState.downloaded)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is UpdateState.Installing -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(spacing.sm))
                        Text(strings.updateInstalling, style = MaterialTheme.typography.bodyMedium)
                    }
                    is UpdateState.Complete -> {
                        Text(strings.updateCompleteDesc, style = MaterialTheme.typography.bodyMedium)
                    }
                    is UpdateState.Idle -> {
                        Text(
                            "${strings.updateCurrentVersion}: ${getAppVersion()}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (updateState) {
                is UpdateState.Idle, is UpdateState.UpToDate -> {
                    TextButton(onClick = onCheckUpdate) {
                        Text(strings.settingsCheckUpdate)
                    }
                }
                is UpdateState.Error -> {
                    TextButton(onClick = {
                        val errorMsg = strings.updateErrorCopyFormat(getAppVersion(), updateState.message, kotlinx.datetime.Clock.System.now().toString())
                        onCopyError(errorMsg)
                        onDismiss()
                    }) {
                        Text(strings.updateCopyError)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onCheckUpdate) {
                        Text(strings.settingsCheckUpdate)
                    }
                }
                is UpdateState.UpdateAvailable -> {
                    Button(onClick = onUpdate) {
                        Text(strings.updateDownload)
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            when (updateState) {
                is UpdateState.Downloading -> {
                    TextButton(onClick = onCancel) {
                        Text(strings.actionCancel)
                    }
                }
                else -> {
                    TextButton(onClick = onDismiss) {
                        Text(strings.actionClose)
                    }
                }
            }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
    }
}
