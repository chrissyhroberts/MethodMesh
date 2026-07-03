package com.example.researchos.modules.nfc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.core.ResearchRuntime

@Composable
internal fun KeyValueSection(
    title: String,
    values: Map<String, String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Divider(Modifier.padding(top = 10.dp, bottom = 10.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (values.isEmpty()) {
            Text("No values.")
        } else {
            values.forEach { (key, value) ->
                Text(
                    text = "$key = ${value.ifBlank { "" }}",
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

internal fun Map<String, String>.filterForDisplay(keys: List<String>): Map<String, String> =
    keys.associateWith { this[it].orEmpty() }


@Composable
internal fun ResearchSessionPreview(
    title: String = "Current research session",
    modifier: Modifier = Modifier
) {
    val session = ResearchRuntime.session
    val observations = session.observations.toList()
    val latest = observations.lastOrNull()

    Column(modifier = modifier.fillMaxWidth()) {
        Divider(Modifier.padding(top = 14.dp, bottom = 10.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Entities: ${session.entities.size}")
        Text("Observations: ${observations.size}")

        if (latest == null) {
            Spacer(Modifier.height(4.dp))
            Text("No ResearchOS observations recorded in this session yet.")
        } else {
            Spacer(Modifier.height(8.dp))
            Text("Latest observation", fontWeight = FontWeight.Bold)
            Text(latest.provenance.methodId, fontFamily = FontFamily.Monospace)

            val displayFields = latest.output.fields
                .filterKeys { key -> key !in researchEnvelopePreviewFields }
                .filterValues { value -> value?.toString()?.isNotBlank() == true }

            val preferredFields = preferredNfcPreviewFields
                .mapNotNull { key -> displayFields[key]?.let { key to it } }

            val remainingFields = displayFields
                .filterKeys { key -> key !in preferredNfcPreviewFields }
                .entries
                .map { it.key to it.value }

            val previewFields = (preferredFields + remainingFields).take(8)

            if (previewFields.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                previewFields.forEach { (key, value) ->
                    Text(
                        text = "• ${key.toReadableFieldLabel()} = ${value.toString().limitForPreview()}",
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text("No display fields available for this observation.")
            }
        }
    }
}

private val preferredNfcPreviewFields = listOf(
    NfcWriteFields.WRITE_SUCCESS,
    NfcWriteFields.WRITE_MESSAGE,
    NfcWriteFields.WRITE_RECORD_TYPE,
    NfcEvidenceFields.TAG_UID_HEX,
    NfcEvidenceFields.NDEF_SUPPORTED,
    NfcEvidenceFields.NDEF_IS_WRITABLE,
    NfcEvidenceFields.NDEF_RECORD_COUNT,
    NfcEvidenceFields.NDEF_TEXT,
    NfcEvidenceFields.NDEF_URI,
    NfcEvidenceFields.TECH_LIST,
    NfcEvidenceFields.TAG_SUMMARY
)

private val researchEnvelopePreviewFields = setOf(
    ResearchOutputFields.EVIDENCE_ID,
    ResearchOutputFields.EVIDENCE_KIND,
    ResearchOutputFields.PHENOMENON,
    ResearchOutputFields.METHOD,
    ResearchOutputFields.TEMPORAL_SEMANTICS,
    ResearchOutputFields.AGGREGATION_SEMANTICS,
    ResearchOutputFields.LINEAGE,
    ResearchOutputFields.PROVENANCE_JSON,
    ResearchOutputFields.CAPTURE_OUTCOME_JSON,
    ResearchOutputFields.QUALITY_JSON,
    ResearchOutputFields.VALIDATION_JSON,
    ResearchOutputFields.ARTIFACT_JSON,
    ResearchOutputFields.EVIDENCE_JSON,
    ResearchOutputFields.EXECUTION_JSON,
    ResearchOutputFields.AS_SIGNAL_TYPE,
    ResearchOutputFields.AS_SIGNAL_SOURCE_SERVICE,
    ResearchOutputFields.AS_TRANSFORMATION_ACTION,
    ResearchOutputFields.AS_TRANSFORMATION_STATUS
)

private fun String.toReadableFieldLabel(): String =
    split('_')
        .joinToString(" ") { part -> part.replaceFirstChar { char -> char.uppercaseChar() } }

private fun String.limitForPreview(maxChars: Int = 96): String =
    if (length <= maxChars) this else take(maxChars - 1) + "…"
