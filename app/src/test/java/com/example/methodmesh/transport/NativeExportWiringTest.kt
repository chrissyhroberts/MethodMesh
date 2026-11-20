package com.example.methodmesh.transport

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NativeExportWiringTest {
    private val root = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
    private fun source(path: String) = File(root, "src/main/java/com/example/methodmesh/" + path).readText()
    @Test fun allFifteenSignalsCardSitesDelegateOriginalExecutionAndCloseoutToOneActionBay() {
        val dir = File(root, "src/main/java/com/example/methodmesh/modules/signals")
        val screens = dir.listFiles()!!.filter { it.name.endsWith("CapabilityScreen.kt") }.map { it.readText() }
        assertEquals(15, screens.sumOf { Regex("SignalCommittedCard\\(").findAll(it).count() })
        assertEquals(15, screens.sumOf { Regex("result = committedResult").findAll(it).count() })
        screens.forEach { assertFalse(it.contains("finishSignalResult(")); assertFalse(it.contains("committedFullJson")) }
    }
    @Test fun signalsUsesCanonicalOptionalAuditAndSuccessGatesInsteadOfAdHocShareIntents() {
        val support = source("modules/signals/SignalUiSupport.kt")
        val text = support.substringAfter("internal fun shareSignalText(").substringBefore("internal data class SignalSaveOutcome")
        assertTrue(text.contains("ResultShare.share(")); assertFalse(text.contains("Intent(Intent.ACTION_SEND)"))
        val card = support.substringAfter("internal fun SignalCommittedCard(").substringBefore("internal fun SignalSpectrumView")
        assertTrue(card.contains("rememberSaveable(result.request.id.value) { mutableStateOf(false) }"))
        assertTrue(card.contains("if (includeFullJson) fullJson(result) else \"\""))
        assertTrue(card.contains("ResultShare.buildShareText(exportText, json)"))
        assertTrue(card.contains("onShareRequested = ::share"))
        val finish = support.substringAfter("internal fun finishSignalResult(").substringBefore("internal fun fullJson")
        assertTrue(finish.contains("!context.isNativePresetRun || !context.isLastStep"))
        assertTrue(finish.contains("if (!onShareRequested()) return"))
        assertTrue(finish.contains("if (!onSaveRequested().saved) return"))
    }
    @Test fun weatherShareAndCopyUseCanonicalTextAndBothModulesGuardIntermediatePresetActions() {
        val weather = source("modules/weather/WeatherPresetSupport.kt")
        val share = weather.substringAfter("fun shareResult(): Boolean").substringBefore("fun saveResult()")
        assertTrue(share.contains("ResultShare.share(")); assertFalse(share.contains("Intent.createChooser"))
        assertTrue(weather.contains("ResultShare.buildShareText(shareText,"))
        listOf(weather, source("modules/networktools/NetworkToolsResultSupport.kt")).forEach {
            assertTrue(it.contains("!context.isNativePresetRun || !context.isLastStep"))
            assertTrue(it.contains("context.isNativePresetRun && context.isLastStep && presetAction"))
            assertFalse(it.contains("else if (context.isNativePresetRun && presetAction"))
        }
    }
    @Test fun typedReceivedMediaUsesGenericMimeHintsWithoutChangingOtherCallers() {
        val support = source("modules/signals/SignalUiSupport.kt")
        assertTrue(support.contains("mediaMimeTypes = mapOf(mediaUri to mimeType)"))
        val repo = source("transport/OutputExportRepository.kt")
        assertTrue(repo.contains("mediaMimeTypes: Map<String, String> = emptyMap()"))
        assertTrue(repo.contains("val usefulMime = resolvedMime ?: mediaMimeTypes[source]"))
    }
}
