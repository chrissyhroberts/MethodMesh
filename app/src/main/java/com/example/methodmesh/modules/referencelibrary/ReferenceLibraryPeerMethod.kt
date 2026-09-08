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

object ReferenceLibraryPeerFields {
    const val STATUS = "library_peer_status"
    const val NETWORK_MODE = "library_peer_network_mode"
    const val SESSION_ID = "library_peer_session_id"
    const val UPLOADED_COUNT = "library_peer_uploaded_count"
    const val UPDATED_COUNT = "library_peer_updated_count"
    const val REMOVED_COUNT = "library_peer_removed_count"
    const val SHELF_CHANGES_COUNT = "library_peer_shelf_changes_count"
    const val BYTES_RECEIVED = "library_peer_bytes_received"
    const val HTTP_REQUEST_COUNT = "library_peer_http_request_count"
    const val STARTED_TIME_ISO = "library_peer_started_time_iso"
    const val FINISHED_TIME_ISO = "library_peer_finished_time_iso"
    const val DURATION_MS = "library_peer_duration_ms"
    const val ERROR = "library_peer_error"

    val outputs = listOf(
        STATUS,
        NETWORK_MODE,
        SESSION_ID,
        UPLOADED_COUNT,
        UPDATED_COUNT,
        REMOVED_COUNT,
        SHELF_CHANGES_COUNT,
        BYTES_RECEIVED,
        HTTP_REQUEST_COUNT,
        STARTED_TIME_ISO,
        FINISHED_TIME_ISO,
        DURATION_MS,
        ERROR
    )
}

object As100ReferenceLibraryPeerMethod : As100Method {
    const val ID = "reference.library.peer.manage"
    private const val VERSION = "0.3.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Nearby reference library manager")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "Manage reference library nearby",
        version = VERSION,
        description = "Run a time-limited local web manager for batch upload and library maintenance over a local-only hotspot or the current Wi-Fi network.",
        outputs = ReferenceLibraryPeerFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Reference",
            "status" to "Development",
            "maturity" to "DEVELOPMENT",
            "connectivity" to "OFFLINE",
            "transport" to "local HTTP over operator-approved local network",
            "security" to "time-limited session, unguessable session token, no cloud service"
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
            request = request,
            values = values(
                status = "unsupported_without_android_ui",
                networkMode = request.context.value("network_mode").ifBlank { "local_hotspot" },
                sessionId = "",
                stats = ReferenceLibraryPeerStats(),
                startedTimeIso = "",
                finishedTimeIso = "",
                durationMs = 0L,
                error = "Nearby library management requires the Android capability screen."
            ),
            invocation = InvocationContext.from(request.context)
        )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[ReferenceLibraryPeerFields.STATUS] == "completed"
        val entity = Entity(
            ArchitectureId("library-peer:${values[ReferenceLibraryPeerFields.SESSION_ID].orEmpty().ifBlank { System.currentTimeMillis().toString() }}"),
            "ReferenceLibraryPeerSession",
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
            diagnostics = if (ok) emptyMap() else mapOf(
                ReferenceLibraryPeerFields.ERROR to values[ReferenceLibraryPeerFields.ERROR].orEmpty()
            )
        ).withInvocationContext(invocation)
    }

    fun values(
        status: String,
        networkMode: String,
        sessionId: String,
        stats: ReferenceLibraryPeerStats,
        startedTimeIso: String,
        finishedTimeIso: String,
        durationMs: Long,
        error: String = ""
    ): Map<String, String> = linkedMapOf(
        ReferenceLibraryPeerFields.STATUS to status,
        ReferenceLibraryPeerFields.NETWORK_MODE to networkMode,
        ReferenceLibraryPeerFields.SESSION_ID to sessionId,
        ReferenceLibraryPeerFields.UPLOADED_COUNT to stats.uploadedCount.toString(),
        ReferenceLibraryPeerFields.UPDATED_COUNT to stats.updatedCount.toString(),
        ReferenceLibraryPeerFields.REMOVED_COUNT to stats.removedCount.toString(),
        ReferenceLibraryPeerFields.SHELF_CHANGES_COUNT to stats.shelfChangesCount.toString(),
        ReferenceLibraryPeerFields.BYTES_RECEIVED to stats.bytesReceived.toString(),
        ReferenceLibraryPeerFields.HTTP_REQUEST_COUNT to stats.httpRequestCount.toString(),
        ReferenceLibraryPeerFields.STARTED_TIME_ISO to startedTimeIso,
        ReferenceLibraryPeerFields.FINISHED_TIME_ISO to finishedTimeIso,
        ReferenceLibraryPeerFields.DURATION_MS to durationMs.coerceAtLeast(0L).toString(),
        ReferenceLibraryPeerFields.ERROR to error
    )

    private fun Map<String, String>.value(key: String): String =
        (this[key] ?: this["input_$key"]).orEmpty()
}
