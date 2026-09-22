package com.example.methodmesh.modules.textdocuments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.method.LinkMovementMethod
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import android.widget.Toast
import io.noties.markwon.Markwon

private enum class DocumentMode { RENDERED, SOURCE }

/**
 * Module-owned full-screen document working surface.
 *
 * It is deliberately a document editor first. Save/share/find live here. The
 * MethodMesh capability return is an optional action supplied only by caller
 * origins that actually need a result.
 */
@Composable
fun DocumentSurface(
    title: String,
    text: String,
    format: DocumentFormat,
    writable: Boolean,
    dirty: Boolean,
    statusMessage: String? = null,
    closeLabel: String = "Close",
    returnLabel: String? = null,
    onTextChange: (String) -> Unit,
    onClose: () -> Unit,
    onSave: (String) -> Unit,
    onSaveAs: (String) -> Unit,
    onSaveAndClose: ((String) -> Unit)? = null,
    onSaveAsAndClose: ((String) -> Unit)? = null,
    onShare: (String) -> Unit,
    onSavePreset: (() -> Unit)? = null,
    onReturn: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val androidContext = LocalContext.current
    var mode by rememberSaveable(format.contractValue) {
        mutableStateOf(if (format == DocumentFormat.MARKDOWN) DocumentMode.RENDERED else DocumentMode.SOURCE)
    }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var findOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var closePrompt by rememberSaveable { mutableStateOf(false) }
    var editorValue by remember { mutableStateOf(TextFieldValue(text)) }

    LaunchedEffect(text) {
        if (editorValue.text != text) {
            val cursor = editorValue.selection.end.coerceIn(0, text.length)
            editorValue = TextFieldValue(text, selection = TextRange(cursor))
        }
    }

    fun requestClose() {
        if (dirty) closePrompt = true else onClose()
    }

    BackHandler { requestClose() }

    val lineCount = remember(text) { if (text.isEmpty()) 1 else text.count { it == '\n' } + 1 }
    val stateLabel = when {
        dirty -> "unsaved"
        writable -> "saved"
        else -> "not yet saved"
    }

    Surface(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = ::requestClose) { Text(closeLabel) }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        "${format.label} · $lineCount lines · ${text.length} chars · $stateLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (mode == DocumentMode.RENDERED) {
                    MarkdownDocument(text, Modifier.fillMaxSize())
                } else {
                    BasicTextField(
                        value = editorValue,
                        onValueChange = { next ->
                            editorValue = next
                            if (next.text != text) onTextChange(next.text)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState()),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (format == DocumentFormat.MARKDOWN) {
                            DropdownMenuItem(
                                text = { Text(if (mode == DocumentMode.RENDERED) "Edit source" else "Preview") },
                                onClick = {
                                    mode = if (mode == DocumentMode.RENDERED) DocumentMode.SOURCE else DocumentMode.RENDERED
                                    menuOpen = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Find / replace") },
                            onClick = { findOpen = true; menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = { onShare(text); menuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy all") },
                            onClick = { copyText(androidContext, title, text); menuOpen = false }
                        )
                        if (onSavePreset != null) {
                            DropdownMenuItem(
                                text = { Text("Save as preset") },
                                onClick = { onSavePreset(); menuOpen = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (writable) "Save" else "Save as…") },
                            onClick = {
                                if (writable) onSave(text) else onSaveAs(text)
                                menuOpen = false
                            }
                        )
                        if (writable) {
                            DropdownMenuItem(
                                text = { Text("Save a copy…") },
                                onClick = { onSaveAs(text); menuOpen = false }
                            )
                        }
                    }
                    FloatingActionButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                        Text("⋮", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(
                    onClick = { if (writable) onSave(text) else onSaveAs(text) },
                    modifier = Modifier.weight(1f)
                ) { Text(if (writable) "Save" else "Save as") }
                TextButton(
                    onClick = { copyText(androidContext, title, text) },
                    modifier = Modifier.weight(1f)
                ) { Text("Copy") }
                TextButton(
                    onClick = { onShare(text) },
                    modifier = Modifier.weight(1f)
                ) { Text("Share") }
                if (onSavePreset != null) {
                    TextButton(
                        onClick = onSavePreset,
                        modifier = Modifier.weight(1f)
                    ) { Text("Preset") }
                } else {
                    TextButton(
                        onClick = { findOpen = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Find") }
                }
            }

            if (findOpen) {
                FindReplaceBar(
                    text = text,
                    query = query,
                    replacement = replacement,
                    onQueryChange = { query = it },
                    onReplacementChange = { replacement = it },
                    onSelectMatch = { start, endExclusive ->
                        mode = DocumentMode.SOURCE
                        editorValue = editorValue.copy(selection = TextRange(start, endExclusive))
                    },
                    onReplaceMatch = { start, endExclusive, replacementText ->
                        val next = text.replaceRange(start, endExclusive, replacementText)
                        val cursor = start + replacementText.length
                        editorValue = TextFieldValue(next, selection = TextRange(cursor))
                        onTextChange(next)
                    },
                    onReplaceAll = {
                        if (query.isNotEmpty()) {
                            val pattern = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
                            val next = pattern.replace(text) { replacement }
                            editorValue = TextFieldValue(next)
                            onTextChange(next)
                        }
                    },
                    onClose = { findOpen = false }
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    statusMessage.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (statusMessage?.startsWith("Save failed") == true || statusMessage?.startsWith("Could not") == true) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
                if (onReturn != null && returnLabel != null) {
                    Button(onClick = { onReturn(text) }) { Text(returnLabel) }
                }
            }
        }
    }

    if (closePrompt) {
        AlertDialog(
            onDismissRequest = { closePrompt = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save your changes before leaving this document?") },
            confirmButton = {
                TextButton(onClick = {
                    closePrompt = false
                    if (writable) {
                        (onSaveAndClose ?: onSave)(text)
                    } else {
                        (onSaveAsAndClose ?: onSaveAs)(text)
                    }
                }) { Text(if (writable) "Save" else "Save as…") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { closePrompt = false }) { Text("Cancel") }
                    TextButton(onClick = { closePrompt = false; onClose() }) { Text("Discard") }
                }
            }
        )
    }
}

@Composable
private fun FindReplaceBar(
    text: String,
    query: String,
    replacement: String,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onSelectMatch: (start: Int, endExclusive: Int) -> Unit,
    onReplaceMatch: (start: Int, endExclusive: Int, replacement: String) -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    val matches = remember(query, text) {
        if (query.isBlank()) emptyList()
        else Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(text).map { it.range }.toList()
    }
    var currentIndex by remember(query, text) { mutableStateOf(-1) }
    val safeIndex = if (matches.isEmpty()) -1 else currentIndex.coerceIn(-1, matches.lastIndex)

    fun select(index: Int) {
        if (matches.isEmpty()) return
        val normalized = ((index % matches.size) + matches.size) % matches.size
        currentIndex = normalized
        val range = matches[normalized]
        onSelectMatch(range.first, range.last + 1)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = {
                    onQueryChange(it)
                    currentIndex = -1
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (query.isBlank()) Text("Find…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
            )
            Text(
                when {
                    matches.isEmpty() -> "0 matches"
                    safeIndex < 0 -> "${matches.size} matches"
                    else -> "${safeIndex + 1}/${matches.size}"
                },
                modifier = Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.labelSmall
            )
            TextButton(onClick = { select(if (safeIndex < 0) matches.lastIndex else safeIndex - 1) }, enabled = matches.isNotEmpty()) { Text("‹") }
            TextButton(onClick = { select(safeIndex + 1) }, enabled = matches.isNotEmpty()) { Text("›") }
            TextButton(onClick = onClose) { Text("Close") }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = replacement,
                onValueChange = onReplacementChange,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (replacement.isBlank()) Text("Replace with…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
            )
            TextButton(
                onClick = {
                    if (matches.isNotEmpty()) {
                        val index = if (safeIndex < 0) 0 else safeIndex
                        val range = matches[index]
                        onReplaceMatch(range.first, range.last + 1, replacement)
                    }
                },
                enabled = matches.isNotEmpty()
            ) { Text("Replace") }
            TextButton(onClick = onReplaceAll, enabled = matches.isNotEmpty()) { Text("All") }
        }
    }
}

@Composable
private fun MarkdownDocument(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val markwon = remember(context) { Markwon.create(context) }
    AndroidView(
        factory = { viewContext: Context ->
            TextView(viewContext).apply {
                textSize = 17f
                setLineSpacing(0f, 1.18f)
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
                setPadding(0, 0, 0, 48)
            }
        },
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        update = { view: TextView -> markwon.setMarkdown(view, markdown) }
    )
}

private fun copyText(context: Context, label: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied document text", Toast.LENGTH_SHORT).show()
}
