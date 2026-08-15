package com.example.researchos.core.scheduling

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import com.example.researchos.transport.android.IntentRouterActivity
import android.os.Bundle
import com.example.researchos.platform.externalforms.ExternalFormCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class SchedulerDispatchActivity : Activity() {
    private var resultHandled = false
    private val testChain: Boolean
        get() = intent.getBooleanExtra("test_chain", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val schedule = SchedulerRepository.get(this, intent.getStringExtra("schedule_id").orEmpty())
        if (schedule == null) { finish(); return }
        SchedulerRepository.recordEvent(this, schedule.id, "dispatch_started:${intent.getStringExtra("notification_kind").orEmpty().ifBlank { "direct" }}")
        if (schedule.chainOrder <= 0) clearChainClipboard(schedule)
        if (schedule.target == SchedulerTarget.WEB_FORM) {
            startActivityForResult(Intent(Intent.ACTION_VIEW, Uri.parse(schedule.targetValue)), 102)
            return
        }
        if (schedule.target == SchedulerTarget.CAPABILITY) {
            startActivityForResult(Intent(this, IntentRouterActivity::class.java).apply {
                action = "com.example.researchos.EXECUTE_METHOD"
                putExtra("method_id", schedule.targetValue)
                runCatching {
                    val modifiers = JSONObject(schedule.targetSettings)
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
            return
        }
        if (schedule.target == SchedulerTarget.CLIPBOARD) {
            publishChainClipboard(schedule, schedule.targetValue, "ResearchOS scheduled action")
            SchedulerRepository.markCompleted(this, schedule)
            val next = SchedulerRepository.nextInChain(this, schedule)
            if (next != null) {
                SchedulerRepository.recordEvent(this, next.id, "chain_dispatch_started")
                startActivity(Intent(this, SchedulerDispatchActivity::class.java)
                    .setAction("com.example.researchos.SCHEDULED_CHAIN_DISPATCH")
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
        val current = SchedulerRepository.get(this, intent.getStringExtra("schedule_id").orEmpty())
        // ODK Collect and browser-based forms commonly return RESULT_CANCELED even
        // after the external activity has completed. For those transports, returning
        // to ResearchOS is the completion signal. Capability calls retain strict
        // RESULT_OK semantics unless this is an explicit test run.
        val completed = resultCode == RESULT_OK || requestCode == 100 || requestCode == 102 || testChain
        if (requestCode == 101 && (resultCode == RESULT_OK || testChain) && data != null) {
            val output = data.getStringExtra("value").orEmpty().ifBlank {
                data.extras?.keySet().orEmpty()
                    .filterNot { it == "value" }
                    .sorted()
                    .joinToString("\n") { key -> "$key = ${data.extras?.get(key)}" }
            }
            if (output.isNotBlank()) {
                current?.let {
                    publishChainClipboard(it, output, "ResearchOS scheduled capability")
                    SchedulerRepository.recordEvent(this, it.id, "completed_output_copied")
                }
            }
        }
        if (completed && current != null) SchedulerRepository.markCompleted(this, current)
        else current?.let { SchedulerRepository.recordEvent(this, it.id, "cancelled") }
        val next = if (completed) current?.let { SchedulerRepository.nextInChain(this, it) } else null
        if (next != null) {
            SchedulerRepository.recordEvent(this, next.id, "chain_dispatch_started")
            startActivity(Intent(this, SchedulerDispatchActivity::class.java)
                .setAction("com.example.researchos.SCHEDULED_CHAIN_DISPATCH")
                .putExtra("schedule_id", next.id)
                .putExtra("test_chain", testChain))
        }
        finish()
    }

    /**
     * A chained run has one clipboard destination. Keep the output from each
     * step instead of replacing it when the next step completes. The first
     * step starts a fresh buffer; later steps append in chain order.
     */
    private fun publishChainClipboard(schedule: ResearchSchedule, value: String, label: String) {
        if (value.isBlank()) return
        val key = "scheduler_clipboard_${schedule.chainId.ifBlank { schedule.id }}"
        val preferences = getSharedPreferences("researchos_scheduler", MODE_PRIVATE)
        val previous = if (schedule.chainOrder <= 0) "" else preferences.getString(key, "").orEmpty()
        val combined = listOf(previous, value).filter { it.isNotBlank() }.joinToString("\n\n")
        preferences.edit().putString(key, combined).apply()
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(label, combined))
    }

    private fun clearChainClipboard(schedule: ResearchSchedule) {
        val key = "scheduler_clipboard_${schedule.chainId.ifBlank { schedule.id }}"
        getSharedPreferences("researchos_scheduler", MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }
}
