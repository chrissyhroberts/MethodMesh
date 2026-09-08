package com.example.methodmesh.modules.networktools

import android.content.Context
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

object NetworkToolsFields {
    // Beef: this is the single value native copy/share should prioritise.
    const val VALUE = "network_value"

    // Structured/core values retained from the original concept contract.
    const val SUMMARY = "network_summary"
    const val RESULT_JSON = "network_result_json"
    const val LATENCY_MS = "network_latency_ms"
    const val HOST = "network_host"
    const val IP = "network_ip"

    // Useful operation-specific scalar fields.
    const val PORT = "network_port"
    const val REACHABLE = "network_reachable"
    const val TCP_OPEN = "network_tcp_open"
    const val INTERFACE = "network_interface"
    const val CIDR = "network_cidr"
    const val DETAIL = "network_detail"

    // Audit-priority fields.
    const val STATUS = "network_status"
    const val OPERATION = "network_operation"
    const val CAPTURED_TIME_ISO = "network_captured_time_iso"
    const val ERROR = "network_error"

    val outputs = listOf(
        VALUE,
        SUMMARY,
        RESULT_JSON,
        LATENCY_MS,
        HOST,
        IP,
        PORT,
        REACHABLE,
        TCP_OPEN,
        INTERFACE,
        CIDR,
        DETAIL,
        STATUS,
        OPERATION,
        CAPTURED_TIME_ISO,
        ERROR
    )
}

object As100NetworkToolsMethod : As100Method {
    const val ID = "network.tools"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Network tools")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.DeviceService,
        name = "Network tools",
        version = VERSION,
        description = "Run bounded one-host network diagnostics or local IPv4 CIDR calculation.",
        inputs = listOf("operation", "host", "port", "timeout_ms", "cidr", "traceroute_max_hops"),
        outputs = NetworkToolsFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Network",
            "status" to "Development",
            "scope" to "single-host-bounded"
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

    /**
     * Headless execution is available for operations that do not require an Android Context.
     * Wi-Fi radio details are intentionally reported as unavailable when a Context has not
     * been supplied by the capability screen/runtime surface.
     */
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(
            request = request,
            values = NetworkToolsRunner.run(request.context, androidContext = null),
            invocation = InvocationContext.from(request.context)
        )

    fun executeWithAndroidContext(
        request: ExecutionRequest,
        androidContext: Context,
        invocation: InvocationContext?
    ): ExecutionResult = result(
        request = request,
        values = NetworkToolsRunner.run(request.context, androidContext.applicationContext),
        invocation = invocation
    )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[NetworkToolsFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("network-diagnostic:${System.currentTimeMillis()}"),
            "NetworkDiagnostic",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.network", ID, VERSION)
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
                NetworkToolsFields.ERROR to values[NetworkToolsFields.ERROR].orEmpty()
            )
        ).withInvocationContext(invocation)
    }
}
