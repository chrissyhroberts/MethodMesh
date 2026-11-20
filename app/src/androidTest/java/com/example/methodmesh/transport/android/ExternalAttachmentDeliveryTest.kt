package com.example.methodmesh.transport.android

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.artifacts.ArtifactRef
import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.runtime.As100MethodRegistry
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ExternalAttachmentDeliveryTest {
    @Test fun fullReturnDeliversEveryStreamReadOnlyAcrossThePublicRouter() = verify("", "FULL")
    @Test fun compactNamespacedReturnStillDeliversUriListsAndCanonicalEvidence() = verify("review", "CORE")
    @Test fun failedOptInLogStillReturnsSucceededEvidenceAndExplicitNamespacedWarning() = verify("review", "CORE", "fail")
    @Test fun confirmedLogKeepsCanonicalEvidenceAndCopiesBothLinkedMediaOnce() = verify("", "FULL", "save")

    private fun verify(namespace: String, payloadMode: String, logMode: String = "") {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val token = UUID.randomUUID().toString()
        val folder = File(app.cacheDir, "attachment-regression-$token").apply { mkdirs() }
        val bytes = listOf("first fixture".toByteArray(), "second fixture".toByteArray())
        fun fixtureUri(name: String, content: ByteArray): String {
            val file = File(folder, name).apply { writeBytes(content) }
            return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file).toString()
        }
        val uris = bytes.mapIndexed { index, content -> fixtureUri("page$index.txt", content) }
        val probe = fixtureUri("audit-only.txt", "not a returned attachment".toByteArray())
        val pages = JSONArray(uris).toString()
        val service = AndroidArtifacts.service(app)
        val log = if (logMode == "save") service.createPersistent("Review log $token", "application/x-ndjson", byteArrayOf().inputStream()) else null
        val summary = if (log != null) service.createPersistent("Review summary $token", "text/plain", byteArrayOf().inputStream()) else null
        val originalRegistry = As100MethodRegistry.all()
        val fixture = object : As100Method {
            override val id = "test.attachment_delivery"
            override val ref = ArchitectureRef(ArchitectureId(id), "Method")
            override val descriptor = MethodDescriptor(ArchitectureId(id), MethodObjectType.Calculation, "Attachment fixture", "1")
            override val contract = MethodContract(ref)
            override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
                ExecutionRequest(action = action, method = ref, context = context, signals = signals, inputs = inputs)
            override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) =
                ExecutionResult(request, TransformationStatus.Succeeded, observations = listOf(
                    Observation(phenomenon = "fixture", values = mapOf(
                        "answer" to "Two fixture files", "document_page_uris_json" to pages,
                        "primary_file_uri" to uris.first(), "audit_json" to """{"uri":"$probe"}"""
                    ), provenance = ProvenanceContext("test"))
                ))
        }
        val latch = CountDownLatch(1)
        val report = AtomicReference<Intent>()
        val callbackAction = "${app.packageName}.ATTACHMENT_TEST.$token"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { report.set(intent); latch.countDown() }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(callbackAction), ContextCompat.RECEIVER_EXPORTED)
        As100MethodRegistry.install(originalRegistry + fixture)
        try {
            val child = Intent().setComponent(ComponentName(app.packageName, IntentRouterActivity::class.java.name))
                .putExtra("method_id", fixture.id).putExtra("return_mode", "flat")
                .putExtra("input_payload_mode", payloadMode).putExtra("input_methodmesh_return_namespace", namespace)
            if (logMode.isNotBlank()) child.putExtra("input_methodmesh_log_ref", log?.id ?: "missing-review-log-$token")
            if (summary != null) child.putExtra("input_methodmesh_log_summary_ref", summary.id)
            val receivingApp = Intent().setComponent(ComponentName(instrumentation.context.packageName, AttachmentReceiverActivity::class.java.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("child", child).putExtra("callback_action", callbackAction)
                .putExtra("callback_package", app.packageName).putExtra("ungranted_probe", probe)
            app.startActivity(receivingApp)
            assertTrue("Receiving app did not finish the roundtrip", latch.await(25, TimeUnit.SECONDS))
            val received = report.get()
            assertNotEquals("Receiver must not share the producer UID", app.applicationInfo.uid, received.getIntExtra("receiver_uid", -1))
            assertNull(received.getStringExtra("receiver_error"))
            assertEquals(Activity.RESULT_OK, received.getIntExtra("result_code", 0))
            assertEquals(uris.toSet(), received.getStringArrayListExtra("streams")!!.toSet())
            assertEquals(2, received.getStringArrayListExtra("streams")!!.size)
            assertEquals(bytes.map { value -> MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) } }.toSet(), received.getStringArrayListExtra("hashes")!!.toSet())
            assertFalse(received.getBooleanExtra("writable", true))
            assertFalse("Audit metadata must not grant access to unrelated files", received.getBooleanExtra("probe_readable", true))
            assertTrue(received.getIntExtra("grant_flags", 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val fields = received.getBundleExtra("returned_fields")!!
            val prefix = namespace.takeIf { it.isNotEmpty() }?.plus("_").orEmpty()
            assertEquals(pages, fields.getString("${prefix}document_page_uris_json"))
            assertTrue(fields.getString("${prefix}methodmesh_full_json")!!.contains("time_assurance"))
            val full = fields.getString("${prefix}methodmesh_full_json")!!
            assertEquals("Succeeded", JSONObject(full).getString("methodmesh_status"))
            assertFalse(JSONObject(full).has("methodmesh_log_warning"))
            if (logMode == "fail") assertTrue(fields.getString("${prefix}methodmesh_log_warning")!!.contains("Captured result returned unchanged"))
            else assertNull(fields.getString("${prefix}methodmesh_log_warning"))
            if (log != null) {
                val entry = service.open(log).bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }.map(::JSONObject).single()
                assertEquals(full, entry.getString("result_json"))
                assertEquals(2, entry.getJSONArray("media").length())
                assertEquals(0, entry.getJSONArray("media_errors").length())
                val linked = service.collection(log.id)
                assertEquals(2, linked.size)
                assertTrue(linked.all { it.entryId == entry.getString("entry_id") && !it.mediaId.isNullOrBlank() })
                assertEquals(bytes.map { it.decodeToString() }.toSet(), linked.map { service.open(it.ref).bufferedReader().use { reader -> reader.readText() } }.toSet())
                assertTrue(service.open(summary!!).bufferedReader().use { it.readText() }.contains("Two fixture files"))
            }
        } finally {
            As100MethodRegistry.install(originalRegistry)
            app.unregisterReceiver(receiver)
            if (log != null) {
                service.collection(log.id).forEach { service.delete(it.ref) }
                service.delete(log)
            }
            if (summary != null) service.delete(summary)
            // Only this test's generated bytes; no application/user outputs.
            folder.deleteRecursively()
        }
    }
}
