package com.example.methodmesh.modules.paperbridge

import android.content.*
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.android.AndroidIntentRequestReader
import com.example.methodmesh.transport.android.ExternalWorkflowActivity
import com.example.methodmesh.transport.workflow.ui.*
import com.example.methodmesh.ui.theme.MethodMeshTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** Actual committed screens with synthetic canonical records; no capture-gate bypass in production. */
class PaperCommittedExportTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun root(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean, node: AccessibilityNodeInfo? = root()): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(predicate, node.getChild(i))?.let { return it }
        return null
    }
    private fun text(label: String) = find({ it.text?.toString() == label })
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Paper committed export fixture timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(30)
        }
    }
    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var value = node
        while (!value.isClickable) value = requireNotNull(value.parent)
        return value
    }
    private fun show(label: String): AccessibilityNodeInfo {
        repeat(3) { find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(120)
        await {
            if (text(label) != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(120)
                false
            }
        }
        return requireNotNull(text(label))
    }
    private fun click(label: String) {
        var node = clickable(show(label))
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(100)
        node = clickable(requireNotNull(text(label)))
        assertTrue("Disabled Paper/Barcode control: $label", node.isEnabled)
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val value = AtomicReference<ExecutionResult>()
        scenario.onActivity { value.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull()) }
        return value.get()
    }
    private fun full(result: ExecutionResult) = OutputFormatter.format(result, ReturnMode.Json,
        includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL)

    private inner class Evidence(val design: Boolean) : AutoCloseable {
        val token = UUID.randomUUID().toString().replace("-", "")
        val method = if (design) As100PaperFormDesignMethod else As100PaperFormTranscribeMethod
        private val files = mutableListOf<File>()
        private fun file(suffix: String, contents: String): Uri {
            val file = File(app.cacheDir, "paper-export-" + token + "-" + suffix).apply { writeText(contents) }
            files += file
            return FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file)
        }
        val raw = file("raw.png", "raw acquisition fixture")
        val page = file("registered.png", "registered page fixture")
        val roi = file("roi.png", "ROI fixture")
        val domain = if (design) listOf(file("form.paperbridge.json", "{\"domain\":true}"),
            file("form.paperbridge.yaml", "schema: fixture"), file("form.paperbridge.zip", "synthetic domain ZIP bytes"), raw, page, roi)
            else listOf(page, roi)
        val requestedDownloads = CopyOnWriteArrayList<Pair<String, String>>()
        val label = "Paper Bridge " + (if (design) "design " else "transcription ") + token
        val expectedText = if (design) token + " v1 · 1 fields" else "Participant ID: Captured answer"
        val result = if (design) As100PaperFormDesignMethod.result(
            ExecutionRequest(action = method.id, method = method.ref), mapOf(
                PaperDesignFields.STATUS to "succeeded", PaperDesignFields.TEMPLATE_ID to token,
                PaperDesignFields.TEMPLATE_VERSION to "1", PaperDesignFields.FIELD_COUNT to "1", PaperDesignFields.ODK_FIELD_COUNT to "1",
                PaperDesignFields.TEMPLATE_JSON to "{\"domain\":true}", PaperDesignFields.TEMPLATE_JSON_URI to domain[0].toString(),
                PaperDesignFields.TEMPLATE_YAML_URI to domain[1].toString(), PaperDesignFields.TEMPLATE_BUNDLE_URI to domain[2].toString(),
                PaperDesignFields.TEMPLATE_IMAGE_URI to raw.toString(), PaperDesignFields.PREPARED_FORM_URI to page.toString(),
                PaperDesignFields.MARKUP_IMAGE_URI to roi.toString()), null)
        else As100PaperFormTranscribeMethod.result(
            ExecutionRequest(action = method.id, method = method.ref), mapOf(
                PaperBridgeFields.STATUS to "succeeded", PaperBridgeFields.TEMPLATE_ID to token,
                PaperBridgeFields.SOURCE_IMAGE to raw.toString(), PaperBridgeFields.SOURCE_SHA256 to "original-source-hash",
                PaperBridgeFields.RECTIFIED_IMAGE to page.toString(), PaperBridgeFields.AUTO_ACCEPTED_COUNT to "1", PaperBridgeFields.REVIEWED_COUNT to "1",
                PaperBridgeFields.VALUES_JSON to JSONObject(mapOf("participant_id" to "Captured answer", "site_sketch" to roi.toString())).toString()),
            mapOf("participant_id" to "Captured answer", "site_sketch" to roi.toString()), null)
        val json = full(result)
        override fun close() {
            downloads(this).forEach { app.contentResolver.delete(it.uri, null, null) }
            files.forEach { if (it.exists()) assertTrue(it.delete()) }
        }
    }
    private data class Download(val uri: Uri, val name: String, val mime: String, val path: String)
    private fun downloads(e: Evidence): List<Download> {
        val collection = MediaStore.Files.getContentUri("external")
        val prefix = "Download/MethodMesh/methodmesh_paper_bridge_" + (if (e.design) "design_" else "transcription_") + e.token + "_%/"
        return app.contentResolver.query(collection, arrayOf("_id", "_display_name", "mime_type", "relative_path"),
            "relative_path LIKE ?", arrayOf(prefix), null)!!.use { cursor -> buildList {
            while (cursor.moveToNext()) add(Download(Uri.withAppendedPath(collection, cursor.getLong(0).toString()), cursor.getString(1), cursor.getString(2), cursor.getString(3)))
        } }
    }
    private fun read(uri: Uri) = app.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    @Suppress("DEPRECATION")
    private fun target(chooser: Intent): Intent = chooser.getParcelableExtra(Intent.EXTRA_INTENT)!!
    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri> = if (intent.action == Intent.ACTION_SEND_MULTIPLE)
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty() else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    private fun filename(uri: Uri) = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
        assertTrue(it.moveToFirst()); it.getString(0)
    }
    private fun auditOn() {
        show("Include full JSON / audit")
        val toggle = requireNotNull(find({ it.isCheckable }))
        assertFalse(toggle.isChecked)
        assertTrue(toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK)); instrumentation.waitForIdleSync()
        await { find({ it.isCheckable })?.isChecked == true }
    }
    private fun fixture(e: Evidence, preset: String = "", denied: Boolean = false, genericMime: Boolean = false,
        run: (ActivityScenario<ExternalWorkflowActivity>, List<ExecutionResult>, List<Intent>) -> Unit) {
        val returned = CopyOnWriteArrayList<ExecutionResult>(); val choosers = CopyOnWriteArrayList<Intent>()
        val incoming = Intent().putExtra("method_id", e.method.id)
            .putExtra("input_methodmesh_native_preset_run", (preset.isNotBlank()).toString())
            .putExtra("input_methodmesh_preset_result_action", preset).putExtra("input_methodmesh_finish_to_launcher", "true")
            .putExtra("input_methodmesh_runtime_fields", PaperBridgeInputs.TEMPLATE_JSON)
        val request = AndroidIntentRequestReader.workflowRequest(incoming)
        val context = CapabilityScreenContext(request.actions.single(), request, 1, 1, CapabilityCompletionMode.ManualConfirmation,
            presentationMode = CapabilityPresentationMode.IntentLaunch, hostOwnsNativePresetCloseout = true)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED && activity.intent.getStringExtra("paper_export_fixture") == e.token) {
                val resolver = ContentResolver.wrap(object : ContentProvider() {
                    override fun onCreate() = true
                    override fun getType(uri: Uri): String? = if (genericMime && uri.authority == app.packageName + ".fileprovider") "application/octet-stream" else app.contentResolver.getType(uri)
                    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = app.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                    override fun insert(uri: Uri, values: ContentValues?): Uri? {
                        if (denied) throw SecurityException("Fixture Downloads denied")
                        if (uri.authority == "media" && values != null) e.requestedDownloads +=
                            values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) to values.getAsString(MediaStore.MediaColumns.MIME_TYPE)
                        return app.contentResolver.insert(uri, values)
                    }
                    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = app.contentResolver.openFileDescriptor(uri, mode)
                    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = app.contentResolver.delete(uri, selection, selectionArgs)
                    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = app.contentResolver.update(uri, values, selection, selectionArgs)
                })
                val guarded = object : ContextWrapper(activity) {
                    override fun getContentResolver() = resolver
                    override fun startActivity(intent: Intent) {
                        if (denied) throw ActivityNotFoundException("Fixture sharing denied")
                        assertEquals(Intent.ACTION_CHOOSER, intent.action); choosers += intent
                    }
                    override fun grantUriPermission(toPackage: String, uri: Uri, flags: Int) { fail("Do not grant every candidate receiver access") }
                }
                activity.setContent { MethodMeshTheme { CompositionLocalProvider(LocalContext provides guarded) {
                    Surface(Modifier.fillMaxSize()) { Column(Modifier.verticalScroll(rememberScrollState())) {
                        val screen = if (e.design) PaperFormDesignCapabilityScreen else PaperFormTranscribeCapabilityScreen
                        screen.Render(context, {}, { returned += it }, {})
                    } }
                } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java).putExtra("paper_export_fixture", e.token)).use { scenario ->
                await {
                    val ready = AtomicReference(false)
                    scenario.onActivity { ready.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().size == 1) }; ready.get()
                }
                scenario.onActivity { activity -> ViewModelProvider(activity)[RetainedExecutionStore::class.java].let { store -> store.slot(store.snapshot().keys.single()).value = e.result } }
                show(if (e.design) "Committed design" else "Committed transcription")
                run(scenario, returned, choosers)
                assertSame(e.result, captured(scenario)); assertEquals(e.json, full(requireNotNull(captured(scenario))))
            }
        } finally { instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) } }
    }
    private fun assertShare(e: Evidence, chooser: Intent, audit: Boolean) {
        val intent = target(chooser); val uris = streams(intent)
        assertEquals(e.domain.toSet(), uris.filter { it in e.domain }.toSet())
        assertEquals(e.domain.size + 1, uris.size)
        if (!e.design) assertFalse(e.raw in uris)
        val text = e.expectedText + if (audit) "\n\nmetadata.json\n" + e.json else ""
        assertNull("File delivery must not fall back to a summary message", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(uris.first() in e.domain)
        val sidecar = uris.single { it !in e.domain }; assertEquals(text, read(sidecar))
        assertTrue(filename(sidecar).matches(Regex("methodmesh_paper_bridge_(design|transcription)_[0-9]{8}_[0-9]{6}_[0-9]{3}Z_result[.]txt")))
        assertEquals(e.domain.count { app.contentResolver.getType(it) == "application/json" }, uris.count { app.contentResolver.getType(it) == "application/json" })
        for (grant in listOf(intent, chooser)) {
            assertTrue(grant.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, grant.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals(uris.toSet(), (0 until grant.clipData!!.itemCount).map { grant.clipData!!.getItemAt(it).uri }.toSet())
        }
        if (e.design) assertTrue(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.contains("application/yaml"))
    }
    private fun shareToggle(design: Boolean) = Evidence(design).use { e -> fixture(e, genericMime = design) { _, returned, choosers ->
        click("Share"); await { choosers.size == 1 }; assertShare(e, choosers[0], false)
        auditOn(); click("Share"); await { choosers.size == 2 }; assertShare(e, choosers[1], true)
        assertEquals(streams(target(choosers[0])).filter { it in e.domain }, streams(target(choosers[1])).filter { it in e.domain })
        assertTrue(returned.isEmpty())
        choosers.flatMap { streams(target(it)) }.filter { it !in e.domain }.distinct().forEach { app.contentResolver.delete(it, null, null) }
    } }
    private fun saveToggle(design: Boolean) = Evidence(design).use { e -> fixture(e, genericMime = design) { _, returned, _ ->
        click("Save to Downloads"); await { downloads(e).size == e.domain.size + 1 }
        auditOn(); click("Save to Downloads"); await { downloads(e).size == e.domain.size * 2 + 3 }
        val groups = downloads(e).groupBy { it.path }; assertEquals(2, groups.size)
        val metadata = downloads(e).filter { it.name.endsWith("_metadata.json") }; assertEquals(1, metadata.size); assertEquals(e.json, read(metadata.single().uri))
        groups.values.forEach { files ->
            val text = files.single { it.name.endsWith("_result.txt") }; assertEquals(e.expectedText, read(text.uri))
            val stem = text.name.removeSuffix("_result.txt"); assertTrue(files.all { it.name.startsWith(stem + "_") })
            val media = files.filter { !it.name.endsWith("_result.txt") && !it.name.endsWith("_metadata.json") }
            assertEquals(e.domain.size, media.size)
            assertEquals(e.domain.map(::read).sorted(), media.map { read(it.uri) }.sorted())
            if (design) {
                val yaml = media.single { it.name.endsWith(".yaml") }
                assertEquals("application/yaml", e.requestedDownloads.single { it.first == yaml.name }.second)
                // MediaProvider can normalize an unknown suffix to octet-stream after insertion.
                // Verify our explicit request separately from Android's stored metadata.
                assertTrue(yaml.mime in setOf("application/yaml", "application/octet-stream"))
                Log.i("PaperExportFixture", "YAML Downloads requested=application/yaml stored=" + yaml.mime)
                assertEquals("application/zip", media.single { it.name.endsWith(".zip") }.mime)
                assertEquals(1, media.count { it.mime == "application/json" })
            } else assertFalse(media.any { read(it.uri) == read(e.raw) })
        }
        assertTrue(returned.isEmpty())
    } }
    @Test fun designerShareKeepsDomainFilesAndAppendsAuditOnlyAsText() = shareToggle(true)
    @Test fun transcriptionShareKeepsRegisteredPageAndRoisWithoutRawScanDuplication() = shareToggle(false)
    @Test fun designerSaveUsesDownloadsCoherentNamesAndOptionalAudit() = saveToggle(true)
    @Test fun transcriptionSaveUsesDownloadsCoherentNamesAndOptionalAudit() = saveToggle(false)
    @Test fun presetShareDispatchesBeforeConfirmingTheOriginalRecord() {
        for (design in listOf(false, true)) Evidence(design).use { e -> fixture(e, preset = "share") { _, returned, choosers ->
            click("Share and finish"); await { returned.size == 1 }; assertEquals(1, choosers.size); assertSame(e.result, returned.single())
            streams(target(choosers.single())).filter { it !in e.domain }.forEach { app.contentResolver.delete(it, null, null) }
        } }
    }
    @Test fun failedPresetShareKeepsBothCommittedScreensOpenAndUnconfirmed() {
        for (design in listOf(false, true)) Evidence(design).use { e -> fixture(e, preset = "share", denied = true) { scenario, returned, choosers ->
            click("Share and finish"); await { find({ it.text?.toString()?.startsWith("Share failed:") == true }) != null }
            assertTrue(returned.isEmpty()); assertTrue(choosers.isEmpty()); scenario.onActivity { assertFalse(it.isFinishing) }
        } }
    }
    @Test fun failedPresetSaveKeepsBothCommittedScreensOpenAndUnconfirmed() {
        for (design in listOf(false, true)) Evidence(design).use { e -> fixture(e, preset = "save", denied = true) { scenario, returned, _ ->
            click("Save and finish"); await { find({ it.text?.toString()?.startsWith("Save failed:") == true }) != null }
            assertTrue(returned.isEmpty()); assertTrue(downloads(e).isEmpty()); scenario.onActivity { assertFalse(it.isFinishing) }
        } }
    }
    @Test fun presetSaveFinishesOnlyAfterTheCompleteDownloadsBundleExists() {
        for (design in listOf(false, true)) Evidence(design).use { e -> fixture(e, preset = "save") { _, returned, _ ->
            click("Save and finish"); await { returned.size == 1 }; assertSame(e.result, returned.single())
            assertEquals(e.domain.size + 1, downloads(e).size); assertFalse(downloads(e).any { it.name.endsWith("_metadata.json") })
        } }
    }
}
