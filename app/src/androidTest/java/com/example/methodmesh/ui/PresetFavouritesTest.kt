package com.example.methodmesh.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.methodmesh.core.methodmesh.runtime.CapabilityFavoritesRepository
import com.example.methodmesh.core.protocols.*
import com.example.methodmesh.core.scheduling.SchedulerDispatchActivity
import com.example.methodmesh.modules.qrcode.As100BarcodeGenerateMethod
import com.example.methodmesh.transport.android.ExternalWorkflowActivity
import com.example.methodmesh.ui.theme.MethodMeshTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Every preference file is test-namespaced; no user preset or favourite is rewritten. */
internal class FavouriteFixtureContext(
    base: Context,
    private val namespace: String,
    private val files: MutableSet<String>,
    private val launches: MutableList<Intent> = CopyOnWriteArrayList()
) : ContextWrapper(base) {
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val scoped = "$namespace.$name"
        files += scoped
        return super.getSharedPreferences(scoped, mode)
    }
    override fun startActivity(intent: Intent) { launches += Intent(intent) }
}

abstract class FavouritesUiFixture {
    protected val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    protected val app get() = instrumentation.targetContext
    private val namespace = "preset-favourite-test-${UUID.randomUUID()}"
    private val files = Collections.synchronizedSet(mutableSetOf<String>())
    protected val launches = CopyOnWriteArrayList<Intent>()
    protected val context: Context get() = FavouriteFixtureContext(app, namespace, files, launches)
    protected fun preset(name: String = "Favourite fixture", id: String = UUID.randomUUID().toString()) =
        ProtocolLibraryRepository.savePreset(context, CapabilityPreset(
            id = id, name = name, methodId = "barcode.generate",
            settingsJson = "{\"barcode_payload\":\"Frozen favourite payload\",\"barcode_format\":\"QR_CODE\"}",
            payloadMode = ProtocolPayloadMode.FULL, resultAction = PresetResultAction.SAVE,
            launchMode = PresetLaunchMode.INTERACTIVE, description = "Favourite regression fixture"
        ))

    @After fun cleanup() { files.toList().forEach { app.deleteSharedPreferences(it) } }

    private fun freshRoot(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean, root: AccessibilityNodeInfo? = freshRoot()): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) find(predicate, root.getChild(index))?.let { return it }
        return null
    }
    protected fun text(label: String) = find({ it.text?.toString()?.contains(label) == true })
    protected fun description(label: String) = find({ it.contentDescription?.toString() == label })
    protected fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Saved favourite fixture timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25)
        }
    }
    protected fun click(label: String, byDescription: Boolean = false) {
        fun target() = if (byDescription) description(label) else text(label)
        await {
            if (target() != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(300)
                false
            }
        }
        var node = requireNotNull(target())
        while (!node.isClickable) node = requireNotNull(node.parent)
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(200)
        node = requireNotNull(target())
        while (!node.isClickable) node = requireNotNull(node.parent)
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    protected fun query(value: String) {
        await {
            if (find({ it.isEditable }) != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                SystemClock.sleep(300)
                false
            }
        }
        assertTrue(find({ it.isEditable })!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }))
        instrumentation.waitForIdleSync()
    }

    protected fun fixture(content: @Composable () -> Unit, run: (ActivityScenario<ExternalWorkflowActivity>) -> Unit) {
        val token = UUID.randomUUID().toString()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED && activity.intent.getStringExtra("favourite_fixture") == token) {
                val scoped = FavouriteFixtureContext(activity, namespace, files, launches)
                activity.setContent { MethodMeshTheme { CompositionLocalProvider(LocalContext provides scoped) { content() } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java)
                .putExtra("favourite_fixture", token)).use { scenario ->
                instrumentation.waitForIdleSync()
                run(scenario)
            }
        } finally {
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        }
    }

}

class PresetFavouritesTest : FavouritesUiFixture() {
    @Test fun presetsAndCapabilityFavouritesHaveIndependentIdsWithoutChangingPresetConfiguration() {
        val first = preset(id = "barcode.generate")
        val second = preset("Another setup")
        CapabilityFavoritesRepository.set(context, first.id, true)
        assertTrue(PresetFavoritesRepository.toggle(context, first.id))
        assertTrue(PresetFavoritesRepository.toggle(context, second.id))
        assertEquals(setOf(first.id, second.id), PresetFavoritesRepository.all(context))
        assertFalse(PresetFavoritesRepository.toggle(context, first.id))
        assertTrue(CapabilityFavoritesRepository.contains(context, first.id))
        assertTrue(PresetFavoritesRepository.contains(context, second.id))
        assertEquals(first, ProtocolLibraryRepository.preset(context, first.id))
    }

    @Test fun renamePreservesFavouriteAndDeletionRemovesOnlyThatShortcut() {
        val first = preset()
        val second = preset("Other favourite")
        PresetFavoritesRepository.set(context, first.id, true)
        PresetFavoritesRepository.set(context, second.id, true)
        val renamed = ProtocolLibraryRepository.savePreset(context, first.copy(name = "Renamed favourite"))
        assertTrue(PresetFavoritesRepository.contains(context, renamed.id))
        assertEquals(first.settingsJson, renamed.settingsJson)
        ProtocolLibraryRepository.removePreset(context, first.id)
        assertFalse(PresetFavoritesRepository.contains(context, first.id))
        assertTrue(PresetFavoritesRepository.contains(context, second.id))
        assertNull(ProtocolLibraryRepository.preset(context, first.id))
        assertEquals(second, ProtocolLibraryRepository.preset(context, second.id))
    }

    @Test fun favouriteSnapshotsDoNotChangeWhenAnotherPresetIsStarred() {
        val first = preset()
        PresetFavoritesRepository.set(context, first.id, true)
        val snapshot = PresetFavoritesRepository.all(context)
        PresetFavoritesRepository.set(context, "another-id", true)
        assertEquals(setOf(first.id), snapshot)
        assertEquals(setOf(first.id, "another-id"), PresetFavoritesRepository.all(context))
    }

    @Test fun libraryStarsAreAccessibleToggleWithoutRunningAndSurviveRecreation() {
        val saved = preset()
        fixture(content = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProtocolLibraryCard(0, showPresets = true, showProtocols = false, expandedByDefault = true)
            }
        }) { scenario ->
            click("Codes")
            click("Add ${saved.name} to favourites", byDescription = true)
            assertTrue(PresetFavoritesRepository.contains(context, saved.id))
            await { description("Remove ${saved.name} from favourites") != null }
            assertTrue(launches.isEmpty())
            scenario.recreate()
            click("Remove ${saved.name} from favourites", byDescription = true)
            assertFalse(PresetFavoritesRepository.contains(context, saved.id))
            assertTrue(launches.isEmpty())
        }
    }

    @Test fun dashboardSearchCanStarAndUnstarPresetWhileExistingCapabilityFavouritesRemain() {
        val saved = preset("Unique favourite search fixture")
        CapabilityFavoritesRepository.set(context, "barcode.generate", true)
        fixture(content = { HomeScreen() }) { scenario ->
            query(saved.name)
            click("Add ${saved.name} to favourites", byDescription = true)
            assertTrue(PresetFavoritesRepository.contains(context, saved.id))
            assertTrue(launches.isEmpty())
            query("")
            await { text(saved.name) != null }
            scenario.recreate()
            await { text(saved.name) != null }
            click("Remove ${saved.name} from favourites", byDescription = true)
            await { text(saved.name) == null }
            assertTrue(CapabilityFavoritesRepository.contains(context, "barcode.generate"))
            await { text(As100BarcodeGenerateMethod.descriptor.name) != null }
            assertTrue(launches.isEmpty())
        }
    }

    @Test fun dashboardFavouriteRunsTheSavedPresetThroughTheExistingDispatcher() {
        val saved = preset()
        PresetFavoritesRepository.set(context, saved.id, true)
        fixture(content = { HomeScreen() }) {
            click(saved.name)
            await { launches.size == 1 }
            run {
                val launch = launches.single()
                assertEquals(SchedulerDispatchActivity::class.java.name, launch.component!!.className)
                assertEquals("com.example.methodmesh.RUN_PRESET", launch.action)
                assertEquals(saved.id, launch.getStringExtra("preset_id"))
                assertTrue(launch.getBooleanExtra("transient_preset_run", false))
                assertTrue(launch.getBooleanExtra("suppress_output", false))
                assertEquals(saved, ProtocolLibraryRepository.preset(context, saved.id))
            }
        }
    }
}

/** Also run in write/read phases around adb force-stop to verify a genuine cold process. */
class PresetFavouritesPersistenceTest {
    @Test fun storedFavouriteAndPresetRemainLinked() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val phase = InstrumentationRegistry.getArguments().getString("favourite_phase", "combined")
        check(phase in setOf("write", "read", "combined"))
        val files = mutableSetOf<String>()
        val context = FavouriteFixtureContext(app, "preset-favourite-cold-start-test", files)
        val id = "cold-start-favourite-fixture"
        if (phase != "read") {
            val saved = ProtocolLibraryRepository.savePreset(context, CapabilityPreset(id = id, name = "Cold-start favourite", methodId = "barcode.generate",
                settingsJson = "{\"barcode_payload\":\"Cold-start payload\"}", launchMode = PresetLaunchMode.INTERACTIVE))
            PresetFavoritesRepository.set(context, saved.id, true)
            // Flush preceding apply operations before the separate process is stopped.
            assertTrue(context.getSharedPreferences("methodmesh_protocol_library", Context.MODE_PRIVATE).edit().commit())
            assertTrue(context.getSharedPreferences("methodmesh_preset_favorites", Context.MODE_PRIVATE).edit().commit())
        }
        if (phase != "write") {
            try {
                assertTrue(PresetFavoritesRepository.contains(context, id))
                val saved = requireNotNull(ProtocolLibraryRepository.preset(context, id))
                assertEquals("Cold-start favourite", saved.name)
                assertEquals("{\"barcode_payload\":\"Cold-start payload\"}", saved.settingsJson)
                assertEquals(PresetLaunchMode.INTERACTIVE, saved.launchMode)
            } finally { files.forEach { app.deleteSharedPreferences(it) } }
        }
    }
}
