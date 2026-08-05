package com.example.researchos.modules.documentscanner

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.Entity
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.core.researchos.MethodObjectType
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.settings.SettingsState
import java.time.Instant

object DocumentScannerFields {
    const val STATUS = "document_scan_status"
    const val PAGE_COUNT = "document_scan_page_count"
    const val PAGE_IMAGE_URIS_JSON = "document_scan_page_image_uris_json"
    const val SCANNER_PDF_URI = "document_scan_pdf_uri"
    const val SEARCHABLE_PDF_URI = "document_scan_searchable_pdf_uri"
    const val OCR_TEXT = "document_scan_ocr_text"
    const val OCR_TEXT_FILE_URI = "document_scan_ocr_text_file_uri"
    const val OCR_PAGE_COUNT = "document_scan_ocr_page_count"
    const val SCANNER_MODE = "document_scan_mode"
    const val GALLERY_IMPORT_ALLOWED = "document_scan_gallery_import_allowed"
    const val PAGE_LIMIT = "document_scan_page_limit"
    const val SCANNED_TIME_ISO = "document_scan_time_iso"
    const val ERROR = "document_scan_error"

    val outputs = listOf(
        STATUS,
        PAGE_COUNT,
        PAGE_IMAGE_URIS_JSON,
        SCANNER_PDF_URI,
        SEARCHABLE_PDF_URI,
        OCR_TEXT,
        OCR_TEXT_FILE_URI,
        OCR_PAGE_COUNT,
        SCANNER_MODE,
        GALLERY_IMPORT_ALLOWED,
        PAGE_LIMIT,
        SCANNED_TIME_ISO,
        ERROR
    )
}

object As100DocumentScannerMethod : As100Method {
    const val ID = "document.scan"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Document scan")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Document scanner",
        version = VERSION,
        description = "Scan paper documents using ML Kit document scanner, OCR pages, and return PDF/text attachments.",
        outputs = DocumentScannerFields.outputs,
        graphOutputs = listOf("document.scan"),
        parameters = mapOf("category" to "Recognition")
    )
    override val contract = MethodContract(
        method = ref,
        requiredContext = emptyList(),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(
            request,
            TransformationStatus.Unsupported,
            diagnostics = mapOf("reason" to "Document scanning requires the Android ML Kit document scanner boundary.")
        )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[DocumentScannerFields.STATUS] == "succeeded"
        val entity = Entity(ArchitectureId("document-scan:${System.currentTimeMillis()}"), "DocumentScan", temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("mlkit.document_scanner", ID, VERSION)
        val observation = Observation(
            phenomenon = "document.scan",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values + (DocumentScannerFields.SCANNED_TIME_ISO to (values[DocumentScannerFields.SCANNED_TIME_ISO] ?: Instant.now().toString())),
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf("document_scan_error" to (values[DocumentScannerFields.ERROR] ?: "Document scan failed."))
        ).withInvocationContext(invocation)
    }
}
