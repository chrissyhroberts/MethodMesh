package com.example.methodmesh.modules.imageredaction

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
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
import com.example.methodmesh.transport.android.ExternalWorkflowActivity
import com.example.methodmesh.transport.workflow.ui.RetainedExecutionStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Real production picker/editor/commit flow. Choosers are intercepted; nothing is sent to another app. */
class PdfRedactionWorkflowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean, root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) find(predicate, root.getChild(index))?.let { return it }
        return null
    }
    private fun node(text: String) = find({ it.text?.toString() == text })
    private fun awaitText(text: String) = await {
        if (node(text) == null) { find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); false } else true
    }
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "PDF workflow timed out: ${visibleText()}" }
            instrumentation.waitForIdleSync(); SystemClock.sleep(25)
        }
    }
    private fun visibleText(root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): String {
        if (root == null) return ""
        return buildString {
            root.text?.let { append(it).append(" | ") }
            root.contentDescription?.let { append(it).append(" | ") }
            for (index in 0 until root.childCount) append(visibleText(root.getChild(index)))
        }.take(2500)
    }
    private fun click(text: String) {
        await {
            if (node(text) == null) { find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); false } else true
        }
        fun clickable(): AccessibilityNodeInfo? {
            var current = node(text)
            while (current != null && !current.isClickable) current = current.parent
            return current
        }
        requireNotNull(clickable()).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        // A restored PDF preview and animated scrolling can replace the accessibility tree.
        // Reacquire the enabled action after those frames instead of dereferencing a stale node.
        var target: AccessibilityNodeInfo? = null
        await { target = clickable()?.takeIf { it.isEnabled }; target != null }
        assertTrue("PDF action did not accept click: $text", requireNotNull(target).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val result = AtomicReference<ExecutionResult>()
        scenario.onActivity { result.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull()) }
        return result.get()
    }
    private fun fixture(scan: AtomicReference<Intent>? = null, scanCode: Int = Activity.RESULT_OK,
        scanStatus: String = "succeeded", run: (File, Uri, AtomicReference<Intent>, AtomicReference<Intent>) -> Unit) {
        val folder = File(app.cacheDir, "pdf-workflow-fixture-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(folder, "sensitive-original.pdf"); PdfRedactionEngineTest.source(source)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", source)
        val picker = AtomicReference<Intent>(); val chooser = AtomicReference<Intent>()
        IntentStubberRegistry.load(object : IntentStubber {
            override fun getActivityResultForIntent(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.getStringExtra("method_id") == RedactionScanContract.METHOD_ID) {
                    scan?.set(intent)
                    val response = Intent()
                        .putExtra("methodmesh_status", if (scanStatus == "succeeded") "Succeeded" else "Failed")
                        .putExtra("document_scan_pdf_uri", uri.toString()).putExtra("document_scan_ocr_text", "UNREDACTED-OCR-SECRET")
                    if (intent.hasExtra("document_scan_status")) response.putExtra("document_scan_status", scanStatus)
                    return Instrumentation.ActivityResult(scanCode, response)
                }
                return when (intent.action) {
                Intent.ACTION_OPEN_DOCUMENT -> {
                    picker.set(intent)
                    Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
                Intent.ACTION_CHOOSER -> { chooser.set(intent); Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null) }
                else -> null
                }
            }
        })
        try { run(source, uri, picker, chooser) }
        finally { IntentStubberRegistry.reset(); folder.deleteRecursively() }
    }
    private fun incoming() = Intent(app, ExternalWorkflowActivity::class.java).putExtra("method_id", As100PdfRedactionMethod.ID)
        .putExtra("input_methodmesh_native_preset_run", "true").putExtra("input_methodmesh_finish_to_launcher", "true")
        .putExtra("input_methodmesh_runtime_fields", "pdf_uri")

    private fun drawMask() {
        click("Expand page")
        // Wait for the dialog window, not the still-present canvas in the underlying activity.
        await { node("Close expanded page") != null }
        await { find({ it.contentDescription?.toString() == "PDF page 1 redaction canvas" }) != null }
        val bounds = Rect()
        find({ it.contentDescription?.toString() == "PDF page 1 redaction canvas" })!!.getBoundsInScreen(bounds)
        val side = minOf(bounds.width(), bounds.height()).toFloat()
        val left = bounds.left + (bounds.width() - side) / 2
        val top = bounds.top + (bounds.height() - side) / 2
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, fraction: Float) {
            val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, left + side * fraction, top + side * fraction, 0)
            motion.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(motion, true)) } finally { motion.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, .1f)
        for (index in 1..12) { SystemClock.sleep(16); event(MotionEvent.ACTION_MOVE, .1f + .3f * index / 12) }
        event(MotionEvent.ACTION_UP, .4f)
        instrumentation.waitForIdleSync()
        click("Close expanded page")
        awaitText("1 region on this page · 1 in document")
    }

    @Test fun phonePickerMasksRotationAndShareUseOnlyTheCommittedRedactedPdf() = fixture { source, sourceUri, picker, chooser ->
        val original = source.readBytes()
        var output: Uri? = null
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(incoming()).use { scenario ->
                click("Open PDF from phone")
                await { node("Page 1 of 2") != null }
                assertArrayEquals(arrayOf("application/pdf"), picker.get().getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
                assertNull(captured(scenario))
                drawMask()
                scenario.recreate()
                awaitText("1 region on this page · 1 in document")
                click("Create redacted PDF")
                await { captured(scenario) != null }
                val result = captured(scenario)!!
                val region = PdfRedactionRegions.decode(result.observations.single().values.getValue(PdfRedactionFields.REGIONS)).single()
                assertEquals("Do not drop the initial touch-slop strip", .1f, region.left, .005f)
                assertEquals("Do not drop the initial touch-slop strip", .1f, region.top, .005f)
                assertEquals(.4f, region.right, .005f); assertEquals(.4f, region.bottom, .005f)
                val full = OutputFormatter.format(result, ReturnMode.Json)
                output = Uri.parse(result.observations.single().values.getValue(PdfRedactionFields.URI))
                assertFalse(full.contains(sourceUri.toString()))
                assertFalse(full.contains(source.name))
                scenario.recreate()
                await { captured(scenario) != null }
                assertSame(result, captured(scenario))
                assertEquals(full, OutputFormatter.format(captured(scenario)!!, ReturnMode.Json))
                click("Share PDF")
                await { chooser.get() != null }
                @Suppress("DEPRECATION") val target = chooser.get().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_SEND, target.action)
                assertEquals("application/pdf", target.type)
                @Suppress("DEPRECATION") val shared = target.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
                assertEquals(output, shared); assertNotEquals(sourceUri, shared)
                assertEquals(1, target.clipData!!.itemCount)
                assertEquals(output, target.clipData!!.getItemAt(0).uri)
                assertTrue(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertEquals(0, target.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                assertArrayEquals(original, source.readBytes())
                val bytes = app.contentResolver.openInputStream(shared)!!.use { it.readBytes() }
                assertEquals(result.observations.single().values[PdfRedactionFields.SHA256], ImageRedactionHash.sha256Hex(bytes))
                // Synthetic-only visual fixture for inspection; no user document is involved.
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    try { File(app.cacheDir, "pdf-redaction-ui-review.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    finally { bitmap.recycle() }
                }
            }
        } finally { output?.let { app.contentResolver.delete(it, null, null) } }
    }

    @Test fun suppliedPdfStillRequiresMaskingAndExternalReturnContainsOnlyNewPdf() = fixture { source, sourceUri, picker, _ ->
        val intent = Intent(app, ExternalWorkflowActivity::class.java).putExtra("method_id", As100PdfRedactionMethod.ID)
            .putExtra("input_pdf_uri", sourceUri.toString())
        var output: Uri? = null
        try {
            ActivityScenario.launchActivityForResult<ExternalWorkflowActivity>(intent).use { scenario ->
                await { node("Page 1 of 2") != null }
                assertNull(picker.get()); assertNull(captured(scenario))
                drawMask(); click("Create redacted PDF")
                val returned = scenario.result
                assertEquals(Activity.RESULT_OK, returned.resultCode)
                val data = returned.resultData
                output = Uri.parse(data.getStringExtra(PdfRedactionFields.URI))
                assertNotEquals(sourceUri, output)
                assertNotNull(data.getStringExtra("methodmesh_full_json"))
                assertFalse(data.getStringExtra("methodmesh_full_json")!!.contains(source.name))
                assertEquals("succeeded", data.getStringExtra(PdfRedactionFields.STATUS))
                assertTrue(app.contentResolver.openInputStream(output!!)!!.use { it.read() } >= 0)
            }
        } finally { output?.let { app.contentResolver.delete(it, null, null) } }
    }

    @Test fun invalidSuppliedDocumentStaysOpenWithoutReturningTheSource() = fixture { source, _, _, chooser ->
        val bad = File(source.parentFile, "bad.pdf").apply { writeText("not a PDF") }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", bad)
        ActivityScenario.launch<ExternalWorkflowActivity>(incoming().putExtra("input_methodmesh_runtime_fields", "")
            .putExtra("input_pdf_uri", uri.toString())).use { scenario ->
            await { find({ it.text?.toString()?.startsWith("Could not open PDF:") == true }) != null }
            assertNull(captured(scenario)); assertNull(chooser.get())
            assertEquals("not a PDF", bad.readText())
            assertNotNull(node("Open PDF from phone"))
        }
    }

    @Test fun scanActionFeedsThePdfEditorWithoutReturningUnredactedScanOrOcr() {
        val scan = AtomicReference<Intent>()
        fixture(scan) { _, sourceUri, _, _ ->
            var output: Uri? = null
            try {
                ActivityScenario.launch<ExternalWorkflowActivity>(incoming().putExtra("input_methodmesh_runtime_fields", "document_source")).use { scenario ->
                    click("Scan document to redact")
                    await { node("Page 1 of 2") != null }
                    assertEquals("document.scan", scan.get().getStringExtra("method_id"))
                    assertTrue("Declare the status field when requesting FULL", scan.get().hasExtra("document_scan_status"))
                    for (key in listOf("run_ocr", "return_searchable_pdf", "return_text_file")) assertEquals("false", scan.get().getStringExtra("input_$key"))
                    assertEquals("disabled", scan.get().getStringExtra("input_timestamp_policy"))
                    assertNull("A scan must not auto-commit a redaction", captured(scenario))
                    drawMask(); click("Create redacted PDF")
                    await { captured(scenario) != null }
                    val result = captured(scenario)!!
                    assertEquals("document_scanner", result.request.context["redaction_document_source"])
                    output = Uri.parse(result.observations.single().values.getValue(PdfRedactionFields.URI))
                    assertNotEquals(sourceUri, output)
                    val full = OutputFormatter.format(result, ReturnMode.Json)
                    assertFalse(full.contains(sourceUri.toString()))
                    assertFalse(full.contains("UNREDACTED-OCR-SECRET"))
                    assertFalse(full.contains("document_scan_pdf_uri"))
                }
            } finally { output?.let { app.contentResolver.delete(it, null, null) } }
        }
    }

    @Test fun cancelledScanKeepsRedactionOpenWithoutAnyResult() {
        val scan = AtomicReference<Intent>()
        fixture(scan, Activity.RESULT_CANCELED) { _, _, _, chooser ->
            ActivityScenario.launch<ExternalWorkflowActivity>(incoming().putExtra("input_methodmesh_runtime_fields", "document_source")).use { scenario ->
                click("Scan document to redact")
                await { node("Scan cancelled. No document was redacted or shared.") != null }
                assertNull(captured(scenario)); assertNull(chooser.get()); assertNull(node("Share PDF"))
            }
        }
    }

    @Test fun unsuccessfulScanCannotUseAnOtherwiseValidUnredactedPdf() {
        val scan = AtomicReference<Intent>()
        fixture(scan, scanStatus = "failed") { _, _, _, _ ->
            ActivityScenario.launch<ExternalWorkflowActivity>(incoming().putExtra("input_methodmesh_runtime_fields", "document_source")).use { scenario ->
                click("Scan document to redact")
                await { find({ it.text?.toString()?.startsWith("Could not use scan:") == true }) != null }
                assertNull(captured(scenario)); assertNull(node("Page 1 of 2")); assertNull(node("Share PDF"))
            }
        }
    }

    @Test fun scannerPresetStartsTheScannerRatherThanThePdfFilePicker() {
        val scan = AtomicReference<Intent>()
        fixture(scan) { _, _, picker, _ ->
            ActivityScenario.launch<ExternalWorkflowActivity>(incoming().putExtra("input_methodmesh_runtime_fields", "")
                .putExtra("input_document_source", "document_scanner")).use { scenario ->
                await { node("Page 1 of 2") != null }
                assertNotNull(scan.get()); assertNull(picker.get()); assertNull(captured(scenario))
            }
        }
    }
}
