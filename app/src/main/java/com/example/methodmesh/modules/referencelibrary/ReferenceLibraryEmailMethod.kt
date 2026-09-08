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
import org.json.JSONArray

object ReferenceLibraryEmailFields {
    const val STATUS = "library_email_status"
    const val RECIPIENT = "library_email_recipient"
    const val SUBJECT = "library_email_subject"
    const val DOCUMENT_IDS_JSON = "library_email_document_ids_json"
    const val ATTACHMENT_URIS_JSON = "library_email_attachment_uris_json"
    const val ATTACHMENT_COUNT = "library_email_attachment_count"
    const val MISSING_DOCUMENT_IDS_JSON = "library_email_missing_document_ids_json"
    const val HANDOFF = "library_email_handoff"
    const val USER_CONFIRMED_SENT = "library_email_user_confirmed_sent"
    const val DELIVERY_CONFIRMED = "library_email_delivery_confirmed"
    const val ERROR = "library_email_error"

    val outputs = listOf(
        STATUS,
        RECIPIENT,
        SUBJECT,
        DOCUMENT_IDS_JSON,
        ATTACHMENT_URIS_JSON,
        ATTACHMENT_COUNT,
        MISSING_DOCUMENT_IDS_JSON,
        HANDOFF,
        USER_CONFIRMED_SENT,
        DELIVERY_CONFIRMED,
        ERROR
    )
}

object As100ReferenceLibraryEmailMethod : As100Method {
    const val ID = "reference.library.email"
    private const val VERSION = "0.2.6"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Reference library email handoff")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "Email library documents",
        version = VERSION,
        description = "Prepare an email with library or piped document attachments and hand it to an installed mail app.",
        outputs = ReferenceLibraryEmailFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Reference",
            "status" to "Development",
            "delivery_semantics" to "user-visible Android mail handoff; MethodMesh does not claim that mail was sent or delivered"
        )
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(
            request,
            values(
                status = "unsupported_without_android_ui",
                recipient = request.context.value("recipient"),
                subject = request.context.value("subject"),
                documentIds = parseIds(request.context.value("document_ids")),
                attachmentUris = parseUris(request.context.value("attachment_uris")),
                missingDocumentIds = emptyList(),
                handoff = "not_started",
                userConfirmedSent = false,
                error = "Email handoff requires the Android capability screen."
            ),
            InvocationContext.from(request.context)
        )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[ReferenceLibraryEmailFields.STATUS] == "user_confirmed_sent"
        val entity = Entity(
            ArchitectureId("library-email:${System.currentTimeMillis()}"),
            "ReferenceDocumentEmailHandoff",
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
            diagnostics = if (ok) emptyMap() else mapOf(ReferenceLibraryEmailFields.ERROR to values[ReferenceLibraryEmailFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun values(
        status: String,
        recipient: String,
        subject: String,
        documentIds: List<String>,
        attachmentUris: List<String>,
        missingDocumentIds: List<String>,
        handoff: String,
        userConfirmedSent: Boolean,
        error: String = ""
    ): Map<String, String> = linkedMapOf(
        ReferenceLibraryEmailFields.STATUS to status,
        ReferenceLibraryEmailFields.RECIPIENT to recipient,
        ReferenceLibraryEmailFields.SUBJECT to subject,
        ReferenceLibraryEmailFields.DOCUMENT_IDS_JSON to JSONArray(documentIds).toString(),
        ReferenceLibraryEmailFields.ATTACHMENT_URIS_JSON to JSONArray(attachmentUris).toString(),
        ReferenceLibraryEmailFields.ATTACHMENT_COUNT to attachmentUris.size.toString(),
        ReferenceLibraryEmailFields.MISSING_DOCUMENT_IDS_JSON to JSONArray(missingDocumentIds).toString(),
        ReferenceLibraryEmailFields.HANDOFF to handoff,
        ReferenceLibraryEmailFields.USER_CONFIRMED_SENT to userConfirmedSent.toString(),
        ReferenceLibraryEmailFields.DELIVERY_CONFIRMED to "false",
        ReferenceLibraryEmailFields.ERROR to error
    )

    fun parseIds(raw: String): List<String> = parseList(raw, allowComma = true)

    fun parseUris(raw: String): List<String> = parseList(raw, allowComma = false)

    private fun parseList(raw: String, allowComma: Boolean): List<String> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return runCatching {
                val array = JSONArray(trimmed)
                buildList {
                    for (i in 0 until array.length()) {
                        array.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
                    }
                }.distinct()
            }.getOrElse { emptyList() }
        }
        val delimiters = if (allowComma) charArrayOf(',', ';', '|', '\n') else charArrayOf(';', '|', '\n')
        return trimmed
            .split(*delimiters)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun Map<String, String>.value(key: String): String =
        (this[key] ?: this["input_$key"]).orEmpty()
}
