package com.example.methodmesh.modules.paperbridge

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Synthetic registered/reviewed sessions, not camera/OCR/designer acceptance. */
class PaperCommitSnapshotTest {
    private class Fixture : AutoCloseable {
        val bitmap = Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888)
        val anchor = PaperAnchorEvidence("top_left", PointF(1f, 2f), 0.9f, 12, 3, 4)
        val text = PaperFieldSpec("answer", "Answer", PaperFieldType.OCR_TEXT, required = true)
        val image = PaperFieldSpec("photo", "Photo", PaperFieldType.IMAGE)
        val na = PaperFieldSpec("unreadable", "Unreadable", PaperFieldType.OCR_TEXT, required = true)
        val scores = linkedMapOf("selected" to 0.75f)
        val working = mutableListOf(
            PaperFieldResult(text, "original", "original", true, false, true, false, "Confirmed", scores),
            PaperFieldResult(image, "content://fixture/original-photo", "content://fixture/original-photo", true, false, false, true, "Image"),
            PaperFieldResult(na, "unclear", "na", true, false, true, false, "NA override", naOverride = true)
        )
        val template = PaperTemplate("methodmesh.paper.v1", "snapshot-fixture", "1", "Synthetic fixture", 12, 16,
            PaperAnchorSpec(), PaperRegistrationSpec(), listOf(text, image, na), "{\"fixture\":\"original\"}")
        val session = PaperExtractionSession(template, Uri.parse("content://fixture/source"), Uri.parse("content://fixture/rectified"),
            bitmap, PaperRectification(bitmap, listOf(anchor), 12, 16, "synthetic-boundary"), 240f,
            working.toList(), "2026-10-23T12:00:00Z", "caller_supplied")
        override fun close() { bitmap.recycle() }
    }
    private val originalAudit = "[{\"field\":\"answer\",\"new_value\":\"original\",\"operator_confirmed\":true}]"

    @Test fun slowHashCannotMixLaterEditsAuditScoresOrRegistrationIntoTheSnapshot() = Fixture().use { fixture -> runBlocking {
        var audit = originalAudit
        val snapshot = PaperCommitSnapshot.capture(fixture.session, fixture.working, audit, false)
        val release = CompletableDeferred<Unit>(); val started = CompletableDeferred<Unit>()
        val called = mutableListOf<String>()
        val prepared = async {
            snapshot.withHashes { uri -> called += uri; started.complete(Unit); release.await(); "hash:" + uri }
        }
        started.await()
        fixture.working[0] = fixture.working[0].copy(finalValue = "later edit", reviewed = false, autoAccepted = true)
        fixture.working[1] = fixture.working[1].copy(finalValue = "content://fixture/later-photo")
        fixture.scores["selected"] = 0.01f; fixture.anchor.point.x = 999f
        audit = "[{\"new_value\":\"later edit\"}]"
        release.complete(Unit)
        val stable = prepared.await(); val values = JSONObject(stable.getValue(PaperBridgeFields.VALUES_JSON))
        assertEquals("original", snapshot.dynamicValues["answer"]); assertEquals("original", values.getString("answer"))
        assertEquals("content://fixture/original-photo", values.getString("photo"))
        assertEquals("na", values.getString("unreadable"))
        assertEquals(originalAudit, stable[PaperBridgeFields.MANUAL_EDITS_JSON]); assertNotEquals(audit, stable[PaperBridgeFields.MANUAL_EDITS_JSON])
        val evidence = JSONObject(stable.getValue(PaperBridgeFields.EXTRACTION_AUDIT_JSON))
        assertEquals("original", evidence.getJSONArray("fields").getJSONObject(0).getString("final"))
        assertEquals(0.75, evidence.getJSONArray("fields").getJSONObject(0).getJSONObject("scores").getDouble("selected"), 0.001)
        assertEquals(1.0, evidence.getJSONArray("anchors").getJSONObject(0).getDouble("x"), 0.001)
        assertEquals("original", evidence.getJSONArray("manual_edits").getJSONObject(0).getString("new_value"))
        assertEquals("3", stable[PaperBridgeFields.FIELD_COUNT]); assertEquals("1", stable[PaperBridgeFields.AUTO_ACCEPTED_COUNT])
        assertEquals("2", stable[PaperBridgeFields.REVIEWED_COUNT]); assertEquals("1", stable[PaperBridgeFields.MANUAL_EDIT_COUNT])
        assertEquals(listOf("unreadable"), JSONArray(stable.getValue(PaperBridgeFields.NA_FIELDS_JSON)).let { (0 until it.length()).map(it::getString) })
        assertEquals(listOf("content://fixture/source", "content://fixture/rectified", "content://fixture/original-photo"), called)
        assertEquals(PaperBridgeFields.outputs.toSet(), stable.keys)
        val attachments = JSONObject(stable.getValue(PaperBridgeFields.ATTACHMENT_METADATA_JSON))
        assertEquals("hash:content://fixture/original-photo", attachments.getJSONArray("field_attachments").getJSONObject(0).getString("sha256"))
        assertEquals(3, snapshot.fieldCount); assertEquals(1, snapshot.autoAcceptedCount); assertEquals(2, snapshot.reviewedCount)
    } }

    @Test fun actualContentUriBytesProduceConsistentSourceRectifiedAndRoiHashes() = Fixture().use { fixture -> runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = (0..2).map { File.createTempFile("paper-commit-" + UUID.randomUUID(), ".bin", context.cacheDir) }
        try {
            files.forEachIndexed { index, file -> file.writeBytes(("Synthetic media " + index).toByteArray()) }
            val uris = files.map { FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it) }
            val working = fixture.working.map { if (it.spec.type == PaperFieldType.IMAGE) it.copy(finalValue = uris[2].toString()) else it }
            val session = fixture.session.copy(sourceUri = uris[0], rectifiedUri = uris[1])
            val snapshot = PaperCommitSnapshot.capture(session, working, originalAudit, false)
            val stable = snapshot.withHashes { uri -> withContext(Dispatchers.IO) { PaperBridgeFiles.sha256(context, Uri.parse(uri)) } }
            val expected = (0..2).map { PaperBridgeFiles.sha256Text("Synthetic media " + it) }
            assertEquals(expected[0], stable[PaperBridgeFields.SOURCE_SHA256]); assertEquals(expected[1], stable[PaperBridgeFields.RECTIFIED_SHA256])
            val metadata = JSONObject(stable.getValue(PaperBridgeFields.ATTACHMENT_METADATA_JSON))
            assertEquals(expected[0], metadata.getJSONObject("source").getString("sha256"))
            assertEquals(expected[1], metadata.getJSONObject("rectified").getString("sha256"))
            assertEquals(expected[2], metadata.getJSONArray("field_attachments").getJSONObject(0).getString("sha256"))
            assertEquals(uris[2].toString(), snapshot.dynamicValues["photo"])
        } finally { files.forEach { assertTrue(it.delete()) } }
    } }

    @Test fun cancellationDuringAnUninterruptibleHashCannotContinueToOtherFiles() = Fixture().use { fixture -> runBlocking {
        val snapshot = PaperCommitSnapshot.capture(fixture.session, fixture.working, originalAudit, false)
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var hashes = 0
        val task = async {
            snapshot.withHashes { hashes++; started.complete(Unit); withContext(NonCancellable) { release.await() }; "old-hash" }
        }
        started.await(); task.cancel(); release.complete(Unit); task.join()
        assertTrue(task.isCancelled); assertEquals(1, hashes)
    } }

    @Test fun emptyOrUnresolvedReviewCannotBecomeACommitSnapshot() = Fixture().use { fixture ->
        listOf(emptyList(), fixture.working.map { it.copy(valid = false, reviewed = false, reviewRequired = true, naOverride = false) }).forEach { results ->
            try { PaperCommitSnapshot.capture(fixture.session, results, originalAudit, false); fail("Unresolved commit accepted") }
            catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("Resolve every review item")) }
        }
    }

    @Test fun callerSuppliedSourcePolicyAndCanonicalFieldsStayUnchanged() = Fixture().use { fixture -> runBlocking {
        for (automatic in listOf(false, true)) {
            val snapshot = PaperCommitSnapshot.capture(fixture.session, fixture.working, originalAudit, automatic)
            val stable = snapshot.withHashes { "fixture-hash" }
            assertEquals(PaperBridgeFields.outputs.toSet(), stable.keys)
            val metadata = JSONObject(stable.getValue(PaperBridgeFields.ATTACHMENT_METADATA_JSON))
            assertEquals(!automatic, metadata.getJSONObject("source").getBoolean("returned_to_caller"))
            assertEquals("2026-10-23T12:00:00Z", stable[PaperBridgeFields.SCAN_TIME_ISO])
            assertEquals(PaperBridgeFiles.sha256Text(fixture.template.rawJson), stable[PaperBridgeFields.TEMPLATE_SHA256])
        }
    } }
}
