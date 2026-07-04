package com.example.researchos.modules.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.TagTechnology
import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.Entity
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.TemporalContext
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.platform.nfc.AndroidNfcDeviceService
import com.example.researchos.platform.nfc.NfcTagSignal
import com.example.researchos.core.research.AggregationSemantics
import com.example.researchos.core.research.ArtifactKind
import com.example.researchos.core.research.ArtifactRecord
import com.example.researchos.core.research.CaptureOutcome
import com.example.researchos.core.research.CaptureOutcomeStatus
import com.example.researchos.core.research.CaptureStrategy
import com.example.researchos.core.research.EvidenceKind
import com.example.researchos.core.research.EvidenceRecord
import com.example.researchos.core.research.InterventionRecord
import com.example.researchos.core.research.LineageSemantics
import com.example.researchos.core.research.ProvenanceRecord
import com.example.researchos.core.research.QualityRecord
import com.example.researchos.core.research.ResearchLayer
import com.example.researchos.core.research.TemporalSemantics
import com.example.researchos.core.research.ValidationRecord
import com.example.researchos.core.research.MethodExecutionRecord
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class NfcReadEvidenceBundle(
    val evidence: EvidenceRecord,
    val artifact: ArtifactRecord,
    val execution: MethodExecutionRecord,
    val signal: Signal,
    val researchosObservation: Observation,
    val transformation: Transformation,
    val executionResult: ExecutionResult,
    val diagnostics: Map<String, String> = emptyMap()
) {
    fun outputFields(): Map<String, String> = linkedMapOf<String, String>().apply {
        putAll(evidence.semanticsMap())
        putAll(evidence.values)
        put(ResearchOutputFields.PROVENANCE_JSON, evidence.provenance.toJson())
        put(ResearchOutputFields.CAPTURE_OUTCOME_JSON, evidence.captureOutcome.toJson())
        put(ResearchOutputFields.QUALITY_JSON, evidence.quality.toJson())
        put(ResearchOutputFields.VALIDATION_JSON, evidence.validation.toJson())
        put(ResearchOutputFields.ARTIFACT_JSON, artifact.toJson())
        put(ResearchOutputFields.EVIDENCE_JSON, evidence.toJson())
        put(ResearchOutputFields.EXECUTION_JSON, execution.toJson())
        put(ResearchOutputFields.AS_SIGNAL_TYPE, signal.signalType)
        put(ResearchOutputFields.AS_SIGNAL_SOURCE_SERVICE, signal.sourceService)
        put(ResearchOutputFields.AS_TRANSFORMATION_ACTION, transformation.action)
        put(ResearchOutputFields.AS_TRANSFORMATION_STATUS, transformation.status.name)
    }
}


data class NfcWriteEvidenceBundle(
    val intervention: InterventionRecord,
    val postWriteRead: NfcReadEvidenceBundle,
    val writeSuccess: Boolean,
    val writeMessage: String,
    val writeSizeBytes: Int
) {
    fun outputFields(): Map<String, String> = linkedMapOf<String, String>().apply {
        put(NfcWriteFields.WRITE_SUCCESS, writeSuccess.toString())
        put(NfcWriteFields.WRITE_MESSAGE, writeMessage)
        put(NfcWriteFields.WRITE_RECORD_TYPE, intervention.inputs["record_type"].orEmpty())
        put(NfcWriteFields.WRITE_SIZE_BYTES, writeSizeBytes.toString())
        put(NfcWriteFields.INTERVENTION_JSON, intervention.toJson())
        put(NfcWriteFields.POST_WRITE_EVIDENCE_JSON, postWriteRead.evidence.toJson())
        putAll(postWriteRead.evidence.values)
        put(ResearchOutputFields.PROVENANCE_JSON, intervention.provenance.toJson())
        put(ResearchOutputFields.CAPTURE_OUTCOME_JSON, intervention.outcome.toJson())
        put(ResearchOutputFields.VALIDATION_JSON, intervention.validation.toJson())
        put(ResearchOutputFields.ARTIFACT_JSON, postWriteRead.artifact.toJson())
        put(ResearchOutputFields.EXECUTION_JSON, postWriteRead.execution.toJson())
    }
}

object NfcTagRepository {

    fun readTagSignal(
        tagSignal: NfcTagSignal,
        methodId: String,
        methodVersion: String,
        methodObjectType: String = "Method",
        methodLabel: String = "NFC Tag Read"
    ): NfcReadEvidenceBundle {
        val signal = tagSignal.signal
        val tag = tagSignal.androidTag
        val provenance = ProvenanceRecord(
            methodId = methodId,
            methodVersion = methodVersion,
            provider = signal.provenance.provider
        )
        val tagValues = extractTagValues(tag)
        val tagEntity = Entity(
            id = ArchitectureId("nfc-tag:${tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty()}"),
            entityType = "NfcTag",
            attributes = mapOf(
                "tag_uid_hex" to tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty(),
                "tag_uid_dec" to tagValues[NfcEvidenceFields.TAG_UID_DEC].orEmpty(),
                "tech_list" to tagValues[NfcEvidenceFields.TECH_LIST].orEmpty()
            )
        )
        val ndefSupported = tagValues[NfcEvidenceFields.NDEF_SUPPORTED] == "true"
        val validation = ValidationRecord(
            passed = tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty().isNotBlank(),
            messages = if (tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty().isBlank()) {
                listOf("Tag UID was not exposed by Android.")
            } else {
                emptyList()
            }
        )
        val quality = QualityRecord(
            valid = validation.passed,
            metrics = mapOf(
                "ndef_supported" to ndefSupported.toString(),
                "record_count" to tagValues[NfcEvidenceFields.NDEF_RECORD_COUNT].orEmpty()
            )
        )
        val captureOutcome = CaptureOutcome(
            strategy = CaptureStrategy.Instant,
            status = CaptureOutcomeStatus.Success,
            message = "NFC tag discovered and decoded."
        )
        val evidence = EvidenceRecord(
            kind = EvidenceKind.Observation,
            phenomenon = "nfc.tag.state",
            values = tagValues,
            method = "android.nfc reader-mode tag discovery with NDEF decoding when available",
            temporalSemantics = TemporalSemantics.PointObservation,
            aggregationSemantics = AggregationSemantics.IdentifyingOnly,
            lineage = LineageSemantics.DeviceReported,
            provenance = provenance,
            captureOutcome = captureOutcome,
            quality = quality,
            validation = validation
        )
        val artifact = ArtifactRecord(
            kind = ArtifactKind.NfcTag,
            mediaType = "application/vnd.researchos.nfc-tag+json",
            values = tagValues,
            provenance = provenance
        )
        val execution = MethodExecutionRecord(
            id = provenance.executionId,
            methodId = methodId,
            methodVersion = methodVersion,
            layers = listOf(
                ResearchLayer.Activity,
                ResearchLayer.Session,
                ResearchLayer.Evidence,
                ResearchLayer.Artifact
            ),
            evidence = listOf(evidence),
            artifacts = listOf(artifact)
        )
        val methodRef = ArchitectureRef(
            id = ArchitectureId(methodId),
            type = methodObjectType,
            label = methodLabel
        )
        val signalRef = ArchitectureRef(
            id = signal.id,
            type = "Signal",
            label = signal.signalType
        )
        val observation = Observation(
            id = ArchitectureId(evidence.id),
            phenomenon = evidence.phenomenon,
            subject = ArchitectureRef(tagEntity.id, tagEntity.objectType, tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty()),
            values = tagValues,
            sourceSignal = signalRef,
            temporalContext = TemporalContext(
                eventTimeEpochMs = signal.temporalContext.eventTimeEpochMs,
                observationTimeEpochMs = evidence.provenance.observedAtEpochMs,
                executionTimeEpochMs = evidence.provenance.observedAtEpochMs,
                systemTimeEpochMs = evidence.provenance.observedAtEpochMs
            ),
            provenance = ProvenanceContext(
                provider = signal.provenance.provider,
                methodId = methodId,
                methodVersion = methodVersion,
                extra = evidence.provenance.extra
            )
        )
        val observationRef = ArchitectureRef(
            id = observation.id,
            type = "Observation",
            label = evidence.phenomenon
        )
        val transformation = Transformation(
            id = ArchitectureId(provenance.executionId),
            action = "interpret.nfc.signal",
            method = methodRef,
            inputs = listOf(signalRef),
            outputs = listOf(observationRef),
            status = if (validation.passed) TransformationStatus.Succeeded else TransformationStatus.Failed,
            diagnostics = quality.metrics,
            temporalContext = observation.temporalContext,
            provenance = observation.provenance
        )
        val executionRequest = As100ExecutionEngine.request(
            id = ArchitectureId(provenance.executionId),
            action = "read.nfc.tag",
            method = methodRef,
            signals = listOf(signal),
            temporalContext = observation.temporalContext
        )
        val executionResult = As100ExecutionEngine.complete(
            request = executionRequest,
            status = transformation.status,
            entities = listOf(tagEntity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = quality.metrics
        )
        return NfcReadEvidenceBundle(
            evidence = evidence,
            artifact = artifact,
            execution = execution,
            signal = signal,
            researchosObservation = observation,
            transformation = transformation,
            executionResult = executionResult
        )
    }

    fun readTag(tag: Tag, methodId: String, methodVersion: String): NfcReadEvidenceBundle =
        readTagSignal(
            tagSignal = AndroidNfcDeviceService.tagSignalFromTag(tag),
            methodId = methodId,
            methodVersion = methodVersion
        )

    fun writeTag(
        tag: Tag,
        request: NfcWriteRequest,
        methodId: String,
        methodVersion: String
    ): NfcWriteEvidenceBundle = writeTagSignal(
        tagSignal = AndroidNfcDeviceService.tagSignalFromTag(tag),
        request = request,
        methodId = methodId,
        methodVersion = methodVersion
    )

    fun writeTagSignal(
        tagSignal: NfcTagSignal,
        request: NfcWriteRequest,
        methodId: String,
        methodVersion: String,
        methodObjectType: String = "Method",
        methodLabel: String = "NFC Tag Write"
    ): NfcWriteEvidenceBundle {
        val tag = tagSignal.androidTag
        val signal = tagSignal.signal
        val provenance = ProvenanceRecord(
            methodId = methodId,
            methodVersion = methodVersion,
            provider = signal.provenance.provider
        )
        val record = buildRecord(request)
        val message = NdefMessage(arrayOf(record))
        val sizeBytes = message.toByteArray().size
        val writeResult = writeNdefMessage(tag, message, sizeBytes)
        val outcome = CaptureOutcome(
            strategy = CaptureStrategy.Manual,
            status = if (writeResult.first) CaptureOutcomeStatus.Success else CaptureOutcomeStatus.Unsupported,
            message = writeResult.second
        )
        val intervention = InterventionRecord(
            action = "nfc.ndef.write",
            target = tag.id.toHexString(),
            inputs = linkedMapOf(
                "record_type" to request.recordType,
                "value" to request.value,
                "mime_type" to request.mimeType,
                "language_code" to request.languageCode,
                "message_size_bytes" to sizeBytes.toString()
            ),
            outcome = outcome,
            provenance = provenance,
            validation = ValidationRecord(
                passed = writeResult.first,
                messages = if (writeResult.first) emptyList() else listOf(writeResult.second)
            )
        )
        val readBack = readTagSignal(
            tagSignal = tagSignal,
            methodId = methodId,
            methodVersion = methodVersion,
            methodObjectType = methodObjectType,
            methodLabel = methodLabel
        )
        val execution = readBack.execution.copy(
            id = provenance.executionId,
            methodId = methodId,
            methodVersion = methodVersion,
            interventions = listOf(intervention),
            diagnostics = mapOf(
                NfcWriteFields.WRITE_SUCCESS to writeResult.first.toString(),
                NfcWriteFields.WRITE_MESSAGE to writeResult.second
            )
        )
        return NfcWriteEvidenceBundle(
            intervention = intervention,
            postWriteRead = readBack.copy(execution = execution),
            writeSuccess = writeResult.first,
            writeMessage = writeResult.second,
            writeSizeBytes = sizeBytes
        )
    }

    private fun extractTagValues(tag: Tag): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        val ndef = Ndef.get(tag)
        val ndefMessage = readNdefMessage(ndef)
        val records = ndefMessage?.records?.asList() ?: emptyList()
        fields[NfcEvidenceFields.TAG_UID_HEX] = tag.id.toHexString()
        fields[NfcEvidenceFields.TAG_UID_DEC] = tag.id.toUnsignedLongString()
        fields[NfcEvidenceFields.TECH_LIST] = tag.techList.joinToString(",") { it.substringAfterLast('.') }
        fields[NfcEvidenceFields.NDEF_SUPPORTED] = (ndef != null).toString()
        fields[NfcEvidenceFields.NDEF_MESSAGE_SIZE_BYTES] = ndefMessage?.toByteArray()?.size?.toString().orEmpty()
        fields[NfcEvidenceFields.NDEF_MAX_SIZE_BYTES] = ndef?.maxSize?.toString().orEmpty()
        fields[NfcEvidenceFields.NDEF_IS_WRITABLE] = ndef?.isWritable?.toString().orEmpty()
        fields[NfcEvidenceFields.NDEF_CAN_MAKE_READ_ONLY] = ndef?.canMakeReadOnly()?.toString().orEmpty()
        fields[NfcEvidenceFields.NDEF_RECORD_COUNT] = records.size.toString()
        fields[NfcEvidenceFields.NDEF_TEXT] = records.mapNotNull { textFromRecord(it) }.joinToString(" | ")
        fields[NfcEvidenceFields.NDEF_URI] = records.mapNotNull { uriFromRecord(it) }.joinToString(" | ")
        fields[NfcEvidenceFields.NDEF_MIME_TYPES] = records.mapNotNull { mimeFromRecord(it) }.distinct().joinToString(",")
        fields[NfcEvidenceFields.NDEF_EXTERNAL_TYPES] = records.mapNotNull { externalTypeFromRecord(it) }.distinct().joinToString(",")
        fields[NfcEvidenceFields.NDEF_PAYLOAD_HEX_ALL] = records.joinToString("|") { it.payload.toHexString() }
        fields[NfcEvidenceFields.NDEF_PAYLOAD_UTF8_ALL] = records.joinToString(" | ") { it.payload.decodeUtf8Guess() }
        fields[NfcEvidenceFields.NDEF_FIRST_PAYLOAD_HEX] = records.firstOrNull()?.payload?.toHexString().orEmpty()
        fields[NfcEvidenceFields.NDEF_FIRST_PAYLOAD_UTF8] = records.firstOrNull()?.payload?.decodeUtf8Guess().orEmpty()
        fields[NfcEvidenceFields.NDEF_RECORDS_JSON] = recordsJson(records).toString()
        fields[NfcEvidenceFields.TAG_SUMMARY] = listOfNotNull(
            fields[NfcEvidenceFields.TAG_UID_HEX]?.takeIf { it.isNotBlank() }?.let { "uid=$it" },
            fields[NfcEvidenceFields.NDEF_TEXT]?.takeIf { it.isNotBlank() }?.let { "text=$it" },
            fields[NfcEvidenceFields.NDEF_URI]?.takeIf { it.isNotBlank() }?.let { "uri=$it" }
        ).joinToString("; ")
        return fields
    }

    private fun readNdefMessage(ndef: Ndef?): NdefMessage? {
        if (ndef == null) return null
        return try {
            if (!ndef.isConnected) ndef.connect()
            val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
            closeQuietly(ndef)
            message
        } catch (_: Exception) {
            closeQuietly(ndef)
            ndef.cachedNdefMessage
        }
    }

    private fun writeNdefMessage(tag: Tag, message: NdefMessage, sizeBytes: Int): Pair<Boolean, String> {
        val ndef = Ndef.get(tag)
        if (ndef == null) return false to "Tag does not expose NDEF technology. Formatting is a platform operation, not part of this method."
        return try {
            ndef.connect()
            when {
                !ndef.isWritable -> false to "Tag is NDEF but not writable."
                ndef.maxSize < sizeBytes -> false to "Tag too small. Need $sizeBytes bytes; tag maximum is ${ndef.maxSize} bytes."
                else -> {
                    ndef.writeNdefMessage(message)
                    true to "NDEF ${sizeBytes}-byte message written."
                }
            }
        } catch (e: Exception) {
            false to "NDEF write failed: ${e.message ?: e::class.java.simpleName}"
        } finally {
            closeQuietly(ndef)
        }
    }

    private fun buildRecord(request: NfcWriteRequest): NdefRecord {
        return when (request.recordType.lowercase(Locale.ROOT)) {
            "uri" -> NdefRecord.createUri(request.value)
            "mime" -> NdefRecord.createMime(
                request.mimeType.ifBlank { "text/plain" },
                request.value.toByteArray(Charsets.UTF_8)
            )
            "external" -> {
                val parts = request.mimeType.split(":", limit = 2)
                val domain = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "researchos"
                val type = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "value"
                NdefRecord.createExternal(domain, type, request.value.toByteArray(Charsets.UTF_8))
            }
            else -> NdefRecord.createTextRecord(request.languageCode.ifBlank { "en" }, request.value)
        }
    }

    private fun closeQuietly(tech: TagTechnology?) {
        try {
            tech?.close()
        } catch (_: Exception) {
            // ignore close errors
        }
    }

    private fun textFromRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN || !record.type.contentEquals(NdefRecord.RTD_TEXT)) return null
        val payload = record.payload ?: return null
        if (payload.isEmpty()) return ""
        val status = payload[0].toInt()
        val languageLength = status and 0x3F
        val charset = if ((status and 0x80) == 0) Charsets.UTF_8 else Charsets.UTF_16
        if (payload.size <= 1 + languageLength) return ""
        return String(payload, 1 + languageLength, payload.size - 1 - languageLength, charset)
    }

    private fun uriFromRecord(record: NdefRecord): String? {
        return try {
            record.toUri()?.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun mimeFromRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_MIME_MEDIA) return null
        return String(record.type ?: ByteArray(0), Charsets.US_ASCII)
    }

    private fun externalTypeFromRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_EXTERNAL_TYPE) return null
        return String(record.type ?: ByteArray(0), Charsets.US_ASCII)
    }

    private fun recordsJson(records: List<NdefRecord>): JSONArray {
        val array = JSONArray()
        records.forEachIndexed { index, record ->
            array.put(
                JSONObject(
                    linkedMapOf<String, Any?>(
                        "index" to index,
                        "tnf" to record.tnf.toInt(),
                        "type_hex" to record.type.toHexString(),
                        "type_utf8" to record.type.decodeUtf8Guess(),
                        "id_hex" to record.id.toHexString(),
                        "payload_hex" to record.payload.toHexString(),
                        "payload_utf8" to record.payload.decodeUtf8Guess(),
                        "text" to textFromRecord(record).orEmpty(),
                        "uri" to uriFromRecord(record).orEmpty(),
                        "mime_type" to mimeFromRecord(record).orEmpty(),
                        "external_type" to externalTypeFromRecord(record).orEmpty()
                    )
                )
            )
        }
        return array
    }
}

private fun ByteArray?.toHexString(): String =
    this?.joinToString(separator = "") { byte -> "%02X".format(byte) }.orEmpty()

private fun ByteArray?.toUnsignedLongString(): String {
    val bytes = this ?: return ""
    var value = 0L
    bytes.forEach { value = (value shl 8) + (it.toInt() and 0xff) }
    return value.toString()
}

private fun ByteArray?.decodeUtf8Guess(): String =
    runCatching { String(this ?: ByteArray(0), Charsets.UTF_8) }.getOrDefault("")
