package com.example.methodmesh.modules.paperbridge

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PaperCommittedExportWiringTest {
    private val root = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
    private fun source(name: String) = File(root, "src/main/java/com/example/methodmesh/modules/paperbridge/$name.kt").readText()
    private val screens get() = listOf(source("PaperFormDesignerCapabilityScreen"), source("PaperFormTranscribeCapabilityScreen"))

    @Test fun committedPersistenceUsesSharedDownloadsAndCannotWriteAnArtifactCollection() {
        val actions = source("PaperBridgeResultActions")
        assertTrue(actions.contains("OutputExportRepository.saveToDownloads("))
        assertFalse(actions.contains("AndroidArtifacts")); assertFalse(actions.contains("createPersistent"))
        screens.forEach {
            assertTrue(it.contains("PaperBridgeResultActions.saveToDownloads("))
            assertFalse(it.contains("saveToFiles(")); assertTrue(it.contains("Save to Downloads"))
        }
    }
    @Test fun nativeTranscriptionOmitsRawScanButCommitEvidenceStillKeepsIt() {
        val attachments = source("PaperFormTranscribeCapabilityScreen").substringAfter("fun committedAttachments()").substringBefore("fun shareCommitted()")
        assertFalse(attachments.contains("PaperBridgeFields.SOURCE_IMAGE"))
        assertTrue(attachments.contains("PaperBridgeFields.RECTIFIED_IMAGE"))
        assertTrue(attachments.contains("PaperFieldType.IMAGE"))
        val snapshot = source("PaperCommitSnapshot")
        assertTrue(snapshot.contains("PaperBridgeFields.SOURCE_IMAGE to extracted.sourceUri.toString()"))
        assertTrue(snapshot.contains("PaperBridgeFields.SOURCE_SHA256 to sourceHash"))
    }
    @Test fun bothPresetActionsAreSuccessfulAndLastStepOnlyBeforeConfirmation() {
        screens.forEach {
            val finish = it.substringAfter("fun finishCommitted(").substringBefore("fun completeDesign(").substringBefore("if (commitController.inFlight)")
            assertTrue(finish.contains("!context.isNativePresetRun || !context.isLastStep"))
            assertTrue(finish.contains("PresetResultAction.SHARE -> { if (shareCommitted()) onConfirmed("))
            assertTrue(finish.contains("PresetResultAction.SAVE -> { if (saveCommitted()) onConfirmed("))
            assertTrue(it.contains("Share and finish")); assertTrue(it.contains("Save and finish"))
        }
    }
    @Test fun auditDefaultsOffAndUsesCanonicalFullProjectionThroughSharedShare() {
        screens.forEach {
            assertTrue(it.contains("mutableStateOf(false)"))
            assertTrue(it.contains("OutputFormatter.PayloadMode.FULL"))
            assertTrue(it.contains("ResultShare.share("))
            assertTrue(it.contains("jsonText = if (includeFullJson) fullJson else \"\""))
            assertFalse(it.contains("jsonFileName ="))
        }
        val workspace = source("PaperBridgeWorkspace")
        assertTrue(workspace.contains("fun saveAndActivateTemplate("))
        assertFalse(workspace.contains("PaperBridgeResultActions"))
    }
}
