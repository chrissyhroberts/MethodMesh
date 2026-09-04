package com.example.methodmesh.core.scheduling

import android.content.Intent
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import com.example.methodmesh.transport.android.IntentRouterActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.protocols.CapabilityPreset
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.core.protocols.ProtocolOutputMode
import com.example.methodmesh.core.protocols.ProtocolPayloadMode
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.platform.externalforms.ExternalFormCatalog
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.ui.theme.MethodMeshTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

class SchedulerDispatchActivity : ComponentActivity() {
    private var resultHandled = false
    private var activeSchedule: ResearchSchedule? = null
    private val testChain: Boolean
        get() = intent.getBooleanExtra("test_chain", false)
    private val transientProtocolRun: Boolean
        get() = intent.getBooleanExtra("transient_protocol_run", false)
    private val transientPresetRun: Boolean
        get() = intent.getBooleanExtra("transient_preset_run", false)
    private val protocolId: String
        get() = intent.getStringExtra("protocol_id").orEmpty()
    private val protocolStepIndex: Int
        get() = intent.getIntExtra("protocol_step_index", 0)
    private val outputGroupFolder: String
        get() = intent.getStringExtra("output_group_folder").orEmpty()
    private val protocolSubmissionId: String
        get() = intent.getStringExtra("protocol_submission_id").orEmpty()
    private val suppressOutput: Boolean
        get() = intent.getBooleanExtra("suppress_output", false)
    private val stepAccepted: Boolean
        get() = intent.getBooleanExtra("methodmesh_step_accepted", false)
    private val finishToLauncher: Boolean
        get() = intent.getBooleanExtra("finish_to_launcher", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val schedule = SchedulerRepository.get(this, intent.getStringExtra("schedule_id").orEmpty())
            ?: transientProtocolSchedule()
            ?: transientPresetSchedule()
        if (schedule == null) { finish(); return }
        activeSchedule = schedule
        SchedulerRepository.recordEvent(this, schedule.id, "dispatch_started:${intent.getStringExtra("notification_kind").orEmpty().ifBlank { "direct" }}")
        if (schedule.chainOrder <= 0 && protocolId.isBlank()) {
            clearChainClipboard(schedule)
            if (schedule.chainId.isNotBlank()) {
                initialiseScheduleChainRun(schedule)
            } else {
                clearRunContext(scheduleRunContextKey(schedule))
            }
        }
        if (schedule.target == SchedulerTarget.PROTOCOL && outputGroupFolder.isBlank()) {
            launchProtocolStep(schedule, schedule.targetValue, 0)
            return
        }
        if (!stepAccepted && shouldShowStepIntro(schedule)) {
            showStepIntro(schedule)
            return
        }
        if (protocolId.isNotBlank()) {
            val protocol = ProtocolLibraryRepository.protocol(this, protocolId)
            val step = protocol?.steps?.sortedBy { it.order }?.getOrNull(protocolStepIndex)
            val preset = step?.let { ProtocolLibraryRepository.preset(this, it.presetId) }
            if (preset == null) {
                SchedulerRepository.recordEvent(this, schedule.id, "protocol_step_missing:$protocolStepIndex")
                finish()
                return
            }
            launchPreset(preset, currentPipeSettings(schedule))
            return
        }
        if (schedule.target == SchedulerTarget.WEB_FORM) {
            startActivityForResult(Intent(Intent.ACTION_VIEW, Uri.parse(schedule.targetValue)), 102)
            return
        }
        if (schedule.target == SchedulerTarget.CAPABILITY) {
            launchCapability(schedule.targetValue, schedule.targetSettings)
            return
        }
        if (schedule.target == SchedulerTarget.PRESET) {
            val preset = ProtocolLibraryRepository.preset(this, schedule.targetValue)
            if (preset == null) {
                SchedulerRepository.recordEvent(this, schedule.id, "preset_not_found:${schedule.targetValue}")
                finish()
                return
            }
            launchPreset(preset, currentPipeSettings(schedule))
            return
        }
        if (schedule.target == SchedulerTarget.CLIPBOARD) {
            publishChainClipboard(schedule, schedule.targetValue, "MethodMesh scheduled action")
            SchedulerRepository.markCompleted(this, schedule)
            val next = SchedulerRepository.nextInChain(this, schedule)
            if (next != null) {
                SchedulerRepository.recordEvent(this, next.id, "chain_dispatch_started")
                startActivity(Intent(this, SchedulerDispatchActivity::class.java)
                    .setAction("com.example.methodmesh.SCHEDULED_CHAIN_DISPATCH")
                    .putExtra("schedule_id", next.id)
                    .putExtra("finish_to_launcher", finishToLauncher)
                    .putExtra("test_chain", testChain))
            }
            finishRun()
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            val form = kotlinx.coroutines.withContext(Dispatchers.IO) {
                ExternalFormCatalog.list(this@SchedulerDispatchActivity, schedule.projectId, schedule.packageName)
                    .firstOrNull { it.id.equals(schedule.targetValue, ignoreCase = true) || it.name.equals(schedule.targetValue, ignoreCase = true) }
            }
            if (form == null) {
                finish()
                return@launch
            }
            startActivityForResult(
                Intent(Intent.ACTION_EDIT).setDataAndType(form.uri, "vnd.android.cursor.item/vnd.odk.form").setPackage(form.packageName),
                100
            )
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultHandled) return
        resultHandled = true
        val current = activeSchedule ?: SchedulerRepository.get(this, intent.getStringExtra("schedule_id").orEmpty())
        if ((requestCode == 100 || requestCode == 102) && current != null && shouldAskManualCompletion(current)) {
            showManualCompletionCheck(current)
            return
        }
        // ODK Collect and browser-based forms commonly return RESULT_CANCELED even
        // after the external activity has completed. For those transports, returning
        // to MethodMesh is the completion signal. Capability calls retain strict
        // RESULT_OK semantics unless this is an explicit test run.
        val completed = resultCode == RESULT_OK || requestCode == 100 || requestCode == 102 || testChain
        val activeProtocol = if (completed && current != null && protocolId.isNotBlank()) {
            ProtocolLibraryRepository.protocol(this, protocolId)
        } else null
        val nextProtocolStep = activeProtocol?.steps?.sortedBy { it.order }?.getOrNull(protocolStepIndex + 1)
        val nextChainStep = if (completed && current != null && protocolId.isBlank()) {
            SchedulerRepository.nextInChain(this, current)
        } else null
        if (requestCode == 101 && (resultCode == RESULT_OK || testChain) && data != null) {
            val returnedFields = returnedFields(data)
            if (completed && current != null) appendCurrentRunContext(current, returnedFields)
            val output = data.getStringExtra("value").orEmpty().ifBlank {
                data.extras?.keySet().orEmpty()
                    .filterNot { it == "value" }
                    .sorted()
                    .joinToString("\n") { key -> "$key = ${data.extras?.getString(key)}" }
            }
            current?.let {
                if (it.target == SchedulerTarget.CLIPBOARD) {
                    if (output.isNotBlank()) {
                        publishChainClipboard(it, output, "MethodMesh scheduled capability")
                        SchedulerRepository.recordEvent(this, it.id, "completed_output_copied")
                    }
                } else if (suppressOutput) {
                    SchedulerRepository.recordEvent(this, it.id, "completed_output_suppressed")
                } else {
                    val mode = ProtocolOutputMode.normalize(
                        activeProtocol?.steps?.sortedBy { step -> step.order }?.getOrNull(protocolStepIndex)?.outputMode
                            ?: ProtocolOutputMode.SAVE
                    )
                    if (mode == ProtocolOutputMode.NONE) {
                        SchedulerRepository.recordEvent(this, it.id, "completed_output_suppressed_by_step")
                    } else {
                        val event = if (mode == ProtocolOutputMode.SHARE) "completed_output_share_ready" else "completed_output_captured"
                        SchedulerRepository.recordEvent(this, it.id, event)
                        if (nextProtocolStep == null && nextChainStep == null) {
                            if (hasMeaningfulPayload(returnedFields)) {
                                Toast.makeText(this, "MethodMesh run complete.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "MethodMesh run complete.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
        val protocolHasNextStep = protocolId.isNotBlank() && nextProtocolStep != null
        val chainHasNextStep = nextChainStep != null
        if (completed && current != null && !protocolHasNextStep && !chainHasNextStep && !transientProtocolRun && !transientPresetRun) {
            SchedulerRepository.markCompleted(this, current)
        } else if (completed && current != null) {
            val event = if (protocolHasNextStep || chainHasNextStep) "step_completed" else "completed"
            SchedulerRepository.recordEvent(this, current.id, event)
        } else current?.let { SchedulerRepository.recordEvent(this, it.id, "cancelled") }
        if (completed && current != null && protocolId.isNotBlank()) {
            val nextStep = nextProtocolStep
            if (nextStep != null) {
                SchedulerRepository.recordEvent(this, current.id, "protocol_step_completed:$protocolStepIndex")
                startActivity(Intent(this, SchedulerDispatchActivity::class.java)
                    .setAction("com.example.methodmesh.SCHEDULED_PROTOCOL_DISPATCH")
                    .putExtra("schedule_id", current.id)
                    .putExtra("protocol_id", protocolId)
                    .putExtra("protocol_step_index", protocolStepIndex + 1)
                    .putExtra("transient_protocol_run", transientProtocolRun)
                    .putExtra("output_group_folder", outputGroupFolder)
                    .putExtra("protocol_submission_id", protocolSubmissionId)
                    .putExtra("suppress_output", suppressOutput)
                    .putExtra("finish_to_launcher", finishToLauncher)
                    .putExtra("test_chain", testChain))
                finishRun()
                return
            }
        }
        val next = nextChainStep
        if (next != null) {
            SchedulerRepository.recordEvent(this, next.id, "chain_dispatch_started")
            startActivity(Intent(this, SchedulerDispatchActivity::class.java)
                .setAction("com.example.methodmesh.SCHEDULED_CHAIN_DISPATCH")
                .putExtra("schedule_id", next.id)
                .putExtra("finish_to_launcher", finishToLauncher)
                .putExtra("test_chain", testChain))
        }
        if (completed && current != null && protocolId.isNotBlank() && transientProtocolRun) {
            showRunResult(activeProtocol?.name ?: current.name, protocolRunContextKey(), outputGroupFolder, current.targetValue)
            return
        }
        if (completed && current != null && protocolId.isBlank() && current.chainId.isNotBlank() && next == null) {
            showRunResult(scheduleChainName(current), scheduleRunContextKey(current), scheduleChainRunFolder(current), current.targetValue)
            return
        }
        finishRun()
    }

    private fun shouldShowStepIntro(schedule: ResearchSchedule): Boolean =
        protocolId.isNotBlank() ||
            schedule.chainId.isNotBlank() ||
            schedule.target in setOf(SchedulerTarget.CAPABILITY, SchedulerTarget.WEB_FORM, SchedulerTarget.ODK_FORM)

    private fun shouldAskManualCompletion(schedule: ResearchSchedule): Boolean =
        schedule.target == SchedulerTarget.WEB_FORM || schedule.target == SchedulerTarget.ODK_FORM

    private fun showStepIntro(schedule: ResearchSchedule) {
        setContent {
            MethodMeshTheme {
                StepIntroScreen(
                    title = stepTitle(schedule),
                    detail = stepDetail(schedule),
                    stepLabel = stepCounterLabel(schedule),
                    lastCompleted = previousStepSummary(),
                    onGo = {
                        intent.putExtra("methodmesh_step_accepted", true)
                        recreate()
                    },
                    onCancel = { showCancelRunConfirmation() }
                )
            }
        }
    }

    private fun showManualCompletionCheck(schedule: ResearchSchedule) {
        setContent {
            MethodMeshTheme {
                ManualCompletionScreen(
                    title = "Did you complete ${stepTitle(schedule)}?",
                    onYes = { completeManualStep(schedule) },
                    onNo = {
                        resultHandled = false
                        intent.putExtra("methodmesh_step_accepted", true)
                        recreate()
                    },
                    onCancel = { showCancelRunConfirmation() }
                )
            }
        }
    }

    private fun completeManualStep(schedule: ResearchSchedule) {
        appendCurrentRunContext(schedule, mapOf("manual_step_completed" to "true", "manual_step_name" to stepTitle(schedule)))
        val next = if (protocolId.isBlank()) SchedulerRepository.nextInChain(this, schedule) else null
        if (next != null) {
            SchedulerRepository.recordEvent(this, next.id, "chain_dispatch_started")
            startActivity(Intent(this, SchedulerDispatchActivity::class.java)
                .setAction("com.example.methodmesh.SCHEDULED_CHAIN_DISPATCH")
                .putExtra("schedule_id", next.id)
                .putExtra("finish_to_launcher", finishToLauncher)
                .putExtra("test_chain", testChain))
            finishRun()
        } else if (schedule.chainId.isNotBlank()) {
            showRunResult(scheduleChainName(schedule), scheduleRunContextKey(schedule), scheduleChainRunFolder(schedule), schedule.targetValue)
        } else {
            SchedulerRepository.markCompleted(this, schedule)
            showRunResult(schedule.name, scheduleRunContextKey(schedule), "", schedule.targetValue)
        }
    }

    private fun showCancelRunConfirmation() {
        setContent {
            MethodMeshTheme {
                CancelRunScreen(
                    onKeepGoing = {
                        intent.putExtra("methodmesh_step_accepted", false)
                        recreate()
                    },
                    onCancelRun = { finishRun() }
                )
            }
        }
    }

    private fun finishRun() {
        if (finishToLauncher) finishAndRemoveTask() else finish()
    }

    private fun stepTitle(schedule: ResearchSchedule): String {
        if (protocolId.isNotBlank()) {
            val protocol = ProtocolLibraryRepository.protocol(this, protocolId)
            val step = protocol?.steps?.sortedBy { it.order }?.getOrNull(protocolStepIndex)
            return step?.name?.ifBlank { null }
                ?: ProtocolLibraryRepository.preset(this, step?.presetId.orEmpty())?.name
                ?: schedule.name
        }
        if (schedule.target == SchedulerTarget.PRESET) {
            return ProtocolLibraryRepository.preset(this, schedule.targetValue)?.name ?: schedule.name
        }
        return schedule.name.ifBlank { schedule.targetValue.ifBlank { schedule.target.name.lowercase().replace('_', ' ') } }
    }

    private fun stepDetail(schedule: ResearchSchedule): String = when {
        protocolId.isNotBlank() -> "Press Go when you are ready to run this protocol step."
        schedule.target == SchedulerTarget.WEB_FORM -> "Press Go to open the web form. When you return, MethodMesh will ask whether it was completed."
        schedule.target == SchedulerTarget.ODK_FORM -> "Press Go to open the form. When you return, MethodMesh will ask whether it was completed."
        schedule.target == SchedulerTarget.PRESET -> "Press Go to run this preset."
        schedule.target == SchedulerTarget.CAPABILITY -> "Press Go to run this capability."
        else -> "Press Go to continue."
    }

    private fun stepCounterLabel(schedule: ResearchSchedule): String = when {
        protocolId.isNotBlank() -> {
            val total = ProtocolLibraryRepository.protocol(this, protocolId)?.steps?.size ?: 1
            "Step ${protocolStepIndex + 1} of $total"
        }
        schedule.chainId.isNotBlank() -> "Step ${schedule.chainOrder + 1}"
        else -> "Ready"
    }

    private fun previousStepSummary(): String {
        val key = when {
            protocolId.isNotBlank() -> protocolRunContextKey()
            activeSchedule?.chainId?.isNotBlank() == true -> scheduleRunContextKey(activeSchedule!!)
            else -> ""
        }
        if (key.isBlank()) return ""
        return readRunContextSteps(key).lastOrNull()?.let { step ->
            val first = step.fields.entries.firstOrNull()
            if (first != null) "Last step completed: ${prettyProtocolFieldLabel(first.key)} ${first.value}" else "Last step completed."
        }.orEmpty()
    }

    private fun launchProtocolStep(schedule: ResearchSchedule, protocolLookup: String, stepIndex: Int) {
        val protocol = ProtocolLibraryRepository.protocol(this, protocolLookup)
        val step = protocol?.steps?.sortedBy { it.order }?.getOrNull(stepIndex)
        if (protocol == null || step == null) {
            SchedulerRepository.recordEvent(this, schedule.id, "protocol_not_found:$protocolLookup")
            finish()
            return
        }
        SchedulerRepository.recordEvent(this, schedule.id, "protocol_step_started:${step.order}:${step.name}")
        val group = outputGroupFolder.ifBlank {
            val submissionId = protocolSubmissionId.ifBlank { UUID.randomUUID().toString() }
            val timestamp = Instant.now().toString().replace(Regex("[^A-Za-z0-9_.-]"), "_")
            "${safeName(protocol.name)}__${submissionId}___${timestamp}"
        }
        val submissionId = protocolSubmissionId.ifBlank { submissionIdFromProtocolFolder(group) }
        if (stepIndex == 0) clearRunContext(protocolRunContextKey(submissionId, group, protocol.id))
        startActivity(Intent(this, SchedulerDispatchActivity::class.java)
            .setAction("com.example.methodmesh.SCHEDULED_PROTOCOL_DISPATCH")
            .putExtra("schedule_id", schedule.id)
            .putExtra("protocol_id", protocol.id)
            .putExtra("protocol_step_index", stepIndex)
            .putExtra("transient_protocol_run", transientProtocolRun)
            .putExtra("output_group_folder", group)
            .putExtra("protocol_submission_id", submissionId)
            .putExtra("suppress_output", suppressOutput)
            .putExtra("finish_to_launcher", finishToLauncher)
            .putExtra("test_chain", testChain))
        finishRun()
    }

    private fun transientProtocolSchedule(): ResearchSchedule? {
        val id = intent.getStringExtra("protocol_id").orEmpty().ifBlank { intent.getStringExtra("protocol_lookup").orEmpty() }
        if (id.isBlank()) return null
        val protocol = ProtocolLibraryRepository.protocol(this, id) ?: return null
        return ResearchSchedule(
            id = "__protocol_run_${protocol.id}",
            name = protocol.name,
            target = SchedulerTarget.PROTOCOL,
            targetValue = protocol.id,
            frequency = SchedulerFrequency.DAILY,
            hour = 0,
            minute = 0,
            enabled = false
        )
    }

    private fun transientPresetSchedule(): ResearchSchedule? {
        val id = intent.getStringExtra("preset_id").orEmpty().ifBlank { intent.getStringExtra("preset_lookup").orEmpty() }
        if (id.isBlank()) return null
        val preset = ProtocolLibraryRepository.preset(this, id) ?: return null
        return ResearchSchedule(
            id = "__preset_run_${preset.id}",
            name = preset.name,
            target = SchedulerTarget.PRESET,
            targetValue = preset.id,
            frequency = SchedulerFrequency.DAILY,
            hour = 0,
            minute = 0,
            enabled = false
        )
    }

    private fun launchPreset(preset: CapabilityPreset, pipeSettings: Map<String, String> = emptyMap()) {
        SchedulerRepository.recordEvent(this, intent.getStringExtra("schedule_id").orEmpty(), "preset_started:${preset.name}")
        launchCapability(preset.methodId, preset.settingsJson, preset.payloadMode, preset.resultAction, pipeSettings)
    }

    private fun launchCapability(
        methodId: String,
        settingsJson: String,
        payloadMode: String = ProtocolPayloadMode.CORE,
        presetResultAction: String = PresetResultAction.HOME,
        pipeSettings: Map<String, String> = currentPipeSettings(activeSchedule)
    ) {
        startActivityForResult(Intent(this, IntentRouterActivity::class.java).apply {
            action = "com.example.methodmesh.EXECUTE_METHOD"
            putExtra("method_id", methodId)
            putExtra("input_payload_mode", ProtocolPayloadMode.normalize(payloadMode))
            putExtra("input_methodmesh_native_preset_run", "true")
            putExtra("input_methodmesh_preset_result_action", PresetResultAction.normalize(presetResultAction))
            if (finishToLauncher) putExtra("input_methodmesh_finish_to_launcher", "true")
            if (protocolId.isNotBlank()) {
                putExtra("input_methodmesh_protocol_step_run", "true")
                putExtra("input_methodmesh_sequence_step_run", "true")
            } else if (activeSchedule?.chainId?.isNotBlank() == true) {
                putExtra("input_methodmesh_sequence_step_run", "true")
            }
            pipeSettings.forEach { (key, value) ->
                if (value.isNotBlank()) putExtra("input_$key", value)
            }
            runCatching {
                val modifiers = JSONObject(settingsJson.ifBlank { "{}" })
                modifiers.keys().forEach { key ->
                    val value = modifiers.optString(key)
                    // Capability settings use the canonical external-input
                    // namespace so the normal RIL transport exposes them to
                    // the selected method. Plain extras are treated as ODK
                    // return placeholders and are intentionally ignored.
                    if (value.isNotBlank() || !hasExtra("input_$key")) {
                        putExtra("input_$key", value)
                    }
                }
            }
        }, 101)
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_.-]"), "_").trim('_').ifBlank { "methodmesh_run" }

    private fun returnedFields(data: Intent): Map<String, String?> {
        val extras = data.extras
        return extras?.keySet().orEmpty()
            .filterNot { it == "value" || it == "return_mode" || it == "payload_mode" }
            .associateWith { key -> extras?.getString(key) }
    }

    private fun hasMeaningfulPayload(fields: Map<String, String?>): Boolean =
        fields.any { (key, value) ->
            value?.isNotBlank() == true &&
                !key.startsWith("methodmesh_") &&
                !key.startsWith("diagnostic_")
        }

    private fun currentPipeSettings(schedule: ResearchSchedule?): Map<String, String> =
        when {
            protocolId.isNotBlank() -> readRunContext(protocolRunContextKey())
            schedule != null && schedule.chainId.isNotBlank() -> readRunContext(scheduleRunContextKey(schedule))
            else -> emptyMap()
        }

    private fun appendCurrentRunContext(schedule: ResearchSchedule, fields: Map<String, String?>) {
        val key = when {
            protocolId.isNotBlank() -> protocolRunContextKey()
            schedule.chainId.isNotBlank() -> scheduleRunContextKey(schedule)
            else -> return
        }
        appendRunContext(key, protocolStepIndex.takeIf { protocolId.isNotBlank() } ?: schedule.chainOrder, fields)
    }

    private fun protocolRunContextKey(
        submissionId: String = protocolSubmissionId,
        folder: String = outputGroupFolder,
        protocol: String = protocolId
    ): String = "protocol_run_context_${submissionId.ifBlank { folder }.ifBlank { protocol }}"

    private fun scheduleRunContextKey(schedule: ResearchSchedule): String =
        "schedule_run_context_${schedule.chainId.ifBlank { schedule.id }}"

    private fun scheduleChainFolderKey(schedule: ResearchSchedule): String =
        "schedule_chain_folder_${schedule.chainId}"

    private fun scheduleChainSubmissionKey(schedule: ResearchSchedule): String =
        "schedule_chain_submission_${schedule.chainId}"

    private fun scheduleChainNameKey(schedule: ResearchSchedule): String =
        "schedule_chain_name_${schedule.chainId}"

    private fun initialiseScheduleChainRun(schedule: ResearchSchedule) {
        val submissionId = UUID.randomUUID().toString()
        val timestamp = Instant.now().toString().replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val folder = "${safeName(scheduleChainName(schedule))}__${submissionId}___${timestamp}"
        getSharedPreferences("methodmesh_scheduler", MODE_PRIVATE).edit()
            .remove(scheduleRunContextKey(schedule))
            .putString(scheduleChainFolderKey(schedule), folder)
            .putString(scheduleChainSubmissionKey(schedule), submissionId)
            .putString(scheduleChainNameKey(schedule), scheduleChainName(schedule))
            .apply()
    }

    private fun scheduleChainRunFolder(schedule: ResearchSchedule): String =
        getSharedPreferences("methodmesh_scheduler", MODE_PRIVATE)
            .getString(scheduleChainFolderKey(schedule), null)
            .orEmpty()

    private fun scheduleChainSubmissionId(schedule: ResearchSchedule): String =
        getSharedPreferences("methodmesh_scheduler", MODE_PRIVATE)
            .getString(scheduleChainSubmissionKey(schedule), null)
            .orEmpty()
            .ifBlank { schedule.chainId.ifBlank { schedule.id } }

    private fun scheduleChainName(schedule: ResearchSchedule): String =
        getSharedPreferences("methodmesh_scheduler", MODE_PRIVATE)
            .getString(scheduleChainNameKey(schedule), null)
            .orEmpty()
            .ifBlank { schedule.name.ifBlank { "Scheduled chain" } }

    private fun appendRunContext(key: String, stepIndex: Int, fields: Map<String, String?>) {
        if (key.isBlank()) return
        val prior = JSONObject(getSharedPreferences("methodmesh_scheduler", MODE_PRIVATE).getString(key, "{}").orEmpty().ifBlank { "{}" })
        val steps = prior.optJSONArray("steps") ?: JSONArray()
        val stepFields = JSONObject()
        fields.forEach { (field, value) ->
            val text = value.orEmpty()
            if (text.isNotBlank()) {
                stepFields.put(field, text)
                prior.put("step_${stepIndex + 1}_$field", text)
                prior.put("previous_$field", text)
                prior.put(field, text)
            }
        }
        steps.put(JSONObject().apply {
            put("step_index", stepIndex)
            put("step_number", stepIndex + 1)
            put("stored_at", Instant.now().toString())
            put("fields", stepFields)
        })
        prior.put("steps", steps)
        getSharedPreferences("methodmesh_scheduler", MODE_PRIVATE).edit().putString(key, prior.toString()).apply()
    }

    private fun readRunContext(key: String): Map<String, String> = runCatching {
        val root = JSONObject(getSharedPreferences("methodmesh_scheduler", MODE_PRIVATE).getString(key, "{}").orEmpty().ifBlank { "{}" })
        buildMap {
            root.keys().forEach { key ->
                if (key != "steps") {
                    val value = root.optString(key)
                    if (value.isNotBlank()) put(key, value)
                }
            }
        }
    }.getOrDefault(emptyMap())

    private fun readRunContextSteps(key: String): List<ProtocolStepSummary> = runCatching {
        val root = JSONObject(getSharedPreferences("methodmesh_scheduler", MODE_PRIVATE).getString(key, "{}").orEmpty().ifBlank { "{}" })
        val steps = root.optJSONArray("steps") ?: JSONArray()
        (0 until steps.length()).mapNotNull { index ->
            val step = steps.optJSONObject(index) ?: return@mapNotNull null
            val fieldsObject = step.optJSONObject("fields") ?: JSONObject()
            val allFields = fieldsObject.keys().asSequence().associateWith { field -> fieldsObject.optString(field, "") }
            val coreFields = runSummaryCoreFields(OutputFormatter.projectFields(allFields, OutputFormatter.PayloadMode.CORE))
                .filterKeys { !it.startsWith("manual_step_") }
                .filterValues { it?.toString()?.isNotBlank() == true }
            val mediaFields = allFields
                .filter { (field, value) -> isHumanMediaUri(field, value) }
                .filterValues { it.isNotBlank() }
            ProtocolStepSummary(
                stepNumber = step.optInt("step_number", index + 1),
                fields = coreFields,
                media = mediaFields
            )
        }.filter { it.fields.isNotEmpty() || it.media.isNotEmpty() }
    }.getOrDefault(emptyList())

    private fun runSummaryCoreFields(fields: Map<String, Any?>): Map<String, Any?> {
        if ("random_first_number" in fields) {
            return fields.filterKeys { it == "random_first_number" }
        }
        if ("barcode_payload" in fields) {
            return fields.filterKeys { it == "barcode_payload" }
        }
        if ("plus_code" in fields) {
            return fields.filterKeys { it == "plus_code" }
        }
        if ("sampling_value" in fields) {
            return fields.filterKeys { it == "sampling_value" }
        }
        return fields
    }

    private fun showRunResult(runName: String, contextKey: String, folderName: String, methodId: String) {
        val steps = readRunContextSteps(contextKey)
        val combinedFields = combinedRunFields(steps)
        setContent {
            MethodMeshTheme {
                RunResultScreen(
                    runName = runName,
                    steps = steps,
                    onClose = { finishRun() },
                    onCopy = { includeJson ->
                        val text = runShareText(runName, steps) + if (includeJson) {
                            "\n\nmetadata.json\n${runMetadataJson(runName, steps)}"
                        } else {
                            ""
                        }
                        if (text.isBlank()) {
                            Toast.makeText(this, "Nothing to copy.", Toast.LENGTH_SHORT).show()
                        } else {
                            getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("MethodMesh result", text))
                            Toast.makeText(this, "Result copied.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShare = { includeJson ->
                        runCatching { shareRunResult(runName, steps, includeJson) }
                            .onFailure { Toast.makeText(this, "Share failed: ${it.message ?: "no share target"}", Toast.LENGTH_SHORT).show() }
                    },
                    onDownload = { includeJson ->
                        runCatching {
                            OutputExportRepository.saveToDownloads(
                                context = this,
                                label = runName,
                                text = runShareText(runName, steps),
                                mediaUris = steps.flatMap { it.media.values },
                                jsonText = if (includeJson) runMetadataJson(runName, steps) else ""
                            )
                        }
                            .onSuccess { Toast.makeText(this, "Saved ${it.summary}", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(this, "Downloads save failed: ${it.message ?: "storage error"}", Toast.LENGTH_SHORT).show() }
                    }
                )
            }
        }
    }

    private fun combinedRunFields(steps: List<ProtocolStepSummary>): Map<String, String?> =
        linkedMapOf<String, String?>().apply {
            steps.forEach { step ->
                step.fields.forEach { (key, value) -> put("step_${step.stepNumber}_$key", value?.toString()) }
                step.media.forEach { (key, value) -> put("step_${step.stepNumber}_$key", value) }
            }
        }

    private fun shareRunResult(runName: String, steps: List<ProtocolStepSummary>, includeJson: Boolean) {
        val mediaUris = steps.flatMap { step -> step.media.values.map(Uri::parse) }
        val text = runShareText(runName, steps)
        val shareable = ArrayList(mediaUris.map { shareableUri(it) })
        if (includeJson) shareable += temporaryJsonShareUri(runMetadataJson(runName, steps))
        if (shareable.isEmpty()) {
            if (text.isBlank()) throw IllegalStateException("No shareable result.")
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Share result").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val intent = if (shareable.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mediaMimeType(shareable.first().toString())
                putExtra(Intent.EXTRA_STREAM, shareable.first())
                if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareable)
            }
        }.apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share result").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun temporaryJsonShareUri(jsonText: String): Uri {
        val folder = File(cacheDir, "methodmesh_share").apply { mkdirs() }
        val file = File(folder, "metadata_${System.currentTimeMillis()}.json")
        file.writeText(jsonText, Charsets.UTF_8)
        return FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
    }

    private fun shareableUri(uri: Uri): Uri =
        if (uri.scheme == "file") {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(uri.path.orEmpty()))
        } else uri

    private fun isHumanMediaUri(key: String, value: String): Boolean {
        if (key.startsWith("methodmesh_") || key.startsWith("diagnostic_")) return false
        if (!(value.startsWith("content://") || value.startsWith("file://") || value.startsWith("/"))) return false
        val lower = value.lowercase()
        return key.contains("image", true) ||
            key.contains("photo", true) ||
            key.contains("pdf", true) ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".pdf")
    }

    private fun clearRunContext(key: String) {
        if (key.isBlank()) return
        getSharedPreferences("methodmesh_scheduler", MODE_PRIVATE).edit().remove(key).apply()
    }

    private fun submissionIdFromProtocolFolder(folderName: String): String {
        val leaf = folderName.substringAfterLast('/')
        return leaf.substringAfter("__", "").substringBefore("___", "")
            .takeIf { it.isNotBlank() }
            ?: leaf.substringAfterLast('_')
    }

    /**
     * A chained run has one clipboard destination. Keep the output from each
     * step instead of replacing it when the next step completes. The first
     * step starts a fresh buffer; later steps append in chain order.
     */
    private fun publishChainClipboard(schedule: ResearchSchedule, value: String, label: String) {
        if (value.isBlank()) return
        val key = "scheduler_clipboard_${schedule.chainId.ifBlank { schedule.id }}"
        val preferences = getSharedPreferences("methodmesh_scheduler", MODE_PRIVATE)
        val previous = if (schedule.chainOrder <= 0) "" else preferences.getString(key, "").orEmpty()
        val combined = listOf(previous, value).filter { it.isNotBlank() }.joinToString("\n\n")
        preferences.edit().putString(key, combined).apply()
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(label, combined))
    }

    private fun clearChainClipboard(schedule: ResearchSchedule) {
        val key = "scheduler_clipboard_${schedule.chainId.ifBlank { schedule.id }}"
        getSharedPreferences("methodmesh_scheduler", MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }
}

private data class ProtocolStepSummary(
    val stepNumber: Int,
    val fields: Map<String, Any?>,
    val media: Map<String, String> = emptyMap()
)

@Composable
private fun StepIntroScreen(
    title: String,
    detail: String,
    stepLabel: String,
    lastCompleted: String,
    onGo: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(stepLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        if (lastCompleted.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
            ) {
                Text(lastCompleted, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(18.dp))
        }
        Text("Next step", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(detail, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onGo, modifier = Modifier.fillMaxWidth()) { Text("Go") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel run") }
    }
}

@Composable
private fun ManualCompletionScreen(
    title: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Confirm step", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Some external screens cannot report completion automatically. Tell MethodMesh what happened so the run can continue safely.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onYes, modifier = Modifier.fillMaxWidth()) { Text("Yes, completed") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onNo, modifier = Modifier.fillMaxWidth()) { Text("No, try again") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel run") }
    }
}

@Composable
private fun CancelRunScreen(
    onKeepGoing: () -> Unit,
    onCancelRun: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Cancel run?", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("This will stop the current preset, protocol or schedule sequence.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onKeepGoing, modifier = Modifier.fillMaxWidth()) { Text("Keep going") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancelRun, modifier = Modifier.fillMaxWidth()) { Text("Yes, cancel") }
    }
}

@Composable
private fun RunResultScreen(
    runName: String,
    steps: List<ProtocolStepSummary>,
    onClose: () -> Unit,
    onCopy: (Boolean) -> Unit,
    onShare: (Boolean) -> Unit,
    onDownload: (Boolean) -> Unit
) {
    var includeFullJson by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Run result", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(runName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        if (steps.isEmpty()) {
            Text("Done.", style = MaterialTheme.typography.titleLarge)
        } else {
            steps.forEach { step ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Step ${step.stepNumber}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        step.fields.forEach { (key, value) ->
                            Text(prettyProtocolFieldLabel(key), style = MaterialTheme.typography.labelMedium)
                            Text(value?.toString().orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                        }
                        step.media.forEach { (key, value) ->
                            Text(prettyProtocolFieldLabel(key), style = MaterialTheme.typography.labelMedium)
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Include full JSON", style = MaterialTheme.typography.titleSmall)
            Switch(checked = includeFullJson, onCheckedChange = { includeFullJson = it })
        }
        Text(
            if (includeFullJson) "Share, copy and downloads will include metadata JSON." else "Share, copy and downloads use only the main result.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onShare(includeFullJson) }, modifier = Modifier.weight(1f), enabled = steps.isNotEmpty()) { Text("Share") }
            OutlinedButton(onClick = { onCopy(includeFullJson) }, modifier = Modifier.weight(1f), enabled = steps.any { it.fields.isNotEmpty() }) { Text("Copy") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onDownload(includeFullJson) }, modifier = Modifier.weight(1f), enabled = steps.isNotEmpty()) { Text("Save to Downloads") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Close") }
        }
    }
}

private fun prettyProtocolFieldLabel(key: String): String =
    when (key) {
        "barcode_payload" -> "Barcode"
        "random_first_number" -> "Random number"
        "plus_code" -> "Plus code"
        "sampling_value" -> "Sampling"
        else -> key.removePrefix("input_")
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
    }

private fun runShareText(runName: String, steps: List<ProtocolStepSummary>): String =
    buildString {
        appendLine(runName)
        steps.forEach { step ->
            step.fields.forEach { (key, value) ->
                appendLine("${prettyProtocolFieldLabel(key)}: ${value?.toString().orEmpty()}")
            }
        }
    }.trim()

private fun runMetadataJson(runName: String, steps: List<ProtocolStepSummary>): String =
    JSONObject().apply {
        put("methodmesh_output_schema", "methodmesh.run.summary")
        put("methodmesh_export_version", "1")
        put("run_name", runName)
        put("created_at", Instant.now().toString())
        put("step_count", steps.size)
        put("steps", JSONArray().apply {
            steps.forEach { step ->
                put(JSONObject().apply {
                    put("step_number", step.stepNumber)
                    put("fields", JSONObject().apply {
                        step.fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
                    })
                    put("media", JSONObject().apply {
                        step.media.forEach { (key, value) -> put(key, value) }
                    })
                })
            }
        })
    }.toString(2)

private fun mediaMimeType(value: String): String {
    val lower = value.lowercase()
    return when {
        lower.endsWith(".pdf") || lower.contains("pdf") -> "application/pdf"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.contains("image") -> "image/jpeg"
        else -> "*/*"
    }
}
