package com.lumecard.app.ui.screens.card

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumecard.app.i18n.I18nStrings
import com.lumecard.app.platform.getMediaBasePath
import com.lumecard.app.platform.pickMediaFile
import com.lumecard.app.platform.saveMediaFile
import com.lumecard.shared.data.generateId
import com.lumecard.shared.model.Media
import com.lumecard.shared.model.MediaType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.text.RegexOption

/** Requires an https URL with a real host; rejects bare schemes and malformed links. */
internal val HTTPS_IMAGE_URL = Regex(
    """^https://[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?(?::\d{1,5})?(?:[/?#][^\s]*)?$""",
    RegexOption.IGNORE_CASE
)

/**
 * Card image editor: add local images (saved into the media dir with SHA-1 dedup) or https URLs,
 * set a caption, choose the card-level mediaTarget, and remove entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardImageEditor(
    media: List<Media>,
    onMediaChange: (List<Media>) -> Unit,
    mediaTarget: String,
    onMediaTargetChange: (String) -> Unit,
    strings: I18nStrings,
) {
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var captionInput by remember { mutableStateOf("") }
    var isPicking by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(strings.cardImageSection, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(
                strings.cardImageHttpsOnly,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                strings.cardImageLocalHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (isPicking) return@Button
                        isPicking = true
                        scope.launch {
                            try {
                                val picked = pickMediaFile()
                                if (picked != null) {
                                    val ref = withContext(Dispatchers.IO) {
                                        saveMediaFile(getMediaBasePath(), picked)
                                    }
                                    val fileName = ref?.let { extractMediaFileName(it) }
                                    if (fileName != null && media.none { it.filePath == fileName }) {
                                        onMediaChange(
                                            media + Media(
                                                id = generateId("img"),
                                                type = MediaType.IMAGE,
                                                filePath = fileName,
                                                caption = captionInput
                                            )
                                        )
                                        captionInput = ""
                                    }
                                }
                            } finally {
                                isPicking = false
                            }
                        }
                    },
                    enabled = !isPicking,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(strings.cardImageAddLocal)
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it; urlError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(strings.cardImageUrlLabel) },
                placeholder = { Text("https://example.com/image.png") },
                singleLine = true,
                isError = urlError != null,
                supportingText = urlError?.let { { Text(it) } }
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = captionInput,
                onValueChange = { captionInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(strings.cardImageCaption) },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val trimmed = urlInput.trim()
                    if (!HTTPS_IMAGE_URL.matches(trimmed)) {
                        urlError = strings.cardImageInvalidUrl
                        return@Button
                    }
                    if (media.none { it.filePath == trimmed }) {
                        onMediaChange(
                            media + Media(
                                id = generateId("img"),
                                type = MediaType.IMAGE,
                                filePath = trimmed,
                                caption = captionInput
                            )
                        )
                        urlInput = ""
                        captionInput = ""
                        urlError = null
                    } else {
                        urlError = strings.cardImageInvalidUrl
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(strings.cardImageAddUrl)
            }

            Spacer(Modifier.height(12.dp))
            Text(strings.cardImageTarget, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = listOf(
                    "both" to strings.cardImageTargetBoth,
                    "front" to strings.cardImageTargetFront,
                    "back" to strings.cardImageTargetBack
                )
                options.forEach { (value, label) ->
                    FilterChip(
                        selected = mediaTarget == value,
                        onClick = { onMediaTargetChange(value) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            if (media.isEmpty()) {
                Text(
                    strings.cardImageEmpty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                media.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.filePath,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        if (item.caption.isNotBlank()) {
                            Text(
                                item.caption,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        IconButton(onClick = { onMediaChange(media - item) }) {
                            Icon(Icons.Default.Delete, contentDescription = strings.cardImageRemove)
                        }
                    }
                }
            }
        }
    }
}

/** Extract "hash.png" from "![image](hash.png)", or accept a bare relative file name. */
private fun extractMediaFileName(ref: String): String? {
    val start = ref.indexOf('(')
    val end = ref.lastIndexOf(')')
    val name = if (start >= 0 && end > start + 1) {
        ref.substring(start + 1, end)
    } else {
        ref.trim()
    }
    return name.trim().takeIf {
        it.isNotBlank() && !it.contains("://") && !it.contains('(') && !it.contains(')')
    }
}
