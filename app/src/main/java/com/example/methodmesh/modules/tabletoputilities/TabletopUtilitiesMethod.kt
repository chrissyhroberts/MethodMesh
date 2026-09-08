package com.example.methodmesh.modules.tabletoputilities

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
import org.json.JSONObject
import java.time.Instant

object TabletopUtilitiesFields {
    const val STATUS = "tabletop_status"
    const val RESULT = "tabletop_result"
    const val WORKSPACE_ID = "tabletop_workspace_id"
    const val WORKSPACE_NAME = "tabletop_workspace_name"
    const val OPERATION = "tabletop_operation"
    const val ROUND = "tabletop_round"
    const val CURRENT_TURN = "tabletop_current_turn"
    const val ACTIVE_SESSION = "tabletop_active_session"
    const val EVENT_ID = "tabletop_event_id"
    const val STATE_JSON = "tabletop_state_json"
    const val AUDIT_JSON = "tabletop_audit_json"
    const val ERROR = "tabletop_error"

    val outputs = listOf(
        STATUS,
        RESULT,
        WORKSPACE_ID,
        WORKSPACE_NAME,
        OPERATION,
        ROUND,
        CURRENT_TURN,
        ACTIVE_SESSION,
        EVENT_ID,
        STATE_JSON,
        AUDIT_JSON,
        ERROR
    )
}

object As100TabletopUtilitiesMethod : As100Method {
    const val ID = "tabletop.state.manage"
    private const val VERSION = "0.1.2"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Tabletop state")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Tabletop utilities",
        version = VERSION,
        description = "Open, inspect or mutate one persistent tabletop game workspace.",
        outputs = TabletopUtilitiesFields.outputs,
        graphOutputs = listOf("tabletop.state"),
        parameters = mapOf(
            "category" to "Development",
            "status" to "Development",
            "offline" to "true",
            "stateful" to "true"
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
    ): ExecutionRequest = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult {
        val settings = settingsState?.asMap()?.mapValues { it.value.toString() } ?: request.context
        val values = if (TabletopUtilitiesRepository.isInitialised()) {
            runOperation(settings, source = transport ?: "runtime")
        } else {
            failureValues(
                operation = settings.value("operation") ?: "snapshot",
                workspaceId = settings.value("workspace_id").orEmpty(),
                error = "Tabletop persistence is not initialised. Stateful runs should use the module capability screen/runtime boundary."
            )
        }
        return result(request, values, InvocationContext.from(request.context))
    }

    fun runOperation(settings: Map<String, String>, source: String = "external"): Map<String, String> {
        val operation = settings.value("operation") ?: "snapshot"
        val requestedId = settings.value("workspace_id").orEmpty()
        val requestedName = settings.value("workspace_name").orEmpty()
        val workspace = when {
            requestedId.isNotBlank() -> TabletopUtilitiesRepository.getWorkspace(requestedId)
            requestedName.isNotBlank() -> TabletopUtilitiesRepository.listWorkspaces(includeArchived = true)
                .firstOrNull { it.name.equals(requestedName, ignoreCase = true) }
            else -> null
        } ?: return failureValues(operation, requestedId, "Workspace not found. Supply workspace_id or workspace_name.")

        return runCatching {
            val eventBefore = TabletopUtilitiesRepository.readAudit(workspace.id, 1).lastOrNull()?.eventId
            val updated = when (operation) {
                "dashboard", "snapshot" -> workspace
                "counter_adjust" -> {
                    val target = settings.value("target_id") ?: error("target_id is required for counter_adjust.")
                    val delta = settings.value("value")?.toIntOrNull() ?: error("value must be an integer delta.")
                    TabletopUtilitiesRepository.mutate(workspace.id, TabletopMutation.AdjustCounter(target, delta), source = source).workspace
                }
                "counter_set" -> {
                    val target = settings.value("target_id") ?: error("target_id is required for counter_set.")
                    val value = settings.value("value")?.toIntOrNull() ?: error("value must be an integer.")
                    TabletopUtilitiesRepository.mutate(workspace.id, TabletopMutation.SetCounter(target, value), source = source).workspace
                }
                "session_start" -> {
                    val note = settings.value("note").orEmpty()
                    TabletopUtilitiesRepository.mutate(workspace.id, TabletopMutation.StartSession(note, Instant.now().toString()), source = source).workspace
                }
                "session_note" -> {
                    val note = settings.value("note") ?: error("note is required for session_note.")
                    TabletopUtilitiesRepository.mutate(workspace.id, TabletopMutation.AddSessionNote(note), source = source).workspace
                }
                "session_finish" -> TabletopUtilitiesRepository.mutate(workspace.id, TabletopMutation.FinishSession(Instant.now().toString()), source = source).workspace
                "initiative_next" -> TabletopUtilitiesRepository.mutate(workspace.id, TabletopMutation.NextTurn, source = source).workspace
                "round_next" -> TabletopUtilitiesRepository.mutate(workspace.id, TabletopMutation.NextRound, source = source).workspace
                "score_record" -> TabletopUtilitiesRepository.mutate(workspace.id, TabletopMutation.RecordScores(Instant.now().toString()), source = source).workspace
                else -> error("Unsupported tabletop operation: $operation")
            }
            val lastEvent = TabletopUtilitiesRepository.readAudit(workspace.id, 1).lastOrNull()
            val newEvent = lastEvent?.takeIf { it.eventId != eventBefore }
            successValues(updated, operation, newEvent)
        }.getOrElse { error ->
            failureValues(operation, workspace.id, error.message ?: "Tabletop operation failed.", workspace.name)
        }
    }

    fun snapshotValues(workspace: GameWorkspace): Map<String, String> = successValues(workspace, "snapshot", null)

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[TabletopUtilitiesFields.STATUS] == "succeeded"
        val workspaceId = values[TabletopUtilitiesFields.WORKSPACE_ID].orEmpty()
        val entity = Entity(
            id = ArchitectureId("tabletop-workspace:${workspaceId.ifBlank { System.currentTimeMillis().toString() }}"),
            entityType = "TabletopWorkspace",
            attributes = values
        )
        val provenance = ProvenanceContext("methodmesh.tabletop.state", ID, VERSION)
        val observation = Observation(
            phenomenon = "tabletop.state",
            subject = ArchitectureRef(entity.id, entity.objectType, values[TabletopUtilitiesFields.WORKSPACE_NAME].orEmpty()),
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
            request = request,
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(TabletopUtilitiesFields.ERROR to values[TabletopUtilitiesFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    private fun successValues(workspace: GameWorkspace, operation: String, event: TabletopAuditEvent?): Map<String, String> {
        val audit = JSONObject()
            .put("methodmesh_method_id", ID)
            .put("method_version", VERSION)
            .put("status", "succeeded")
            .put("operation", operation)
            .put("workspace_id", workspace.id)
            .put("workspace_name", workspace.name)
            .put("event", event?.let(::auditEventJson))
            .put("recent_events", JSONArray().apply {
                TabletopUtilitiesRepository.readAudit(workspace.id, 25).forEach { put(auditEventJson(it)) }
            })
            .put("generated_time_iso", Instant.now().toString())
            .toString()
        val mainResult = event?.summary ?: TabletopUtilitiesRepository.summary(workspace)
        return linkedMapOf(
            TabletopUtilitiesFields.STATUS to "succeeded",
            TabletopUtilitiesFields.RESULT to mainResult,
            TabletopUtilitiesFields.WORKSPACE_ID to workspace.id,
            TabletopUtilitiesFields.WORKSPACE_NAME to workspace.name,
            TabletopUtilitiesFields.OPERATION to operation,
            TabletopUtilitiesFields.ROUND to workspace.initiative.round.toString(),
            TabletopUtilitiesFields.CURRENT_TURN to workspace.initiative.current?.name.orEmpty(),
            TabletopUtilitiesFields.ACTIVE_SESSION to workspace.activeSession?.name.orEmpty(),
            TabletopUtilitiesFields.EVENT_ID to event?.eventId.orEmpty(),
            TabletopUtilitiesFields.STATE_JSON to TabletopUtilitiesRepository.workspaceJson(workspace),
            TabletopUtilitiesFields.AUDIT_JSON to audit,
            TabletopUtilitiesFields.ERROR to ""
        )
    }

    private fun failureValues(operation: String, workspaceId: String, error: String, workspaceName: String = ""): Map<String, String> {
        val audit = JSONObject()
            .put("methodmesh_method_id", ID)
            .put("method_version", VERSION)
            .put("status", "failed")
            .put("operation", operation)
            .put("workspace_id", workspaceId)
            .put("error", error)
            .put("generated_time_iso", Instant.now().toString())
            .toString()
        return linkedMapOf(
            TabletopUtilitiesFields.STATUS to "failed",
            TabletopUtilitiesFields.RESULT to "",
            TabletopUtilitiesFields.WORKSPACE_ID to workspaceId,
            TabletopUtilitiesFields.WORKSPACE_NAME to workspaceName,
            TabletopUtilitiesFields.OPERATION to operation,
            TabletopUtilitiesFields.ROUND to "",
            TabletopUtilitiesFields.CURRENT_TURN to "",
            TabletopUtilitiesFields.ACTIVE_SESSION to "",
            TabletopUtilitiesFields.EVENT_ID to "",
            TabletopUtilitiesFields.STATE_JSON to "",
            TabletopUtilitiesFields.AUDIT_JSON to audit,
            TabletopUtilitiesFields.ERROR to error
        )
    }

    private fun auditEventJson(e: TabletopAuditEvent): JSONObject = JSONObject()
        .put("event_id", e.eventId)
        .put("workspace_id", e.workspaceId)
        .put("session_id", e.sessionId)
        .put("timestamp_iso", e.timestampIso)
        .put("event_type", e.eventType)
        .put("summary", e.summary)
        .put("entity_id", e.entityId)
        .put("source", e.source)
        .put("before", e.beforeValue)
        .put("after", e.afterValue)
        .put("reverses_event_id", e.reversesEventId)
        .put("external_reference", e.externalReference)
        .put("workspace_sha256", e.workspaceSha256)

    private fun Map<String, String>.value(key: String): String? =
        (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }
}
