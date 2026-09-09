package com.example.methodmesh.core.scheduling

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.transport.android.IntentRouterActivity
import org.json.JSONObject

class SchedulePlanDispatchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val instance = SchedulePlanStore.instance(this, intent.getStringExtra("instance_id").orEmpty())
        val occurrence = instance?.occurrences?.firstOrNull { it.id == intent.getStringExtra("occurrence_id") }
        if (instance == null || occurrence == null) { finish(); return }
        val actionIndex = intent.getIntExtra("action_index", 0).coerceAtLeast(0)
        val action = occurrence.actions.getOrNull(actionIndex)
        if (action == null) { complete(instance.id, occurrence.id, ScheduleOccurrenceState.COMPLETED); return }
        if (action?.type == ScheduleActionType.PRESET) {
            val preset = ProtocolLibraryRepository.preset(this, action.presetId)
            if (preset == null) {
                complete(instance.id, occurrence.id, ScheduleOccurrenceState.FAILED)
                return
            }
            startActivityForResult(Intent(this, IntentRouterActivity::class.java).apply {
                this.action = "com.example.methodmesh.EXECUTE_METHOD"
                putExtra("method_id", preset.methodId)
                putExtra("input_methodmesh_native_preset_run", "true")
                putExtra("input_methodmesh_headless", "true")
                putExtra("input_methodmesh_preset_result_action", preset.resultAction)
                putExtra("action_index", actionIndex)
                runCatching { JSONObject(preset.settingsJson.ifBlank { "{}" }).keys().forEach { key -> putExtra("input_$key", JSONObject(preset.settingsJson).optString(key)) } }
            }, REQUEST_PRESET)
        } else {
            Toast.makeText(this, action?.message?.ifBlank { occurrence.laneName } ?: occurrence.laneName, Toast.LENGTH_LONG).show()
            continueOrComplete(instance.id, occurrence.id, actionIndex)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PRESET) return
        val instanceId = intent.getStringExtra("instance_id").orEmpty()
        val occurrenceId = intent.getStringExtra("occurrence_id").orEmpty()
        val instance = SchedulePlanStore.instance(this, instanceId)
        val occurrence = instance?.occurrences?.firstOrNull { it.id == occurrenceId }
        val actionIndex = intent.getIntExtra("action_index", 0)
        if (instance == null || occurrence == null || resultCode != RESULT_OK) {
            complete(instanceId, occurrenceId, ScheduleOccurrenceState.FAILED)
        } else {
            continueOrComplete(instanceId, occurrenceId, actionIndex)
        }
    }

    private fun continueOrComplete(instanceId: String, occurrenceId: String, actionIndex: Int) {
        val instance = SchedulePlanStore.instance(this, instanceId)
        val occurrence = instance?.occurrences?.firstOrNull { it.id == occurrenceId }
        if (instance == null || occurrence == null) { complete(instanceId, occurrenceId, ScheduleOccurrenceState.FAILED); return }
        val nextIndex = actionIndex + 1
        if (nextIndex >= occurrence.actions.size) {
            complete(instanceId, occurrenceId, ScheduleOccurrenceState.COMPLETED)
        } else {
            startActivity(Intent(this, SchedulePlanDispatchActivity::class.java).apply {
                putExtra("instance_id", instanceId)
                putExtra("occurrence_id", occurrenceId)
                putExtra("action_index", nextIndex)
            })
            finish()
        }
    }

    private fun complete(instanceId: String, occurrenceId: String, state: ScheduleOccurrenceState) {
        SchedulePlanStore.updateOccurrence(this, instanceId, occurrenceId, state, java.time.ZonedDateTime.now())
        SchedulePlanStore.instance(this, instanceId)?.let { SchedulePlanRuntime.armNext(this, it) }
        finish()
    }

    companion object { private const val REQUEST_PRESET = 701 }
}
