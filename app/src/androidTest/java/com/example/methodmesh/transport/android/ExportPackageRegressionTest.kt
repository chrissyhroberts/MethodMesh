package com.example.methodmesh.transport.android

import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.transport.OutputExportRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class ExportPackageRegressionTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val label = "reviewexport${UUID.randomUUID().toString().replace("-", "")}"
    private var configured = ""
    private fun uri(file: File) = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file).toString()
    private fun result(fields: Map<String, String?>) = ExecutionResult(
        ExecutionRequest(action = label, method = ArchitectureRef(ArchitectureId(label), "Method")), TransformationStatus.Succeeded,
        observations = listOf(Observation(phenomenon = "fixture", values = fields.mapValues { it.value.orEmpty() }, provenance = ProvenanceContext("test"))))
    private fun json(uri: String) = JSONObject(app.contentResolver.openInputStream(Uri.parse(uri))!!.bufferedReader().use { it.readText() })
    private fun rows(): List<Uri> {
        val collection = MediaStore.Files.getContentUri("external")
        return app.contentResolver.query(collection, arrayOf("_id"), "relative_path LIKE ?", arrayOf("Documents/MethodMesh/outputs/%$label%/"), null)!!.use { cursor ->
            buildList { while (cursor.moveToNext()) add(Uri.withAppendedPath(collection, cursor.getLong(0).toString())) }
        }
    }
    @Before fun isolateOutputSetting() {
        configured = OutputExportRepository.configuredFolder(app)
        OutputExportRepository.setConfiguredFolder(app, null)
    }
    @After fun cleanupOwnOutputs() {
        rows().forEach { app.contentResolver.delete(it, null, null) }
        OutputExportRepository.setConfiguredFolder(app, configured.takeIf { it.isNotBlank() }?.let(Uri::parse))
    }

    @Test fun bothSingleRunExportPathsCopyArraysAndAliasesWithoutBrokenReferences() {
        val source = File(app.cacheDir, "$label.pdf").apply { writeText("%PDF-1.4 review fixture") }
        try {
            val sourceUri = uri(source)
            val fields = linkedMapOf("file_uri" to sourceUri, "alias_uri" to sourceUri, "file_uris_json" to JSONArray(listOf(sourceUri)).toString())
            val exports = listOf(OutputExportRepository.exportFlatPackage(app, label, label, "Succeeded", fields), OutputExportRepository.exportPackage(app, result(fields)))
            exports.forEach { export ->
                assertEquals(1, export.attachments.size)
                val media = export.attachments.single()
                val values = json(export.json.uri).getJSONArray("steps").getJSONObject(0).getJSONObject("fields")
                assertEquals(media.filename, values.getString("file_uri"))
                assertEquals(media.filename, values.getString("alias_uri"))
                assertEquals(media.filename, JSONArray(values.getString("file_uris_json")).getString(0))
                assertArrayEquals(source.readBytes(), app.contentResolver.openInputStream(Uri.parse(media.uri))!!.use { it.readBytes() })
            }
            assertTrue(source.exists())
        } finally { source.delete() }
    }

    @Test fun allThreePackagePathsFailInsteadOfOmittingMissingAttachments() {
        val fields = mapOf("file_uri" to uri(File(app.cacheDir, "$label-missing.pdf")))
        assertTrue(runCatching { OutputExportRepository.exportFlatPackage(app, label, label, "Succeeded", fields) }.isFailure)
        assertTrue(runCatching { OutputExportRepository.exportPackage(app, result(fields)) }.isFailure)
        assertTrue(runCatching { OutputExportRepository.exportProtocolStepPackage(app, label, label, label, 0, "Step", label, "Succeeded", fields) }.isFailure)
        assertTrue("No successful package JSON should exist", rows().isEmpty())
    }

    @Test fun allThreePackagePathsRejectUnavailableConfiguredFolderWithoutDefaultFallback() {
        OutputExportRepository.setConfiguredFolder(app, Uri.parse("content://missing.review.provider/tree/folder"))
        assertTrue(runCatching { OutputExportRepository.exportFlatPackage(app, label, label, "Succeeded", emptyMap()) }.isFailure)
        assertTrue(runCatching { OutputExportRepository.exportPackage(app, result(emptyMap())) }.isFailure)
        assertTrue(runCatching { OutputExportRepository.exportProtocolStepPackage(app, label, label, label, 0, "Step", label, "Succeeded", emptyMap()) }.isFailure)
        assertTrue(rows().isEmpty())
    }

    @Test fun unreadableProtocolHistoryIsNotReplacedAsAnEmptyRun() {
        val first = OutputExportRepository.exportProtocolStepPackage(app, label, label, label, 0, "First", label, "Succeeded", mapOf("answer" to "one"))
        val damaged = "not valid JSON history"
        app.contentResolver.openOutputStream(Uri.parse(first.json.uri), "rwt")!!.use { it.write(damaged.toByteArray()) }
        assertTrue(runCatching { OutputExportRepository.exportProtocolStepPackage(app, label, label, label, 1, "Second", label, "Succeeded", mapOf("answer" to "two")) }.isFailure)
        assertEquals(damaged, app.contentResolver.openInputStream(Uri.parse(first.json.uri))!!.bufferedReader().use { it.readText() })
        assertEquals(1, rows().size)
    }

    @Test fun laterProtocolStepRetainsPriorStepsAndOriginalSubmissionIdentity() {
        OutputExportRepository.exportProtocolStepPackage(app, label, label, label, 0, "First", label, "Succeeded", mapOf("answer" to "one"))
        val second = OutputExportRepository.exportProtocolStepPackage(app, label, label, "do-not-replace-original", 1, "Second", label, "Succeeded", mapOf("answer" to "two"))
        val stored = json(second.json.uri)
        assertEquals(label, stored.getString("methodmesh_submission_id"))
        assertEquals(2, stored.getInt("step_count"))
        assertEquals("one", stored.getJSONArray("steps").getJSONObject(0).getJSONObject("fields").getString("answer"))
        assertEquals("two", stored.getJSONArray("steps").getJSONObject(1).getJSONObject("fields").getString("answer"))
    }
}
