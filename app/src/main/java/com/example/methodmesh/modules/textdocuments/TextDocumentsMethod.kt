package com.example.methodmesh.modules.textdocuments

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState

object TextDocumentFields {
    const val STATUS = "document_status"
    const val TEXT = "document_text"
    const val TITLE = "document_title"
    const val FORMAT = "document_format"
    const val ERROR = "document_error"
    val outputs = listOf(STATUS, TEXT, TITLE, FORMAT, ERROR)
}

object As100TextDocumentsMethod : As100Method {
    const val ID = "document.text.open"
    private const val VERSION = "0.11.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Text documents")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "Text documents",
        version = VERSION,
        description = "Create, open, edit, save, copy and share plain text, Markdown, JSON and JSON Lines documents.",
        outputs = TextDocumentFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Documents",
            "status" to "Development",
            "maturity" to "Development",
            "connectivity" to "OFFLINE"
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
    ) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val text = request.context.value("document_text").orEmpty()
        val title = request.context.value("document_title").orEmpty().ifBlank { "document.txt" }
        val format = DocumentFormat.fromContract(request.context.value("document_format")).contractValue
        val values = if (text.isNotEmpty()) {
            success(text, title, format)
        } else {
            failure(title, format, "Interactive document selection or editing is required.")
        }
        return result(request, values, InvocationContext.from(request.context))
    }

    fun success(text: String, title: String, format: String): Map<String, String> = linkedMapOf(
        TextDocumentFields.STATUS to "succeeded",
        TextDocumentFields.TEXT to text,
        TextDocumentFields.TITLE to title,
        TextDocumentFields.FORMAT to format,
        TextDocumentFields.ERROR to ""
    )

    fun failure(title: String, format: String, error: String): Map<String, String> = linkedMapOf(
        TextDocumentFields.STATUS to "failed",
        TextDocumentFields.TEXT to "",
        TextDocumentFields.TITLE to title,
        TextDocumentFields.FORMAT to format,
        TextDocumentFields.ERROR to error
    )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[TextDocumentFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.textdocuments", ID, VERSION)
        val observation = Observation(
            phenomenon = ID,
            subject = null,
            values = values,
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
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(TextDocumentFields.ERROR to values[TextDocumentFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    private fun Map<String, String>.value(key: String): String? =
        (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }
}
