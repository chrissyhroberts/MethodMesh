package com.example.methodmesh.modules.referencelibrary

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
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

object ReferenceLibraryFields {
    const val STATUS = "library_status"
    const val DOCUMENT_ID = "library_document_id"
    const val DOCUMENT_TITLE = "library_document_title"
    const val DOCUMENT_URI = "library_document_uri"
    const val DOCUMENT_MIME = "library_document_mime"
    const val ARTIFACT_REF = "library_artifact_ref"
    const val SHELF = "library_shelf"
    const val SOURCE = "library_source"
    const val VERSION = "library_version"
    const val ERROR = "library_error"

    val outputs = listOf(STATUS, DOCUMENT_ID, DOCUMENT_TITLE, DOCUMENT_URI, DOCUMENT_MIME, ARTIFACT_REF, SHELF, SOURCE, VERSION, ERROR)
}

object As100ReferenceLibraryMethod : As100Method {
    const val ID = "reference.library.open"
    private const val VERSION = "0.3.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Reference library")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "Reference library",
        version = VERSION,
        description = "Select a locally available reference document.",
        outputs = ReferenceLibraryFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Reference", "status" to "Development", "maturity" to "DEVELOPMENT", "connectivity" to "OFFLINE")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val requestedId = (request.context["document_id"] ?: request.context["input_document_id"]).orEmpty()
        val values = failure(
            documentId = requestedId,
            shelf = request.context["shelf"] ?: request.context["input_shelf"] ?: "all",
            status = if (requestedId.isBlank()) "no_selection" else "document_not_resolved",
            error = if (requestedId.isBlank()) {
                "No reference document was selected."
            } else {
                "The requested document ID must be resolved by the device-local Reference Library."
            }
        )
        return result(request, values, InvocationContext.from(request.context))
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[ReferenceLibraryFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("library-document:${values[ReferenceLibraryFields.DOCUMENT_ID].orEmpty().ifBlank { System.currentTimeMillis().toString() }}"),
            "ReferenceDocument",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.reference.library", ID, VERSION)
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
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(ReferenceLibraryFields.ERROR to values[ReferenceLibraryFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun selected(document: LibraryDocument): Map<String, String> = linkedMapOf(
        ReferenceLibraryFields.STATUS to "succeeded",
        ReferenceLibraryFields.DOCUMENT_ID to document.id,
        ReferenceLibraryFields.DOCUMENT_TITLE to document.title,
        ReferenceLibraryFields.DOCUMENT_URI to document.uri,
        ReferenceLibraryFields.DOCUMENT_MIME to document.mimeType,
        ReferenceLibraryFields.ARTIFACT_REF to "artifact://library.${document.id}",
        ReferenceLibraryFields.SHELF to document.shelf,
        ReferenceLibraryFields.SOURCE to document.source,
        ReferenceLibraryFields.VERSION to document.version,
        ReferenceLibraryFields.ERROR to ""
    )

    fun failure(documentId: String, shelf: String, status: String, error: String): Map<String, String> = linkedMapOf(
        ReferenceLibraryFields.STATUS to status,
        ReferenceLibraryFields.DOCUMENT_ID to documentId,
        ReferenceLibraryFields.DOCUMENT_TITLE to "",
        ReferenceLibraryFields.DOCUMENT_URI to "",
        ReferenceLibraryFields.DOCUMENT_MIME to "",
        ReferenceLibraryFields.ARTIFACT_REF to "",
        ReferenceLibraryFields.SHELF to shelf,
        ReferenceLibraryFields.SOURCE to "",
        ReferenceLibraryFields.VERSION to "",
        ReferenceLibraryFields.ERROR to error
    )
}
