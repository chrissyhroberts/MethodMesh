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
import com.example.researchos.core.GraphField
import com.example.researchos.core.GraphOutput
import com.example.researchos.core.RequiredWhen
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.MethodRequest
import com.example.researchos.core.MethodResult
import com.example.researchos.core.MethodStatus
import com.example.researchos.presentation.nfc.NfcReadHelpScreen
import com.example.researchos.settings.MethodSetting
import com.example.researchos.settings.SettingsState

class NfcReadMethod : Method {

    override val manifest = MethodManifest(
        id = ID,
        name = "NFC Tag Read",
        description = "Identify and observe an NFC tag as structured evidence plus immutable tag artifact.",
        version = VERSION,
        category = MethodCategory.NFC,
        status = MethodStatus.Experimental
    )

    override val settings = listOf(
        MethodSetting.BooleanSetting(
            id = "read_once",
            label = "Read once",
            description = "Stop updating the observation after the first tag is read.",
            group = "Capture",
            defaultValue = true
        ),
        MethodSetting.TextSetting(
            id = "field_filter",
            label = "Field filter",
            description = "Optional comma-separated list of evidence fields to expose to transport. Leave blank for all standard tag fields.",
            group = "Output",
            defaultValue = ""
        )
    )

    override val outputSchema = MethodOutputSchema(
        graphOutputs = nfcReadGraphSchema(),
        fields = evidenceFieldSchema() + researchEnvelopeSchema()
    )

    @Composable
    override fun Demo(settingsState: SettingsState) {
        val initialStatus = rememberNfcAvailabilityMessage()
        var active by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf(initialStatus) }
        var bundle by remember { mutableStateOf<NfcReadEvidenceBundle?>(null) }

        NfcDeviceServiceEffect(
            enabled = active,
            onStatus = { status = it },
            onSignal = { tagSignal ->
                val result = As100NfcReadMethod.readBundle(tagSignal)
                bundle = result
                status = "NFC tag read and recorded as a ResearchOS Observation."
                active = false
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
                Text(if (active) "Cancel scan" else "Scan NFC tag")
            }
            Spacer(Modifier.height(16.dp))

            val current = bundle
            if (current == null) {
                Text("No NFC tag read yet.")
            } else {
                val fieldFilter = parseFieldFilter(settingsState.getString("field_filter"))
                val displayFields = applyFieldFilter(current.evidence.values, fieldFilter)

                KeyValueSection("NFC evidence", displayFields)
                KeyValueSection("Research semantics", current.evidence.semanticsMap())
                KeyValueSection("Provenance", current.evidence.provenance.asMap())
                KeyValueSection("Capture", current.evidence.captureOutcome.asMap())
                KeyValueSection("Quality", current.evidence.quality.asMap())
                KeyValueSection("Validation", current.evidence.validation.asMap())
                KeyValueSection("Artifact", current.artifact.asMap())
            }

            ResearchSessionPreview()
        }
    }

    @Composable
    override fun Help() {
        NfcReadHelpScreen()
    }

    override fun buildOutput(settingsState: SettingsState): MethodOutput = MethodOutput()

    override fun execute(request: MethodRequest): MethodResult {
        return MethodResult(
            success = false,
            errorMessage = "NFC read is an interactive device capture. The shell starts a session, receives an Android Tag, and maps it to Evidence/Artifact records."
        )
    }

    companion object {
        const val ID = "nfc_tag_read"
        const val VERSION = "0.3.0"
    }
}

internal fun parseFieldFilter(value: String): Set<String> =
    value.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()

internal fun applyFieldFilter(fields: Map<String, String>, filter: Set<String>): Map<String, String> =
    if (filter.isEmpty()) fields else fields.filterKeys { it in filter }

internal fun evidenceFieldSchema(): List<MethodField> =
    NfcEvidenceFields.tagOutputFields.map { key ->
        MethodField(
            id = key,
            label = key,
            type = if (key.endsWith("_json")) MethodFieldType.Json else MethodFieldType.Text,
            required = false,
            requiredWhen = if (key == NfcEvidenceFields.TAG_UID_HEX) RequiredWhen.OnSuccessfulCapture else RequiredWhen.IfAvailable,
            graphPath = "Observation.values.$key"
        )
    }

internal fun researchEnvelopeSchema(): List<MethodField> = listOf(
    MethodField(ResearchOutputFields.EVIDENCE_ID, "Evidence ID", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.id"),
    MethodField(ResearchOutputFields.EVIDENCE_KIND, "Evidence kind", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.knowledgeType"),
    MethodField(ResearchOutputFields.PHENOMENON, "Phenomenon", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.phenomenon"),
    MethodField(ResearchOutputFields.METHOD, "Method", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.provenance.methodId"),
    MethodField(ResearchOutputFields.TEMPORAL_SEMANTICS, "Temporal semantics", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.temporalContext"),
    MethodField(ResearchOutputFields.AGGREGATION_SEMANTICS, "Aggregation semantics", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.values"),
    MethodField(ResearchOutputFields.LINEAGE, "Lineage", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Transformation.inputs/outputs"),
    MethodField(ResearchOutputFields.PROVENANCE_JSON, "Provenance JSON", MethodFieldType.Json, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.provenance"),
    MethodField(ResearchOutputFields.CAPTURE_OUTCOME_JSON, "Capture outcome JSON", MethodFieldType.Json, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "ExecutionResult.validation/quality"),
    MethodField(ResearchOutputFields.QUALITY_JSON, "Quality JSON", MethodFieldType.Json, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "ExecutionResult.quality"),
    MethodField(ResearchOutputFields.VALIDATION_JSON, "Validation JSON", MethodFieldType.Json, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "ExecutionResult.validation"),
    MethodField(ResearchOutputFields.ARTIFACT_JSON, "Artifact JSON", MethodFieldType.Json, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Entity/Attribute artifact envelope"),
    MethodField(ResearchOutputFields.EVIDENCE_JSON, "Evidence JSON", MethodFieldType.Json, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation"),
    MethodField(ResearchOutputFields.EXECUTION_JSON, "Execution JSON", MethodFieldType.Json, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "ExecutionResult"),
    MethodField(ResearchOutputFields.AS_SIGNAL_TYPE, "AS1.00 signal type", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Signal.signalType"),
    MethodField(ResearchOutputFields.AS_SIGNAL_SOURCE_SERVICE, "AS1.00 signal source service", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Signal.sourceService"),
    MethodField(ResearchOutputFields.AS_TRANSFORMATION_ACTION, "AS1.00 transformation action", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Transformation.action"),
    MethodField(ResearchOutputFields.AS_TRANSFORMATION_STATUS, "AS1.00 transformation status", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Transformation.status")
)

internal fun nfcReadGraphSchema(): List<GraphOutput> = listOf(
    GraphOutput(
        id = "nfc_tag_entity",
        objectType = KnowledgeObjectType.Entity,
        entityType = "NfcTag",
        subjectRole = "observed_artifact",
        description = "The physical NFC tag encountered by Android's NFC device service.",
        fields = listOf(
            GraphField(NfcEvidenceFields.TAG_UID_HEX, "Entity.attributes.tag_uid_hex", MethodFieldType.Text, RequiredWhen.OnSuccessfulCapture),
            GraphField(NfcEvidenceFields.TECH_LIST, "Entity.attributes.tech_list", MethodFieldType.Text, RequiredWhen.IfAvailable)
        )
    ),
    GraphOutput(
        id = "nfc_tag_state_observation",
        objectType = KnowledgeObjectType.Observation,
        phenomenon = "nfc.tag.state",
        subjectRole = "observed_artifact",
        description = "Observed readable state of the NFC tag at capture time.",
        fields = NfcEvidenceFields.tagOutputFields.map { key ->
            GraphField(
                id = key,
                path = "Observation.values.$key",
                type = if (key.endsWith("_json")) MethodFieldType.Json else MethodFieldType.Text,
                requiredWhen = if (key == NfcEvidenceFields.TAG_UID_HEX) RequiredWhen.OnSuccessfulCapture else RequiredWhen.IfAvailable
            )
        }
    )
)

internal fun nfcWriteGraphSchema(): List<GraphOutput> = nfcReadGraphSchema() + listOf(
    GraphOutput(
        id = "nfc_write_transformation",
        objectType = KnowledgeObjectType.Observation,
        phenomenon = "nfc.tag.write_outcome",
        subjectRole = "observed_artifact",
        description = "Outcome of the requested NDEF write operation, followed by a post-write tag observation.",
        fields = listOf(
            GraphField(NfcWriteFields.WRITE_SUCCESS, "Observation.values.write_success", MethodFieldType.Boolean, RequiredWhen.OnSuccessfulCapture),
            GraphField(NfcWriteFields.WRITE_MESSAGE, "Observation.values.write_message", MethodFieldType.Text, RequiredWhen.OnSuccessfulCapture),
            GraphField(NfcWriteFields.WRITE_RECORD_TYPE, "Observation.values.write_record_type", MethodFieldType.Text, RequiredWhen.OnSuccessfulCapture),
            GraphField(NfcWriteFields.WRITE_SIZE_BYTES, "Observation.values.write_size_bytes", MethodFieldType.Integer, RequiredWhen.IfAvailable),
            GraphField(NfcWriteFields.INTERVENTION_JSON, "Transformation.diagnostics/intervention", MethodFieldType.Json, RequiredWhen.OnSuccessfulCapture),
            GraphField(NfcWriteFields.POST_WRITE_EVIDENCE_JSON, "Observation[nfc.tag.state]", MethodFieldType.Json, RequiredWhen.OnSuccessfulCapture)
        )
    )
)
