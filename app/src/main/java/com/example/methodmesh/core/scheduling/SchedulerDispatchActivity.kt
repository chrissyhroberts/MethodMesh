package com.example.methodmesh.core.scheduling

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import com.example.methodmesh.transport.android.IntentRouterActivity
import android.os.Bundle
import android.widget.Toast
import com.example.methodmesh.core.protocols.CapabilityPreset
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.core.protocols.ProtocolOutputMode
import com.example.methodmesh.core.protocols.ProtocolPayloadMode
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.platform.externalforms.ExternalFormCatalog
import com.example.methodmesh.transport.OutputExportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class SchedulerDispatchActivity : Activity() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val schedule = SchedulerRepository.get(this, intent.getStringExtra("schedule_id").orEmpty())
            ?: transientProtocolSchedule()
            ?: transientPresetSchedule()
        if (schedule == null) { finish(); return }
        activeSchedule = schedule
        SchedulerRepository.recordEvent(this, schedule.id, "dispatch_started:${intent.getStringExtra("notification_kind").orEmpty().ifBlank { "direct" }}")
        if (schedule.chainOrder <= 0) clearChainClipboard(schedule)
        if (schedule.target == SchedulerTarget.PROTOCOL && outputGroupFolder.isBlank()) {
            launchProtocolStep(schedule, schedule.targetValue, 0)
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
            launchPreset(preset)
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
            launchPreset(preset)
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
                    .putExtra("test_chain", testChain))
            }
            finish()
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
        // ODK Collect and browser-based forms commonly return RESULT_CANCELED even
        // after the external activity has completed. For those transports, returning
        // to MethodMesh is the completion signal. Capability calls retain strict
        // RESULT_OK semantics unless this is an explicit test run.
        val completed = resultCode == RESULT_OK || requestCode == 100 || requestCode == 102 || testChain
        val activeProtocol = if (completed && current != null && protocolId.isNotBlank()) {
            ProtocolLibraryRepository.protocol(this, protocolId)
        } else null
        val nextProtocolStep = activeProtocol?.steps?.sortedBy { it.order }?.getOrNull(protocolStepIndex + 1)
        if (requestCode == 101 && (resultCode == RESULT_OK || testChain) && data != null) {
            val output = data.getStringExtra("value").orEmpty().ifBlank {
                data.extras?.keySet().orEmpty()
                    .filterNot { it == "value" }
                    .sorted()
                    .joinToString("\n") { key -> "$key = ${data.extras?.get(key)}" }
            }
            if (output.isNotBlank()) {
                current?.let {
                    if (it.target == SchedulerTarget.CLIPBOARD) {
                        publishChainClipboard(it, output, "MethodMesh scheduled capability")
                        SchedulerRepository.recordEvent(this, it.id, "completed_output_copied")
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
                            val exported = exportReturnedFields(it, data)
                            val event = if (mode == ProtocolOutputMode.SHARE) "completed_output_share_ready" else "completed_output_exported"
                            SchedulerRepository.recordEvent(this, it.id, "$event:${exported.folderName}")
                            if (nextProtocolStep == null) {
                                val message = if (mode == ProtocolOutputMode.SHARE) {
                                    "MethodMesh output ready to share: ${exported.folderName}"
                                } else {
                                    "Saved MethodMesh output: ${exported.folderName}"
                                }
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                                OutputExportRepository.notifySaved(this, exported)
                            }
                        }
                    }
                }
            }
        }
        if (completed && current != null && !transientProtocolRun && !transientPresetRun) SchedulerRepository.markCompleted(this, current)
        else if (completed && current != null) SchedulerRepository.recordEvent(this, current.id, "completed")
        else current?.let { SchedulerRepository.recordEvent(this, it.id, "cancelled") }
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
                    .putExtra("test_chain", testChain))
                finish()
                return
            }
        }
        val next = if (completed) current?.let { SchedulerRepository.nextInChain(this, it) } else null
        if (next != null) {
            SchedulerRepository.recordEvent(this, next.id, "chain_dispatch_started")
            startActivity(Intent(this, SchedulerDispatchActivity::class.java)
                .setAction("com.example.methodmesh.SCHEDULED_CHAIN_DISPATCH")
                .putExtra("schedule_id", next.id)
                .putExtra("test_chain", testChain))
        }
        finish()
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
        startActivity(Intent(this, SchedulerDispatchActivity::class.java)
            .setAction("com.example.methodmesh.SCHEDULED_PROTOCOL_DISPATCH")
            .putExtra("schedule_id", schedule.id)
            .putExtra("protocol_id", protocol.id)
            .putExtra("protocol_step_index", stepIndex)
            .putExtra("transient_protocol_run", transientProtocolRun)
            .putExtra("output_group_folder", group)
            .putExtra("protocol_submission_id", submissionId)
            .putExtra("suppress_output", suppressOutput)
            .putExtra("test_chain", testChain))
        finish()
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

    private fun launchPreset(preset: CapabilityPreset) {
        SchedulerRepository.recordEvent(this, intent.getStringExtra("schedule_id").orEmpty(), "preset_started:${preset.name}")
        launchCapability(preset.methodId, preset.settingsJson, preset.payloadMode, preset.resultAction)
    }

    private fun launchCapability(
        methodId: String,
        settingsJson: String,
        payloadMode: String = ProtocolPayloadMode.CORE,
        presetResultAction: String = PresetResultAction.HOME
    ) {
        startActivityForResult(Intent(this, IntentRouterActivity::class.java).apply {
            action = "com.example.methodmesh.EXECUTE_METHOD"
            putExtra("method_id", methodId)
            putExtra("input_payload_mode", ProtocolPayloadMode.normalize(payloadMode))
            putExtra("input_methodmesh_native_preset_run", "true")
            putExtra("input_methodmesh_preset_result_action", PresetResultAction.normalize(presetResultAction))
            runCatching {
                val modifiers = JSONObject(settingsJson.ifBlank { "{}" })
                modifiers.keys().forEach { key ->
                    val value = modifiers.optString(key)
                    // Capability settings use the canonical external-input
                    // namespace so the normal RIL transport exposes them to
                    // the selected method. Plain extras are treated as ODK
                    // return placeholders and are intentionally ignored.
                    putExtra("input_$key", value)
                }
            }
        }, 101)
    }

    private fun exportReturnedFields(schedule: ResearchSchedule, data: Intent): OutputExportRepository.ExportPackage {
        val extras = data.extras
        val fields = extras?.keySet().orEmpty()
            .filterNot { it == "value" || it == "return_mode" || it == "payload_mode" }
            .associateWith { key -> extras?.get(key)?.toString() }
        val methodId = fields["methodmesh_method_id"] ?: schedule.targetValue
        val status = fields["methodmesh_status"] ?: "Succeeded"
        val protocol = protocolId.takeIf { it.isNotBlank() }?.let { ProtocolLibraryRepository.protocol(this, it) }
        val protocolStep = protocol?.steps?.sortedBy { it.order }?.getOrNull(protocolStepIndex)
        val label = when {
            protocol != null -> "${"%02d".format(protocolStepIndex + 1)}_${safeName(protocolStep?.name ?: schedule.name)}"
            schedule.target == SchedulerTarget.PRESET -> safeName(schedule.name)
            else -> safeName(methodId)
        }
        val parent = outputGroupFolder.takeIf { it.isNotBlank() }
        if (protocol != null && parent != null) {
            return OutputExportRepository.exportProtocolStepPackage(
                context = this,
                protocolFolder = parent,
                protocolName = protocol.name,
                protocolSubmissionId = protocolSubmissionId.ifBlank { submissionIdFromProtocolFolder(parent) },
                stepIndex = protocolStepIndex,
                stepName = protocolStep?.name ?: schedule.name,
                methodId = methodId,
                status = status,
                fields = fields
            )
        }
        return OutputExportRepository.exportFlatPackage(
            context = this,
            packageLabel = label,
            methodId = methodId,
            status = status,
            fields = fields,
            parentFolder = parent
        )
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_.-]"), "_").trim('_').ifBlank { "methodmesh_run" }

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
