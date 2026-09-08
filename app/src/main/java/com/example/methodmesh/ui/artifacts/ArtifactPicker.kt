package com.example.methodmesh.ui.artifacts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
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
import com.example.methodmesh.core.artifacts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.UUID

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
        Button(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.padding(16.dp)) { Text("Import file") }
        status?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
        ArtifactPicker(ArtifactPickerRequest(lifecycles = setOf(ArtifactLifecycle.PERSISTENT)), { ref ->
            scope.launch { selected = withContext(Dispatchers.IO) { AndroidArtifacts.service(context).resolve(ref) } }
        }, revision)
        LaunchedEffect(selected?.ref) {
            previewText = null
            previewImage = null
            val artifact = selected ?: return@LaunchedEffect
            val previewableText = artifact.mimeType.startsWith("text/") ||
                artifact.mimeType in setOf("application/json", "application/xml", "text/csv")
            val previewableImage = artifact.mimeType.startsWith("image/")
            val previewablePdf = artifact.mimeType.equals("application/pdf", ignoreCase = true)
            if (!previewableText && !previewableImage && !previewablePdf) return@LaunchedEffect
            previewLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    AndroidArtifacts.service(context).open(artifact.ref).use { input ->
                        if (previewableText) {
                            input.bufferedReader().use { it.readText().take(MAX_TEXT_PREVIEW_CHARS) }
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
                                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                                contentScale = ContentScale.Fit
                            )
                            previewText != null -> Surface(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ) {
                                Text(
                                    previewText!!,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
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
                confirmButton = { TextButton(onClick = { exporter.launch(artifact.displayName) }) { Text("Save a copy") } },
                dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
        }
    }
}

private const val MAX_TEXT_PREVIEW_CHARS = 16_000

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
