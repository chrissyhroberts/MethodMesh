package com.example.methodmesh.core.scheduling

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import java.io.File
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityDependencyScreen
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.android.IntentRouterActivity

object SchedulerTransferCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SchedulerImportMethod.id
    override val title = "Import schedules"
    override val description = "Import a signed schedule bundle directly or through QR/NFC."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val settings = context.request.settings + context.action.settings
        var activeTransport by remember { mutableStateOf(settings["schedule_transport"].orEmpty().uppercase()) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready to import a schedule bundle.") }
        var pastedBundle by remember { mutableStateOf(settings["schedule_bundle"].orEmpty()) }
        fun importPayload(payload: String) {
            result = runCatching {
                val decoded = SchedulerBundle.import(androidContext, payload)
                SchedulerTransferResults.success(context, As100SchedulerImportMethod.id, decoded.schedules.size, decoded.hash)
            }.getOrElse { error ->
                status = error.message ?: "Schedule import failed."
                SchedulerTransferResults.failure(context, As100SchedulerImportMethod.id, status)
            }
            activeTransport = ""
            if (context.startsImmediately) result?.let(onConfirmed)
        }
        if (activeTransport == "QR" || activeTransport == "NFC") {
            val dependency = if (activeTransport == "QR") "qr.scan" else "nfc_tag_read"
            CapabilityDependencyScreen(dependency, context, onResult = { dependencyResult ->
                val fields = OutputFormatter.fields(dependencyResult, includeProvenance = false)
                val payload = fields["qr_payload"]?.toString()
                    ?: fields["ndef_text"]?.toString()
                    ?: fields["ndef_first_payload_utf8"]?.toString().orEmpty()
                if (payload.isBlank()) status = "The $activeTransport result did not contain a schedule bundle." else importPayload(payload)
            }, onCancel = onCancel)
            return
        }
        LaunchedEffect(Unit) {
            settings["schedule_bundle"]?.takeIf { it.isNotBlank() }?.let(::importPayload)
        }
        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, {}, { result?.let(onConfirmed) }, onCancel) {
            Text(status)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(pastedBundle, { pastedBundle = it }, label = { Text("Schedule bundle") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
            Button(onClick = {
                val clipboard = androidContext.getSystemService(ClipboardManager::class.java)
                pastedBundle = clipboard.primaryClip?.getItemAt(0)?.coerceToText(androidContext)?.toString().orEmpty()
                status = if (pastedBundle.isBlank()) "Clipboard is empty." else "Bundle pasted from clipboard."
            }, modifier = Modifier.fillMaxWidth()) { Text("Paste from clipboard") }
            Button(onClick = { if (pastedBundle.isBlank()) status = "Paste or enter a schedule bundle first." else importPayload(pastedBundle) }, modifier = Modifier.fillMaxWidth()) { Text("Import bundle") }
            OutlinedButton(onClick = { 
                activeTransport = "QR"
            }, modifier = Modifier.fillMaxWidth()) { Text("Scan QR bundle") }
            OutlinedButton(onClick = {
                activeTransport = "NFC"
            }, modifier = Modifier.fillMaxWidth()) { Text("Read NFC bundle") }
        }
    }
}

object SchedulerExportCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SchedulerExportMethod.id
    override val title = "Export schedules"
    override val description = "Export schedules as a portable integrity-checked bundle."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var nfcStatus by remember { mutableStateOf("") }
        val settings = context.request.settings + context.action.settings
        val schedules = remember { SchedulerRepository.all(androidContext) }
        val scheduleGroups = remember(schedules) { schedules.sortedWith(compareBy<ResearchSchedule> { it.chainId.ifBlank { it.id } }.thenBy { it.chainOrder }).groupBy { it.chainId.ifBlank { it.id } }.values.toList() }
        var selectedScheduleId by remember { mutableStateOf(settings["schedule_id"].orEmpty()) }
        var chooserOpen by remember { mutableStateOf(false) }
        val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            val payload = result?.let { OutputFormatter.fields(it, false)["schedule_bundle"]?.toString().orEmpty() }.orEmpty()
            if (uri != null && payload.isNotBlank()) runCatching {
                androidContext.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
        }
        val qrFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
            val bitmap = qrBitmap
            if (uri != null && bitmap != null) androidContext.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        LaunchedEffect(selectedScheduleId) {
            val bundle = SchedulerBundle.export(androidContext, selectedScheduleId)
            val compact = SchedulerBundle.exportCompact(androidContext, selectedScheduleId)
            result = SchedulerTransferResults.export(context, bundle, compact).also { }
            // Keep the payload available to the manual transfer controls below.
            // The result remains the canonical RIL return value.
            if (context.startsImmediately) result?.let(onConfirmed)
        }
        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, {}, { result?.let(onConfirmed) }, onCancel) {
            Text("Schedule bundle ready. Use the returned payload with QR or NFC transport.")
            OutlinedButton(onClick = { chooserOpen = true }, modifier = Modifier.fillMaxWidth()) {
                val selected = schedules.firstOrNull { it.id == selectedScheduleId }
                Text(if (selectedScheduleId.isBlank()) "Export: all schedules" else "Export: ${selected?.name ?: "selected chain"}")
            }
            if (chooserOpen) {
                Dialog(onDismissRequest = { chooserOpen = false }) {
                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                        LazyColumn(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            item {
                                DropdownMenuItem(text = { Text("All schedules") }, onClick = { selectedScheduleId = ""; chooserOpen = false })
                            }
                            items(scheduleGroups) { group ->
                                val first = group.first()
                                DropdownMenuItem(
                                    text = { Text(if (group.size > 1) "${first.name.removeSuffix(" 1")} (${group.size}-step chain)" else first.name) },
                                    onClick = { selectedScheduleId = first.id; chooserOpen = false }
                                )
                            }
                        }
                    }
                }
            }
            val fields = result?.let { OutputFormatter.fields(it, false).orEmpty() }.orEmpty()
            val payload = fields["schedule_bundle"]?.toString().orEmpty()
            val compactPayload = fields["schedule_bundle_compact"]?.toString().orEmpty()
            Spacer(Modifier.height(10.dp))
            if (compactPayload.isNotBlank()) {
                val nfcBytes = compactPayload.toByteArray(Charsets.UTF_8).size
                Text(
                    "NFC transport payload: $nfcBytes bytes. Small tags may not have enough NDEF capacity; export one chain or use a larger tag if needed.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = {
                androidContext.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("MethodMesh schedule bundle", payload))
            }, modifier = Modifier.fillMaxWidth()) { Text("Copy bundle to clipboard") }
            Button(onClick = { fileLauncher.launch("methodmesh-schedules.json") }, modifier = Modifier.fillMaxWidth()) { Text("Save bundle as file") }
            Button(onClick = {
                qrBitmap = runCatching {
                    val size = 768
                    val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
                    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
                        for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }.getOrNull()
            }, modifier = Modifier.fillMaxWidth()) { Text("Show QR code") }
            qrBitmap?.let { Image(it.asImageBitmap(), "Schedule bundle QR code", modifier = Modifier.fillMaxWidth()) }
            Button(onClick = { qrFileLauncher.launch("methodmesh-schedule-qr.png") }, enabled = qrBitmap != null, modifier = Modifier.fillMaxWidth()) { Text("Save QR code image") }
            Button(onClick = {
                qrBitmap?.let { bitmap ->
                    val file = File(androidContext.cacheDir, "methodmesh-schedule-qr.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    val uri = FileProvider.getUriForFile(androidContext, "${androidContext.packageName}.fileprovider", file)
                    androidContext.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share QR code"))
                }
            }, enabled = qrBitmap != null, modifier = Modifier.fillMaxWidth()) { Text("Share QR code image") }
            Button(onClick = {
                androidContext.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, payload) }, "Share schedule bundle"))
            }, modifier = Modifier.fillMaxWidth()) { Text("Share bundle") }
            Button(onClick = {
                val value = compactPayload.ifBlank { payload }
                val bytes = value.toByteArray(Charsets.UTF_8).size
                if (bytes > 480) {
                    nfcStatus = "This bundle is $bytes bytes and is too large for a small NFC tag. Select one chain or use a higher-capacity tag."
                } else {
                    androidContext.startActivity(Intent(androidContext, IntentRouterActivity::class.java).apply {
                        action = "com.example.methodmesh.EXECUTE_METHOD"
                        putExtra("method_id", "nfc_tag_write")
                        putExtra("input_value", value)
                        putExtra("input_record_type", "text/plain")
                        putExtra("input_overwrite_policy", "replace")
                    })
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Write bundle to NFC") }
            if (nfcStatus.isNotBlank()) Text(nfcStatus, style = MaterialTheme.typography.bodySmall)
            Text("For QR transfer, copy/share this bundle into a QR generator, then import it with Scan QR bundle.")
        }
    }
}

private object SchedulerTransferResults {
    fun export(context: CapabilityScreenContext, bundle: String, compact: String): ExecutionResult = result(context, As100SchedulerExportMethod.id, mapOf(
        "scheduler_transfer_status" to "exported", "schedule_bundle" to bundle, "schedule_bundle_compact" to compact
    ))

    fun success(context: CapabilityScreenContext, method: String, count: Int, hash: String): ExecutionResult = result(context, method, mapOf(
        "scheduler_transfer_status" to "imported", "schedule_count" to count, "schedule_bundle_sha256" to hash
    ))

    fun failure(context: CapabilityScreenContext, method: String, error: String): ExecutionResult = result(context, method, mapOf(
        "scheduler_transfer_status" to "failed", "scheduler_error" to error
    ), failed = true)

    private fun result(context: CapabilityScreenContext, method: String, values: Map<String, Any?>, failed: Boolean = false): ExecutionResult {
        val ref = ArchitectureRef(ArchitectureId(method), "Method", method)
        val request = As100ExecutionEngine.request(action = method, method = ref, context = context.request.invocationContext.asMap(method) + context.request.settings + context.action.settings)
        val provenance = ProvenanceContext(provider = "methodmesh.scheduler", methodId = method, methodVersion = "1.0.0")
        val observation = Observation(phenomenon = "methodmesh.schedule_transfer", subject = null, values = values.mapValues { it.value?.toString().orEmpty() }, temporalContext = request.temporalContext, provenance = provenance)
        val status = if (failed) TransformationStatus.Failed else TransformationStatus.Succeeded
        return As100ExecutionEngine.complete(request, status, observations = listOf(observation), diagnostics = if (failed) mapOf("error" to values["scheduler_error"].toString()) else emptyMap()).withInvocationContext(context.request.invocationContext)
    }
}
