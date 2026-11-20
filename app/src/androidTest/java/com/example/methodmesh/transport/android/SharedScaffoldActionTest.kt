package com.example.methodmesh.transport.android

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.*
import com.example.methodmesh.ui.theme.MethodMeshTheme
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Production scaffold/action handler, controlled chooser/Downloads; no NFC radio or remote service. */
class SharedScaffoldActionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun root(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean,
        node: AccessibilityNodeInfo? = root()): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(predicate, node.getChild(i))?.let { return it }
        return null
    }
    private fun text(value: String) = find({ it.text?.toString() == value })
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 12_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Shared scaffold fixture timed out" }
            instrumentation.waitForIdleSync(); SystemClock.sleep(25)
        }
    }
    private fun button(label: String): AccessibilityNodeInfo {
        var node = requireNotNull(text(label)) { "Missing: " + label }
        while (!node.isClickable) node = requireNotNull(node.parent)
        return node
    }
    private fun clickable(target: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var node = target
        while (!node.isClickable) node = requireNotNull(node.parent)
        return node
    }
    private fun show(label: String) {
        repeat(4) { find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        instrumentation.waitForIdleSync()
        var visible: AccessibilityNodeInfo? = null
        await {
            visible = text(label)
            if (visible != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(80); false
            }
        }
        requireNotNull(visible).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
    }
    private fun click(label: String) {
        show(label)
        var target: AccessibilityNodeInfo? = null
        await { target = text(label); target != null && clickable(target!!).isEnabled }
        clickable(target!!).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        await { target = text(label); target != null && clickable(target!!).isEnabled && clickable(target!!).isVisibleToUser }
        assertTrue(clickable(target!!).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun status(prefix: String) {
        repeat(4) { find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        await {
            if (find({ it.text?.toString()?.startsWith(prefix) == true }) != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(80); false
            }
        }
    }
    private fun toggleAudit() {
        show("Include full JSON / audit")
        assertTrue(requireNotNull(find({ it.isCheckable })).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        await { find({ it.isCheckable })?.isChecked == true }
    }
    private fun full(result: ExecutionResult) = OutputFormatter.format(result, ReturnMode.Json, true,
        payloadMode = OutputFormatter.PayloadMode.FULL)
    @Suppress("DEPRECATION") private fun target(chooser: Intent): Intent = chooser.getParcelableExtra(Intent.EXTRA_INTENT)!!
    @Suppress("DEPRECATION") private fun streams(intent: Intent): List<Uri> = if (intent.action == Intent.ACTION_SEND_MULTIPLE)
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    private fun read(uri: Uri) = app.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }

    private inner class Evidence(val bundle: Boolean = false) : AutoCloseable {
        val token = UUID.randomUUID().toString()
        val id = if (bundle) com.example.methodmesh.modules.nfc.As100NfcOdkFormCompilerMethod.ID
            else com.example.methodmesh.modules.randomnumber.As100RandomNumberMethod.ID
        val label = "Scaffold review " + token
        val primary = "Visible result " + token
        val file = File(app.cacheDir, "scaffold-" + token + ".zip").apply { if (bundle) writeText("PK synthetic delivery bundle " + token) }
        val extra = File(app.cacheDir, "scaffold-" + token + ".pdf").apply { if (bundle) writeText("%PDF synthetic compiler constituent") }
        val fileUri = if (bundle) FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file) else null
        val extraUri = if (bundle) FileProvider.getUriForFile(app, app.packageName + ".fileprovider", extra) else null
        fun result() = ExecutionResult(ExecutionRequest(action = id, method = ArchitectureRef(ArchitectureId(id), "Method")),
            TransformationStatus.Succeeded, observations = listOf(Observation(phenomenon = id,
                values = mapOf("fixture_result" to primary, "fixture_audit_json" to """{"fixture":"private diagnostic"}""") +
                    if (bundle) mapOf("delivery_zip_uri" to fileUri.toString(), "compiled_pdf_uri" to extraUri.toString()) else emptyMap(),
                provenance = ProvenanceContext("test"))))
        val record = mutableStateOf(result())
        val original get() = record.value
        val choosers = CopyOnWriteArrayList<Intent>()
        val created = CopyOnWriteArrayList<Uri>()
        val returned = CopyOnWriteArrayList<ExecutionResult>()
        val confirmedOwners = CopyOnWriteArrayList<ExternalWorkflowActivity>()
        val events = CopyOnWriteArrayList<String>()
        val denyShare = AtomicBoolean(false)
        val denySave = AtomicBoolean(false)
        val slow = AtomicBoolean(false)
        val inserts = AtomicInteger()
        val published = AtomicInteger()
        val back = AtomicInteger()
        val retry = AtomicInteger()
        val cancel = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finishedWrite = CountDownLatch(1)
        val active = AtomicReference<ExternalWorkflowActivity>()
        fun preview(): Map<String, Any?> = mapOf("fixture_result" to primary) +
            if (bundle) mapOf("delivery_zip_uri" to fileUri, "compiled_pdf_uri" to extraUri) else emptyMap()
        override fun close() {
            release.countDown()
            if (entered.count == 0L) finishedWrite.await(8, TimeUnit.SECONDS)
            created.forEach { app.contentResolver.delete(it, null, null) }
            choosers.flatMap { streams(target(it)) }.filter { it != fileUri && it != extraUri }.distinct()
                .forEach { app.contentResolver.delete(it, null, null) }
            file.delete(); extra.delete()
        }
    }

    private fun fixture(e: Evidence, action: String = "", total: Int = 1, automatic: Boolean = false,
        emptyPreview: Boolean = false, launcher: Boolean = true,
        run: (ActivityScenario<ExternalWorkflowActivity>) -> Unit) {
        val intent = Intent().putExtra("method_id", e.id)
            .putExtra("input_methodmesh_native_preset_run", action.isNotBlank().toString())
            .putExtra("input_methodmesh_preset_result_action", action)
            .putExtra("input_methodmesh_finish_to_launcher", launcher.toString())
            .putExtra("input_methodmesh_share_recipient", "fixture@example.invalid")
        val request = AndroidIntentRequestReader.workflowRequest(intent)
        val context = CapabilityScreenContext(request.actions.single(), request, 1, total,
            if (automatic) CapabilityCompletionMode.AutomaticReturn else CapabilityCompletionMode.ManualConfirmation,
            presentationMode = CapabilityPresentationMode.IntentLaunch, hostOwnsNativePresetCloseout = false)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED && activity.intent.getStringExtra("scaffold_fixture") == e.token) {
                e.active.set(activity)
                val resolver = ContentResolver.wrap(object : ContentProvider() {
                    override fun onCreate() = true
                    override fun getType(uri: Uri) = app.contentResolver.getType(uri)
                    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? =
                        app.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = app.contentResolver.openFileDescriptor(uri, mode)
                    override fun insert(uri: Uri, values: ContentValues?): Uri? {
                        assertNotEquals("Downloads must not block the UI thread", Looper.getMainLooper(), Looper.myLooper())
                        e.inserts.incrementAndGet()
                        if (e.denySave.get()) throw SecurityException("Fixture storage denied")
                        if (e.slow.get()) {
                            e.entered.countDown()
                            check(e.release.await(25, TimeUnit.SECONDS)) { "Blocked save not released" }
                        }
                        return app.contentResolver.insert(uri, values)?.also { e.created += it }
                    }
                    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
                        val count = app.contentResolver.update(uri, values, selection, selectionArgs)
                        if (values?.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 0) {
                            e.events += "published"; e.published.incrementAndGet(); e.finishedWrite.countDown()
                        }
                        return count
                    }
                    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) =
                        app.contentResolver.delete(uri, selection, selectionArgs)
                })
                val storage = object : ContextWrapper(app) { override fun getContentResolver() = resolver }
                val guarded = object : ContextWrapper(activity) {
                    override fun getContentResolver() = resolver
                    override fun getApplicationContext(): Context = storage
                    override fun startActivity(intent: Intent) {
                        if (e.denyShare.get()) throw ActivityNotFoundException("Fixture chooser denied")
                        assertEquals("The scaffold must delegate Home to its owner", Intent.ACTION_CHOOSER, intent.action)
                        e.events += "chooser"; e.choosers += intent
                    }
                }
                activity.setContent { MethodMeshTheme { CompositionLocalProvider(LocalContext provides guarded) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        val original = e.record.value
                        CapabilityScreenScaffold(e.label, e.id, context, true, original,
                            if (emptyPreview) emptyMap() else e.preview(),
                            { e.back.incrementAndGet() }, { e.retry.incrementAndGet() },
                            { e.events += "confirmed"; e.returned += original; e.confirmedOwners += activity },
                            { e.cancel.incrementAndGet() }) { Text("Working inputs") }
                    }
                } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java)
                .putExtra("scaffold_fixture", e.token)).use { scenario ->
                await { e.active.get() != null }
                run(scenario)
            }
        } finally {
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        }
    }

    @Test fun finalPresetShareDispatchesBeforeConfirmingTheExactOriginalWithRecipientHint() = Evidence().use { e ->
        fixture(e, "share") {
            click("Share and finish"); await { e.returned.size == 1 }
            assertEquals(listOf("chooser", "confirmed"), e.events)
            val sent = target(e.choosers.single())
            assertEquals(e.primary, sent.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals(listOf("fixture@example.invalid"), sent.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList())
            assertEquals(e.primary, read(streams(sent).single()))
            assertSame(e.original, e.returned.single())
        }
    }

    @Test fun failedShareKeepsTheOriginalOpenAndNeedsAnExplicitRetryAfterRotation() = Evidence().use { e ->
        e.denyShare.set(true)
        fixture(e, "share") { scenario ->
            click("Share and finish"); status("Share failed:")
            assertTrue(e.returned.isEmpty()); assertTrue(e.choosers.isEmpty())
            e.denyShare.set(false); scenario.recreate(); status("Share failed:")
            assertTrue(e.returned.isEmpty()); assertTrue(e.choosers.isEmpty())
            click("Share and finish"); await { e.returned.size == 1 }
            assertSame(e.original, e.returned.single())
        }
    }

    @Test fun blockedSaveLocksNavigationAndFinishesOnceOnTheCurrentActivityAfterRotation() = Evidence().use { e ->
        e.slow.set(true)
        fixture(e, "save") { scenario ->
            click("Save and finish"); assertTrue(e.entered.await(8, TimeUnit.SECONDS))
            for (label in listOf("Save and finish", "Retry", "Back", "Cancel")) {
                show(label); assertFalse(label, button(label).isEnabled)
            }
            assertTrue(e.returned.isEmpty()); assertEquals(1, e.inserts.get())
            val old = e.active.get()
            instrumentation.runOnMainSync { old.recreate() }
            await { e.active.get() !== old }
            show("Save and finish"); assertFalse(button("Save and finish").isEnabled)
            e.release.countDown()
            await { e.returned.size == 1 && e.published.get() == 1 }
            assertEquals(listOf("published", "confirmed"), e.events)
            assertSame(e.original, e.returned.single())
            assertSame(e.active.get(), e.confirmedOwners.single())
            scenario.recreate(); instrumentation.waitForIdleSync()
            assertEquals(1, e.inserts.get()); assertEquals(1, e.returned.size)
        }
    }

    @Test fun failedSaveNeverConfirmsOrReplaysButAllowsExplicitSuccessfulRetry() = Evidence().use { e ->
        e.denySave.set(true)
        fixture(e, "save") { scenario ->
            click("Save and finish"); status("Save failed:")
            assertEquals(1, e.inserts.get()); assertTrue(e.returned.isEmpty())
            e.denySave.set(false); scenario.recreate(); status("Save failed:")
            assertEquals(1, e.inserts.get()); assertTrue(e.returned.isEmpty())
            click("Save and finish"); await { e.returned.size == 1 }
            assertEquals(2, e.inserts.get()); assertSame(e.original, e.returned.single())
        }
    }

    @Test fun intermediateStepsConfirmWithoutApplyingTheFinalPresetAction() {
        for (action in listOf("save", "share")) Evidence().use { e ->
            fixture(e, action, total = 2) {
                click("Continue"); await { e.returned.size == 1 }
                assertEquals(0, e.inserts.get()); assertTrue(e.choosers.isEmpty())
                assertSame(e.original, e.returned.single())
            }
        }
    }

    @Test fun homeAlwaysDelegatesConfirmationEvenWhenTheHostOwnershipFlagIsAbsent() = Evidence().use { e ->
        fixture(e, "home", launcher = false) {
            click("Home"); await { e.returned.size == 1 }
            assertEquals(listOf("confirmed"), e.events)
            assertTrue(e.choosers.isEmpty())
        }
    }

    @Test fun manualSaveAndShareKeepTheResultOpenAndAuditRetainsExactFullJsonAcrossRotation() = Evidence().use { e ->
        fixture(e) { scenario ->
            click("Save to Downloads"); status("Saved "); assertEquals(1, e.created.size); assertTrue(e.returned.isEmpty())
            assertEquals(e.primary, read(e.created.single()))
            toggleAudit(); scenario.recreate(); show("Include full JSON / audit")
            assertTrue(find({ it.isCheckable })!!.isChecked)
            click("Share result"); status("Sharing ")
            val sent = target(e.choosers.single())
            val payload = sent.getStringExtra(Intent.EXTRA_TEXT)!!
            assertEquals(e.primary + "\n\nmetadata.json\n" + full(e.original), payload)
            assertEquals(payload, read(streams(sent).single()))
            assertTrue(e.returned.isEmpty())
            click("Save to Downloads"); status("Saved "); await { e.created.size == 3 && e.published.get() == 3 }
            val saved = e.created.map(::read)
            assertEquals(2, saved.count { it == e.primary }); assertEquals(1, saved.count { it == full(e.original) })
            click("Done"); await { e.returned.size == 1 }
        }
    }

    @Test fun compilerDeliveryZipRemainsTheOnlyDomainAttachmentWithAndWithoutAudit() = Evidence(bundle = true).use { e ->
        fixture(e) {
            click("Share file"); status("Sharing ")
            toggleAudit(); click("Share file"); status("Sharing ")
            assertEquals(2, e.choosers.size)
            for ((index, chooser) in e.choosers.withIndex()) {
                val sent = target(chooser)
                assertFalse(sent.hasExtra(Intent.EXTRA_TEXT))
                assertEquals(e.fileUri, streams(sent).first())
                assertFalse(streams(sent).contains(e.extraUri))
                assertEquals(e.file.readText(), read(streams(sent).first()))
                val summary = read(streams(sent).last())
                assertTrue(summary.contains(e.primary))
                assertEquals(index == 1, summary.contains(full(e.original)))
            }
        }
    }

    @Test fun emptyPreviewCannotBypassAFinalPresetShare() = Evidence().use { e ->
        fixture(e, "share", emptyPreview = true) {
            click("Share and finish"); status("Share failed:")
            assertTrue(e.returned.isEmpty()); assertTrue(e.choosers.isEmpty())
        }
    }

    @Test fun automaticExternalReturnNeverRunsNativeExportsOrDuplicatesAnOriginalOnRotation() = Evidence().use { e ->
        fixture(e, "share", automatic = true) { scenario ->
            await { e.returned.size == 1 }
            assertSame(e.original, e.returned.single())
            scenario.recreate(); instrumentation.waitForIdleSync()
            assertEquals(1, e.returned.size); assertTrue(e.choosers.isEmpty()); assertEquals(0, e.inserts.get())
            scenario.onActivity { e.record.value = e.result() }
            await { e.returned.size == 2 }
            assertNotEquals(e.returned[0].request.id, e.returned[1].request.id)
        }
    }
}
