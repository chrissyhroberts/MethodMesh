package com.example.methodmesh.modules.webactions

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
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant

object OdkCentralRoundtripFields {
    const val STATUS = "web_central_status"
    const val RESULT = "web_central_result"
    const val COMPLETED = "web_central_completed"
    const val TRANSACTION_ID = "web_central_transaction_id"
    const val HOST = "web_central_host"
    const val LINK_KIND = "web_central_link_kind"
    const val RENDERER_MODE = "web_central_renderer_mode"
    const val CALLBACK_RECEIVED = "web_central_callback_received"
    const val COMPLETION_SIGNAL = "web_central_completion_signal"
    const val STARTED_TIME_ISO = "web_central_started_time_iso"
    const val COMPLETED_TIME_ISO = "web_central_completed_time_iso"
    const val DURATION_MS = "web_central_duration_ms"
    const val AUDIT_JSON = "web_central_audit_json"
    const val ERROR = "web_central_error"

    val outputs = listOf(
        STATUS,
        RESULT,
        COMPLETED,
        TRANSACTION_ID,
        HOST,
        LINK_KIND,
        RENDERER_MODE,
        CALLBACK_RECEIVED,
        COMPLETION_SIGNAL,
        STARTED_TIME_ISO,
        COMPLETED_TIME_ISO,
        DURATION_MS,
        AUDIT_JSON,
        ERROR
    )
}

object EnketoRoundtripFields {
    const val STATUS = "web_enketo_status"
    const val RESULT = "web_enketo_result"
    const val COMPLETED = "web_enketo_completed"
    const val TRANSACTION_ID = "web_enketo_transaction_id"
    const val FORM_ID = "web_enketo_form_id"
    const val SERVER_URL = "web_enketo_server_url"
    const val LAUNCH_HOST = "web_enketo_launch_host"
    const val CALLBACK_RECEIVED = "web_enketo_callback_received"
    const val COMPLETION_SIGNAL = "web_enketo_completion_signal"
    const val STARTED_TIME_ISO = "web_enketo_started_time_iso"
    const val COMPLETED_TIME_ISO = "web_enketo_completed_time_iso"
    const val DURATION_MS = "web_enketo_duration_ms"
    const val AUDIT_JSON = "web_enketo_audit_json"
    const val ERROR = "web_enketo_error"

    val outputs = listOf(
        STATUS,
        RESULT,
        COMPLETED,
        TRANSACTION_ID,
        FORM_ID,
        SERVER_URL,
        LAUNCH_HOST,
        CALLBACK_RECEIVED,
        COMPLETION_SIGNAL,
        STARTED_TIME_ISO,
        COMPLETED_TIME_ISO,
        DURATION_MS,
        AUDIT_JSON,
        ERROR
    )
}

object WebRoundtripFields {
    const val STATUS = "web_roundtrip_status"
    const val RESULT = "web_roundtrip_result"
    const val COMPLETED = "web_roundtrip_completed"
    const val TRANSACTION_ID = "web_roundtrip_transaction_id"
    const val URL = "web_roundtrip_url"
    const val HOST = "web_roundtrip_host"
    const val CALLBACK_RECEIVED = "web_roundtrip_callback_received"
    const val COMPLETION_SIGNAL = "web_roundtrip_completion_signal"
    const val STARTED_TIME_ISO = "web_roundtrip_started_time_iso"
    const val COMPLETED_TIME_ISO = "web_roundtrip_completed_time_iso"
    const val DURATION_MS = "web_roundtrip_duration_ms"
    const val AUDIT_JSON = "web_roundtrip_audit_json"
    const val ERROR = "web_roundtrip_error"

    val outputs = listOf(
        STATUS,
        RESULT,
        COMPLETED,
        TRANSACTION_ID,
        URL,
        HOST,
        CALLBACK_RECEIVED,
        COMPLETION_SIGNAL,
        STARTED_TIME_ISO,
        COMPLETED_TIME_ISO,
        DURATION_MS,
        AUDIT_JSON,
        ERROR
    )
}

object WebOpenFields {
    const val STATUS = "web_open_status"
    const val RESULT = "web_open_result"
    const val URL = "web_open_url"
    const val HOST = "web_open_host"
    const val LAUNCHED_TIME_ISO = "web_open_launched_time_iso"
    const val AUDIT_JSON = "web_open_audit_json"
    const val ERROR = "web_open_error"

    val outputs = listOf(
        STATUS,
        RESULT,
        URL,
        HOST,
        LAUNCHED_TIME_ISO,
        AUDIT_JSON,
        ERROR
    )
}

object As100OdkCentralRoundtripMethod : As100Method {
    const val ID = "web.odk_central_roundtrip"
    const val VERSION = "0.6.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "ODK Central form")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "ODK Central form",
        version = VERSION,
        description = "Paste a web-form link from ODK Central, complete the submission, and return only when Central reaches MethodMesh's one-shot return URL.",
        inputs = listOf("url", "timeout_seconds", "allow_insecure_http"),
        outputs = OdkCentralRoundtripFields.outputs,
        graphOutputs = listOf("web.odk.central.roundtrip"),
        parameters = mapOf(
            "category" to "Interoperability",
            "status" to "Development",
            "online" to "required",
            "interaction" to "interactive",
            "renderer" to "central_selected"
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

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult = As100ExecutionEngine.complete(
        request,
        TransformationStatus.Unsupported,
        diagnostics = mapOf("reason" to "ODK Central roundtrip requires the interactive Android web surface.")
    )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult = completeWebResult(
        methodId = ID,
        version = VERSION,
        ref = ref,
        phenomenon = "web.odk.central.roundtrip",
        statusField = OdkCentralRoundtripFields.STATUS,
        errorField = OdkCentralRoundtripFields.ERROR,
        request = request,
        values = values,
        invocation = invocation
    )

    fun success(
        transactionId: String,
        sourceUrlHash: String,
        host: String,
        linkKind: String,
        startedIso: String,
        completedIso: String,
        durationMs: Long,
        publicAccessTokenPresent: Boolean,
        completionSignal: String = "central_return_url"
    ): Map<String, String> {
        val audit = JSONObject().apply {
            put("method_id", ID)
            put("method_version", VERSION)
            put("transaction_id", transactionId)
            put("source_url_sha256", sourceUrlHash)
            put("host", host)
            put("link_kind", linkKind)
            put("renderer_mode", "central_selected")
            put("public_access_token_present", publicAccessTokenPresent)
            put("callback_received", completionSignal == "central_return_url")
            put("completion_signal", completionSignal)
            put("submission_receipt_verified", false)
            put("started_time_iso", startedIso)
            put("completed_time_iso", completedIso)
            put("duration_ms", durationMs)
            put("source_url_returned", false)
            put("secret_fields_returned", false)
        }.toString()

        return linkedMapOf(
            OdkCentralRoundtripFields.STATUS to "succeeded",
            OdkCentralRoundtripFields.RESULT to "ODK Central form submitted",
            OdkCentralRoundtripFields.COMPLETED to "true",
            OdkCentralRoundtripFields.TRANSACTION_ID to transactionId,
            OdkCentralRoundtripFields.HOST to host,
            OdkCentralRoundtripFields.LINK_KIND to linkKind,
            OdkCentralRoundtripFields.RENDERER_MODE to "central_selected",
            OdkCentralRoundtripFields.CALLBACK_RECEIVED to (completionSignal == "central_return_url").toString(),
            OdkCentralRoundtripFields.COMPLETION_SIGNAL to completionSignal,
            OdkCentralRoundtripFields.STARTED_TIME_ISO to startedIso,
            OdkCentralRoundtripFields.COMPLETED_TIME_ISO to completedIso,
            OdkCentralRoundtripFields.DURATION_MS to durationMs.coerceAtLeast(0L).toString(),
            OdkCentralRoundtripFields.AUDIT_JSON to audit,
            OdkCentralRoundtripFields.ERROR to ""
        )
    }

    fun failure(message: String): Map<String, String> = linkedMapOf(
        OdkCentralRoundtripFields.STATUS to "failed",
        OdkCentralRoundtripFields.RESULT to "",
        OdkCentralRoundtripFields.COMPLETED to "false",
        OdkCentralRoundtripFields.TRANSACTION_ID to "",
        OdkCentralRoundtripFields.HOST to "",
        OdkCentralRoundtripFields.LINK_KIND to "",
        OdkCentralRoundtripFields.RENDERER_MODE to "central_selected",
        OdkCentralRoundtripFields.CALLBACK_RECEIVED to "false",
        OdkCentralRoundtripFields.COMPLETION_SIGNAL to "",
        OdkCentralRoundtripFields.STARTED_TIME_ISO to "",
        OdkCentralRoundtripFields.COMPLETED_TIME_ISO to "",
        OdkCentralRoundtripFields.DURATION_MS to "",
        OdkCentralRoundtripFields.AUDIT_JSON to JSONObject().apply {
            put("method_id", ID)
            put("method_version", VERSION)
            put("error", message)
            put("source_url_returned", false)
            put("secret_fields_returned", false)
        }.toString(),
        OdkCentralRoundtripFields.ERROR to message
    )
}

object As100EnketoRoundtripMethod : As100Method {
    const val ID = "web.precooked_enketo"
    const val VERSION = "0.6.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Precooked Enketo session")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Precooked Enketo session",
        version = VERSION,
        description = "Create a fresh Enketo single-submit session, optionally prefill it from runtime MethodMesh values, wait for its return redirect, then commit completion.",
        inputs = listOf(
            "api_base_url", "server_url", "form_id", "api_token", "single_mode",
            "prefill_bindings_json", "defaults_json", "theme", "timeout_seconds", "allow_insecure_http"
        ),
        outputs = EnketoRoundtripFields.outputs,
        graphOutputs = listOf("web.enketo.roundtrip"),
        parameters = mapOf(
            "category" to "Interoperability",
            "status" to "Development",
            "online" to "required",
            "interaction" to "interactive",
            "mode" to "programmatic_prefill"
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

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult = unsupported(request, "Precooked Enketo session requires the interactive Android web surface.")

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult = completeWebResult(
        methodId = ID,
        version = VERSION,
        ref = ref,
        phenomenon = "web.enketo.roundtrip",
        statusField = EnketoRoundtripFields.STATUS,
        errorField = EnketoRoundtripFields.ERROR,
        request = request,
        values = values,
        invocation = invocation
    )

    fun success(
        transactionId: String,
        formId: String,
        serverUrl: String,
        launchHost: String,
        startedIso: String,
        completedIso: String,
        durationMs: Long,
        singleMode: String,
        defaultsCount: Int,
        bindingCount: Int
    ): Map<String, String> {
        val audit = JSONObject().apply {
            put("method_id", ID)
            put("method_version", VERSION)
            put("transaction_id", transactionId)
            put("form_id", formId)
            put("server_url", serverUrl)
            put("launch_host", launchHost)
            put("single_mode", singleMode)
            put("resolved_defaults_count", defaultsCount)
            put("prefill_binding_count", bindingCount)
            put("callback_received", true)
            put("completion_signal", "enketo_return_url")
            put("submission_receipt_verified", false)
            put("started_time_iso", startedIso)
            put("completed_time_iso", completedIso)
            put("duration_ms", durationMs)
            put("prefill_values_returned", false)
            put("secret_fields_returned", false)
        }.toString()

        return linkedMapOf(
            EnketoRoundtripFields.STATUS to "succeeded",
            EnketoRoundtripFields.RESULT to "Precooked Enketo session completed",
            EnketoRoundtripFields.COMPLETED to "true",
            EnketoRoundtripFields.TRANSACTION_ID to transactionId,
            EnketoRoundtripFields.FORM_ID to formId,
            EnketoRoundtripFields.SERVER_URL to serverUrl,
            EnketoRoundtripFields.LAUNCH_HOST to launchHost,
            EnketoRoundtripFields.CALLBACK_RECEIVED to "true",
            EnketoRoundtripFields.COMPLETION_SIGNAL to "enketo_return_url",
            EnketoRoundtripFields.STARTED_TIME_ISO to startedIso,
            EnketoRoundtripFields.COMPLETED_TIME_ISO to completedIso,
            EnketoRoundtripFields.DURATION_MS to durationMs.coerceAtLeast(0L).toString(),
            EnketoRoundtripFields.AUDIT_JSON to audit,
            EnketoRoundtripFields.ERROR to ""
        )
    }

    fun failure(message: String): Map<String, String> = linkedMapOf(
        EnketoRoundtripFields.STATUS to "failed",
        EnketoRoundtripFields.RESULT to "",
        EnketoRoundtripFields.COMPLETED to "false",
        EnketoRoundtripFields.TRANSACTION_ID to "",
        EnketoRoundtripFields.FORM_ID to "",
        EnketoRoundtripFields.SERVER_URL to "",
        EnketoRoundtripFields.LAUNCH_HOST to "",
        EnketoRoundtripFields.CALLBACK_RECEIVED to "false",
        EnketoRoundtripFields.COMPLETION_SIGNAL to "",
        EnketoRoundtripFields.STARTED_TIME_ISO to "",
        EnketoRoundtripFields.COMPLETED_TIME_ISO to "",
        EnketoRoundtripFields.DURATION_MS to "",
        EnketoRoundtripFields.AUDIT_JSON to JSONObject().apply {
            put("method_id", ID)
            put("method_version", VERSION)
            put("error", message)
            put("prefill_values_returned", false)
            put("secret_fields_returned", false)
        }.toString(),
        EnketoRoundtripFields.ERROR to message
    )

    private fun unsupported(request: ExecutionRequest, message: String): ExecutionResult =
        As100ExecutionEngine.complete(
            request,
            TransformationStatus.Unsupported,
            diagnostics = mapOf("reason" to message)
        )
}

object As100WebRoundtripMethod : As100Method {
    const val ID = "web.roundtrip"
    const val VERSION = "0.6.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Web roundtrip")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Web roundtrip",
        version = VERSION,
        description = "Run an in-app web workflow that returns through a one-shot MethodMesh callback URL.",
        inputs = listOf("url", "callback_parameter", "callback_placeholder", "timeout_seconds", "allow_insecure_http"),
        outputs = WebRoundtripFields.outputs,
        graphOutputs = listOf("web.roundtrip"),
        parameters = mapOf(
            "category" to "Interoperability",
            "status" to "Development",
            "online" to "required",
            "interaction" to "interactive"
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

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult = As100ExecutionEngine.complete(
        request,
        TransformationStatus.Unsupported,
        diagnostics = mapOf("reason" to "Web roundtrip requires the interactive Android web surface.")
    )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult = completeWebResult(
        methodId = ID,
        version = VERSION,
        ref = ref,
        phenomenon = "web.roundtrip",
        statusField = WebRoundtripFields.STATUS,
        errorField = WebRoundtripFields.ERROR,
        request = request,
        values = values,
        invocation = invocation
    )

    fun success(
        transactionId: String,
        originalUrl: String,
        host: String,
        callbackParameter: String,
        startedIso: String,
        completedIso: String,
        durationMs: Long
    ): Map<String, String> {
        val audit = JSONObject().apply {
            put("method_id", ID)
            put("method_version", VERSION)
            put("transaction_id", transactionId)
            put("url_sha256", sha256(originalUrl))
            put("host", host)
            put("callback_parameter", callbackParameter)
            put("callback_received", true)
            put("completion_signal", "return_url")
            put("started_time_iso", startedIso)
            put("completed_time_iso", completedIso)
            put("duration_ms", durationMs)
            put("secret_fields_returned", false)
        }.toString()

        return linkedMapOf(
            WebRoundtripFields.STATUS to "succeeded",
            WebRoundtripFields.RESULT to "Web workflow completed",
            WebRoundtripFields.COMPLETED to "true",
            WebRoundtripFields.TRANSACTION_ID to transactionId,
            WebRoundtripFields.URL to originalUrl,
            WebRoundtripFields.HOST to host,
            WebRoundtripFields.CALLBACK_RECEIVED to "true",
            WebRoundtripFields.COMPLETION_SIGNAL to "return_url",
            WebRoundtripFields.STARTED_TIME_ISO to startedIso,
            WebRoundtripFields.COMPLETED_TIME_ISO to completedIso,
            WebRoundtripFields.DURATION_MS to durationMs.coerceAtLeast(0L).toString(),
            WebRoundtripFields.AUDIT_JSON to audit,
            WebRoundtripFields.ERROR to ""
        )
    }

    fun failure(message: String): Map<String, String> = linkedMapOf(
        WebRoundtripFields.STATUS to "failed",
        WebRoundtripFields.RESULT to "",
        WebRoundtripFields.COMPLETED to "false",
        WebRoundtripFields.TRANSACTION_ID to "",
        WebRoundtripFields.URL to "",
        WebRoundtripFields.HOST to "",
        WebRoundtripFields.CALLBACK_RECEIVED to "false",
        WebRoundtripFields.COMPLETION_SIGNAL to "",
        WebRoundtripFields.STARTED_TIME_ISO to "",
        WebRoundtripFields.COMPLETED_TIME_ISO to "",
        WebRoundtripFields.DURATION_MS to "",
        WebRoundtripFields.AUDIT_JSON to JSONObject().apply {
            put("method_id", ID)
            put("method_version", VERSION)
            put("error", message)
            put("secret_fields_returned", false)
        }.toString(),
        WebRoundtripFields.ERROR to message
    )
}

object As100WebOpenMethod : As100Method {
    const val ID = "web.open"
    const val VERSION = "0.6.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Open web page")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Open web page",
        version = VERSION,
        description = "Dispatch a safe HTTP(S) URL to the Android browser and return a dispatch result.",
        inputs = listOf("url", "allow_insecure_http"),
        outputs = WebOpenFields.outputs,
        graphOutputs = listOf("web.open"),
        parameters = mapOf(
            "category" to "Interoperability",
            "status" to "Development",
            "interaction" to "external_browser"
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

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult = As100ExecutionEngine.complete(
        request,
        TransformationStatus.Unsupported,
        diagnostics = mapOf("reason" to "Browser dispatch requires the Android capability screen.")
    )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult = completeWebResult(
        methodId = ID,
        version = VERSION,
        ref = ref,
        phenomenon = "web.open",
        statusField = WebOpenFields.STATUS,
        errorField = WebOpenFields.ERROR,
        request = request,
        values = values,
        invocation = invocation
    )

    fun success(url: String, host: String, launchedIso: String): Map<String, String> {
        val audit = JSONObject().apply {
            put("method_id", ID)
            put("method_version", VERSION)
            put("url_sha256", sha256(url))
            put("host", host)
            put("launched_time_iso", launchedIso)
            put("completion_claim", "browser_dispatch_only")
        }.toString()
        return linkedMapOf(
            WebOpenFields.STATUS to "succeeded",
            WebOpenFields.RESULT to "Opened $host",
            WebOpenFields.URL to url,
            WebOpenFields.HOST to host,
            WebOpenFields.LAUNCHED_TIME_ISO to launchedIso,
            WebOpenFields.AUDIT_JSON to audit,
            WebOpenFields.ERROR to ""
        )
    }

    fun failure(message: String): Map<String, String> = linkedMapOf(
        WebOpenFields.STATUS to "failed",
        WebOpenFields.RESULT to "",
        WebOpenFields.URL to "",
        WebOpenFields.HOST to "",
        WebOpenFields.LAUNCHED_TIME_ISO to Instant.now().toString(),
        WebOpenFields.AUDIT_JSON to JSONObject().apply {
            put("method_id", ID)
            put("method_version", VERSION)
            put("error", message)
        }.toString(),
        WebOpenFields.ERROR to message
    )
}

private fun completeWebResult(
    methodId: String,
    version: String,
    ref: ArchitectureRef,
    phenomenon: String,
    statusField: String,
    errorField: String,
    request: ExecutionRequest,
    values: Map<String, String>,
    invocation: InvocationContext?
): ExecutionResult {
    val ok = values[statusField] == "succeeded"
    val provenance = ProvenanceContext("methodmesh.web_actions", methodId, version)
    val observation = Observation(
        phenomenon = phenomenon,
        values = values,
        temporalContext = request.temporalContext,
        provenance = provenance
    )
    val transformation = Transformation(
        action = methodId,
        method = ref,
        outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
        status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
        temporalContext = request.temporalContext,
        provenance = provenance
    )
    return As100ExecutionEngine.complete(
        request = request,
        status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
        observations = listOf(observation),
        transformations = listOf(transformation),
        diagnostics = if (ok) emptyMap() else mapOf(errorField to values[errorField].orEmpty())
    ).withInvocationContext(invocation)
}

internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
