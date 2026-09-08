package com.example.methodmesh.modules.referencelibrary

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.CapabilityPreset
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.ui.artifacts.ArtifactPicker
import com.example.methodmesh.core.artifacts.ArtifactLifecycle
import com.example.methodmesh.core.artifacts.ArtifactPickerRequest
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

object ReferenceLibraryCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ReferenceLibraryMethod.ID
    override val title = "Reference library"
    override val description = "Useful documents, ready when the network is not."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val repository = remember { ReferenceLibraryRepository(appContext) }
        // The library is a persistent workspace whenever the host is giving the user
        // control. Keep that workspace inline even when a native preset opened it with
        // an intent-style presentation; the generic Result page exposes implementation
        // fields (URI, artifact ref, shelf metadata) that are not useful for browsing.
        // Automatic-return callers such as ODK and protocol dependencies still use the
        // capability result contract below.
        val dashboardMode = context.completionMode == CapabilityCompletionMode.ManualConfirmation
        // Reading and selecting are deliberately different operations. Any manual
        // UI is allowed to browse/read without creating an ExecutionResult.
        // Automatic-return callers (ODK/protocol dependency flows) keep tap-to-return.
        val manualReadingMode = context.completionMode == CapabilityCompletionMode.ManualConfirmation
        // Native preset runs still need an explicit hand-off, but the hand-off happens
        // from the library row rather than by navigating to the generic Result page.
        val explicitUseAction = manualReadingMode && context.isNativePresetRun
        var documents by remember { mutableStateOf(repository.documents()) }
        var customShelves by remember { mutableStateOf(repository.customShelves()) }
        var query by rememberSaveable { mutableStateOf(context.action.settings["query"] ?: context.action.settings["input_query"] ?: "") }
        var shelf by rememberSaveable { mutableStateOf(context.action.settings["shelf"] ?: context.action.settings["input_shelf"] ?: "all") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingShelf by rememberSaveable { mutableStateOf("personal") }
        var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var scannerActive by rememberSaveable { mutableStateOf(false) }
        var peerManagerActive by rememberSaveable { mutableStateOf(false) }
        var scannerHandled by rememberSaveable { mutableStateOf(false) }
        var pendingScanUri by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingScanName by rememberSaveable { mutableStateOf("") }
        var pendingScanShelf by rememberSaveable { mutableStateOf("personal") }
        var editingDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
        var editDocumentTitle by rememberSaveable { mutableStateOf("") }
        var editDocumentShelf by rememberSaveable { mutableStateOf("personal") }
        var manageShelves by rememberSaveable { mutableStateOf(false) }
        var addFromFiles by rememberSaveable { mutableStateOf(false) }
        var newShelfName by rememberSaveable { mutableStateOf("") }
        var shelfToDelete by rememberSaveable { mutableStateOf<String?>(null) }
        val allShelves = BUILT_IN_SHELVES + customShelves
        fun shelfLabel(id: String): String = allShelves.firstOrNull { it.id == id }?.label ?: id.labelForShelf()

        fun executionFor(values: Map<String, String>): ExecutionResult {
            val request = As100ReferenceLibraryMethod.request(
                action = As100ReferenceLibraryMethod.ID,
                context = context.request.invocationContext.asMap(As100ReferenceLibraryMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            return As100ReferenceLibraryMethod.result(request, values, context.request.invocationContext)
        }

        fun selectDocument(document: LibraryDocument) {
            selectedId = document.id
            repository.markOpened(document.id)
            documents = repository.documents()
            result = executionFor(As100ReferenceLibraryMethod.selected(document))
            statusMessage = "Selected ${document.title}"
            if (context.submitsImmediately) result?.let(onConfirmed)
        }

        fun launchViewer(document: LibraryDocument) {
            // Keep external readers in the same Android task. Using NEW_TASK here
            // can make Back reveal an older MethodMesh result activity instead of
            // the live Reference Library dashboard.
            runCatching {
                appContext.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(document.uri), document.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            }.onFailure {
                statusMessage = "No installed app can open this document type."
            }
        }

        fun shareDocument(document: LibraryDocument) {
            if (!repository.isReadable(document)) {
                statusMessage = "Document unavailable. Re-import it or restore access to its storage location."
                return
            }
            val uri = Uri.parse(document.uri)
            runCatching {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = document.mimeType.ifBlank { "application/octet-stream" }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(appContext.contentResolver, document.title, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                appContext.startActivity(
                    Intent.createChooser(sendIntent, "Share document").apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            }.onFailure {
                statusMessage = "No installed app can share this document."
            }
        }

        fun openDocument(document: LibraryDocument) {
            if (!repository.isReadable(document)) {
                if (manualReadingMode) {
                    // A failed read is an inline library message, not a capability result.
                    selectedId = null
                    result = null
                    statusMessage = "Document unavailable. Re-import it or restore access to its storage location."
                    return
                }
                val failed = executionFor(
                    As100ReferenceLibraryMethod.failure(
                        documentId = document.id,
                        shelf = document.shelf,
                        status = "unavailable",
                        error = "This document is no longer readable. Re-import it or restore access to its storage location."
                    )
                )
                result = failed
                statusMessage = "Document unavailable"
                if (context.submitsImmediately) onConfirmed(failed)
                return
            }

            if (manualReadingMode) {
                // Reading is navigation, not capture. Clear any stale result before
                // opening an external reader so Back always reveals the library UI.
                selectedId = null
                result = null
                repository.markOpened(document.id)
                documents = repository.documents()
                statusMessage = null
                launchViewer(document)
                return
            }

            // Automatic-return callers such as ODK use a tap as the document
            // selection and return it immediately without opening a reader.
            selectDocument(document)
        }

        if (scannerActive) {
            val scannerScreen = remember { MethodMeshModuleRegistry.screenFor("document.scan") }
            if (scannerScreen == null) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Document scanner unavailable", style = MaterialTheme.typography.titleLarge)
                        Text("Install or enable the MethodMesh Document Scanner module, then try again.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { scannerActive = false }) { Text("Back to library") }
                    }
                }
            } else {
                val scannerAction = remember {
                    ExternalActionRequest(
                        requestedId = "document.scan",
                        settings = mapOf(
                            "page_limit" to "20",
                            "scanner_mode" to "full",
                            "allow_gallery_import" to "true",
                            "run_ocr" to "true",
                            "return_searchable_pdf" to "true",
                            "return_text_file" to "false"
                        )
                    )
                }
                val scannerContext = context.copy(
                    action = scannerAction,
                    request = context.request.copy(
                        actions = listOf(scannerAction),
                        source = "reference_library_dependency"
                    ),
                    stepNumber = 1,
                    totalSteps = 1,
                    completionMode = CapabilityCompletionMode.AutomaticReturn,
                    presentationMode = CapabilityPresentationMode.Dashboard,
                    onSettingsChanged = {}
                )
                scannerScreen.Render(
                    context = scannerContext,
                    onBack = { scannerActive = false },
                    onConfirmed = { scanResult ->
                        if (!scannerHandled) {
                            scannerHandled = true
                            val fields = OutputFormatter.fields(scanResult, includeProvenance = false)
                            val scannedUri = fields["document_scan_searchable_pdf_uri"]?.toString().orEmpty()
                                .ifBlank { fields["document_scan_pdf_uri"]?.toString().orEmpty() }
                            if (scannedUri.isBlank()) {
                                scannerActive = false
                                statusMessage = "The scanner completed but did not return a PDF."
                            } else {
                                pendingScanUri = scannedUri
                                pendingScanName = "Scanned document"
                                pendingScanShelf = if (shelf == "all") "personal" else shelf
                                scannerActive = false
                            }
                        }
                    },
                    onCancel = {
                        scannerHandled = true
                        scannerActive = false
                        statusMessage = "Scan cancelled"
                    }
                )
            }
            return
        }

        if (peerManagerActive) {
            val peerAction = remember {
                ExternalActionRequest(
                    requestedId = As100ReferenceLibraryPeerMethod.ID,
                    canonicalId = As100ReferenceLibraryPeerMethod.ID,
                    settings = mapOf(
                        "network_mode" to "local_hotspot",
                        "session_minutes" to "30",
                        "max_file_mb" to "250",
                        "default_shelf" to (if (shelf == "all") "personal" else shelf),
                        "allow_edits" to "true"
                    )
                )
            }
            val peerContext = context.copy(
                action = peerAction,
                request = context.request.copy(
                    actions = listOf(peerAction),
                    source = "reference_library_dependency"
                ),
                stepNumber = 1,
                totalSteps = 1,
                completionMode = CapabilityCompletionMode.ManualConfirmation,
                presentationMode = CapabilityPresentationMode.Dashboard,
                onSettingsChanged = {}
            )
            ReferenceLibraryPeerCapabilityScreen.Render(
                context = peerContext,
                onBack = {
                    peerManagerActive = false
                    documents = repository.documents()
                    customShelves = repository.customShelves()
                },
                onConfirmed = { resultValue ->
                    peerManagerActive = false
                    documents = repository.documents()
                    customShelves = repository.customShelves()
                    val fields = OutputFormatter.fields(resultValue, includeProvenance = false)
                    val count = fields[ReferenceLibraryPeerFields.UPLOADED_COUNT]?.toString()?.toIntOrNull() ?: 0
                    statusMessage = if (count > 0) "Nearby session added $count document${if (count == 1) "" else "s"}" else "Nearby session finished"
                },
                onCancel = {
                    peerManagerActive = false
                    documents = repository.documents()
                    customShelves = repository.customShelves()
                }
            )
            return
        }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            val permissionPersisted = runCatching {
                appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            }.getOrDefault(false)

            val name = runCatching {
                appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull().orEmpty().ifBlank { "Imported document" }
            val mime = appContext.contentResolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
            val imported = repository.importDocument(name, pendingShelf, uri, mime)
            documents = repository.documents()
            statusMessage = if (permissionPersisted) "Added to ${pendingShelf.labelForShelf()}" else "Added; long-term access depends on the document provider"
            if (!dashboardMode) {
                selectDocument(imported)
            } else {
                selectedId = null
                result = null
            }
        }

        val batchPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            var managedFallbacks = 0
            var failures = 0
            uris.forEach { uri ->
                runCatching {
                    val permissionPersisted = runCatching {
                        appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        true
                    }.getOrDefault(false)
                    val name = runCatching {
                        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull().orEmpty().ifBlank { "Imported document" }
                    val mime = appContext.contentResolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
                    if (permissionPersisted) {
                        repository.importDocument(name, pendingShelf, uri, mime)
                    } else {
                        repository.importManagedCopy(
                            sourceUri = uri,
                            title = name,
                            shelf = pendingShelf,
                            mimeType = mime,
                            source = "Batch import",
                            storageFolder = "imports"
                        )
                        managedFallbacks += 1
                    }
                }.onFailure { failures += 1 }
            }
            documents = repository.documents()
            selectedId = null
            result = null
            val added = uris.size - failures
            statusMessage = buildString {
                append("Added $added document${if (added == 1) "" else "s"} to ${pendingShelf.labelForShelf()}")
                if (managedFallbacks > 0) append(" · $managedFallbacks copied into managed storage")
                if (failures > 0) append(" · $failures failed")
            }
        }

        LaunchedEffect(query, shelf) {
            context.onSettingsChanged(mapOf("query" to query, "shelf" to shelf))
        }

        LaunchedEffect(context.presentationMode, context.action.settings, documents) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && !launched) {
                launched = true
                val targetId = context.action.settings["document_id"] ?: context.action.settings["input_document_id"]
                if (!targetId.isNullOrBlank()) {
                    val target = documents.firstOrNull { it.id == targetId }
                    if (target != null) {
                        if (repository.isReadable(target)) {
                            if (context.isNativePresetRun) openDocument(target) else selectDocument(target)
                        } else {
                            val failed = executionFor(
                                As100ReferenceLibraryMethod.failure(
                                    documentId = targetId,
                                    shelf = target.shelf,
                                    status = "unavailable",
                                    error = "The requested reference document is no longer readable on this device."
                                )
                            )
                            result = failed
                            statusMessage = "Requested document unavailable"
                            if (context.submitsImmediately) onConfirmed(failed)
                        }
                    } else {
                        val failed = executionFor(
                            As100ReferenceLibraryMethod.failure(
                                documentId = targetId,
                                shelf = shelf,
                                status = "not_found",
                                error = "The requested reference document ID is not installed on this device."
                            )
                        )
                        result = failed
                        statusMessage = "Requested document not found"
                        if (context.submitsImmediately) onConfirmed(failed)
                    }
                }
            }
        }

        val visible = documents
            .filter { shelf == "all" || it.shelf == shelf }
            .filter {
                query.isBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.source.contains(query, ignoreCase = true) ||
                    it.shelf.contains(query.replace(' ', '_'), ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<LibraryDocument> { it.favourite }
                    .thenByDescending { it.lastOpenedEpochMs }
                    .thenBy { it.title.lowercase() }
            )
        val recent = documents.filter { it.lastOpenedEpochMs > 0 }.sortedByDescending { it.lastOpenedEpochMs }.take(5)

        val libraryContent: @Composable () -> Unit = {
            val darkTheme = isSystemInDarkTheme()
            val readingRoom = if (darkTheme) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
            } else {
                MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f)
            }
            val readingRoomText = if (darkTheme) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.inverseOnSurface
            }
            val readingRoomMuted = readingRoomText.copy(alpha = 0.68f)

            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${documents.size} document${if (documents.size == 1) "" else "s"}  ·  ${allShelves.size} shelves",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (dashboardMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { peerManagerActive = true }) { Text("Nearby") }
                            TextButton(onClick = { manageShelves = true }) { Text("Shelves") }
                        }
                    }
                }

                Spacer(Modifier.height(13.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search your library") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(Modifier.height(11.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(LibraryShelf("all", "All")) + allShelves, key = { it.id }) { item ->
                        val count = if (item.id == "all") documents.size else documents.count { it.shelf == item.id }
                        ShelfPill(
                            text = if (count > 0) "${item.label}  $count" else item.label,
                            selected = shelf == item.id,
                            onClick = { shelf = item.id }
                        )
                    }
                }

                if (recent.isNotEmpty() && query.isBlank() && shelf == "all") {
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = readingRoom
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 17.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Continue reading",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = readingRoomText
                                )
                                Text(
                                    "RECENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(recent, key = { it.id }) { document ->
                                    DocumentTile(
                                        document = document,
                                        selected = document.id == selectedId,
                                        shelfLabel = shelfLabel(document.shelf),
                                        onOpen = { openDocument(document) },
                                        darkSurface = true,
                                        contentColor = readingRoomText,
                                        mutedColor = readingRoomMuted
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(if (shelf == "all") "Library" else shelfLabel(shelf))
                    if (visible.isNotEmpty()) {
                        Text(
                            visible.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (visible.isEmpty()) {
                    EmptyLibraryCard(
                        filtered = documents.isNotEmpty(),
                        onImport = {
                            pendingShelf = if (shelf == "all") "personal" else shelf
                            if (dashboardMode) batchPicker.launch(arrayOf("*/*")) else picker.launch(arrayOf("*/*"))
                        }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = if (darkTheme) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                        }
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 5.dp)) {
                            visible.forEachIndexed { index, document ->
                                LibraryRow(
                                    document = document,
                                    selected = document.id == selectedId,
                                    onOpen = { openDocument(document) },
                                    shelfLabel = shelfLabel(document.shelf),
                                    onFavourite = {
                                        repository.toggleFavourite(document.id)
                                        documents = repository.documents()
                                    },
                                    onEdit = {
                                        editingDocumentId = document.id
                                        editDocumentTitle = document.title
                                        editDocumentShelf = document.shelf
                                    },
                                    onCreatePreset = {
                                        val saved = ProtocolLibraryRepository.savePreset(
                                            appContext,
                                            CapabilityPreset(
                                                name = "Open ${document.title}",
                                                methodId = As100ReferenceLibraryMethod.ID,
                                                settingsJson = org.json.JSONObject().apply {
                                                    put("document_id", document.id)
                                                }.toString(),
                                                description = "Open ${document.title} from the Reference Library."
                                            )
                                        )
                                        statusMessage = "Preset saved: ${saved.name}"
                                    },
                                    onUse = if (explicitUseAction) {
                                        { selectDocument(document) }
                                    } else null,
                                    onShare = { shareDocument(document) },
                                    onRemove = {
                                        repository.remove(document.id)
                                        if (selectedId == document.id) {
                                            selectedId = null
                                            result = null
                                        }
                                        documents = repository.documents()
                                        statusMessage = "Removed from library; original file was not deleted"
                                    }
                                )
                                if (index < visible.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                                    )
                                }
                            }
                        }
                    }
                }

                statusMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
                    ) {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                if (dashboardMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Button(
                            onClick = {
                                scannerHandled = false
                                scannerActive = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp)
                        ) { Text("Scan") }
                        OutlinedButton(
                            onClick = {
                                pendingShelf = if (shelf == "all") "personal" else shelf
                                batchPicker.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp)
                        ) { Text("Add files") }
                    }
                    TextButton(
                        onClick = { addFromFiles = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add from Files") }
                } else {
                    Button(
                        onClick = {
                            pendingShelf = if (shelf == "all") "personal" else shelf
                            picker.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp)
                    ) { Text("Add document") }
                }
            }
        }

        if (dashboardMode) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                libraryContent()
            }
        } else {
            CapabilityScreenScaffold(
                title = title,
                capabilityId = capabilityId,
                context = context,
                canGoBack = context.stepNumber > 1,
                capturedResult = result,
                resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
                onBack = onBack,
                onRetry = { selectedId?.let(repository::document)?.let(::selectDocument) },
                onConfirm = { result?.let(onConfirmed) },
                onCancel = onCancel,
                content = libraryContent
            )
        }

        if (dashboardMode && addFromFiles) {
            AlertDialog(
                onDismissRequest = { addFromFiles = false },
                title = { Text("Add from Files") },
                text = {
                    ArtifactPicker(
                        request = ArtifactPickerRequest(lifecycles = setOf(ArtifactLifecycle.PERSISTENT)),
                        onSelected = { ref ->
                            runCatching {
                                val artifact = AndroidArtifacts.service(appContext).resolve(ref)
                                repository.importArtifact(
                                    artifact = artifact,
                                    shelf = if (shelf == "all") "personal" else shelf
                                )
                            }.onSuccess { added ->
                                documents = repository.documents()
                                statusMessage = "Added ${added.title} to ${shelfLabel(added.shelf)}"
                                addFromFiles = false
                            }.onFailure { error ->
                                statusMessage = "Could not add file: ${error.message ?: "file unavailable"}"
                            }
                        }
                    )
                },
                confirmButton = { TextButton(onClick = { addFromFiles = false }) { Text("Done") } }
            )
        }

        if (dashboardMode && pendingScanUri != null) {
            AlertDialog(
                onDismissRequest = {
                    pendingScanUri = null
                    pendingScanName = ""
                },
                title = { Text("Save scan to shelf") },
                text = {
                    Column {
                        Text("Give the scanned document a useful name and choose where it belongs.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pendingScanName,
                            onValueChange = { pendingScanName = it.take(100) },
                            label = { Text("Document name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Shelf", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(allShelves, key = { it.id }) { item ->
                                FilterChip(
                                    selected = pendingScanShelf == item.id,
                                    onClick = { pendingScanShelf = item.id },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = pendingScanName.isNotBlank(),
                        onClick = {
                            val uri = pendingScanUri ?: return@Button
                            runCatching {
                                repository.importManagedCopy(
                                    sourceUri = Uri.parse(uri),
                                    title = pendingScanName.trim(),
                                    shelf = pendingScanShelf
                                )
                            }.onSuccess { imported ->
                                documents = repository.documents()
                                statusMessage = "Scanned ${imported.title} into ${shelfLabel(imported.shelf)}"
                                pendingScanUri = null
                                pendingScanName = ""
                            }.onFailure { error ->
                                statusMessage = "Could not save scan: ${error.message ?: "storage error"}"
                            }
                        }
                    ) { Text("Save to shelf") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingScanUri = null
                        pendingScanName = ""
                    }) { Text("Discard") }
                }
            )
        }

        if (dashboardMode && editingDocumentId != null) {
            AlertDialog(
                onDismissRequest = { editingDocumentId = null },
                title = { Text("Edit library entry") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = editDocumentTitle,
                            onValueChange = { editDocumentTitle = it.take(100) },
                            label = { Text("Document name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Shelf", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(allShelves, key = { it.id }) { item ->
                                FilterChip(
                                    selected = editDocumentShelf == item.id,
                                    onClick = { editDocumentShelf = item.id },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = editDocumentTitle.isNotBlank(),
                        onClick = {
                            val id = editingDocumentId ?: return@Button
                            repository.updateDocument(id, editDocumentTitle, editDocumentShelf)?.let { updated ->
                                documents = repository.documents()
                                statusMessage = "Updated ${updated.title}"
                            }
                            editingDocumentId = null
                        }
                    ) { Text("Save changes") }
                },
                dismissButton = { TextButton(onClick = { editingDocumentId = null }) { Text("Cancel") } }
            )
        }

        if (dashboardMode && manageShelves) {
            AlertDialog(
                onDismissRequest = { manageShelves = false },
                title = { Text("Manage shelves") },
                text = {
                    Column {
                        Text("Built-in shelves stay available for presets and ODK. Custom shelves are local to this library.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newShelfName,
                            onValueChange = { newShelfName = it.take(40) },
                            label = { Text("New shelf name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = newShelfName.isNotBlank(),
                            onClick = {
                                runCatching { repository.addCustomShelf(newShelfName) }
                                    .onSuccess { created ->
                                        customShelves = repository.customShelves()
                                        shelf = created.id
                                        newShelfName = ""
                                        statusMessage = "Created ${created.label}"
                                    }
                                    .onFailure { error -> statusMessage = error.message ?: "Could not create shelf" }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Create shelf") }
                        if (customShelves.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text("Custom shelves", style = MaterialTheme.typography.labelLarge)
                            customShelves.forEach { custom ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${custom.label}  ${documents.count { it.shelf == custom.id }}", modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                                    TextButton(onClick = { manageShelves = false; shelfToDelete = custom.id }) { Text("Delete") }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { manageShelves = false }) { Text("Done") } }
            )
        }

        val deletingShelf = shelfToDelete?.let { id -> customShelves.firstOrNull { it.id == id } }
        if (dashboardMode && deletingShelf != null) {
            val affected = documents.count { it.shelf == deletingShelf.id }
            AlertDialog(
                onDismissRequest = { shelfToDelete = null },
                title = { Text("Delete ${deletingShelf.label}?") },
                text = {
                    Text(
                        if (affected == 0) "This removes the custom shelf."
                        else "$affected document${if (affected == 1) "" else "s"} will move to Personal. No files will be deleted."
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val moved = repository.deleteCustomShelf(deletingShelf.id, "personal")
                        customShelves = repository.customShelves()
                        documents = repository.documents()
                        if (shelf == deletingShelf.id) shelf = "personal"
                        if (editDocumentShelf == deletingShelf.id) editDocumentShelf = "personal"
                        if (pendingScanShelf == deletingShelf.id) pendingScanShelf = "personal"
                        statusMessage = if (moved == 0) "Deleted ${deletingShelf.label}" else "Deleted ${deletingShelf.label}; moved $moved document${if (moved == 1) "" else "s"} to Personal"
                        shelfToDelete = null
                    }) { Text("Delete shelf") }
                },
                dismissButton = { TextButton(onClick = { shelfToDelete = null }) { Text("Cancel") } }
            )
        }
    }
}

private val BUILT_IN_SHELVES = REFERENCE_LIBRARY_BUILT_IN_SHELVES

private fun String.labelForShelf(): String =
    BUILT_IN_SHELVES.firstOrNull { it.id == this }?.label ?: replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ShelfPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val background = if (selected) {
        if (darkTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        else MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (darkTheme) 0.42f else 0.34f)
    }
    val foreground = if (selected) {
        if (darkTheme) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.inverseOnSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = background
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = foreground,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
        )
    }
}

private fun documentKind(document: LibraryDocument): String = when {
    document.mimeType.contains("pdf", ignoreCase = true) -> "PDF"
    document.mimeType.startsWith("image/", ignoreCase = true) -> "IMG"
    document.mimeType.startsWith("text/", ignoreCase = true) -> "TXT"
    else -> "DOC"
}

@Composable
private fun DocumentTile(
    document: LibraryDocument,
    selected: Boolean,
    shelfLabel: String,
    onOpen: () -> Unit,
    darkSurface: Boolean = false,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    mutedColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val tileColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        darkSurface -> contentColor.copy(alpha = 0.075f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    }
    Surface(
        modifier = Modifier
            .width(172.dp)
            .height(148.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        color = tileColor
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    documentKind(document),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (darkSurface) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (document.favourite) {
                    Text("★", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            Column {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    shelfLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(
    document: LibraryDocument,
    selected: Boolean,
    shelfLabel: String,
    onOpen: () -> Unit,
    onFavourite: () -> Unit,
    onEdit: () -> Unit,
    onCreatePreset: () -> Unit,
    onUse: (() -> Unit)? = null,
    onShare: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
            }
        ) {
            Column(
                modifier = Modifier.width(42.dp).padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    documentKind(document),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (document.favourite) {
                    Spacer(Modifier.width(6.dp))
                    Text("★", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(shelfLabel)
                    if (document.source.isNotBlank() && !document.source.equals("User supplied", ignoreCase = true)) {
                        append("  ·  ${document.source}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(4.dp))
        Box {
            TextButton(onClick = { menuExpanded = true }) {
                Text("•••", style = MaterialTheme.typography.titleSmall)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (onUse != null) {
                    DropdownMenuItem(
                        text = { Text("Use this document") },
                        onClick = { menuExpanded = false; onUse() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Share this document") },
                    onClick = { menuExpanded = false; onShare() }
                )
                DropdownMenuItem(
                    text = { Text(if (document.favourite) "Remove favourite" else "Add to favourites") },
                    onClick = { menuExpanded = false; onFavourite() }
                )
                DropdownMenuItem(
                    text = { Text("Rename or move") },
                    onClick = { menuExpanded = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Save as preset") },
                    onClick = { menuExpanded = false; onCreatePreset() }
                )
                DropdownMenuItem(
                    text = { Text("Remove from library") },
                    onClick = { menuExpanded = false; onRemove() }
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(filtered: Boolean, onImport: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp)) {
            Text(
                if (filtered) "Nothing found" else "A quiet shelf",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (filtered) "Try another search or shelf." else "Add documents, maps or other useful files and keep them ready offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp, bottom = 8.dp)
            )
            TextButton(onClick = onImport) { Text(if (filtered) "Add document" else "Add first document") }
        }
    }
}
