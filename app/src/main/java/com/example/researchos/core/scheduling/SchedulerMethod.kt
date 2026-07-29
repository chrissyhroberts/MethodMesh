package com.example.researchos.core.scheduling

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
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
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.settings.SettingsState
import java.time.Instant

object SchedulerFields {
    const val SCHEDULE_ID = "schedule_id"
    const val STATUS = "scheduler_status"
    const val NEXT_RUN = "scheduler_next_run_iso"
    const val TARGET = "scheduler_target"
    const val TARGET_VALUE = "scheduler_target_value"
    const val FREQUENCY = "scheduler_frequency"
    const val ERROR = "scheduler_error"
    val outputs = listOf(SCHEDULE_ID, STATUS, NEXT_RUN, TARGET, TARGET_VALUE, FREQUENCY, ERROR)
}

data class SchedulerOutcome(val schedule: ResearchSchedule? = null, val status: String, val error: String = "")

object As100SchedulerMethod : As100Method {
    const val ID = "scheduler.create"
    private const val VERSION = "1.0.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Create a ResearchOS schedule")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow, name = "Create schedule", version = VERSION,
        description = "Schedule an ODK form, web form, or future ResearchOS process.", outputs = SchedulerFields.outputs,
        graphOutputs = listOf("researchos.schedule"), parameters = mapOf("category" to "Operations", "status" to "Experimental")
    )
    override val contract = MethodContract(method = ref, requiredContext = emptyList(), producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) = As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "Scheduler requires the Android alarm boundary."))

    fun result(request: ExecutionRequest, outcome: SchedulerOutcome, invocation: InvocationContext?): ExecutionResult {
        val s = outcome.schedule
        val values = linkedMapOf(
            SchedulerFields.SCHEDULE_ID to s?.id.orEmpty(), SchedulerFields.STATUS to outcome.status,
            SchedulerFields.NEXT_RUN to s?.nextOccurrence()?.toInstant()?.toString().orEmpty(),
            SchedulerFields.TARGET to s?.target?.name.orEmpty(), SchedulerFields.TARGET_VALUE to s?.targetValue.orEmpty(),
            SchedulerFields.FREQUENCY to s?.frequency?.name.orEmpty(), SchedulerFields.ERROR to outcome.error
        )
        val provenance = ProvenanceContext(provider = "android.alarm_manager", methodId = ID, methodVersion = VERSION)
        val observation = Observation(phenomenon = "researchos.schedule", subject = null, values = values, temporalContext = request.temporalContext, provenance = provenance)
        val status = if (outcome.status == "created") TransformationStatus.Succeeded else TransformationStatus.Failed
        val transformation = Transformation(action = ID, method = ref, outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)), status = status, diagnostics = mapOf("status" to outcome.status, "error" to outcome.error), temporalContext = request.temporalContext, provenance = provenance)
        return As100ExecutionEngine.complete(request = request, status = status, observations = listOf(observation), transformations = listOf(transformation), diagnostics = mapOf("status" to outcome.status, "error" to outcome.error)).withInvocationContext(invocation)
    }
}

abstract class SchedulerTransferMethod(
    override val id: String,
    private val name: String,
    private val descriptionText: String
) : As100Method {
    override val ref = ArchitectureRef(ArchitectureId(id), "Method", name)
    override val descriptor = MethodDescriptor(id = ArchitectureId(id), methodType = MethodObjectType.Workflow, name = name, version = "1.0.0", description = descriptionText, outputs = emptyList(), graphOutputs = emptyList(), parameters = mapOf("category" to "Operations", "status" to "Stable"))
    override val contract = MethodContract(method = ref, requiredContext = emptyList(), producedKnowledgeTypes = emptyList(), producedFields = emptyList(), producedGraphOutputs = emptyList())
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) = As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "Scheduler transfer is handled by the Android core boundary."))
}

object As100SchedulerExportMethod : SchedulerTransferMethod("scheduler.export", "Export schedules", "Export schedules as a portable integrity-checked bundle.")
object As100SchedulerImportMethod : SchedulerTransferMethod("scheduler.import", "Import schedules", "Import a portable schedule bundle, directly or through QR/NFC.")
