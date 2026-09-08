package com.example.methodmesh.ui.artifacts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.artifacts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val service = AndroidArtifacts.service(context)
                val session = "import-${UUID.randomUUID()}"
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported file"
                val ref = service.linkExternal(uri.toString(), name,
                    context.contentResolver.getType(uri) ?: "application/octet-stream", session)
                service.persist(ref)
                service.endSession(session)
                ref
            } }.onSuccess { revision++; status = "File selected; persistence is explicit." }
                .onFailure { status = it.message }
        }
    }
    Column(Modifier.padding(16.dp)) {
        Text("Files", style = MaterialTheme.typography.headlineSmall)
        Text("Shared artifacts used by MethodMesh capabilities.", modifier = Modifier.padding(bottom = 12.dp))
        Button(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Choose file") }
        status?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
        OutlinedTextField(query, { query = it }, label = { Text("Search files") }, modifier = Modifier.fillMaxWidth())
        val files = remember(revision, query) { AndroidArtifacts.service(context).query(
            ArtifactPickerRequest(lifecycles = setOf(ArtifactLifecycle.PERSISTENT), query = query)) }
        Text("${files.size} persistent files", modifier = Modifier.padding(vertical = 10.dp))
        files.forEach { artifact ->
            Column(Modifier.fillMaxWidth().clickable { }.padding(vertical = 8.dp)) {
                Text(artifact.displayName)
                Text(artifact.mimeType, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }
}
