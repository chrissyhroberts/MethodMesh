package com.example.researchos.modules.sms

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
