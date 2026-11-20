package com.example.methodmesh.transport.android

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.runtime.As100MethodRegistry
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputExportRepository
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class DownloadsSaveRegressionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun label() = "review${UUID.randomUUID().toString().replace("-", "")}"
    private fun uri(file: File) = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file).toString()
    private fun rows(label: String): List<Uri> {
        val collection = MediaStore.Files.getContentUri("external")
        return app.contentResolver.query(collection, arrayOf("_id"), "relative_path LIKE ?", arrayOf("Download/MethodMesh/methodmesh_${label}_%/"), null)!!.use { cursor ->
            buildList { while (cursor.moveToNext()) add(Uri.withAppendedPath(collection, cursor.getLong(0).toString())) }
        }
    }

    @Test fun successfulSaveContainsAllBytesSpecificTypesAndOneCoherentStem() {
        val source = File(app.cacheDir, "${label()}.pdf").apply { writeText("%PDF-1.4\nfixture bytes") }
        var saved: OutputExportRepository.DownloadsExport? = null
        try {
            saved = OutputExportRepository.saveToDownloads(app, label(), "Useful result", listOf(uri(source)), "{\"fixture\":true}")
            assertEquals(3, saved.files.size)
            assertTrue(saved.files.all { it.filename.startsWith(saved.folderName) })
            val media = saved.files.single { it.field == "media_1" }
            assertEquals("application/pdf", media.mimeType)
            assertTrue(media.filename.endsWith(".pdf"))
            assertArrayEquals(source.readBytes(), app.contentResolver.openInputStream(Uri.parse(media.uri))!!.use { it.readBytes() })
            val text = saved.files.single { it.field == "result_text" }
            assertEquals("Useful result", app.contentResolver.openInputStream(Uri.parse(text.uri))!!.bufferedReader().use { it.readText() })
            assertTrue(source.exists())
        } finally {
            saved?.files?.forEach { app.contentResolver.delete(Uri.parse(it.uri), null, null) }
            source.delete()
        }
    }

    @Test fun missingMediaFailsAndRemovesOnlyThePartialAttempt() {
        val name = label()
        val source = File(app.cacheDir, "$name.pdf").apply { writeText("%PDF-1.4\nfixture bytes") }
        try {
            val failure = runCatching {
                OutputExportRepository.saveToDownloads(app, name, "Useful result", listOf(uri(source), uri(File(app.cacheDir, "$name-missing.pdf"))))
            }.exceptionOrNull()
            assertNotNull("A missing attachment must not be silently omitted", failure)
            assertTrue(failure!!.message.orEmpty().contains("No files from this attempt were kept"))
            assertTrue(rows(name).isEmpty())
            assertTrue("Rollback must not delete producer/source bytes", source.exists())
        } finally { source.delete() }
    }

    @Test fun saveAndFinishFailureKeepsTheCommittedScreenOpenWithoutConfirming() {
        val registry = As100MethodRegistry.all()
        val missing = uri(File(app.cacheDir, "${label()}-missing.pdf"))
        val fixture = object : As100Method {
            override val id = "test.failed_save_closeout"
            override val ref = ArchitectureRef(ArchitectureId(id), "Method")
            override val descriptor = MethodDescriptor(ArchitectureId(id), MethodObjectType.Calculation, "Save fixture", "1")
            override val contract = MethodContract(ref)
            override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
                ExecutionRequest(action = action, method = ref, context = context, signals = signals, inputs = inputs)
            override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) =
                ExecutionResult(request, TransformationStatus.Succeeded, observations = listOf(
                    Observation(phenomenon = "fixture", values = mapOf("answer" to "Committed fixture", "output_file_uri" to missing), provenance = ProvenanceContext("test"))
                ))
        }
        As100MethodRegistry.install(registry + fixture)
        try {
            val incoming = Intent(app, ExternalWorkflowActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra("method_id", fixture.id).putExtra("input_methodmesh_native_preset_run", "true")
                .putExtra("input_methodmesh_preset_result_action", "save").putExtra("input_methodmesh_finish_to_launcher", "true")
            ActivityScenario.launch<ExternalWorkflowActivity>(incoming).use { scenario ->
                await { node("Save and finish") != null }
                var button = node("Save and finish")!!
                while (!button.isClickable) button = button.parent!!
                button.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                instrumentation.waitForIdleSync()
                assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                await { node("Downloads save failed:", prefix = true) != null }
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
                scenario.onActivity { activity ->
                    assertFalse(activity.isFinishing)
                    val run = ViewModelProvider(activity)[ExternalWorkflowViewModel::class.java]
                    assertFalse(run.resultReturned)
                    assertTrue(run.progress.confirmed.isEmpty())
                    assertEquals(1, run.genericResults.size)
                }
                scenario.recreate()
                await { node("Downloads save failed:", prefix = true) != null }
            }
        } finally { As100MethodRegistry.install(registry) }
    }

    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Save regression timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25)
        }
    }
    private fun node(text: String, root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow, prefix: Boolean = false): AccessibilityNodeInfo? {
        if (root == null) return null
        val value = root.text?.toString().orEmpty()
        if (if (prefix) value.startsWith(text) else value == text) return root
        for (index in 0 until root.childCount) node(text, root.getChild(index), prefix)?.let { return it }
        return null
    }
}
