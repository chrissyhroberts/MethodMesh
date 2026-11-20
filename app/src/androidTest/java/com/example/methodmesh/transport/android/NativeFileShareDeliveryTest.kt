package com.example.methodmesh.transport.android

import android.content.*
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.methodmesh.modules.documentscanner.DocumentScannerFields
import com.example.methodmesh.transport.ResultShare
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exact real PDF/ZIP bytes through the system chooser to another UID. No WhatsApp or TSA request. */
class NativeFileShareDeliveryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private class Capture(base: Context) : ContextWrapper(base) {
        lateinit var chooser: Intent
        override fun startActivity(intent: Intent) { chooser = intent }
        override fun grantUriPermission(toPackage: String, uri: Uri, flags: Int) { error("No broad pre-selection grants") }
    }
    @Suppress("DEPRECATION") private fun target(chooser: Intent): Intent = chooser.getParcelableExtra(Intent.EXTRA_INTENT)!!
    @Suppress("DEPRECATION") private fun streams(intent: Intent): List<Uri> = if (intent.action == Intent.ACTION_SEND_MULTIPLE)
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty() else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    private fun bytes(uri: Uri) = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private fun find(label: String, node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == label) return node
        for (i in 0 until node.childCount) find(label, node.getChild(i))?.let { return it }
        return null
    }
    private fun chooseReceiver(previousWindow: Int?) {
        val until = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < until) {
            if (android.os.Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            var node = if (root?.windowId != previousWindow) find("File delivery regression receiver", root) else null
            if (node != null) {
                while (!node!!.isClickable) node = requireNotNull(node.parent)
                assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)); return
            }
            instrumentation.waitForIdleSync(); SystemClock.sleep(100)
        }
        error("Test-only receiver not found in system chooser")
    }
    @Test fun scannedPdfOcrAndTimestampFilesArriveAsReadableFilesNotAFilenameMessage() = verify(scan = true, audit = false)
    @Test fun timestampProofZipAndItsAuditStayAsFilesAcrossTheChooser() = verify(scan = false, audit = true)
    @Test fun plainTimestampProofZipStillArrivesWithoutAudit() = verify(scan = false, audit = false)

    private fun verify(scan: Boolean, audit: Boolean) {
        val token = UUID.randomUUID().toString()
        val folder = File(app.cacheDir, "native-file-delivery-$token").apply { mkdirs() }
        fun uri(file: File) = FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file)
        val pdf = File(folder, "scan.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create())
            page.canvas.drawText("Synthetic scanner transport fixture", 15f, 50f, Paint())
            document.finishPage(page); pdf.outputStream().use(document::writeTo)
        } finally { document.close() }
        val zip = File(folder, "timestamp-proof.zip")
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry("fixture.txt")); it.write("Synthetic bytes; NOT a validated timestamp proof".toByteArray()); it.closeEntry()
        }
        val text = File(folder, "ocr.txt").apply { writeText("Synthetic OCR result") }
        val hidden = File(folder, "unselected-original.pdf").apply { writeBytes(pdf.readBytes()) }
        val pdfUri = uri(pdf); val zipUri = uri(zip); val textUri = uri(text); val probe = uri(hidden)
        val attachments = if (scan) ResultShare.shareableMediaAttachments(linkedMapOf(
            DocumentScannerFields.SCANNER_PDF_URI to probe.toString(),
            DocumentScannerFields.SEARCHABLE_PDF_URI to pdfUri.toString(),
            DocumentScannerFields.OCR_TEXT_FILE_URI to textUri.toString(),
            DocumentScannerFields.TIMESTAMP_PROOF_URI to zipUri.toString()
        )) else listOf(ResultShare.Attachment(zip.name, zipUri))
        val capture = Capture(app)
        val summary = if (scan) "Scanned document: scan.pdf" else "Proof ZIP: timestamp-proof.zip"
        val json = if (audit) "{\"fixture\":\"audit\"}" else ""
        ResultShare.share(capture, "Share fixture files", summary, attachments, jsonText = json)
        val intent = target(capture.chooser); val sent = streams(intent)
        val report = AtomicReference<Intent>(); val complete = CountDownLatch(1)
        val action = app.packageName + ".NATIVE_FILE_DELIVERY." + token
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { report.set(intent); complete.countDown() }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED)
        try {
            assertNull("Files must not compete with an EXTRA_TEXT filename message", intent.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals(attachments.map { it.uri }, sent.dropLast(1)); assertEquals(attachments.first().uri, sent.first())
            assertEquals(ResultShare.buildShareText(summary, json), bytes(sent.last()).decodeToString())
            assertTrue(intent.clipData!!.description.hasMimeType("application/zip"))
            assertTrue(intent.clipData!!.description.hasMimeType("text/plain"))
            if (scan) assertTrue(intent.clipData!!.description.hasMimeType("application/pdf"))
            val chosen = Intent(intent).setComponent(ComponentName(instrumentation.context.packageName, NativeFileShareReceiverActivity::class.java.name))
                .putExtra("callback_action", action).putExtra("callback_package", app.packageName)
                .putExtra("ungranted_probe", probe.toString())
            // Offer only our explicitly identified test receiver as an initial target; never choose a user's messaging app.
            capture.chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(chosen))
            val previousWindow = instrumentation.uiAutomation.rootInActiveWindow?.windowId
            capture.chooser.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            app.startActivity(capture.chooser); chooseReceiver(previousWindow)
            assertTrue("Separate-UID file receiver did not finish", complete.await(15, TimeUnit.SECONDS))
            val received = report.get()
            assertNull(received.getStringExtra("receiver_error"))
            assertNotEquals(app.applicationInfo.uid, received.getIntExtra("receiver_uid", -1))
            assertFalse(received.getBooleanExtra("has_message_text", true))
            assertEquals(sent.map(Uri::toString), received.getStringArrayListExtra("streams"))
            assertEquals(sent.map(Uri::toString), received.getStringArrayListExtra("clip_uris"))
            assertEquals(sent.map { hash(bytes(it)) }, received.getStringArrayListExtra("hashes"))
            assertEquals(sent.map { bytes(it).size.toString() }, received.getStringArrayListExtra("sizes"))
            assertEquals(sent.map { app.contentResolver.getType(it) }, received.getStringArrayListExtra("mime_types"))
            assertTrue(received.getStringArrayListExtra("clip_types")!!.contains("application/zip"))
            assertFalse(received.getBooleanExtra("writable", true)); assertFalse(received.getBooleanExtra("probe_readable", true))
        } finally {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            app.unregisterReceiver(receiver)
            sent.filter { it !in attachments.map { a -> a.uri } }.forEach { app.contentResolver.delete(it, null, null) }
            folder.listFiles().orEmpty().forEach { assertTrue(it.delete()) }; assertTrue(folder.delete())
        }
    }
}
