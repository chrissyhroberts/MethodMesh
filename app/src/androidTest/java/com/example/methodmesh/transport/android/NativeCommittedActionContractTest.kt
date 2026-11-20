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
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.modules.networktools.NetworkCommittedActions
import com.example.methodmesh.modules.weather.WeatherCommittedActions
import com.example.methodmesh.modules.signals.SignalCommittedCard
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.ui.theme.MethodMeshTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import com.example.methodmesh.transport.workflow.ui.CanonicalCommittedResultActions
import com.example.methodmesh.transport.workflow.ui.RetainedExecutionStore
import androidx.lifecycle.ViewModelProvider
import com.example.methodmesh.modules.media.MediaLibraryStateCapabilityScreen
import com.example.methodmesh.modules.expenses.ExpensesManagerCapabilityScreen
import com.example.methodmesh.modules.svgselector.SvgSelectorCapabilityScreen
import com.example.methodmesh.modules.geocaching.MethodSurface
import java.util.concurrent.atomic.AtomicReference

/** Real committed action components, synthetic frozen records; no network/sensor/scanner/receiver app. */
class NativeCommittedActionContractTest {
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
    private fun visibleText(): String {
        val values = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            node.text?.toString()?.let { values += it.take(160) }
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        visit(root()); return values.joinToString(" | ").take(2400)
    }
    private fun text(label: String) = find({ it.text?.toString() == label })
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Native committed action fixture timed out: " + visibleText() }
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

    private data class Row(val uri: Uri, val name: String, val path: String, val mime: String)
    private fun rows(prefix: String): List<Row> {
        val collection = MediaStore.Files.getContentUri("external")
        return app.contentResolver.query(collection, arrayOf("_id", "_display_name", "relative_path", "mime_type"),
            "relative_path LIKE ?", arrayOf("Download/MethodMesh/" + prefix + "%/"), null)!!.use { cursor -> buildList {
            while (cursor.moveToNext()) add(Row(Uri.withAppendedPath(collection, cursor.getLong(0).toString()), cursor.getString(1), cursor.getString(2), cursor.getString(3)))
        } }
    }
    private fun full(result: ExecutionResult) = OutputFormatter.format(result, ReturnMode.Json, true, payloadMode = OutputFormatter.PayloadMode.FULL)
    private fun read(uri: Uri) = app.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    @Suppress("DEPRECATION") private fun target(chooser: Intent): Intent = chooser.getParcelableExtra(Intent.EXTRA_INTENT)!!
    @Suppress("DEPRECATION") private fun streams(intent: Intent): List<Uri> = if (intent.action == Intent.ACTION_SEND_MULTIPLE)
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty() else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    private fun auditOn(module: String) {
        show(if (module == "signal" || module == "canonical") "Include full JSON / audit" else "Include full JSON")
        val toggle = requireNotNull(find({ it.isCheckable })); assertFalse(toggle.isChecked)
        assertTrue(toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK)); instrumentation.waitForIdleSync()
        await { find({ it.isCheckable })?.isChecked == true }
    }
    private inner class Evidence(val module: String, val media: Boolean = false, val slowWrites: Boolean = false) : AutoCloseable {
        val writeEntered = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val writes = AtomicInteger()
        val builds = AtomicInteger()
        val token = UUID.randomUUID().toString().replace("-", "")
        val id = when (module) { "weather" -> "weather.conditions"; "network" -> "network.tools"; "canonical", "mediaScreen" -> "media.library.state"; "expensesScreen" -> "expenses.manage"; "svgScreen" -> "svg.select"; "geoBoundary" -> "geocache.project_waypoint"; else -> if (media) "signal.qr.receive" else "signal.qr.transmit" }
        val label = "Review " + module + " " + token
        val file = File(app.cacheDir, "native-" + token + ".bin").apply { if (media) writeText("%PDF-1.4 synthetic received bytes") }
        val mediaUri = if (media) FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file) else null
        val primary = "Captured result " + token
        val result = ExecutionResult(ExecutionRequest(action = id, method = ArchitectureRef(ArchitectureId(id), "Method")),
            TransformationStatus.Succeeded, observations = listOf(Observation(phenomenon = id, provenance = ProvenanceContext("fixture"), values = mapOf(
                when (module) { "weather" -> "weather_conditions_result"; "network" -> "network_value"; "canonical" -> "media_value"; else -> "signal_result" } to primary))))
        val json = full(result)
        val prefix = ResultShare.coherentFileStem(if (module == "signal") id.replace('.', ' ') else label, Instant.EPOCH).substringBefore("19700101")
        val before = rows(prefix).map { it.uri }.toSet()
        val choosers = CopyOnWriteArrayList<Intent>()
        fun downloads() = rows(prefix).filter { it.uri !in before }
        override fun close() {
            releaseWrite.countDown()
            downloads().forEach { app.contentResolver.delete(it.uri, null, null) }
            choosers.flatMap { streams(target(it)) }.filter { it != mediaUri }.distinct().forEach { app.contentResolver.delete(it, null, null) }
            if (file.exists()) assertTrue(file.delete())
        }
    }
    private fun fixture(e: Evidence, preset: String = "", total: Int = 1, denied: Boolean = false,
        screenFixture: String = "", run: (ActivityScenario<ExternalWorkflowActivity>, List<ExecutionResult>) -> Unit) {
        println("Committed fixture: " + e.module + " preset=" + preset)
        val returned = CopyOnWriteArrayList<ExecutionResult>()
        val request = AndroidIntentRequestReader.workflowRequest(Intent().putExtra("method_id", e.id)
            .putExtra("input_methodmesh_native_preset_run", (preset.isNotBlank()).toString())
            .putExtra("input_methodmesh_preset_result_action", preset).putExtra("input_methodmesh_finish_to_launcher", "true"))
        val context = CapabilityScreenContext(request.actions.single(), request, 1, total, CapabilityCompletionMode.ManualConfirmation,
            presentationMode = CapabilityPresentationMode.IntentLaunch, hostOwnsNativePresetCloseout = true)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED && activity.intent.getStringExtra("native_action_fixture") == e.token) {
                val resolver = ContentResolver.wrap(object : ContentProvider() {
                    override fun onCreate() = true
                    override fun getType(uri: Uri) = app.contentResolver.getType(uri)
                    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = app.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? = app.contentResolver.openFileDescriptor(uri, mode)
                    override fun insert(uri: Uri, values: ContentValues?): Uri? {
                        if (denied) throw SecurityException("Fixture storage denied")
                        if (e.slowWrites) {
                            assertNotEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
                            e.writes.incrementAndGet(); e.writeEntered.countDown()
                            check(e.releaseWrite.await(20, TimeUnit.SECONDS)) { "Slow fixture not released" }
                        }
                        return app.contentResolver.insert(uri, values)
                    }
                    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = app.contentResolver.update(uri, values, selection, selectionArgs)
                    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = app.contentResolver.delete(uri, selection, selectionArgs)
                })
                val storage = object : ContextWrapper(app) { override fun getContentResolver() = resolver }
                val guarded = object : ContextWrapper(activity) {
                    override fun getApplicationContext(): android.content.Context = storage
                    override fun getContentResolver() = resolver
                    override fun startActivity(intent: Intent) {
                        if (denied) throw ActivityNotFoundException("Fixture share denied")
                        assertEquals("No Home/permission detour on modern Android", Intent.ACTION_CHOOSER, intent.action)
                        e.choosers += intent
                    }
                    override fun grantUriPermission(toPackage: String, uri: Uri, flags: Int) { fail("Do not pre-grant every receiver") }
                }
                activity.setContent { MethodMeshTheme { CompositionLocalProvider(LocalContext provides guarded) {
                    when (screenFixture) {
                        "mediaScreen" -> Column(Modifier.verticalScroll(rememberScrollState())) { MediaLibraryStateCapabilityScreen.Render(context, {}, { returned += it }, {}) }
                        "expensesScreen" -> Column(Modifier.verticalScroll(rememberScrollState())) { ExpensesManagerCapabilityScreen.Render(context, {}, { returned += it }, {}) }
                        "svgScreen" -> Column(Modifier.verticalScroll(rememberScrollState())) { SvgSelectorCapabilityScreen.Render(context, {}, { returned += it }, {}) }
                        "geoBoundary" -> MethodSurface(e.label, "Synthetic frozen snapshot", context, {}, {}, true,
                            onDone = {}, committedKey = mapOf("frozen" to e.primary),
                            committedResultFactory = { e.builds.incrementAndGet(); e.result }, onDoneResult = { returned += it }) { androidx.compose.material3.Text(e.primary) }
                        else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            when (e.module) {
                                "weather" -> WeatherCommittedActions(context, e.label, e.result, false) { returned += e.result }
                                "network" -> NetworkCommittedActions(context, e.label, e.result, false) { returned += e.result }
                                "canonical" -> CanonicalCommittedResultActions(e.result, e.label, context, onDone = { returned += e.result })
                                else -> SignalCommittedCard(e.label, "payload", e.primary, emptyList(), null,
                                    { _, _ -> }, context, e.result, { returned += it }, exportText = e.primary,
                                    mediaUri = e.mediaUri?.toString().orEmpty(), mediaMime = if (e.media) "application/pdf" else "")
                            }
                        }
                    }
                } } }
            }
        }
        // Compose Dialog replaces LocalContext. Monitor only chooser dispatch, never a recipient action.
        val chooserMonitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? {
                if (screenFixture == "svgScreen" && intent.action == Intent.ACTION_CHOOSER) {
                    e.choosers += intent
                    return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
                }
                return null
            }
        }
        instrumentation.addMonitor(chooserMonitor)
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java).putExtra("native_action_fixture", e.token)).use { scenario ->
                if (screenFixture.isNotBlank() && screenFixture != "geoBoundary") {
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val store = ViewModelProvider(activity)[RetainedExecutionStore::class.java]
                        store.slot(store.snapshot().keys.single()).value = e.result
                    }
                }
                show("Share"); run(scenario, returned); assertEquals(e.json, full(e.result))
            }
        } finally {
            instrumentation.removeMonitor(chooserMonitor)
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        }
    }
    private fun checkShare(e: Evidence, chooser: Intent, audit: Boolean): String {
        val intent = target(chooser); val uris = streams(intent)
        val message = if (e.media) {
            assertNull(intent.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals(e.mediaUri, uris.first())
            read(uris.single { it != e.mediaUri })
        } else requireNotNull(intent.getStringExtra(Intent.EXTRA_TEXT))
        val base = message.substringBefore("\n\nmetadata.json\n")
        assertTrue(base.contains(e.primary)); assertEquals(audit, message.contains("\n\nmetadata.json\n"))
        if (audit) assertEquals(base + "\n\nmetadata.json\n" + e.json, message)
        val sidecar = uris.single { it != e.mediaUri }; assertEquals(message, read(sidecar))
        assertEquals(if (e.media) 2 else 1, uris.size)
        assertFalse(uris.any { app.contentResolver.getType(it) == "application/json" })
        for (grant in listOf(intent, chooser)) {
            assertTrue(grant.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(uris.toSet(), (0 until grant.clipData!!.itemCount).map { grant.clipData!!.getItemAt(it).uri }.toSet())
        }
        if (e.media) {
            assertTrue(requireNotNull(e.mediaUri) in uris)
            assertTrue(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.contains("application/pdf"))
            assertEquals("%PDF-1.4 synthetic received bytes", read(requireNotNull(e.mediaUri)))
        }
        return base
    }
    private fun auditCycle(module: String, media: Boolean = false) = Evidence(module, media).use { e -> fixture(e) { scenario, returned ->
        click("Share"); await { e.choosers.size == 1 }; val beef = checkShare(e, e.choosers[0], false)
        val save = if (module == "signal" || module == "canonical") "Save to Downloads" else "Save"
        click(save); await { e.downloads().size == if (media) 2 else 1 }
        assertFalse(e.downloads().any { it.name.endsWith("_metadata.json") })
        if (module == "canonical") await { text("Save to Downloads") != null && find({ it.text?.toString()?.startsWith("Saved ") == true }) != null }
        auditOn(module); scenario.recreate(); show("Share")
        assertTrue(find({ it.isCheckable })!!.isChecked)
        click("Share"); await { e.choosers.size == 2 }; assertEquals(beef, checkShare(e, e.choosers[1], true))
        click("Copy")
        val copied = AtomicReference<String>()
        scenario.onActivity { copied.set(it.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()) }
        assertEquals(beef + "\n\nmetadata.json\n" + e.json, copied.get())
        click(save); await { e.downloads().size == if (media) 5 else 3 }
        if (module == "canonical") await { text("Save to Downloads") != null && find({ it.text?.toString()?.startsWith("Saved ") == true }) != null }
        assertEquals(e.json, read(e.downloads().single { it.name.endsWith("_metadata.json") }.uri))
        e.downloads().groupBy { it.path }.values.forEach { files ->
            val text = files.single { it.name.endsWith("_result.txt") }; assertEquals(beef, read(text.uri))
            val stem = text.name.removeSuffix("_result.txt"); assertTrue(files.all { it.name.startsWith(stem + "_") })
            if (media) {
                val file = files.single { it.name.endsWith(".pdf") }; assertEquals("application/pdf", file.mime)
                assertEquals("%PDF-1.4 synthetic received bytes", read(file.uri))
            }
        }
        assertTrue(returned.isEmpty())
    } }
    @Test fun weatherAuditCycleUsesEquivalentTextSidecarAndOptionalDownloadsMetadata() = auditCycle("weather")
    @Test fun networkAuditCycleUsesEquivalentTextSidecarAndOptionalDownloadsMetadata() = auditCycle("network")
    @Test fun signalTextAuditCycleDefaultsOffAndKeepsOriginalEvidence() = auditCycle("signal")
    @Test fun signalReceivedFileAuditCyclePreservesTypedBytesAndSameMediaUri() = auditCycle("signal", true)
    @Test fun weatherAndNetworkIntermediatePresetStepsNeverExportOrNavigate() {
        for (module in listOf("weather", "network")) for (action in listOf("share", "save", "home")) Evidence(module).use { e -> fixture(e, preset = action, total = 2) { _, returned ->
            click("Done"); await { returned.size == 1 }; assertSame(e.result, returned.single())
            assertTrue(e.choosers.isEmpty()); assertTrue(e.downloads().isEmpty())
        } }
    }
    @Test fun allThreeFinalPresetShareActionsDispatchBeforeConfirmation() {
        for (module in listOf("weather", "network", "signal")) Evidence(module).use { e -> fixture(e, preset = "share") { _, returned ->
            click("Share and finish"); await { returned.size == 1 }; assertEquals(1, e.choosers.size); assertSame(e.result, returned.single())
        } }
    }
    @Test fun failedPresetShareNeverConfirmsOrOpensHome() {
        for (module in listOf("weather", "network", "signal")) Evidence(module).use { e -> fixture(e, preset = "share", denied = true) { scenario, returned ->
            click("Share and finish"); await { find({ it.text?.toString()?.startsWith("Share failed:") == true }) != null }
            assertTrue(returned.isEmpty()); assertTrue(e.choosers.isEmpty()); scenario.onActivity { assertFalse(it.isFinishing) }
        } }
    }
    @Test fun failedPresetSaveNeverConfirmsOrOpensHome() {
        for (module in listOf("weather", "network", "signal")) Evidence(module).use { e -> fixture(e, preset = "save", denied = true) { scenario, returned ->
            click("Save and finish"); await { find({ it.text?.toString()?.contains("save failed:", ignoreCase = true) == true }) != null }
            assertTrue(returned.isEmpty()); assertTrue(e.downloads().isEmpty()); scenario.onActivity { assertFalse(it.isFinishing) }
        } }
    }
    @Test fun canonicalActionsAuditCyclePreservesTextMetadataAndRotation() = auditCycle("canonical")
    @Test fun canonicalIntermediateActionsOnlyConfirmOriginalResult() {
        for (action in listOf("share", "save", "home")) Evidence("canonical").use { e -> fixture(e, preset = action, total = 2) { _, returned ->
            click("Done"); await { returned.size == 1 }; assertSame(e.result, returned.single())
            assertTrue(e.choosers.isEmpty()); assertTrue(e.downloads().isEmpty())
        } }
    }
    @Test fun canonicalFinalShareDispatchesBeforeOneConfirmation() = Evidence("canonical").use { e -> fixture(e, preset = "share") { scenario, returned ->
        click("Share and finish"); await { returned.size == 1 }; assertEquals(1, e.choosers.size)
        assertSame(e.result, returned.single()); checkShare(e, e.choosers.single(), false)
        scenario.recreate(); show("Share"); assertEquals(1, returned.size); assertEquals(1, e.choosers.size)
    } }
    @Test fun canonicalFailedShareRetainsErrorAndNeverConfirms() = Evidence("canonical").use { e -> fixture(e, preset = "share", denied = true) { scenario, returned ->
        click("Share and finish"); await { find({ it.text?.toString()?.startsWith("Share failed:") == true }) != null }
        scenario.recreate(); show("Share and finish")
        assertNotNull(find({ it.text?.toString()?.startsWith("Share failed:") == true }))
        assertTrue(returned.isEmpty()); assertTrue(e.choosers.isEmpty())
    } }
    @Test fun canonicalFailedSaveRetainsErrorAndNeverConfirms() = Evidence("canonical").use { e -> fixture(e, preset = "save", denied = true) { scenario, returned ->
        click("Save and finish"); await { find({ it.text?.toString()?.startsWith("Save failed:") == true }) != null }
        scenario.recreate(); show("Save and finish")
        assertNotNull(find({ it.text?.toString()?.startsWith("Save failed:") == true }))
        assertTrue(returned.isEmpty()); assertTrue(e.downloads().isEmpty())
    } }
    @Test fun canonicalSlowSaveKeepsUiResponsiveAndSurvivesRotationWithoutReplay() = Evidence("canonical", slowWrites = true).use { e ->
        fixture(e, preset = "save") { scenario, returned ->
            click("Save and finish"); assertTrue(e.writeEntered.await(5, TimeUnit.SECONDS))
            // A main-thread callback and recreation both complete while storage is blocked.
            scenario.onActivity { assertFalse(it.isFinishing) }
            assertTrue(returned.isEmpty()); assertFalse(clickable(show("Save and finish")).isEnabled)
            scenario.recreate(); show("Save and finish")
            assertFalse(clickable(show("Save and finish")).isEnabled); assertEquals(1, e.writes.get())
            e.releaseWrite.countDown(); await { returned.size == 1 }; assertSame(e.result, returned.single())
            assertEquals(1, e.writes.get()); assertEquals(1, e.downloads().size)
            assertTrue(read(e.downloads().single().uri).contains(e.primary))
            scenario.recreate(); show("Share"); assertEquals(1, returned.size); assertEquals(1, e.writes.get())
        }
    }
    /** Frozen boundary injection exercises actual screens, not acquisition/provider/persistent-edit workflows. */
    @Test fun mediaExpensesAndSvgActualScreensKeepOriginalEvidenceAcrossRotationAndDone() {
        for (module in listOf("mediaScreen", "expensesScreen", "svgScreen")) Evidence(module).use { e ->
            fixture(e, screenFixture = module) { scenario, returned ->
                scenario.recreate(); show("Share")
                scenario.onActivity { activity ->
                    val store = ViewModelProvider(activity)[RetainedExecutionStore::class.java]
                    assertSame(e.result, store.snapshot().values.filterNotNull().single())
                }
                click("Done"); await { returned.size == 1 }; assertSame(e.result, returned.single())
                assertTrue(e.choosers.isEmpty())
            }
        }
    }
    @Test fun mediaAndExpensesActualScreensCannotBypassFailedFinalPresetShare() {
        for (module in listOf("mediaScreen", "expensesScreen")) Evidence(module).use { e ->
            fixture(e, preset = "share", denied = true, screenFixture = module) { scenario, returned ->
                assertNull(text("Done"))
                click("Share and finish"); await { find({ it.text?.toString()?.startsWith("Share failed:") == true }) != null }
                scenario.recreate(); show("Share and finish"); assertNull(text("Done"))
                assertTrue(returned.isEmpty()); assertTrue(e.choosers.isEmpty())
            }
        }
    }
    @Test fun svgActualPresetDialogOnlyFinishesAfterChooserDispatch() = Evidence("svgScreen").use { e ->
        fixture(e, preset = "share", screenFixture = "svgScreen") { scenario, returned ->
            assertNull(text("Done")); scenario.recreate(); show("Share and finish"); assertNull(text("Done"))
            click("Share and finish"); await { returned.size == 1 }
            assertSame(e.result, returned.single()); assertEquals(1, e.choosers.size)
            assertEquals(e.json, full(e.result))
        }
    }
    @Test fun geocachingCommittedBoundaryRetainsFactoryResultThroughRotationAndCloseout() = Evidence("geoBoundary").use { e ->
        fixture(e, preset = "share", screenFixture = "geoBoundary") { scenario, returned ->
            assertEquals(1, e.builds.get()); scenario.recreate(); show("Share and finish")
            assertEquals(1, e.builds.get()); click("Share and finish"); await { returned.size == 1 }
            assertSame(e.result, returned.single()); assertEquals(1, e.choosers.size); assertEquals(1, e.builds.get())
        }
    }
    @Test fun permissionActivityOnModernAndroidFinishesWithoutRequestingOrSaving() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val info = app.packageManager.getActivityInfo(ComponentName(app, DownloadStoragePermissionActivity::class.java), 0)
        assertFalse(info.exported)
        ActivityScenario.launch<DownloadStoragePermissionActivity>(Intent(app, DownloadStoragePermissionActivity::class.java)).use {
            await { it.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
        }
    }
    @Test fun specificProviderMimeRemainsAuthoritativeOverAConflictingHint() {
        val source = File(app.cacheDir, "mime-" + UUID.randomUUID() + ".png").apply { writeText("synthetic PNG bytes") }
        val uri = FileProvider.getUriForFile(app, app.packageName + ".fileprovider", source).toString()
        var saved: OutputExportRepository.DownloadsExport? = null
        try {
            saved = OutputExportRepository.saveToDownloads(app, "Mime fixture " + UUID.randomUUID(), "Result", listOf(uri), mediaMimeTypes = mapOf(uri to "application/pdf"))
            val media = saved.files.single { it.field == "media_1" }
            assertEquals("image/png", media.mimeType); assertTrue(media.filename.endsWith(".png"))
        } finally { saved?.files?.forEach { app.contentResolver.delete(Uri.parse(it.uri), null, null) }; assertTrue(source.delete()) }
    }
}
