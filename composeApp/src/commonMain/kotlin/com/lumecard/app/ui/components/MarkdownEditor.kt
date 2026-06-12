package com.lumecard.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "输入Markdown内容...",
    label: String? = null
) {
    var isPreview by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // 工具栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !isPreview,
                onClick = { isPreview = false },
                label = { Text("编辑") }
            )
            FilterChip(
                selected = isPreview,
                onClick = { isPreview = true },
                label = { Text("预览") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isPreview) {
            // Markdown预览
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
            ) {
                Text(
                    text = value.ifBlank { "*暂无内容*" },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            // 编辑器
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                label = label?.let { { Text(it) } },
                placeholder = { Text(placeholder) },
                minLines = 10
            )
        }
    }
}
