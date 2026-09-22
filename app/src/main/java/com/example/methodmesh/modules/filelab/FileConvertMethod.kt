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
import java.time.Instant

object FileConvertFields {
    const val SUMMARY = "filelab_conversion_summary"
    const val SOURCE_NAME = "filelab_source_name"
    const val SOURCE_FORMAT = "filelab_source_format"
    const val TARGET_FORMAT = "filelab_target_format"
    const val OUTPUT_NAME = "filelab_output_name"
    const val OUTPUT_URI = "filelab_output_uri"
    const val OUTPUT_MIME_TYPE = "filelab_output_mime_type"
    const val OUTPUT_SIZE_BYTES = "filelab_output_size_bytes"
    const val OUTPUT_SHA256 = "filelab_output_sha256"
    const val WARNINGS_JSON = "filelab_conversion_warnings_json"
    const val CONVERTED_TIME_ISO = "filelab_converted_time_iso"
    const val STATUS = "filelab_convert_status"
    const val ERROR = "filelab_convert_error"

    val outputs = listOf(
        SUMMARY, SOURCE_NAME, SOURCE_FORMAT, TARGET_FORMAT,
        OUTPUT_NAME, OUTPUT_URI, OUTPUT_MIME_TYPE, OUTPUT_SIZE_BYTES,
        OUTPUT_SHA256, WARNINGS_JSON, CONVERTED_TIME_ISO, STATUS, ERROR
    )
}

object As100FileConvertMethod : As100Method {
    const val ID = "file.convert"
    const val VERSION = "0.7.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Convert file")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Convert file",
        version = VERSION,
        description = "Convert a supported local file using an explicit executable route. Supports CBZ/PDF, common text-to-PDF and common image-to-PDF conversions.",
        inputs = listOf("source_uri", "source_name", "target_format", "max_dimension", "jpeg_quality"),
        outputs = FileConvertFields.outputs,
        graphOutputs = listOf("file.conversion"),
        parameters = mapOf(
            "category" to "File Lab",
            "status" to "Development",
            "maturity" to "Development",
            "connectivity" to "Offline",
            "interactive" to "true",
            "core_return" to FileConvertFields.OUTPUT_URI,
            "odk_metadata_return" to "methodmesh_full_json",
            "implemented_routes" to "cbz->pdf|pdf->cbz|text-like->pdf|png/jpeg/webp/bmp/gif->pdf"
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
        val existingOutput = request.context[FileConvertFields.OUTPUT_URI]
        if (!existingOutput.isNullOrBlank()) {
            val values = FileConvertFields.outputs.associateWith { request.context[it].orEmpty() }
            return result(request, values, success = request.context[FileConvertFields.STATUS] != "failed")
        }
        return result(
            request,
            failureValues("Interactive Android file access is required for conversion."),
            success = false
        )
    }

    fun values(sourceName: String, artifact: ConversionArtifact): Map<String, String> = linkedMapOf(
        FileConvertFields.SUMMARY to "${artifact.sourceFormat.uppercase()} → ${artifact.targetFormat.uppercase()} · ${artifact.outputSizeBytes} bytes",
        FileConvertFields.SOURCE_NAME to sourceName,
        FileConvertFields.SOURCE_FORMAT to artifact.sourceFormat,
        FileConvertFields.TARGET_FORMAT to artifact.targetFormat,
        FileConvertFields.OUTPUT_NAME to artifact.outputName,
        FileConvertFields.OUTPUT_URI to artifact.outputUri,
        FileConvertFields.OUTPUT_MIME_TYPE to artifact.outputMimeType,
        FileConvertFields.OUTPUT_SIZE_BYTES to artifact.outputSizeBytes.toString(),
        FileConvertFields.OUTPUT_SHA256 to artifact.outputSha256,
        FileConvertFields.WARNINGS_JSON to FileLabJson.stringListJson(artifact.warnings),
        FileConvertFields.CONVERTED_TIME_ISO to Instant.now().toString(),
        FileConvertFields.STATUS to "succeeded",
        FileConvertFields.ERROR to ""
    )

    fun failureValues(message: String): Map<String, String> = linkedMapOf(
        FileConvertFields.SUMMARY to "File conversion failed",
        FileConvertFields.CONVERTED_TIME_ISO to Instant.now().toString(),
        FileConvertFields.STATUS to "failed",
        FileConvertFields.ERROR to message
    )

    fun result(request: ExecutionRequest, values: Map<String, String>, success: Boolean): ExecutionResult =
        FileLabMethodSupport.complete(request, ref, ID, VERSION, "file.conversion", values, success, values[FileConvertFields.ERROR])
}
