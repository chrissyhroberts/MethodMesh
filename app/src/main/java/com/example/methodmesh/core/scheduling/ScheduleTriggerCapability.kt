package com.example.methodmesh.core.scheduling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.time.ZonedDateTime

/** Shared activation boundary for widgets, presets, protocols, people, and future condition providers. */
object ScheduleTriggerRuntime {
    fun start(context: android.content.Context, bundleId: String, anchor: ZonedDateTime = ZonedDateTime.now()): Int {
        val schedules = SchedulerRepository.all(context).filter { it.id.startsWith("${bundleId}_") }
        schedules.forEach { schedule ->
            SchedulerRepository.save(context, schedule.copy(enabled = true, anchorAt = anchor))
        }
        return schedules.size
    }

    fun triggerEvent(context: android.content.Context, eventKey: String, anchor: ZonedDateTime = ZonedDateTime.now()): Int =
        SchedulerRepository.triggerEvent(context, eventKey, anchor)
}

object As100ScheduleRunMethod : As100Method {
    const val ID = "run.schedule"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Start a MethodMesh schedule")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow, name = "Run schedule", version = "1.0.0",
        description = "Start a saved schedule and establish its relative-time anchor.",
        outputs = listOf(SchedulerFields.SCHEDULE_ID, SchedulerFields.STATUS, SchedulerFields.NEXT_RUN),
        graphOutputs = listOf("methodmesh.schedule.run"), parameters = mapOf("category" to "Operations", "status" to "Experimental")
    )
    override val contract = MethodContract(method = ref, requiredContext = emptyList(), producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) = As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "Schedule activation requires the Android alarm boundary."))
}

object ScheduleTriggerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ScheduleRunMethod.ID
    override val title = "Run schedule"
    override val description = "Start a saved schedule and set its relative-time anchor."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val bundles = remember { CronScheduleBundleStore.all(app) }
        val requestedId = context.action.settings["schedule_id"].orEmpty().ifBlank { context.request.settings["schedule_id"].orEmpty() }
        var selectedId by remember { mutableStateOf(requestedId) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("") }

        fun start() {
            if (selectedId.isBlank()) { status = "Choose a schedule to start."; return }
            val count = ScheduleTriggerRuntime.start(app, selectedId)
            if (count == 0) { status = "That schedule has no runnable tasks."; return }
            val schedule = SchedulerRepository.get(app, "${selectedId}_0")
            val request = As100ScheduleRunMethod.request(capabilityId, emptyMap(), emptyList(), emptyList())
            val execution = As100SchedulerMethod.result(request, SchedulerOutcome(schedule, "started"), context.request.invocationContext)
            result = execution
            status = "Started $count task${if (count == 1) "" else "s"}."
            onConfirmed(execution)
        }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { result = null }, { result?.let(onConfirmed) }, onCancel) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Start a schedule", style = MaterialTheme.typography.headlineSmall)
                Text("Relative tasks count from the moment you start it.", style = MaterialTheme.typography.bodyMedium)
                bundles.forEach { bundle ->
                    OutlinedButton(onClick = { selectedId = bundle.id }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selectedId == bundle.id) "✓ ${bundle.name}" else bundle.name)
                    }
                }
                if (bundles.isEmpty()) Text("No saved schedules yet.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = ::start, enabled = selectedId.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Start schedule") }
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
