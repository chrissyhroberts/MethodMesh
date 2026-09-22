package com.example.methodmesh.modules.textdocuments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.methodmesh.ui.theme.MethodMeshTheme
import kotlinx.coroutines.launch

/** Android VIEW/EDIT entry point for supported text documents. */
class DocumentActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MethodMeshTheme {
                ExternalDocumentRoute(
                    incomingUri = intent?.data,
                    incomingMime = intent?.type,
                    incomingFlags = intent?.flags ?: 0,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
private fun ExternalDocumentRoute(
    incomingUri: Uri?,
    incomingMime: String?,
    incomingFlags: Int,
    onFinish: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { TextDocumentRepository(context) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var loadError by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("Document") }
    var formatName by rememberSaveable { mutableStateOf(DocumentFormat.TEXT.contractValue) }
    var text by rememberSaveable { mutableStateOf("") }
    var writable by rememberSaveable { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var currentUri by rememberSaveable { mutableStateOf(incomingUri?.toString()) }
    var pendingSaveAsText by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCloseAfterSaveAs by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        val body = pendingSaveAsText
        pendingSaveAsText = null
        val closeAfter = pendingCloseAfterSaveAs
        pendingCloseAfterSaveAs = false
        if (uri != null && body != null) {
            DocumentIo.retainAccess(context, uri, result.data?.flags ?: 0)
            scope.launch {
                DocumentIo.write(context, uri, body).fold(
                    onSuccess = {
                        val newTitle = DocumentIo.displayName(context, uri) ?: title
                        val format = DocumentFormat.detect(newTitle, context.contentResolver.getType(uri))
                        currentUri = uri.toString()
                        title = newTitle
                        formatName = format.contractValue
                        text = body
                        writable = true
                        dirty = false
                        status = "Saved"
                        repository.remember(uri, title, format.mimeType)
                        if (closeAfter) onFinish()
                    },
                    onFailure = { error -> status = "Save failed. ${DocumentIo.userFacingWriteError(error)}" }
                )
            }
        }
    }

    fun requestSaveAs(body: String, closeAfter: Boolean = false) {
        pendingSaveAsText = body
        pendingCloseAfterSaveAs = closeAfter
        saveAs.launch(DocumentIo.createIntent(DocumentFormat.fromContract(formatName), title))
    }

    fun save(body: String, closeAfter: Boolean = false) {
        val uri = currentUri?.let(Uri::parse)
        if (uri == null || !writable) {
            requestSaveAs(body, closeAfter)
            return
        }
        scope.launch {
            DocumentIo.write(context, uri, body).fold(
                onSuccess = {
                    text = body
                    dirty = false
                    status = "Saved"
                    repository.remember(uri, title, DocumentFormat.fromContract(formatName).mimeType)
                    if (closeAfter) onFinish()
                },
                onFailure = { error -> status = "Save failed. ${DocumentIo.userFacingWriteError(error)}" }
            )
        }
    }

    LaunchedEffect(incomingUri) {
        if (loaded) return@LaunchedEffect
        if (incomingUri == null) {
            loadError = "No document was supplied by the calling app."
            loaded = true
            return@LaunchedEffect
        }
        DocumentIo.retainAccess(context, incomingUri, incomingFlags)
        DocumentIo.read(context, incomingUri, incomingMime).fold(
            onSuccess = { document ->
                title = document.title
                formatName = document.format.contractValue
                text = document.text
                writable = incomingFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 ||
                    document.writable
                currentUri = incomingUri.toString()
                dirty = false
                repository.remember(incomingUri, document.title, document.format.mimeType)
                loaded = true
            },
            onFailure = { error ->
                loadError = "MethodMesh could not open this document. ${DocumentIo.userFacingReadError(error)}"
                loaded = true
            }
        )
    }

    if (!loaded) {
        Surface(Modifier.fillMaxSize()) {
            Text("Opening document…", modifier = Modifier.padding(24.dp))
        }
        return
    }

    if (loadError != null) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp)) {
                Text("Unable to open document", style = MaterialTheme.typography.headlineSmall)
                Text(loadError.orEmpty(), modifier = Modifier.padding(top = 10.dp))
                Button(onClick = onFinish, modifier = Modifier.padding(top = 18.dp)) { Text("Return") }
            }
        }
        return
    }

    val format = DocumentFormat.fromContract(formatName)
    DocumentSurface(
        title = title,
        text = text,
        format = format,
        writable = writable,
        dirty = dirty,
        statusMessage = status,
        closeLabel = "Close",
        onTextChange = {
            text = it
            dirty = true
            status = null
        },
        onClose = onFinish,
        onSave = { body -> save(body) },
        onSaveAs = { body -> requestSaveAs(body) },
        onSaveAndClose = { body -> save(body, closeAfter = true) },
        onSaveAsAndClose = { body -> requestSaveAs(body, closeAfter = true) },
        onShare = { body ->
            DocumentSharing.share(context, title, format, body)
                .onFailure { status = "Could not share document." }
        }
    )
}
