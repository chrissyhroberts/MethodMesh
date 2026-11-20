package com.example.methodmesh.modules.paperbridge

import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PaperCommittedValuesTest {
    @Test fun missingResultCannotProduceCommittedValues() {
        assertTrue(paperCommittedFields(null).isEmpty())
        assertTrue(paperCommittedDynamicFields(null).isEmpty())
    }
    @Test fun designerProjectionUsesOriginalObservationWithoutReconstruction() {
        val original = As100PaperFormDesignMethod.result(
            ExecutionRequest(action = As100PaperFormDesignMethod.id, method = As100PaperFormDesignMethod.ref),
            mapOf(PaperDesignFields.STATUS to "succeeded", PaperDesignFields.TEMPLATE_ID to "frozen-form",
                PaperDesignFields.TEMPLATE_JSON_URI to "content://fixture/original-schema",
                PaperDesignFields.TEMPLATE_JSON to "{\"version\":1}"), null)
        val full = OutputFormatter.format(original, ReturnMode.Json, includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL)
        assertSame(original.observations.single().values, paperCommittedFields(original))
        assertEquals("content://fixture/original-schema", paperCommittedFields(original)[PaperDesignFields.TEMPLATE_JSON_URI])
        assertTrue(paperCommittedDynamicFields(original).isEmpty())
        assertEquals(full, OutputFormatter.format(original, ReturnMode.Json, includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL))
    }
    @Test fun transcriptionDynamicProjectionUsesCanonicalJsonAndPreservesEvidence() {
        val original = As100PaperFormTranscribeMethod.result(
            ExecutionRequest(action = As100PaperFormTranscribeMethod.id, method = As100PaperFormTranscribeMethod.ref),
            mapOf(PaperBridgeFields.STATUS to "succeeded", PaperBridgeFields.VALUES_JSON to "{\"answer\":\"original\",\"attachment\":\"content://fixture/photo\"}",
                PaperBridgeFields.EXTRACTION_AUDIT_JSON to "{\"reviewed\":true}",
                PaperBridgeFields.SOURCE_SHA256 to "original-hash"),
            mapOf("answer" to "flat-projection-must-not-replace-canonical-json"), null)
        val full = OutputFormatter.format(original, ReturnMode.Json, includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL)
        assertEquals(mapOf("answer" to "original", "attachment" to "content://fixture/photo"), paperCommittedDynamicFields(original))
        assertSame(original.observations.single().values, paperCommittedFields(original))
        assertEquals("original-hash", paperCommittedFields(original)[PaperBridgeFields.SOURCE_SHA256])
        assertEquals(full, OutputFormatter.format(original, ReturnMode.Json, includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL))
    }
    @Test fun allFiveScreensRetainExecutionsAndNeverRestoreFromSavedValueJsonOrIds() {
        val root = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
        val names = listOf("paperbridge/PaperFormDesignerCapabilityScreen", "paperbridge/PaperFormTranscribeCapabilityScreen",
            "qrcode/BarcodeGenerateCapabilityScreen", "qrcode/QrCodeCapabilityScreen", "qrcode/BarcodeCloneCapabilityScreen")
        names.forEach { name ->
            val source = File(root, "src/main/java/com/example/methodmesh/modules/$name.kt").readText()
            assertTrue(name, source.contains("rememberRetainedExecutionResult("))
            assertTrue(name, source.contains("CapturedExecutionRecovery("))
            listOf("restoredCommitted", "restoredSnapshot", "resultFromSavedState", "committedValuesJson", "committedStableJson",
                "committedDynamicJson", "snapshotRequestId", "committedRequestId", "workingRequestId").forEach {
                assertFalse("$name still uses $it", source.contains(it))
            }
        }
        val transcribe = File(root, "src/main/java/com/example/methodmesh/modules/paperbridge/PaperFormTranscribeCapabilityScreen.kt").readText()
        val startup = transcribe.substringAfter("LaunchedEffect(Unit) {").substringBefore("fun commit()")
        assertTrue("Committed transcription must not replay OCR/acquisition", startup.indexOf("if (hadCommitted) return@LaunchedEffect") in 0 until startup.indexOf("runExtraction("))
    }
}
