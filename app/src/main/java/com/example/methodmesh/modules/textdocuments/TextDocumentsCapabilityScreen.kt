package com.example.methodmesh.modules.textdocuments

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.CapabilityPreset
import com.example.methodmesh.core.protocols.PresetLaunchMode
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.core.protocols.ProtocolPayloadMode
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.launch
import org.json.JSONObject

object TextDocumentsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TextDocumentsMethod.ID
    override val title = "Text documents"
    override val description = "Create, open, edit, save, copy and share text, Markdown, JSON and JSON Lines files."
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val scope = rememberCoroutineScope()
        val repository = remember { TextDocumentRepository(appContext) }

        val directNativePreset = context.isNativePresetRun &&
            context.completionMode == CapabilityCompletionMode.ManualConfirmation
        val standaloneToolkit = context.presentationMode == CapabilityPresentationMode.Dashboard || directNativePreset
        val allowPresetSave = standaloneToolkit
        val isOdkLaunch = context.request.source.contains("odk", ignoreCase = true) ||
            context.request.invocationContext.caller.contains("odk", ignoreCase = true)
        val isProtocolOrSequence = context.request.settings["methodmesh_protocol_step_run"] == "true" ||
            context.request.settings["input_methodmesh_protocol_step_run"] == "true" ||
            context.request.settings["methodmesh_sequence_step_run"] == "true" ||
            context.request.settings["input_methodmesh_sequence_step_run"] == "true"

        val suppliedText = contextValue(context, "document_text")
        val suppliedTitle = contextValue(context, "document_title").ifBlank { "document.txt" }
        val suppliedFormat = DocumentFormat.fromContract(contextValue(context, "document_format"))
        val requestedMode = contextValue(context, "document_mode").trim().lowercase()
        val initialMode = when {
            requestedMode == "new" -> "new"
            requestedMode == "edit" -> "edit"
            requestedMode == "library" -> "library"
            suppliedText.isNotEmpty() -> "edit"
            else -> "library"
        }
        val callerRequestedEditor = initialMode == "new" || initialMode == "edit"
        val initialEditorOpen = callerRequestedEditor
        val initialText = if (initialMode == "new") "" else suppliedText

        var currentUri by rememberSaveable { mutableStateOf<String?>(null) }
        var currentTitle by rememberSaveable {
            mutableStateOf(if (initialEditorOpen) DocumentIo.ensureExtension(suppliedTitle, suppliedFormat) else "")
        }
        var currentFormatName by rememberSaveable {
            mutableStateOf(if (initialEditorOpen) suppliedFormat.contractValue else "")
        }
        var currentText by rememberSaveable { mutableStateOf(initialText) }
        var currentWritable by rememberSaveable { mutableStateOf(false) }
        var dirty by rememberSaveable { mutableStateOf(initialText.isNotEmpty() && initialMode == "new") }
        var editorOpen by rememberSaveable { mutableStateOf(initialEditorOpen) }
        var recentRevision by rememberSaveable { mutableStateOf(0) }
        var newMenuOpen by rememberSaveable { mutableStateOf(false) }
        var clipboardMenuOpen by rememberSaveable { mutableStateOf(false) }
        var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingSaveAsText by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingCloseAfterSaveAs by rememberSaveable { mutableStateOf(false) }
        var presetDialogOpen by rememberSaveable { mutableStateOf(false) }
        var presetDialogFromEditor by rememberSaveable { mutableStateOf(false) }

        fun leaveEditor() {
            when {
                standaloneToolkit || !callerRequestedEditor -> {
                    editorOpen = false
                    statusMessage = null
                }
                context.stepNumber > 1 -> onBack()
                else -> onCancel()
            }
        }

        fun openBuffer(title: String, format: DocumentFormat, text: String = "") {
            currentUri = null
            currentTitle = DocumentIo.ensureExtension(title, format)
            currentFormatName = format.contractValue
            currentText = text
            currentWritable = false
            dirty = text.isNotEmpty()
            editorOpen = true
            statusMessage = if (text.isEmpty()) "New document" else "New document from clipboard"
        }

        fun requestPresetDialog(fromEditor: Boolean) {
            presetDialogFromEditor = fromEditor
            presetDialogOpen = true
        }

        fun savePreset(draft: TextDocumentPresetDraft) {
            val format = DocumentFormat.fromContract(
                currentFormatName.ifBlank { suppliedFormat.contractValue }
            )
            val suggestedTitle = DocumentIo.ensureExtension(
                currentTitle.ifBlank { "document.${format.extension}" },
                format
            )
            val settings = linkedMapOf<String, String>()
            val description = when (draft.behavior) {
                TextDocumentPresetBehavior.LIBRARY -> {
                    settings["document_mode"] = "library"
                    "Open the Text documents toolkit."
                }
                TextDocumentPresetBehavior.NEW -> {
                    settings["document_mode"] = "new"
                    settings["document_title"] = suggestedTitle
                    settings["document_format"] = format.contractValue
                    "Create a new ${format.label.lowercase()} document."
                }
                TextDocumentPresetBehavior.TEMPLATE -> {
                    settings["document_mode"] = "edit"
                    settings["document_title"] = suggestedTitle
                    settings["document_format"] = format.contractValue
                    settings["document_text"] = currentText
                    "Create an editable document from the saved text template."
                }
            }
            val json = JSONObject().apply {
                settings.forEach { (key, value) -> put(key, value) }
            }.toString()
            val saved = ProtocolLibraryRepository.savePreset(
                appContext,
                CapabilityPreset(
                    name = draft.name,
                    methodId = capabilityId,
                    settingsJson = json,
                    payloadMode = ProtocolPayloadMode.CORE,
                    resultAction = PresetResultAction.HOME,
                    launchMode = PresetLaunchMode.INTERACTIVE,
                    description = description
                )
            )
            presetDialogOpen = false
            statusMessage = "Saved preset: ${saved.name}"
        }

        val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                DocumentIo.retainAccess(
                    appContext,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                scope.launch {
                    DocumentIo.read(appContext, uri).fold(
                        onSuccess = { document ->
                            currentUri = uri.toString()
                            currentTitle = document.title
                            currentFormatName = document.format.contractValue
                            currentText = document.text
                            currentWritable = document.writable
                            dirty = false
                            editorOpen = true
                            statusMessage = null
                            repository.remember(uri, document.title, document.format.mimeType)
                            recentRevision++
                        },
                        onFailure = { error ->
                            statusMessage = "Could not open document. ${DocumentIo.userFacingReadError(error)}"
                        }
                    )
                }
            }
        }

        val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            val uri = activityResult.data?.data
            val body = pendingSaveAsText
            pendingSaveAsText = null
            val closeAfter = pendingCloseAfterSaveAs
            pendingCloseAfterSaveAs = false
            if (uri != null && body != null) {
                val flags = activityResult.data?.flags ?: 0
                DocumentIo.retainAccess(appContext, uri, flags)
                scope.launch {
                    DocumentIo.write(appContext, uri, body).fold(
                        onSuccess = {
                            val newTitle = DocumentIo.displayName(appContext, uri) ?: currentTitle
                            val format = DocumentFormat.detect(newTitle, appContext.contentResolver.getType(uri))
                            currentUri = uri.toString()
                            currentTitle = newTitle
                            currentFormatName = format.contractValue
                            currentWritable = true
                            currentText = body
                            dirty = false
                            statusMessage = "Saved"
                            repository.remember(uri, currentTitle, format.mimeType)
                            recentRevision++
                            if (closeAfter) leaveEditor()
                        },
                        onFailure = { error -> statusMessage = "Save failed. ${DocumentIo.userFacingWriteError(error)}" }
                    )
                }
            }
        }

        fun requestSaveAs(body: String, closeAfter: Boolean = false) {
            val format = DocumentFormat.fromContract(currentFormatName)
            pendingSaveAsText = body
            pendingCloseAfterSaveAs = closeAfter
            saveAs.launch(DocumentIo.createIntent(format, currentTitle))
        }

        fun save(body: String, closeAfter: Boolean = false) {
            val uri = currentUri?.let(Uri::parse)
            if (uri == null || !currentWritable) {
                requestSaveAs(body, closeAfter)
                return
            }
            scope.launch {
                DocumentIo.write(appContext, uri, body).fold(
                    onSuccess = {
                        currentText = body
                        dirty = false
                        statusMessage = "Saved"
                        repository.remember(uri, currentTitle, DocumentFormat.fromContract(currentFormatName).mimeType)
                        recentRevision++
                        if (closeAfter) leaveEditor()
                    },
                    onFailure = { error -> statusMessage = "Save failed. ${DocumentIo.userFacingWriteError(error)}" }
                )
            }
        }

        fun returnDocument(body: String) {
            val format = DocumentFormat.fromContract(currentFormatName)
            val request = As100TextDocumentsMethod.request(
                action = capabilityId,
                context = context.request.invocationContext.asMap(capabilityId) +
                    context.action.settings +
                    mapOf(
                        "document_text" to body,
                        "document_title" to currentTitle.ifBlank { "document.${format.extension}" },
                        "document_format" to format.contractValue,
                        "document_mode" to "edit"
                    ),
                signals = emptyList(),
                inputs = emptyList()
            )
            onConfirmed(
                As100TextDocumentsMethod.result(
                    request,
                    As100TextDocumentsMethod.success(
                        body,
                        currentTitle.ifBlank { "document.${format.extension}" },
                        format.contractValue
                    ),
                    context.request.invocationContext
                )
            )
        }

        if (editorOpen) {
            val format = DocumentFormat.fromContract(currentFormatName)
            val returnLabel = when {
                standaloneToolkit -> null
                isOdkLaunch -> "Return to form"
                isProtocolOrSequence -> "Continue"
                else -> "Return result"
            }
            DocumentSurface(
                title = currentTitle.ifBlank { "Document" },
                text = currentText,
                format = format,
                writable = currentWritable,
                dirty = dirty,
                statusMessage = statusMessage,
                closeLabel = when {
                    standaloneToolkit -> "Library"
                    callerRequestedEditor -> "Cancel"
                    else -> "Choose another"
                },
                returnLabel = returnLabel,
                onTextChange = {
                    currentText = it
                    dirty = true
                    statusMessage = null
                },
                onClose = ::leaveEditor,
                onSave = { body -> save(body) },
                onSaveAs = { body -> requestSaveAs(body) },
                onSaveAndClose = { body -> save(body, closeAfter = true) },
                onSaveAsAndClose = { body -> requestSaveAs(body, closeAfter = true) },
                onShare = { body ->
                    DocumentSharing.share(appContext, currentTitle, format, body)
                        .onFailure { statusMessage = "Could not share document." }
                },
                onSavePreset = if (allowPresetSave) ({ requestPresetDialog(fromEditor = true) }) else null,
                onReturn = if (returnLabel == null) null else ::returnDocument
            )

            if (presetDialogOpen) {
                SaveTextDocumentPresetDialog(
                    defaultName = if (currentTitle.isBlank()) "Text documents" else "New ${format.label} document",
                    defaultBehavior = TextDocumentPresetBehavior.NEW,
                    allowTemplate = currentText.isNotEmpty(),
                    format = format,
                    onDismiss = { presetDialogOpen = false },
                    onSave = ::savePreset
                )
            }
            return
        }

        val recent = remember(recentRevision) { repository.recent() }

        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Text documents", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                when {
                                    standaloneToolkit -> "A small offline editor for plain text, Markdown, JSON and JSON Lines."
                                    isOdkLaunch -> "Choose or create the document to return to your form."
                                    else -> "Choose or create the document to return to the calling workflow."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { if (context.stepNumber > 1) onBack() else onCancel() }) { Text("Close") }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Button(onClick = { newMenuOpen = true }) { Text("New") }
                            DropdownMenu(expanded = newMenuOpen, onDismissRequest = { newMenuOpen = false }) {
                                DocumentFormat.values().forEach { format ->
                                    DropdownMenuItem(
                                        text = { Text(format.label) },
                                        onClick = {
                                            newMenuOpen = false
                                            openBuffer("document.${format.extension}", format)
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                openDocument.launch(
                                    arrayOf(
                                        "text/plain",
                                        "text/markdown",
                                        "application/json",
                                        "text/json",
                                        "application/x-ndjson",
                                        "application/ndjson",
                                        "application/jsonl"
                                    )
                                )
                            }
                        ) { Text("Open file") }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            OutlinedButton(onClick = { clipboardMenuOpen = true }) { Text("New from clipboard…") }
                            DropdownMenu(expanded = clipboardMenuOpen, onDismissRequest = { clipboardMenuOpen = false }) {
                                DocumentFormat.values().forEach { format ->
                                    DropdownMenuItem(
                                        text = { Text(format.label) },
                                        onClick = {
                                            clipboardMenuOpen = false
                                            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clipText = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                                                ?.getItemAt(0)?.coerceToText(appContext)?.toString().orEmpty()
                                            openBuffer("clipboard.${format.extension}", format, clipText)
                                        }
                                    )
                                }
                            }
                        }
                        if (allowPresetSave) {
                            OutlinedButton(onClick = { requestPresetDialog(fromEditor = false) }) {
                                Text("Save as preset")
                            }
                        }
                    }
                }

                statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    item {
                        Text(
                            message,
                            color = if (message.startsWith("Could not") || message.startsWith("Save failed")) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(2.dp))
                    Text("Recent files", style = MaterialTheme.typography.titleMedium)
                }

                if (recent.isEmpty()) {
                    item { Text("Files you open or save here will appear in this list.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(recent, key = { it.uri }) { document ->
                        Card(
                            Modifier.fillMaxWidth().clickable {
                                val uri = Uri.parse(document.uri)
                                scope.launch {
                                    DocumentIo.read(appContext, uri, document.mimeType).fold(
                                        onSuccess = { opened ->
                                            currentUri = uri.toString()
                                            currentTitle = opened.title
                                            currentFormatName = opened.format.contractValue
                                            currentText = opened.text
                                            currentWritable = opened.writable
                                            dirty = false
                                            editorOpen = true
                                            statusMessage = null
                                            repository.remember(uri, opened.title, opened.format.mimeType)
                                            recentRevision++
                                        },
                                        onFailure = {
                                            repository.forget(document.uri)
                                            recentRevision++
                                            statusMessage = "${document.title} is no longer available. Open it again from the source app."
                                        }
                                    )
                                }
                            }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(document.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    DocumentFormat.detect(document.title, document.mimeType).label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (presetDialogOpen) {
            val format = suppliedFormat
            SaveTextDocumentPresetDialog(
                defaultName = "Text documents",
                defaultBehavior = if (presetDialogFromEditor) TextDocumentPresetBehavior.NEW else TextDocumentPresetBehavior.LIBRARY,
                allowTemplate = presetDialogFromEditor && currentText.isNotEmpty(),
                format = format,
                onDismiss = { presetDialogOpen = false },
                onSave = ::savePreset
            )
        }
    }
}

private fun contextValue(context: CapabilityScreenContext, key: String): String =
    context.action.settings[key]
        ?: context.action.settings["input_$key"]
        ?: context.request.settings[key]
        ?: context.request.settings["input_$key"]
        ?: ""
