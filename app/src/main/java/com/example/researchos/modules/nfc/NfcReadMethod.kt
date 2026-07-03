package com.example.researchos.modules.nfc

import androidx.compose.runtime.Composable
import com.example.researchos.core.Method
import com.example.researchos.core.MethodCategory
import com.example.researchos.core.MethodField
import com.example.researchos.core.MethodFieldType
import com.example.researchos.core.MethodManifest
import com.example.researchos.core.MethodOutput
import com.example.researchos.core.MethodOutputSchema
import com.example.researchos.core.MethodRequest
import com.example.researchos.core.MethodResult
import com.example.researchos.core.MethodStatus
import com.example.researchos.presentation.nfc.NfcReadDemoScreen
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
        fields = evidenceFieldSchema() + researchEnvelopeSchema()
    )

    @Composable
    override fun Demo(settingsState: SettingsState) {
        NfcReadDemoScreen(settingsState)
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
            required = key == NfcEvidenceFields.TAG_UID_HEX
        )
    }

internal fun researchEnvelopeSchema(): List<MethodField> = listOf(
    MethodField(ResearchOutputFields.EVIDENCE_ID, "Evidence ID", MethodFieldType.Text, required = true),
    MethodField(ResearchOutputFields.EVIDENCE_KIND, "Evidence kind", MethodFieldType.Text, required = true),
    MethodField(ResearchOutputFields.PHENOMENON, "Phenomenon", MethodFieldType.Text, required = true),
    MethodField(ResearchOutputFields.METHOD, "Method", MethodFieldType.Text, required = true),
    MethodField(ResearchOutputFields.TEMPORAL_SEMANTICS, "Temporal semantics", MethodFieldType.Text, required = true),
    MethodField(ResearchOutputFields.AGGREGATION_SEMANTICS, "Aggregation semantics", MethodFieldType.Text, required = true),
    MethodField(ResearchOutputFields.LINEAGE, "Lineage", MethodFieldType.Text, required = true),
    MethodField(ResearchOutputFields.PROVENANCE_JSON, "Provenance JSON", MethodFieldType.Json, required = true),
    MethodField(ResearchOutputFields.CAPTURE_OUTCOME_JSON, "Capture outcome JSON", MethodFieldType.Json, required = true),
    MethodField(ResearchOutputFields.QUALITY_JSON, "Quality JSON", MethodFieldType.Json, required = true),
    MethodField(ResearchOutputFields.VALIDATION_JSON, "Validation JSON", MethodFieldType.Json, required = true),
    MethodField(ResearchOutputFields.ARTIFACT_JSON, "Artifact JSON", MethodFieldType.Json, required = true),
    MethodField(ResearchOutputFields.EVIDENCE_JSON, "Evidence JSON", MethodFieldType.Json, required = true),
    MethodField(ResearchOutputFields.EXECUTION_JSON, "Execution JSON", MethodFieldType.Json, required = true),
    MethodField(ResearchOutputFields.AS_SIGNAL_TYPE, "AS1.00 signal type", MethodFieldType.Text, required = true),
    MethodField(ResearchOutputFields.AS_SIGNAL_SOURCE_SERVICE, "AS1.00 signal source service", MethodFieldType.Text, required = true),
    MethodField(ResearchOutputFields.AS_TRANSFORMATION_ACTION, "AS1.00 transformation action", MethodFieldType.Text, required = true),
    MethodField(ResearchOutputFields.AS_TRANSFORMATION_STATUS, "AS1.00 transformation status", MethodFieldType.Text, required = true)
)
