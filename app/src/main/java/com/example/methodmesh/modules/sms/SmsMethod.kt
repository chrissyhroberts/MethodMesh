package com.example.methodmesh.modules.sms

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
import java.time.Instant

object SmsFields {
    const val PHONE = "sms_phone"
    const val MESSAGE = "sms_message"
    const val MESSAGE_SHA256 = "sms_message_sha256"
    const val STATUS = "sms_status"
    const val PARTS = "sms_parts"
    const val SENT_TIME_ISO = "sms_sent_time_iso"
    const val ERROR = "sms_error"

    val outputs = listOf(PHONE, MESSAGE, MESSAGE_SHA256, STATUS, PARTS, SENT_TIME_ISO, ERROR)
}

object As100SendSmsMethod : As100Method {
    const val ID = "sms.send"
    private const val VERSION = "1.0.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Send a short SMS")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Send SMS",
        version = VERSION,
        description = "Send a templated SMS message to a phone number.",
        outputs = SmsFields.outputs,
        graphOutputs = listOf("sms.send")
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
            diagnostics = mapOf("reason" to "SMS sending requires the Android SMS boundary.")
        )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[SmsFields.STATUS] == "sent"
        val entity = Entity(ArchitectureId("sms:${System.currentTimeMillis()}"), "SmsMessage", temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("android.telephony", ID, VERSION)
        val observation = Observation(
            phenomenon = "sms.send",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values + (SmsFields.SENT_TIME_ISO to Instant.now().toString()),
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
            diagnostics = if (ok) emptyMap() else mapOf("sms_error" to (values[SmsFields.ERROR] ?: "SMS was not sent."))
        ).withInvocationContext(invocation ?: InvocationContext.from(emptyMap()))
    }
}
