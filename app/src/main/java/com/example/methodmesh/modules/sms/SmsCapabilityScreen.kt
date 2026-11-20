package com.example.methodmesh.modules.sms

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

object SmsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SendSmsMethod.ID
    override val title = "Send SMS"
    override val description = "Send an identical SMS separately to one or more phone numbers."

    @SuppressLint("MissingPermission")
    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val supplied = remember(context.request.settings, context.action.settings, context.request.invocationContext) {
            context.request.invocationContext.asMap(context.action.canonicalId) + context.request.settings + context.action.settings
        }
        var phone by rememberSaveable { mutableStateOf(supplied.firstPresent("sms_phone", "input_sms_phone", "phone", "input_phone", "recipient_phone")) }
        var message by rememberSaveable { mutableStateOf(supplied.firstPresent("sms_message", "input_sms_message", "message", "input_message", "sms_message_template", "input_sms_message_template")) }
        val needsRuntimePhone = phone.isBlank() ||
            context.settingIsRuntimeInput("sms_phone") ||
            context.settingIsRuntimeInput("phone")
        val needsRuntimeMessage = message.isBlank() ||
            context.settingIsRuntimeInput("sms_message") ||
            context.settingIsRuntimeInput("message")
        var status by rememberSaveable { mutableStateOf(if (needsRuntimePhone || needsRuntimeMessage) "Enter SMS details." else "Ready to send SMS.") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var requestedByPermission by remember { mutableStateOf(false) }
        var autoAttempted by rememberSaveable { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            status = if (granted) "SMS permission granted." else "SMS permission was denied."
            requestedByPermission = granted
        }

        LaunchedEffect(phone, message) {
            context.onSettingsChanged(
                mapOf(
                    "sms_phone" to phone,
                    "sms_message" to message
                )
            )
        }

        fun finalMessage(): String = message

        fun record(values: Map<String, String>) {
            val request = As100SendSmsMethod.request(
                action = As100SendSmsMethod.ID,
                context = context.request.invocationContext.asMap(As100SendSmsMethod.ID) + supplied,
                signals = emptyList(),
                inputs = emptyList()
            )
            result = As100SendSmsMethod.result(request, values, context.request.invocationContext)
        }

        fun sendNow() {
            val recipients = parseSmsRecipients(phone)
            val message = finalMessage().trim()
            if (recipients.isEmpty()) {
                status = "Enter at least one phone number."
                record(failedSmsValues(phone.trim(), message, "Missing phone number."))
                return
            }
            if (message.isBlank()) {
                status = "Enter a message."
                record(failedSmsValues(phone.trim(), message, "Missing SMS message."))
                return
            }
            if (!hasSmsPermission(androidContext)) {
                status = "SMS permission required."
                permissionLauncher.launch(Manifest.permission.SEND_SMS)
                return
            }
            runCatching {
                val manager = smsManager(androidContext)
                val parts = manager.divideMessage(message)
                val results = recipients.map { recipient ->
                    runCatching {
                        if (parts.size > 1) manager.sendMultipartTextMessage(recipient, null, parts, null, null)
                        else manager.sendTextMessage(recipient, null, message, null, null)
                        mapOf("phone" to recipient, "status" to "sent", "error" to "")
                    }.getOrElse { error ->
                        mapOf("phone" to recipient, "status" to "failed", "error" to (error.message ?: error::class.java.simpleName))
                    }
                }
                val failed = results.filter { it["status"] != "sent" }
                val overallStatus = when {
                    failed.isEmpty() -> "sent"
                    failed.size == results.size -> "failed"
                    else -> "partial"
                }
                status = when (overallStatus) {
                    "sent" -> "SMS sent individually to ${recipients.size} recipient(s)."
                    "partial" -> "SMS sent to ${recipients.size - failed.size} of ${recipients.size} recipients."
                    else -> "SMS failed for all ${recipients.size} recipients."
                }
                record(smsValues(phone.trim(), recipients, message, parts.size, overallStatus, results))
            }.onFailure { error ->
                val messageText = error.message ?: error::class.java.simpleName
                status = "SMS failed: $messageText"
                record(failedSmsValues(phone.trim(), message, messageText))
            }
        }

        LaunchedEffect(requestedByPermission) {
            if (requestedByPermission) {
                requestedByPermission = false
                sendNow()
            }
        }
        LaunchedEffect(context.startsImmediately, context.isNativePresetRun, needsRuntimePhone, needsRuntimeMessage) {
            val shouldAutoSend = context.startsImmediately || (context.isNativePresetRun && !needsRuntimePhone && !needsRuntimeMessage)
            if (shouldAutoSend && !autoAttempted && !needsRuntimePhone && !needsRuntimeMessage) {
                autoAttempted = true
                sendNow()
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; sendNow() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            if (needsRuntimePhone || needsRuntimeMessage) {
                Text("Enter SMS details.", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            if (needsRuntimePhone) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone numbers") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
            }
            if (needsRuntimeMessage) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("Recipients: ${parseSmsRecipients(phone).size}", style = MaterialTheme.typography.bodySmall)
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            Button({ sendNow() }, Modifier.fillMaxWidth(), enabled = phone.isNotBlank() && message.isNotBlank()) { Text(if (result == null) "Send SMS" else "Send again") }
        }
    }
}

internal fun parseSmsRecipients(value: String): List<String> = value
    .split(Regex("[,;\\r\\n]+"))
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

private fun Map<String, String>.firstPresent(vararg keys: String): String = keys.firstNotNullOfOrNull { key -> get(key)?.trim()?.takeIf(String::isNotBlank) }.orEmpty()

private fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

@Suppress("DEPRECATION")
private fun smsManager(context: Context): SmsManager =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
    else SmsManager.getDefault()

private fun failedSmsValues(phone: String, message: String, error: String): Map<String, String> = mapOf(
    SmsFields.PHONE to phone,
    SmsFields.RECIPIENTS_JSON to JSONArray(parseSmsRecipients(phone)).toString(),
    SmsFields.RECIPIENT_COUNT to parseSmsRecipients(phone).size.toString(),
    SmsFields.RECIPIENT_RESULTS_JSON to "[]",
    SmsFields.MESSAGE to message,
    SmsFields.MESSAGE_SHA256 to if (message.isBlank()) "" else sha256Hex(message),
    SmsFields.STATUS to "failed",
    SmsFields.PARTS to "0",
    SmsFields.ERROR to error
)

private fun smsValues(
    inputPhone: String,
    recipients: List<String>,
    message: String,
    parts: Int,
    status: String,
    results: List<Map<String, String>>
): Map<String, String> = mapOf(
    SmsFields.PHONE to inputPhone,
    SmsFields.RECIPIENTS_JSON to JSONArray(recipients).toString(),
    SmsFields.RECIPIENT_COUNT to recipients.size.toString(),
    SmsFields.RECIPIENT_RESULTS_JSON to JSONArray(results.map { JSONObject(it) }).toString(),
    SmsFields.MESSAGE to message,
    SmsFields.MESSAGE_SHA256 to sha256Hex(message),
    SmsFields.STATUS to status,
    SmsFields.PARTS to parts.toString(),
    SmsFields.ERROR to results.firstOrNull { it["status"] != "sent" }?.get("error").orEmpty()
)

private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
