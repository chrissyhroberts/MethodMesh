package com.example.methodmesh.transport.android

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.methodmesh.transport.ResultShare
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Captures the chooser; never sends a message or opens another app. */
class NativeShareContractTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private class CaptureContext(base: Context) : ContextWrapper(base) {
        lateinit var chooser: Intent
        override fun startActivity(intent: Intent) { chooser = intent }
        override fun grantUriPermission(toPackage: String, uri: Uri, modeFlags: Int) {
            error("Share must not pre-grant attachment access to every possible receiver")
        }
        @Suppress("DEPRECATION")
        fun target(): Intent = chooser.getParcelableExtra(Intent.EXTRA_INTENT)!!
    }
    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri> = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    } else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    private fun read(uri: Uri) = app.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    private fun checkGrants(context: CaptureContext) {
        val target = context.target()
        for (intent in listOf(context.chooser, target)) {
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals(streams(target).toSet(), (0 until intent.clipData!!.itemCount).map { intent.clipData!!.getItemAt(it).uri }.toSet())
        }
    }
    private fun cleanup(vararg intents: Intent) {
        intents.flatMap(::streams).distinct().forEach { app.contentResolver.delete(it, null, null) }
    }

    @Test fun textShareSuppliesEquivalentMessageTextAndAReadableTextFile() {
        val context = CaptureContext(app)
        ResultShare.share(context, "Share fixture", "Useful result", emptyList())
        val target = context.target()
        try {
            assertEquals(Intent.ACTION_SEND, target.action)
            assertEquals("text/plain", target.type)
            assertEquals("Useful result", target.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals(target.getStringExtra(Intent.EXTRA_TEXT), read(streams(target).single()))
            checkGrants(context)
        } finally { cleanup(target) }
    }

    @Test fun auditToggleChangesTextWithoutAddingAJsonStreamOrChangingTheMediaSet() {
        val source = File(app.cacheDir, "share-${UUID.randomUUID()}.png").apply { writeText("fixture image bytes") }
        val media = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", source)
        val off = CaptureContext(app)
        val on = CaptureContext(app)
        ResultShare.share(off, "Share fixture", "Useful result", listOf(ResultShare.Attachment("fixture.png", media)))
        ResultShare.share(on, "Share fixture", "Useful result", listOf(ResultShare.Attachment("fixture.png", media)), jsonText = "{\"audit\":true}")
        val before = off.target()
        val after = on.target()
        try {
            assertEquals(2, streams(before).size)
            assertEquals(2, streams(after).size)
            assertTrue(media in streams(before) && media in streams(after))
            assertNull(before.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals(media, streams(before).first())
            val text = "Useful result\n\nmetadata.json\n{\"audit\":true}"
            assertNull(after.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals(media, streams(after).first())
            assertTrue(after.clipData!!.description.hasMimeType("image/png"))
            assertTrue(after.clipData!!.description.hasMimeType("text/plain"))
            assertEquals(text, read(streams(after).single { it != media }))
            assertEquals("Useful result", read(streams(before).single { it != media }))
            assertFalse(streams(after).any { app.contentResolver.getType(it) == "application/json" })
            checkGrants(on)
        } finally { cleanup(before, after); source.delete() }
    }

    @Test fun aGenuineDomainJsonFileAndTheExistingRecipientHintArePreserved() {
        val source = File(app.cacheDir, "domain-${UUID.randomUUID()}.json").apply { writeText("{\"domain\":true}") }
        val domain = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", source)
        val context = CaptureContext(app)
        ResultShare.share(context, "Share fixture", "Domain result", listOf(ResultShare.Attachment("domain.json", domain)),
            jsonText = "{\"audit\":true}", recipientHint = "fixture@example.invalid")
        val target = context.target()
        try {
            assertEquals(listOf(domain), streams(target).filter { app.contentResolver.getType(it) == "application/json" })
            assertArrayEquals(arrayOf("fixture@example.invalid"), target.getStringArrayExtra(Intent.EXTRA_EMAIL))
            assertNull(target.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals(domain, streams(target).first())
            assertTrue(read(streams(target).single { it != domain }).contains("metadata.json"))
            checkGrants(context)
        } finally { cleanup(target); source.delete() }
    }
}
