package com.example.researchos.modules.nfc

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
import com.example.researchos.core.Method
import com.example.researchos.core.MethodCategory
import com.example.researchos.core.MethodField
import com.example.researchos.core.MethodFieldType
import com.example.researchos.core.MethodManifest
import com.example.researchos.core.MethodOutput
import com.example.researchos.core.MethodOutputSchema
import com.example.researchos.core.RequiredWhen
import com.example.researchos.core.MethodRequest
import com.example.researchos.core.MethodResult
import com.example.researchos.core.MethodStatus
import com.example.researchos.settings.MethodSetting
import com.example.researchos.settings.SettingsState

class NfcWriteMethod : Method {

    override val manifest = MethodManifest(
        id = ID,
        name = "NFC Tag Write",
        description = "Perform an NDEF write intervention and return write outcome plus post-write observation.",
        version = VERSION,
        category = MethodCategory.NFC,
        status = MethodStatus.Experimental
    )

    override val settings = listOf(
        MethodSetting.ChoiceSetting(
            id = "record_type",
            label = "Record type",
            description = "NDEF record type to write.",
            group = "Intervention",
            defaultValue = "text",
            choices = listOf("text", "uri", "mime", "external")
        ),
        MethodSetting.TextSetting(
            id = "value",
            label = "Value",
            description = "Value to write to the tag. Runtime context may override this setting.",
            group = "Intervention",
            defaultValue = ""
        ),
        MethodSetting.TextSetting(
            id = "mime_type",
            label = "MIME or external type",
            description = "For MIME records, use e.g. text/plain. For external records, use domain:type.",
            group = "Intervention",
            defaultValue = "text/plain"
        ),
        MethodSetting.TextSetting(
            id = "language_code",
            label = "Language code",
            description = "Language code for text records.",
            group = "Intervention",
            defaultValue = "en"
        )
    )

    override val outputSchema = MethodOutputSchema(
        graphOutputs = nfcWriteGraphSchema(),
        fields = listOf(
            MethodField(NfcWriteFields.WRITE_SUCCESS, "Write success", MethodFieldType.Boolean, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.values.write_success"),
            MethodField(NfcWriteFields.WRITE_MESSAGE, "Write message", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.values.write_message"),
            MethodField(NfcWriteFields.WRITE_RECORD_TYPE, "Write record type", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.values.write_record_type"),
            MethodField(NfcWriteFields.WRITE_SIZE_BYTES, "Write size bytes", MethodFieldType.Integer, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.write_size_bytes"),
            MethodField(NfcWriteFields.INTERVENTION_JSON, "Intervention JSON", MethodFieldType.Json, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Transformation.diagnostics/intervention"),
            MethodField(NfcWriteFields.POST_WRITE_EVIDENCE_JSON, "Post-write evidence JSON", MethodFieldType.Json, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation[nfc.tag.state]")
        ) + evidenceFieldSchema() + researchEnvelopeSchema()
    )

    @Composable
    override fun Demo(settingsState: SettingsState) {
        val initialStatus = rememberNfcAvailabilityMessage()
        var active by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf(initialStatus) }
        var bundle by remember { mutableStateOf<NfcWriteEvidenceBundle?>(null) }

        NfcDeviceServiceEffect(
            enabled = active,
            onStatus = { status = it },
            onSignal = { tagSignal ->
                val request = NfcWriteRequest(
                    recordType = settingsState.getString("record_type"),
                    value = settingsState.getString("value"),
                    mimeType = settingsState.getString("mime_type"),
                    languageCode = settingsState.getString("language_code")
                )
                val result = As100NfcWriteMethod.writeBundle(tagSignal, request)
                bundle = result
                status = result.writeMessage
                active = false
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("NFC Tag Write", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Configured value: ${settingsState.getString("value").ifBlank { "<empty>" }}")
            Text(status)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { active = !active }) {
                Text(if (active) "Cancel write session" else "Write to next NFC tag")
            }
            Spacer(Modifier.height(16.dp))
            val current = bundle
            if (current == null) {
                Text("No write intervention yet.")
            } else {
                KeyValueSection(
                    "Intervention outcome",
                    linkedMapOf(
                        NfcWriteFields.WRITE_SUCCESS to current.writeSuccess.toString(),
                        NfcWriteFields.WRITE_MESSAGE to current.writeMessage,
                        NfcWriteFields.WRITE_RECORD_TYPE to current.intervention.inputs["record_type"].orEmpty(),
                        NfcWriteFields.WRITE_SIZE_BYTES to current.writeSizeBytes.toString()
                    )
                )
                KeyValueSection("Intervention", current.intervention.asMap())
                KeyValueSection("Post-write evidence", current.postWriteRead.evidence.values)
                KeyValueSection("Provenance", current.intervention.provenance.asMap())
                KeyValueSection("Capture", current.intervention.outcome.asMap())
                KeyValueSection("Validation", current.intervention.validation.asMap())
                KeyValueSection("Artifact", current.postWriteRead.artifact.asMap())
            }

            ResearchSessionPreview()
        }
    }

    @Composable
    override fun Help() {
        Column(Modifier.padding(16.dp)) {
            Text("NFC Tag Write", fontWeight = FontWeight.Bold)
            Text("This method is an intervention. It attempts to write an NDEF text, URI, MIME or external record, records the action outcome and then returns a post-write NFC tag observation. It does not format non-NDEF tags and it does not handle ODK or other transport concerns.")
        }
    }

    override fun buildOutput(settingsState: SettingsState): MethodOutput = MethodOutput()

    override fun execute(request: MethodRequest): MethodResult {
        return MethodResult(
            success = false,
            errorMessage = "NFC write is an interactive intervention. The shell starts a session, receives an Android Tag, and maps the action to Intervention plus post-write Evidence records."
        )
    }

    companion object {
        const val ID = "nfc_tag_write"
        const val VERSION = "0.2.0"
    }
}
