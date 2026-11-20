package com.example.methodmesh.modules.imageredaction

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.intent.IntentStubber
import androidx.test.runner.intent.IntentStubberRegistry
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.android.ExternalWorkflowActivity
import com.example.methodmesh.transport.workflow.ui.RetainedExecutionStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Synthetic photo bytes, real production screen. Camera/picker intents never open hardware or another app. */
class PhotoRedactionWorkflowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun freshRoot(): AccessibilityNodeInfo? {
        // API 37 can retain off-screen child IDs after scrolling a tall Compose editor.
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean, root: AccessibilityNodeInfo? = freshRoot()): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) find(predicate, root.getChild(index))?.let { return it }
        return null
    }
    private fun node(text: String) = find({ it.text?.toString() == text })
    private fun visibleText(root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): String {
        if (root == null) return ""
        return buildString {
            root.text?.let { append(it).append(" | ") }
            root.contentDescription?.let { append(it).append(" | ") }
            if (root.isScrollable) append("SCROLLABLE ").append(root.className).append(" | ")
            for (index in 0 until root.childCount) append(visibleText(root.getChild(index)))
        }.take(1600)
    }
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 12_000
        while (!condition()) {
            if (SystemClock.uptimeMillis() >= end) {
                instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
                    try { File(app.cacheDir, "photo-redaction-failure.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    finally { screenshot.recycle() }
                }
                error("Photo redaction timed out: ${visibleText()}")
            }
            instrumentation.waitForIdleSync(); SystemClock.sleep(25)
        }
    }
    private fun click(text: String) {
        await {
            if (node(text) != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(150)
                false
            }
        }
        var target = node(text)!!; while (!target.isClickable) target = requireNotNull(target.parent)
        target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        await {
            var current = node(text)
            while (current != null && !current.isClickable) current = current.parent
            current?.isEnabled == true
        }
        target = node(text)!!; while (!target.isClickable) target = requireNotNull(target.parent)
        assertTrue("Disabled photo action: $text", target.isEnabled)
        assertTrue("Photo action did not accept click: $text", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val result = AtomicReference<ExecutionResult>()
        scenario.onActivity { result.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull()) }
        return result.get()
    }
    private fun incoming(source: String) = Intent(app, ExternalWorkflowActivity::class.java)
        .putExtra("method_id", "image.redact").putExtra("input_source", source)
        .putExtra("input_grid_rows", "1").putExtra("input_grid_columns", "1")
        .putExtra("input_methodmesh_native_preset_run", "true").putExtra("input_methodmesh_finish_to_launcher", "true")
    private fun selectWholePhoto() {
        await { find({ it.contentDescription?.toString() == "Image to redact" }) != null }
        find({ it.contentDescription?.toString() == "Image to redact" })!!.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        // Accessibility scrolling is animated; idle alone can precede its final position.
        SystemClock.sleep(350)
        instrumentation.waitForIdleSync()
        val bounds = Rect()
        find({ it.contentDescription?.toString() == "Image to redact" })!!.getBoundsInScreen(bounds)
        instrumentation.uiAutomation.takeScreenshot()?.let { image ->
            try { File(app.cacheDir, "photo-redaction-touch-review.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { image.recycle() }
        }
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            if (action == MotionEvent.ACTION_UP) SystemClock.sleep(80)
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        instrumentation.waitForIdleSync()
    }
    private fun fixture(cancelCamera: Boolean = false, chooser: AtomicReference<Intent>? = null,
        run: (File, AtomicInteger, AtomicInteger) -> Unit) {
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.CAMERA)
        val source = File(app.cacheDir, "saved-photo-${UUID.randomUUID()}.png")
        val image = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try { source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { image.recycle() }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", source)
        val cameras = AtomicInteger(); val pickers = AtomicInteger()
        var cameraUri: Uri? = null
        IntentStubberRegistry.load(object : IntentStubber {
            override fun getActivityResultForIntent(intent: Intent): Instrumentation.ActivityResult? = when (intent.action) {
                Intent.ACTION_GET_CONTENT -> {
                    assertEquals("image/*", intent.type); pickers.incrementAndGet()
                    Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri))
                }
                MediaStore.ACTION_IMAGE_CAPTURE -> {
                    cameras.incrementAndGet()
                    @Suppress("DEPRECATION") val destination = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
                    cameraUri = destination
                    if (!cancelCamera) app.contentResolver.openOutputStream(destination)!!.use { it.write(source.readBytes()) }
                    Instrumentation.ActivityResult(if (cancelCamera) Activity.RESULT_CANCELED else Activity.RESULT_OK, null)
                }
                Intent.ACTION_CHOOSER -> if (chooser != null) {
                    chooser.set(intent)
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                } else null
                else -> null
            }
        })
        try { run(source, cameras, pickers) }
        finally { IntentStubberRegistry.reset(); source.delete(); cameraUri?.let { app.contentResolver.delete(it, null, null) } }
    }
    private fun verifyPhoto(source: File, scenario: ActivityScenario<ExternalWorkflowActivity>, expectedSource: String, rotate: Boolean) {
        val before = source.readBytes()
        selectWholePhoto(); click("Create redacted image")
        await { captured(scenario) != null }
        val result = captured(scenario)!!
        val fields = result.observations.single().values
        val output = Uri.parse(fields.getValue(ImageRedactionFields.REDACTED_IMAGE_URI))
        try {
            assertEquals(expectedSource, fields[ImageRedactionFields.SOURCE])
            val bytes = app.contentResolver.openInputStream(output)!!.use { it.readBytes() }
            assertEquals(ImageRedactionHash.sha256Hex(bytes), fields[ImageRedactionFields.REDACTED_IMAGE_SHA256])
            val redacted = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            try { assertEquals(Color.BLACK, redacted.getPixel(100, 100)) } finally { redacted.recycle() }
            assertArrayEquals(before, source.readBytes())
            if (rotate) {
                val full = OutputFormatter.format(result, ReturnMode.Json)
                scenario.recreate(); await { captured(scenario) != null }
                assertSame(result, captured(scenario)); assertEquals(full, OutputFormatter.format(captured(scenario)!!, ReturnMode.Json))
            }
        } finally { app.contentResolver.delete(output, null, null) }
    }
    @Test fun alreadySavedPhotoUsesPickerNotCameraAndRetainsCommittedEvidenceOnRotation() = fixture { source, cameras, pickers ->
        ActivityScenario.launch<ExternalWorkflowActivity>(incoming("file_picker")).use { scenario ->
            verifyPhoto(source, scenario, "file_picker", true)
            assertEquals(0, cameras.get()); assertEquals(1, pickers.get())
        }
    }
    @Test fun newCameraPhotoUsesCameraNotPickerAndCreatesOnlyTheRedactedOutput() = fixture { source, cameras, pickers ->
        ActivityScenario.launch<ExternalWorkflowActivity>(incoming("camera")).use { scenario ->
            verifyPhoto(source, scenario, "camera", false)
            assertEquals(1, cameras.get()); assertEquals(0, pickers.get())
        }
    }
    @Test fun runtimePhotoChoiceCanSelectAnAlreadySavedImageWithoutOpeningCamera() = fixture { source, cameras, pickers ->
        ActivityScenario.launch<ExternalWorkflowActivity>(incoming("camera").putExtra("input_methodmesh_runtime_fields", "input_source")).use { scenario ->
            click("Choose saved photo")
            verifyPhoto(source, scenario, "file_picker", false)
            assertEquals(0, cameras.get()); assertEquals(1, pickers.get())
        }
    }
    @Test fun cancelledCameraDoesNotProduceAResultOrFallBackToSavedPhoto() = fixture(cancelCamera = true) { _, cameras, pickers ->
        ActivityScenario.launch<ExternalWorkflowActivity>(incoming("camera")).use { scenario ->
            await { node("Camera capture cancelled.") != null }
            assertNull(captured(scenario)); assertEquals(1, cameras.get()); assertEquals(0, pickers.get())
        }
    }
    @Test fun sharingTheRedactedPhotoSendsOnlyOneReadableJpegNotAHashTextFile() {
        val chooser = AtomicReference<Intent>()
        fixture(chooser = chooser) { source, _, _ ->
            var output: Uri? = null
            try {
                ActivityScenario.launch<ExternalWorkflowActivity>(incoming("file_picker")).use { scenario ->
                    val original = source.readBytes()
                    selectWholePhoto(); click("Create redacted image")
                    await { captured(scenario) != null }
                    val result = captured(scenario)!!
                    val fields = result.observations.single().values
                    output = Uri.parse(fields.getValue(ImageRedactionFields.REDACTED_IMAGE_URI))
                    click("Share image")
                    await { chooser.get() != null }
                    @Suppress("DEPRECATION") val target = chooser.get().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                    assertEquals(Intent.ACTION_SEND, target.action)
                    assertEquals("image/jpeg", target.type)
                    @Suppress("DEPRECATION") val shared = target.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
                    assertEquals(output, shared)
                    assertNull("No hash, URI or MethodMesh text accompanies the photo", target.getStringExtra(Intent.EXTRA_TEXT))
                    assertEquals(1, target.clipData!!.itemCount)
                    assertEquals(output, target.clipData!!.getItemAt(0).uri)
                    assertTrue(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                    assertEquals(0, target.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val bytes = app.contentResolver.openInputStream(shared)!!.use { it.readBytes() }
                    assertEquals(fields[ImageRedactionFields.REDACTED_IMAGE_SHA256], ImageRedactionHash.sha256Hex(bytes))
                    val photo = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    try { assertEquals(Color.BLACK, photo.getPixel(100, 100)) } finally { photo.recycle() }
                    assertArrayEquals(original, source.readBytes())
                    assertTrue("Audit remains available without being shared by default",
                        OutputFormatter.format(result, ReturnMode.Json).contains(fields.getValue(ImageRedactionFields.REDACTED_IMAGE_SHA256)))

                    // Check audit delivery directly at the existing shared transport boundary.
                    // The production audit toggle/binding is unchanged; avoid a platform-dependent
                    // accessibility lookup for the Switch obscuring this payload contract test.
                    val auditedChooser = AtomicReference<Intent>()
                    val auditedContext = object : ContextWrapper(app) {
                        override fun startActivity(intent: Intent) { auditedChooser.set(intent) }
                    }
                    val canonical = OutputFormatter.format(result, ReturnMode.Json)
                    ResultShare.share(auditedContext, "Share result", "",
                        listOf(ResultShare.Attachment(fields.getValue(ImageRedactionFields.REDACTED_IMAGE_NAME), shared)), jsonText = canonical)
                    @Suppress("DEPRECATION") val audited = auditedChooser.get().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                    assertEquals(Intent.ACTION_SEND_MULTIPLE, audited.action)
                    @Suppress("DEPRECATION") val streams = audited.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!
                    assertEquals(2, streams.size)
                    assertTrue(shared in streams)
                    val textUri = streams.single { it != shared }
                    assertEquals("text/plain", app.contentResolver.getType(textUri))
                    assertNull(audited.getStringExtra(Intent.EXTRA_TEXT))
                    assertEquals("metadata.json\n$canonical", app.contentResolver.openInputStream(textUri)!!.bufferedReader().use { it.readText() })
                    assertFalse(streams.any { app.contentResolver.getType(it) == "application/json" })
                    assertEquals(2, audited.clipData!!.itemCount)
                    assertArrayEquals(bytes, app.contentResolver.openInputStream(shared)!!.use { it.readBytes() })
                    assertSame(result, captured(scenario))
                }
            } finally { output?.let { app.contentResolver.delete(it, null, null) } }
        }
    }
    @Test fun expandedPhotoKeepsZoomAndModeOnRotationBeforeCreatingTheRedactedCopy() = fixture { source, _, _ ->
        ActivityScenario.launch<ExternalWorkflowActivity>(incoming("file_picker")).use { scenario ->
            click("Expand photo")
            click("+")
            click("Pan / zoom")
            scenario.recreate()
            await { node("Close expanded photo") != null && find({ it.text?.toString()?.startsWith("2.0×") == true }) != null }
            assertNull(captured(scenario))
            click("Redact"); click("Close expanded photo")
            verifyPhoto(source, scenario, "file_picker", false)
        }
    }
    @Test fun runtimeGridWithFixedSavedPhotoSourceOffersExplicitAcquisition() = fixture { source, cameras, pickers ->
        ActivityScenario.launch<ExternalWorkflowActivity>(incoming("file_picker").putExtra("input_methodmesh_runtime_fields", "grid_rows")).use { scenario ->
            click("Open image")
            verifyPhoto(source, scenario, "file_picker", false)
            assertEquals(0, cameras.get()); assertEquals(1, pickers.get())
        }
    }
    @Test fun cancellingANewCameraAttemptDoesNotRelabelThePreviouslyLoadedSavedPhoto() = fixture(cancelCamera = true) { source, cameras, pickers ->
        ActivityScenario.launch<ExternalWorkflowActivity>(incoming("file_picker").putExtra("input_methodmesh_runtime_fields", "input_source")).use { scenario ->
            click("Choose saved photo")
            await { find({ it.contentDescription?.toString() == "Image to redact" }) != null }
            click("Take new photo")
            await { node("Camera capture cancelled.") != null }
            verifyPhoto(source, scenario, "file_picker", false)
            assertEquals(1, cameras.get()); assertEquals(1, pickers.get())
        }
    }
}
