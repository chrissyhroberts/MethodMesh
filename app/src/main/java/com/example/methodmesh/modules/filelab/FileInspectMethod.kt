package com.example.methodmesh.modules.filelab

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState
import java.io.File
import java.time.Instant

object FileInspectFields {
    const val SUMMARY = "filelab_inspection_summary"
    const val SOURCE_NAME = "filelab_source_name"
    const val SOURCE_URI = "filelab_source_uri"
    const val FORMAT_ID = "filelab_format_id"
    const val FORMAT_NAME = "filelab_format_name"
    const val MIME_TYPE = "filelab_mime_type"
    const val CONFIDENCE = "filelab_confidence"
    const val SIZE_BYTES = "filelab_size_bytes"
    const val SHA256 = "filelab_sha256"
    const val INSPECTION_DEPTH = "filelab_inspection_depth"
    const val FORMAT_DESCRIPTION = "filelab_format_description"
    const val FORMAT_TECHNICAL_DESCRIPTION = "filelab_format_technical_description"
    const val FORMAT_TYPICAL_USES = "filelab_format_typical_uses"
    const val FORMAT_COMMON_PRODUCERS = "filelab_format_common_producers"
    const val FORMAT_SUGGESTED_APPS = "filelab_format_suggested_apps"
    const val RELATED_FORMATS = "filelab_related_formats"
    const val FORMAT_CAUTIONS = "filelab_format_cautions"
    const val FORMAT_KNOWLEDGE_JSON = "filelab_format_knowledge_json"
    const val FACTS_JSON = "filelab_facts_json"
    const val WARNINGS_JSON = "filelab_warnings_json"
    const val EVIDENCE_JSON = "filelab_evidence_json"
    const val AVAILABLE_ACTIONS = "filelab_available_actions"
    const val INSPECTED_TIME_ISO = "filelab_inspected_time_iso"
    const val STATUS = "filelab_inspect_status"
    const val ERROR = "filelab_inspect_error"

    val outputs = listOf(
        SUMMARY, SOURCE_NAME, SOURCE_URI, FORMAT_ID, FORMAT_NAME, MIME_TYPE,
        CONFIDENCE, SIZE_BYTES, SHA256, INSPECTION_DEPTH, FORMAT_DESCRIPTION,
        FORMAT_TECHNICAL_DESCRIPTION, FORMAT_TYPICAL_USES, FORMAT_COMMON_PRODUCERS,
        FORMAT_SUGGESTED_APPS, RELATED_FORMATS, FORMAT_CAUTIONS, FORMAT_KNOWLEDGE_JSON, FACTS_JSON, WARNINGS_JSON, EVIDENCE_JSON, AVAILABLE_ACTIONS, INSPECTED_TIME_ISO, STATUS, ERROR
    )
}

object As100FileInspectMethod : As100Method {
    const val ID = "file.inspect"
    const val VERSION = "0.7.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Inspect file")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Inspect file",
        version = VERSION,
        description = "Identify a file from content, calculate SHA-256, inspect safe metadata and expose format-specific facts without changing the source.",
        inputs = listOf("source_uri", "source_name"),
        outputs = FileInspectFields.outputs,
        graphOutputs = listOf("file.inspection"),
        parameters = mapOf(
            "category" to "File Lab",
            "status" to "Development",
            "maturity" to "Development",
            "connectivity" to "Offline",
            "interactive" to "true",
            "core_return" to FileInspectFields.SUMMARY,
            "odk_metadata_return" to "methodmesh_full_json",
            "source_uri" to "optional caller-supplied content URI; native picker is used when absent"
        )
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val raw = request.context["source_uri"] ?: request.context["input_source_uri"] ?: request.context["source_path"] ?: request.context["input_source_path"]
        val file = raw?.removePrefix("file://")?.let(::File)
        if (file == null || !file.isFile) {
            return result(
                request,
                failureValues("Interactive Android file access is required for content URIs."),
                success = false
            )
        }
        return runCatching {
            val inspection = FileLabEngine.inspect(file.name, file.length()) { file.inputStream() }
            result(request, values(inspection, acquiredSourceUri = ""), success = true)
        }.getOrElse { error -> result(request, failureValues(error.message ?: "Inspection failed"), success = false) }
    }

    fun values(inspection: FileInspection, acquiredSourceUri: String): Map<String, String> = linkedMapOf(
        FileInspectFields.SUMMARY to "${inspection.formatName} — ${inspection.knowledge?.description.orEmpty().ifBlank { "Recognised file format." }}",
        FileInspectFields.SOURCE_NAME to inspection.displayName,
        FileInspectFields.SOURCE_URI to acquiredSourceUri,
        FileInspectFields.FORMAT_ID to inspection.formatId,
        FileInspectFields.FORMAT_NAME to inspection.formatName,
        FileInspectFields.MIME_TYPE to inspection.mimeType.orEmpty(),
        FileInspectFields.CONFIDENCE to inspection.confidence.toString(),
        FileInspectFields.SIZE_BYTES to inspection.sizeBytes.toString(),
        FileInspectFields.SHA256 to inspection.sha256,
        FileInspectFields.INSPECTION_DEPTH to inspection.inspectionDepth.name.lowercase(),
        FileInspectFields.FORMAT_DESCRIPTION to inspection.knowledge?.description.orEmpty(),
        FileInspectFields.FORMAT_TECHNICAL_DESCRIPTION to inspection.knowledge?.technicalIdentity.orEmpty(),
        FileInspectFields.FORMAT_TYPICAL_USES to inspection.knowledge?.typicalUses.orEmpty(),
        FileInspectFields.FORMAT_COMMON_PRODUCERS to inspection.knowledge?.commonProducers.orEmpty().joinToString("|"),
        FileInspectFields.FORMAT_SUGGESTED_APPS to inspection.knowledge?.typicalSoftware.orEmpty().joinToString("|"),
        FileInspectFields.RELATED_FORMATS to inspection.knowledge?.relatedFormats.orEmpty().joinToString("|"),
        FileInspectFields.FORMAT_CAUTIONS to inspection.knowledge?.caution.orEmpty(),
        FileInspectFields.FORMAT_KNOWLEDGE_JSON to FileLabJson.knowledgeJson(inspection.knowledge),
        FileInspectFields.FACTS_JSON to FileLabJson.factsJson(inspection),
        FileInspectFields.WARNINGS_JSON to FileLabJson.stringListJson(inspection.warnings),
        FileInspectFields.EVIDENCE_JSON to FileLabJson.evidenceJson(inspection),
        FileInspectFields.AVAILABLE_ACTIONS to inspection.availableActions.joinToString("|"),
        FileInspectFields.INSPECTED_TIME_ISO to Instant.now().toString(),
        FileInspectFields.STATUS to "succeeded",
        FileInspectFields.ERROR to ""
    )

    fun failureValues(message: String): Map<String, String> = linkedMapOf(
        FileInspectFields.SUMMARY to "File inspection failed",
        FileInspectFields.INSPECTED_TIME_ISO to Instant.now().toString(),
        FileInspectFields.STATUS to "failed",
        FileInspectFields.ERROR to message
    )

    fun result(request: ExecutionRequest, values: Map<String, String>, success: Boolean): ExecutionResult =
        FileLabMethodSupport.complete(request, ref, ID, VERSION, "file.inspection", values, success, values[FileInspectFields.ERROR])
}
