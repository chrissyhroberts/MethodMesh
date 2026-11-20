package com.example.methodmesh.transport

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LegacyDownloadAccessPolicyTest {
    @Test fun modernAndroidDoesNotCheckOrRequestLegacyStorage() {
        (29..37).forEach { api -> LegacyDownloadAccessPolicy.requireAccess(api,
            { error("Modern save must not require legacy permission") }, { error("Unexpected request") }) }
    }
    @Test fun supportedLegacyAndroidWithAccessDoesNotPrompt() {
        listOf(27, 28).forEach { api -> LegacyDownloadAccessPolicy.requireAccess(api, { true }, { error("Already granted") }) }
    }
    @Test fun missingLegacyAccessRequestsOnlyPermissionAndReturnsAnExplicitRetryError() {
        listOf(27, 28).forEach { api ->
            val events = mutableListOf<String>()
            try {
                LegacyDownloadAccessPolicy.requireAccess(api, { false }, { events += "permission" })
                events += "write"; fail("Missing permission reported success")
            } catch (expected: IllegalStateException) {
                assertEquals(LegacyDownloadAccessPolicy.RETRY_MESSAGE, expected.message)
            }
            assertEquals(listOf("permission"), events)
        }
    }
    @Test fun grantingPermissionDoesNotReplaySaveAndOnlyExplicitRetryWrites() {
        var granted = false; var prompts = 0; var writes = 0
        runCatching { LegacyDownloadAccessPolicy.requireAccess(28, { granted }, { prompts++ }) }
        granted = true
        assertEquals(0, writes)
        LegacyDownloadAccessPolicy.requireAccess(28, { granted }, { prompts++ }); writes++
        assertEquals(1, prompts); assertEquals(1, writes)
    }
    @Test fun revokedPermissionIsCheckedAgainAtTheNextSave() {
        var granted = true; var prompted = false
        LegacyDownloadAccessPolicy.requireAccess(27, { granted }, { error("Already allowed") })
        granted = false
        assertTrue(runCatching { LegacyDownloadAccessPolicy.requireAccess(27, { granted }, { prompted = true }) }.isFailure)
        assertTrue(prompted)
    }
    @Test fun failedPermissionNavigationCannotFallThroughToWriting() {
        var wrote = false
        val error = runCatching {
            LegacyDownloadAccessPolicy.requireAccess(28, { false }, { throw IllegalStateException("Permission UI unavailable") })
            wrote = true
        }.exceptionOrNull()
        assertEquals("Permission UI unavailable", error?.message); assertFalse(wrote)
    }
    @Test fun explicitSettingsRouteIsOnlyForPreviouslyAskedNonRationaleState() {
        assertFalse(LegacyDownloadAccessPolicy.needsSettings(false, false))
        assertFalse(LegacyDownloadAccessPolicy.needsSettings(false, true))
        assertFalse(LegacyDownloadAccessPolicy.needsSettings(true, true))
        assertTrue(LegacyDownloadAccessPolicy.needsSettings(true, false))
    }
    @Test fun sharedPreflightRunsBeforeAnyFilesAndPrivatePermissionActivityCannotReplayEvidence() {
        val root = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
        fun source(path: String) = File(root, "src/main/" + path).readText()
        val repo = source("java/com/example/methodmesh/transport/OutputExportRepository.kt").substringAfter("fun saveToDownloads(")
        assertTrue(repo.indexOf("LegacyDownloadsPermission.requireAccess(context)") in 0 until repo.indexOf("val operationInstant"))
        val activity = source("java/com/example/methodmesh/transport/android/DownloadStoragePermissionActivity.kt")
        listOf("ExecutionResult", "OutputExportRepository", "getStringExtra", "openOutputStream").forEach { assertFalse(activity.contains(it)) }
        assertTrue(activity.contains("Back to result")); assertTrue(activity.contains("Open app settings"))
        val manifest = source("AndroidManifest.xml").substringAfter(".transport.android.DownloadStoragePermissionActivity").substringBefore("/>")
        assertTrue(manifest.contains("android:exported=\"false\""))
    }
}
