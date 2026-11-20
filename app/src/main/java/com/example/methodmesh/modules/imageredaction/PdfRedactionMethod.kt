package com.example.methodmesh.modules.imageredaction

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState

object PdfRedactionFields {
    const val STATUS = "pdf_redaction_status"
    const val URI = "redacted_pdf_uri"
    const val NAME = "redacted_pdf_name"
    const val SHA256 = "redacted_pdf_sha256"
    const val PAGE_COUNT = "pdf_redaction_page_count"
    const val REGION_COUNT = "pdf_redaction_region_count"
    const val REGIONS = "pdf_redaction_regions_json"
    const val MODE = "pdf_redaction_mode"
    const val DPI = "pdf_redaction_resolution_dpi"
    const val CREATED = "pdf_redaction_created_time_iso"
    const val ERROR = "pdf_redaction_error"
    val outputs = listOf(STATUS, URI, NAME, SHA256, PAGE_COUNT, REGION_COUNT, REGIONS, MODE, DPI, CREATED, ERROR)
}

object As100PdfRedactionMethod : As100Method {
    const val ID = "pdf.redact"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Redact document / PDF")
    override val descriptor = MethodDescriptor(id = ArchitectureId(ID), methodType = MethodObjectType.SignalInterpreter,
        name = "Redact document / PDF", version = VERSION,
        description = "Scan a paper document or choose a PDF, remove rectangular regions and return a new image-only PDF. Original content/objects are not copied.",
        outputs = PdfRedactionFields.outputs, graphOutputs = listOf("pdf.redacted"),
        parameters = mapOf("category" to "Document", "status" to "Development", "connectivity" to "OFFLINE"))
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, mapOf(PdfRedactionFields.STATUS to "failed", PdfRedactionFields.ERROR to
            "PDF redaction requires the interactive screen. Open the PDF and mark the regions to remove."), InvocationContext.from(request.context))

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[PdfRedactionFields.STATUS] == "succeeded" && values[PdfRedactionFields.URI].orEmpty().isNotBlank()
        val status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed
        val provenance = ProvenanceContext("methodmesh.pdf_redaction", ID, VERSION)
        val observation = Observation(phenomenon = "pdf.redacted", subject = null, values = values,
            temporalContext = request.temporalContext, provenance = provenance)
        val transformation = Transformation(action = ID, method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)), status = status,
            temporalContext = request.temporalContext, provenance = provenance)
        return As100ExecutionEngine.complete(request = request, status = status, observations = listOf(observation),
            transformations = listOf(transformation), diagnostics = if (ok) emptyMap() else
                mapOf(PdfRedactionFields.ERROR to values[PdfRedactionFields.ERROR].orEmpty())).withInvocationContext(invocation)
    }
}
