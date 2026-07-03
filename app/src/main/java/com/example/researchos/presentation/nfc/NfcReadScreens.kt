package com.example.researchos.presentation.nfc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.modules.nfc.NfcDeviceServiceEffect
import com.example.researchos.modules.nfc.NfcEvidenceFields
import com.example.researchos.modules.nfc.As100NfcReadMethod
import com.example.researchos.modules.nfc.NfcReadEvidenceBundle
import com.example.researchos.modules.nfc.applyFieldFilter
import com.example.researchos.modules.nfc.parseFieldFilter
import com.example.researchos.modules.nfc.rememberNfcAvailabilityMessage
import com.example.researchos.modules.nfc.KeyValueSection
import com.example.researchos.settings.SettingsState

/**
 * Presentation-owned NFC read demo screen.
 *
 * This keeps Compose UI out of the method implementation while preserving the legacy
 * Method.Demo entry point during the migration to headless AS1.00 methods.
 */
@Composable
fun NfcReadDemoScreen(settingsState: SettingsState) {
    val initialStatus = rememberNfcAvailabilityMessage()
    var active by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(initialStatus) }
    var bundle by remember { mutableStateOf<NfcReadEvidenceBundle?>(null) }
    val readOnce = settingsState.getBoolean("read_once")
    val fieldFilter = parseFieldFilter(settingsState.getString("field_filter"))

    NfcDeviceServiceEffect(
        enabled = active,
        onStatus = { status = it },
        onSignal = { tagSignal ->
            val read = As100NfcReadMethod.readBundle(tagSignal)
            bundle = read
            status = "Tag read: ${read.evidence.values[NfcEvidenceFields.TAG_UID_HEX].orEmpty()}"
            if (readOnce) active = false
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("NFC Tag Read", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(status)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { active = !active }) {
            Text(if (active) "Stop NFC read session" else "Start NFC read session")
        }
        Spacer(Modifier.height(16.dp))
        val current = bundle
        if (current == null) {
            Text("No tag observation yet.")
        } else {
            val evidenceValues = applyFieldFilter(current.evidence.values, fieldFilter)
            KeyValueSection("Evidence values", evidenceValues)
            KeyValueSection("Measurement semantics", current.evidence.semanticsMap())
            KeyValueSection("Provenance", current.evidence.provenance.asMap())
            KeyValueSection("Capture", current.evidence.captureOutcome.asMap())
            KeyValueSection("Quality", current.evidence.quality.asMap())
            KeyValueSection("Validation", current.evidence.validation.asMap())
            KeyValueSection("Artifact", current.artifact.asMap())
        }
    }
}

/** Presentation-owned NFC read help screen. */
@Composable
fun NfcReadHelpScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("NFC Tag Read", fontWeight = FontWeight.Bold)
        Text("This method produces an Observation and an immutable NFC tag Artifact. It reads the UID, Android tag technologies, NDEF support, NDEF write state, NDEF records, decoded text/URI/MIME/external records and raw payloads. It does not implement ODK, XLSForm appearances, or return-intent mechanics; those remain in the ResearchOS transport layer.")
    }
}
