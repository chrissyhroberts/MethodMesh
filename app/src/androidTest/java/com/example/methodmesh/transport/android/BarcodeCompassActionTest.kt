package com.example.methodmesh.transport.android

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.*
import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.modules.compass.*
import com.example.methodmesh.modules.qrcode.*
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.*
import com.example.methodmesh.ui.theme.MethodMeshTheme
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real module-owned committed components, canonical synthetic captures. No camera/physical compass certification. */
class BarcodeCompassActionTest {
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
            check(SystemClock.uptimeMillis() < end) { "Barcode/Compass action fixture timed out" }
            instrumentation.waitForIdleSync(); SystemClock.sleep(35)
        }
    }
    private fun showMatching(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(3) { find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        instrumentation.waitForIdleSync(); SystemClock.sleep(100)
        await {
            if (find(predicate) != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(100); false
            }
        }
        return requireNotNull(find(predicate))
    }
    private fun show(label: String) = showMatching { it.text?.toString() == label }
    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var value = node
        while (!value.isClickable) value = requireNotNull(value.parent)
        return value
    }
    private fun click(label: String) {
        var node = clickable(show(label))
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync(); SystemClock.sleep(100)
        node = clickable(requireNotNull(text(label)))
        assertTrue("Disabled: $label", node.isEnabled); assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun toggleAudit() {
        show("Include full JSON / audit")
        assertTrue(requireNotNull(find({ it.isCheckable })).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun status(prefix: String) = showMatching { it.text?.toString()?.startsWith(prefix) == true }
    private fun full(result: ExecutionResult) = OutputFormatter.format(result, ReturnMode.Json, true, payloadMode = OutputFormatter.PayloadMode.FULL)
    private data class Download(val uri: Uri, val name: String, val path: String)
    private fun rows(prefix: String): List<Download> = app.contentResolver.query(MediaStore.Files.getContentUri("external"),
        arrayOf("_id", "_display_name", "relative_path"), "relative_path LIKE ?", arrayOf("Download/MethodMesh/" + prefix + "%/"), null)!!.use { cursor ->
        buildList { while (cursor.moveToNext()) add(Download(Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), cursor.getLong(0).toString()), cursor.getString(1), cursor.getString(2))) }
    }
    private fun read(uri: Uri) = app.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    @Suppress("DEPRECATION") private fun target(chooser: Intent): Intent = chooser.getParcelableExtra(Intent.EXTRA_INTENT)!!
    @Suppress("DEPRECATION") private fun streams(intent: Intent): List<Uri> = if (intent.action == Intent.ACTION_SEND_MULTIPLE)
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty() else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))

    private inner class Evidence(val barcode: Boolean, payload: String = "Decoded fixture " + UUID.randomUUID()) : AutoCloseable {
        val token = UUID.randomUUID().toString()
        val id = if (barcode) "barcode.scan" else "compass.read"
        val label = if (barcode) "Barcode scan" else "Compass"
        val result = if (barcode) As100BarcodeScanMethod.execute(As100BarcodeScanMethod.request(id,
            mapOf("barcode_payload" to payload, "barcode_source" to "test_fixture", "barcode_format" to "QR_CODE")), null, "test_fixture")
        else As100CompassMethod.capture(As100CompassMethod.request(id, emptyMap(), emptyList(), emptyList()), 91f,
            "bearing", 90f, 5f, "flat", "device_top_edge", 0f, 0f, 3, null)
        val primary = OutputFormatter.fields(result, false).getValue(if (barcode) "barcode_payload" else CompassFields.RESULT).toString()
        val json = full(result)
        val prefix = ResultShare.coherentFileStem(label, Instant.EPOCH).substringBefore("19700101")
        val createdUris = CopyOnWriteArrayList<Uri>()
        val published = AtomicInteger()
        val writeFinished = CountDownLatch(1)
        val choosers = CopyOnWriteArrayList<Intent>()
        val denied = AtomicBoolean(false)
        val slow = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val inserts = AtomicInteger()
        val busy = AtomicBoolean(false)
        val newScans = AtomicInteger()
        fun downloads() = rows(prefix).filter { it.uri in createdUris }
        override fun close() {
            release.countDown()
            if (entered.count == 0L) writeFinished.await(5, TimeUnit.SECONDS)
            downloads().forEach { app.contentResolver.delete(it.uri, null, null) }
            choosers.flatMap { streams(target(it)) }.distinct().forEach { app.contentResolver.delete(it, null, null) }
        }
    }
    private fun fixture(e: Evidence, action: String = "", total: Int = 1,
        run: (ActivityScenario<ExternalWorkflowActivity>, List<ExecutionResult>) -> Unit) {
        val returned = CopyOnWriteArrayList<ExecutionResult>()
        val incoming = Intent().putExtra("method_id", e.id)
            .putExtra("input_methodmesh_native_preset_run", action.isNotBlank().toString())
            .putExtra("input_methodmesh_preset_result_action", action).putExtra("input_methodmesh_finish_to_launcher", "true")
        val request = AndroidIntentRequestReader.workflowRequest(incoming)
        val context = CapabilityScreenContext(request.actions.single(), request, 1, total, CapabilityCompletionMode.ManualConfirmation,
            presentationMode = CapabilityPresentationMode.IntentLaunch, hostOwnsNativePresetCloseout = true)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED && activity.intent.getStringExtra("barcode_compass_actions") == e.token) {
                val resolver = ContentResolver.wrap(object : ContentProvider() {
                    override fun onCreate() = true
                    override fun getType(uri: Uri) = app.contentResolver.getType(uri)
                    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = app.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = app.contentResolver.openFileDescriptor(uri, mode)
                    override fun insert(uri: Uri, values: ContentValues?): Uri? {
                        e.inserts.incrementAndGet()
                        if (e.denied.get()) throw SecurityException("Fixture storage denied")
                        if (e.slow.get()) {
                            assertNotEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
                            e.entered.countDown(); check(e.release.await(20, TimeUnit.SECONDS)) { "Slow save not released" }
                        }
                        return app.contentResolver.insert(uri, values)?.also { e.createdUris += it }
                    }
                    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
                        val updated = app.contentResolver.update(uri, values, selection, selectionArgs)
                        if (values?.getAsInteger("is_pending") == 0 && updated > 0) { e.published.incrementAndGet(); e.writeFinished.countDown() }
                        return updated
                    }
                    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
                        val deleted = app.contentResolver.delete(uri, selection, selectionArgs)
                        e.writeFinished.countDown(); return deleted
                    }
                })
                val storage = object : ContextWrapper(app) { override fun getContentResolver() = resolver }
                val guarded = object : ContextWrapper(activity) {
                    override fun getContentResolver() = resolver
                    override fun getApplicationContext(): Context = storage
                    override fun startActivity(intent: Intent) {
                        check(intent.action == Intent.ACTION_CHOOSER) { "Unexpected Home/camera/external launch" }
                        if (e.denied.get()) throw ActivityNotFoundException("Fixture share denied")
                        e.choosers += intent
                    }
                    override fun grantUriPermission(toPackage: String, uri: Uri, flags: Int) { fail("No broad grants") }
                }
                val confirm: (ExecutionResult) -> Unit = { value ->
                    if (action == "share" && total == 1) assertEquals(1, e.choosers.size)
                    if (action == "save" && total == 1) assertTrue(e.downloads().isNotEmpty())
                    returned += value
                }
                activity.setContent { MethodMeshTheme { CompositionLocalProvider(LocalContext provides guarded) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (e.barcode) BarcodeScanCommittedResult(context, e.result, false, {}, { _, _ -> }, {}, confirm,
                            { e.newScans.incrementAndGet() }, { e.busy.set(it) })
                        else CompassCommittedResult(context, e.result, false, {}, { _, _ -> }, confirm, false, {}, { e.busy.set(it) })
                    }
                } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java).putExtra("barcode_compass_actions", e.token)).use { scenario ->
                show("Share"); run(scenario, returned); assertEquals(e.json, full(e.result))
            }
        } finally { e.release.countDown(); instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) } }
    }
    private fun checkShare(e: Evidence, chooser: Intent, audit: Boolean) {
        val intent = target(chooser)
        val expected = e.primary + if (audit) "\n\nmetadata.json\n" + e.json else ""
        assertEquals(Intent.ACTION_SEND, intent.action); assertEquals("text/plain", intent.type)
        assertEquals(expected, intent.getStringExtra(Intent.EXTRA_TEXT)); assertEquals(expected, read(streams(intent).single()))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(streams(intent), (0 until intent.clipData!!.itemCount).map { intent.clipData!!.getItemAt(it).uri })
    }
    private fun auditCycle(barcode: Boolean) = Evidence(barcode).use { e -> fixture(e) { scenario, returned ->
        assertFalse(find({ it.isCheckable })!!.isChecked)
        click("Share"); await { e.choosers.size == 1 }; checkShare(e, e.choosers[0], false)
        click("Save to Downloads"); status("Saved "); await { e.published.get() == 1 && !e.busy.get() }; assertEquals(1, e.downloads().size)
        assertEquals(e.primary, read(e.downloads().single().uri))
        toggleAudit(); scenario.recreate(); show("Include full JSON / audit"); assertTrue(find({ it.isCheckable })!!.isChecked)
        click("Share"); await { e.choosers.size == 2 }; checkShare(e, e.choosers[1], true)
        click("Copy"); val copied = AtomicReference<String>()
        scenario.onActivity { copied.set(it.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()) }
        assertEquals(e.primary + "\n\nmetadata.json\n" + e.json, copied.get())
        click("Save to Downloads"); status("Saved "); await { e.published.get() == 3 && !e.busy.get() }; assertEquals(3, e.downloads().size)
        assertEquals(e.json, read(e.downloads().single { it.name.endsWith("_metadata.json") }.uri))
        assertTrue(returned.isEmpty())
    } }
    @Test fun barcodeAuditCyclePreservesPayloadAndOriginalFullEvidence() = auditCycle(true)
    @Test fun compassAuditCycleKeepsOnlyHumanReadingAndOriginalFullEvidence() = auditCycle(false)
    @Test fun intermediateStepsNeverExportOrApplyFinalPresetLabels() {
        for (barcode in listOf(true, false)) for (action in listOf("home", "share", "save")) Evidence(barcode).use { e -> fixture(e, action, 2) { _, returned ->
            assertNull(text("Share and finish")); assertNull(text("Save and finish"))
            click("Done"); await { returned.size == 1 }; assertSame(e.result, returned.single())
            assertTrue(e.choosers.isEmpty()); assertTrue(e.downloads().isEmpty())
        } }
    }
    @Test fun finalPresetShareDispatchesBeforeConfirmingOriginalExactlyOnce() {
        for (barcode in listOf(true, false)) Evidence(barcode).use { e -> fixture(e, "share") { scenario, returned ->
            click("Share and finish"); await { returned.size == 1 }; checkShare(e, e.choosers.single(), false)
            assertSame(e.result, returned.single()); scenario.recreate(); show("Share"); assertEquals(1, returned.size); assertEquals(1, e.choosers.size)
        } }
    }
    @Test fun finalPresetSavePersistsBeforeConfirmingOriginal() {
        for (barcode in listOf(true, false)) Evidence(barcode).use { e -> fixture(e, "save") { _, returned ->
            click("Save and finish"); await { returned.size == 1 }; assertSame(e.result, returned.single())
            assertEquals(1, e.downloads().size); assertEquals(e.primary, read(e.downloads().single().uri))
        } }
    }
    @Test fun failedShareStaysUnconfirmedThroughRotationUntilExplicitRetry() {
        for (barcode in listOf(true, false)) Evidence(barcode).use { e -> e.denied.set(true); fixture(e, "share") { scenario, returned ->
            click("Share and finish"); status("Share failed:"); assertTrue(returned.isEmpty()); scenario.recreate(); status("Share failed:")
            e.denied.set(false); assertTrue(returned.isEmpty()); click("Share and finish"); await { returned.size == 1 }; assertSame(e.result, returned.single())
        } }
    }
    @Test fun failedSaveStaysUnconfirmedThroughRotationUntilExplicitRetry() {
        for (barcode in listOf(true, false)) Evidence(barcode).use { e -> e.denied.set(true); fixture(e, "save") { scenario, returned ->
            click("Save and finish"); status("Save failed:"); assertTrue(returned.isEmpty()); assertTrue(e.downloads().isEmpty())
            scenario.recreate(); status("Save failed:"); e.denied.set(false); assertTrue(returned.isEmpty())
            click("Save and finish"); await { returned.size == 1 }; assertSame(e.result, returned.single()); assertEquals(2, e.inserts.get())
        } }
    }
    @Test fun slowSaveDoesNotBlockUiReplayOrPermitNewScan() {
        for (barcode in listOf(true, false)) Evidence(barcode).use { e -> e.slow.set(true); fixture(e, "save") { scenario, returned ->
            click("Save and finish"); assertTrue(e.entered.await(5, TimeUnit.SECONDS)); scenario.onActivity { assertFalse(it.isFinishing) }
            assertTrue(e.busy.get()); assertFalse(clickable(show("Save and finish")).isEnabled)
            if (barcode) assertFalse(clickable(show("New scan")).isEnabled)
            scenario.recreate(); show("Save and finish"); assertEquals(1, e.inserts.get()); assertTrue(returned.isEmpty())
            e.release.countDown(); await { returned.size == 1 }; assertEquals(1, e.inserts.get()); assertSame(e.result, returned.single()); assertEquals(0, e.newScans.get())
        } }
    }
    @Test fun uriLookingBarcodeIsSharedAsLiteralTextNotAnAttachment() = Evidence(true, "content://not-a-real-provider/private/record.pdf").use { e -> fixture(e) { _, returned ->
        assertNull(text("Open link")); click("Share"); await { e.choosers.size == 1 }; checkShare(e, e.choosers.single(), false)
        assertTrue(returned.isEmpty()); assertNotEquals(e.primary, streams(target(e.choosers.single())).single().toString())
    } }
}
