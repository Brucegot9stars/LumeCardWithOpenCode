package com.lumecard.app.ui.screens.splash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.components.LumeCardTopBar
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.data.SplashQuoteData
import com.lumecard.shared.data.SplashQuoteManager
import com.lumecard.shared.data.SplashQuotesCollection
import com.lumecard.app.platform.pickOpenFile
import com.lumecard.app.platform.pickSaveFile
import com.lumecard.app.platform.readFileContent
import com.lumecard.app.platform.writeFileContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject

class SplashQuoteListScreen : Screen {
    override val key: ScreenKey = "SplashQuoteList"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val strings = koinInject<I18nManager>().strings
        val vm: SplashQuoteViewModel = koinInject()
        val userQuotes by vm.userQuotes.collectAsState()
        val scope = rememberCoroutineScope()
        val spacing = LumeCardTheme.spacing
        val radius = LumeCardTheme.radius
        val snackbar = remember { SnackbarHostState() }
        val json = remember { Json { ignoreUnknownKeys = true; prettyPrint = true } }

        var showDeleteConfirm by remember { mutableStateOf(false) }
        var deletingIndex by remember { mutableStateOf(-1) }

        LaunchedEffect(Unit) { vm.load() }

        Scaffold(
            topBar = {
                LumeCardTopBar(
                    title = strings.splashQuoteTitle,
                    onBack = { navigator.pop() },
                    action = {
                        Row {
                            IconButton(onClick = {
                                scope.launch {
                                    val path = withContext(Dispatchers.IO) { pickOpenFile("application/json") }
                                    if (path != null) {
                                        val content = readFileContent(path)
                                        if (content != null) {
                                            try {
                                                val collection = json.decodeFromString<SplashQuotesCollection>(content)
                                                vm.importQuotes(collection, SplashQuoteManager.ImportMode.APPEND)
                                                snackbar.showSnackbar(strings.splashQuoteImportSuccess)
                                            } catch (_: Exception) {
                                                snackbar.showSnackbar(strings.splashQuoteImportError)
                                            }
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.FileUpload, contentDescription = strings.splashQuoteImport)
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    val path = withContext(Dispatchers.IO) { pickSaveFile("splash_quotes.json", "application/json") }
                                    if (path != null) {
                                        vm.exportQuotes { collection ->
                                            val content = json.encodeToString(SplashQuotesCollection.serializer(), collection)
                                            writeFileContent(path, content)
                                            scope.launch { snackbar.showSnackbar(strings.splashQuoteExportSuccess) }
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.FileDownload, contentDescription = strings.splashQuoteExport)
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navigator.push(SplashQuoteEditScreen(-1, null)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.splashQuoteAdd)
                }
            },
        ) { padding ->
            if (userQuotes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(strings.splashQuoteEmpty, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    itemsIndexed(userQuotes, key = { i, _ -> i }) { index, quote ->
                        QuoteItem(
                            quote = quote,
                            strings = strings,
                            onEdit = { navigator.push(SplashQuoteEditScreen(index, quote)) },
                            onDelete = {
                                deletingIndex = index; showDeleteConfirm = true
                            },
                        )
                    }
                }
            }
        }

        // ── Delete confirm ──────────────────────────────
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(strings.splashQuoteDeleteConfirm) },
                text = { Text(strings.splashQuoteDeleteConfirmDesc) },
                confirmButton = {
                    Button(onClick = { vm.deleteQuote(deletingIndex); showDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text(strings.actionDelete)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text(strings.actionCancel) }
                },
            )
        }
    }
}

@Composable
private fun QuoteItem(
    quote: SplashQuoteData,
    strings: com.lumecard.app.i18n.I18nStrings,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "“",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quote.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (quote.author.isNotBlank()) {
                    Text(
                        text = strings.splashQuoteAuthorPrefix(quote.author),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
