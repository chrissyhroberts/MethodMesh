package com.example.methodmesh.ui.artifacts

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.methodmesh.core.artifacts.*
import com.example.methodmesh.core.config.MethodMeshConfigurationBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Shared selection surface for any capability. Selection returns identity, not a saved copy. */
@Composable
fun ArtifactPicker(request: ArtifactPickerRequest, onSelected: (ArtifactRef) -> Unit, revision: Int = 0) {
    val context = LocalContext.current
    var query by remember { mutableStateOf(request.query) }
    var artifacts by remember { mutableStateOf(emptyList<Artifact>()) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query, request, revision) {
        runCatching { withContext(Dispatchers.IO) {
            AndroidArtifacts.service(context).query(request.copy(query = query))
        } }.onSuccess { artifacts = it; failure = null }.onFailure { failure = it.message }
    }
    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text("Search files") }, modifier = Modifier.fillMaxWidth())
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("${artifacts.size} files", modifier = Modifier.padding(vertical = 8.dp))
        artifacts.forEach { artifact ->
            Column(Modifier.fillMaxWidth().clickable { onSelected(artifact.ref) }.padding(vertical = 10.dp)) {
                Text(artifact.displayName)
                Text("${artifact.mimeType} · ${artifact.origin.name.lowercase()}", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }
}

/** Persistent Files is one consumer of the shared picker and Artifact Service. */
@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Artifact?>(null) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var previewImage by remember { mutableStateOf<Bitmap?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Artifact?>(null) }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MethodMeshConfigurationBackup.MIME_TYPE)) { destination ->
        if (destination != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destination)?.use {
                        MethodMeshConfigurationBackup.write(context, it)
                    } ?: error("Cannot open backup destination")
                }
            }.onSuccess { status = "Configuration backup exported." }
                .onFailure { status = "Backup export failed: ${it.message ?: "storage error"}" }
        }
    }
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        MethodMeshConfigurationBackup.restore(context, it)
                    } ?: error("Cannot open backup")
                }
            }.onSuccess { revision++; status = "Configuration restored. Restart MethodMesh to apply it." }
                .onFailure { status = "Restore failed: ${it.message ?: "invalid backup"}" }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(
        selected?.mimeType ?: "application/octet-stream"
    )) { destination ->
        val artifact = selected
        if (destination != null && artifact != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                AndroidArtifacts.service(context).open(artifact.ref).use { input ->
                    context.contentResolver.openOutputStream(destination)?.use { input.copyTo(it) }
                        ?: error("Cannot open destination")
                }
            } }.onSuccess { status = "Copy saved." }.onFailure { status = it.message }
            selected = null
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val service = AndroidArtifacts.service(context)
                val session = "import-${UUID.randomUUID()}"
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "Imported file"
                val ref = service.linkExternal(uri.toString(), name,
                    context.contentResolver.getType(uri) ?: "application/octet-stream", session)
                try { ArtifactStore(service).save(ref) } finally { service.endSession(session) }
            } }.onSuccess { revision++; status = "File saved." }.onFailure { status = it.message }
        }
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Button(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Import file") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                runCatching {
                    val bytes = ByteArrayOutputStream().also { MethodMeshConfigurationBackup.write(context, it) }.toByteArray()
                    AndroidArtifacts.service(context).createPersistent(
                        name = "MethodMesh configuration backup.zip",
                        mime = MethodMeshConfigurationBackup.MIME_TYPE,
                        input = ByteArrayInputStream(bytes)
                    )
                }.onSuccess { revision++; status = "Configuration backup saved to Files." }
                    .onFailure { status = "Backup failed: ${it.message ?: "storage error"}" }
            }, modifier = Modifier.weight(1f)) { Text("Backup") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            TextButton(onClick = { backupExporter.launch(MethodMeshConfigurationBackup.FILE_NAME) }, modifier = Modifier.weight(1f)) { Text("Export backup") }
            TextButton(onClick = {
                runCatching {
                    val backup = File(context.cacheDir, "MethodMesh-Configuration-Backup.zip")
                    backup.outputStream().use { MethodMeshConfigurationBackup.write(context, it) }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backup)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = MethodMeshConfigurationBackup.MIME_TYPE
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(context.contentResolver, backup.name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share configuration backup"))
                }.onFailure { status = "Share failed: ${it.message ?: "could not create backup"}" }
            }, modifier = Modifier.weight(1f)) { Text("Share backup") }
            TextButton(onClick = { backupImporter.launch(arrayOf(MethodMeshConfigurationBackup.MIME_TYPE, "application/zip")) }, modifier = Modifier.weight(1f)) { Text("Restore backup") }
        }
        status?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
        ArtifactPicker(ArtifactPickerRequest(lifecycles = setOf(ArtifactLifecycle.PERSISTENT)), { ref ->
            scope.launch { selected = withContext(Dispatchers.IO) { AndroidArtifacts.service(context).resolve(ref) } }
        }, revision)
        LaunchedEffect(selected?.ref) {
            previewText = null
            previewImage = null
            val artifact = selected ?: return@LaunchedEffect
            val previewableText = artifact.mimeType.startsWith("text/") ||
                artifact.mimeType in setOf("application/json", "application/jsonl", "application/xml", "text/csv") ||
                artifact.displayName.endsWith(".json", ignoreCase = true) ||
                artifact.displayName.endsWith(".jsonl", ignoreCase = true)
            val previewableImage = artifact.mimeType.startsWith("image/")
            val previewablePdf = artifact.mimeType.equals("application/pdf", ignoreCase = true) ||
                artifact.displayName.endsWith(".pdf", ignoreCase = true)
            if (!previewableText && !previewableImage && !previewablePdf) return@LaunchedEffect
            previewLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    AndroidArtifacts.service(context).open(artifact.ref).use { input ->
                        if (previewableText) {
                            input.bufferedReader().use { formatPreviewText(it.readText().take(MAX_TEXT_PREVIEW_CHARS), artifact.mimeType, artifact.displayName) }
                        } else if (previewablePdf) {
                            previewPdf(context, input)
                        } else {
                            BitmapFactory.decodeStream(input)
                        }
                    }
                }
            }.onSuccess { preview ->
                if (previewableText) previewText = preview as? String else previewImage = preview as? Bitmap
            }.onFailure { status = "Preview unavailable: ${it.message ?: "file could not be read"}" }
            previewLoading = false
        }
        selected?.let { artifact ->
            AlertDialog(onDismissRequest = { selected = null }, title = { Text(artifact.displayName) },
                text = {
                    Column {
                        when {
                            previewLoading -> Text("Loading preview…")
                            previewImage != null -> Image(
                                bitmap = previewImage!!.asImageBitmap(),
                                contentDescription = artifact.displayName,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                                contentScale = ContentScale.Fit
                            )
                            previewText != null -> Surface(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 520.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ) {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    androidx.compose.foundation.layout.Box(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                                        Text(previewText!!, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            else -> Text("No inline preview for this file type.")
                        }
                        Text(
                            "${artifact.mimeType} · ${artifact.origin.name.lowercase()}\nSHA-256: ${artifact.sha256 ?: "Not recorded"}",
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Row {
                        if (artifact.mimeType == MethodMeshConfigurationBackup.MIME_TYPE) {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            AndroidArtifacts.service(context).open(artifact.ref).use {
                                                MethodMeshConfigurationBackup.restore(context, it)
                                            }
                                        }
                                    }.onSuccess {
                                        selected = null
                                        revision++
                                        status = "Configuration restored. Restart MethodMesh to apply it."
                                    }.onFailure { error ->
                                        status = "Restore failed: ${error.message ?: "invalid backup"}"
                                    }
                                }
                            }) { Text("Restore") }
                        }
                        TextButton(onClick = { confirmDelete = artifact }) { Text("Delete") }
                        TextButton(onClick = { exporter.launch(artifact.displayName) }) { Text("Save a copy") }
                        TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    val shareFile = withContext(Dispatchers.IO) {
                                        File.createTempFile("methodmesh-share-", "-${artifact.displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")}", context.cacheDir).also { target ->
                                            AndroidArtifacts.service(context).open(artifact.ref).use { input ->
                                                target.outputStream().use { input.copyTo(it) }
                                            }
                                        }
                                    }
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = artifact.mimeType
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = ClipData.newUri(context.contentResolver, artifact.displayName, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }, "Share file"))
                                }.onFailure { error -> status = "Share failed: ${error.message ?: "could not prepare file"}" }
                            }
                        }) { Text("Share") }
                    }
                },
                dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
        }
        confirmDelete?.let { artifact ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text("Remove from Files?") },
                text = { Text("${artifact.displayName} will be removed from MethodMesh Files. The original external file will not be deleted.") },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching { AndroidArtifacts.service(context).delete(artifact.ref) }
                            .onSuccess {
                                confirmDelete = null
                                selected = null
                                revision++
                                status = "Removed from Files."
                            }
                            .onFailure { error ->
                                confirmDelete = null
                                status = error.message
                            }
                    }) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
            )
        }
    }
}

private const val MAX_TEXT_PREVIEW_CHARS = 16_000

private fun formatPreviewText(text: String, mimeType: String, name: String): String {
    val isJson = mimeType.equals("application/json", true) ||
        name.endsWith(".json", true) || name.endsWith(".jsonl", true)
    if (!isJson) return text
    return runCatching {
        text.lineSequence().filter { it.isNotBlank() }.joinToString("\n") { line ->
            when {
                line.trimStart().startsWith("{") -> JSONObject(line).toString(2)
                line.trimStart().startsWith("[") -> JSONArray(line).toString(2)
                else -> line
            }
        }
    }.getOrElse { text }
}

private fun previewPdf(context: android.content.Context, input: java.io.InputStream): Bitmap? {
    val temporaryFile = File.createTempFile("methodmesh-preview-", ".pdf", context.cacheDir)
    return try {
        temporaryFile.outputStream().use { output -> input.copyTo(output) }
        PdfRenderer(ParcelFileDescriptor.open(temporaryFile, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            if (renderer.pageCount == 0) return null
            renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    } finally {
        temporaryFile.delete()
    }
}
