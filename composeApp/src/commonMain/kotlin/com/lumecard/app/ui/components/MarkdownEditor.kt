package com.lumecard.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumecard.app.i18n.I18nManager
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null
) {
    val strings = koinInject<I18nManager>().strings
    var isPreview by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !isPreview,
                onClick = { isPreview = false },
                label = { Text(strings.actionEdit) }
            )
            FilterChip(
                selected = isPreview,
                onClick = { isPreview = true },
                label = { Text(strings.actionPreview) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isPreview) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
            ) {
                Text(
                    text = value.ifBlank { strings.editorEmptyPreview },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                label = label?.let { { Text(it) } },
                placeholder = { Text(placeholder ?: strings.editorPlaceholder) },
                minLines = 10
            )
        }
    }
}
