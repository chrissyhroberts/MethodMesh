package com.example.researchos.modules.sms

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
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import java.security.MessageDigest

object SmsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SendSmsMethod.ID
    override val title = "Send SMS"
    override val description = "Send a short templated SMS message to a phone number."

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
        val phone = supplied.firstPresent("sms_phone", "input_sms_phone", "phone", "input_phone", "recipient_phone")
        val message = supplied.firstPresent("sms_message", "input_sms_message", "message", "input_message", "sms_message_template", "input_sms_message_template")
        var status by rememberSaveable { mutableStateOf("Ready to send SMS.") }
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
            val cleanPhone = phone.trim()
            val message = finalMessage().trim()
            if (cleanPhone.isBlank()) {
                status = "Enter a phone number."
                record(failedSmsValues(cleanPhone, message, "Missing phone number."))
                return
            }
            if (message.isBlank()) {
                status = "Enter a message."
                record(failedSmsValues(cleanPhone, message, "Missing SMS message."))
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
                if (parts.size > 1) manager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
                else manager.sendTextMessage(cleanPhone, null, message, null, null)
                status = "SMS sent to $cleanPhone."
                record(
                    mapOf(
                        SmsFields.PHONE to cleanPhone,
                        SmsFields.MESSAGE to message,
                        SmsFields.MESSAGE_SHA256 to sha256Hex(message),
                        SmsFields.STATUS to "sent",
                        SmsFields.PARTS to parts.size.toString(),
                        SmsFields.ERROR to ""
                    )
                )
            }.onFailure { error ->
                val messageText = error.message ?: error::class.java.simpleName
                status = "SMS failed: $messageText"
                record(failedSmsValues(cleanPhone, message, messageText))
            }
        }

        LaunchedEffect(requestedByPermission) {
            if (requestedByPermission) {
                requestedByPermission = false
                sendNow()
            }
        }
        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && !autoAttempted) {
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
            Text("Message preview", fontWeight = FontWeight.SemiBold)
            Text(finalMessage(), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Text("Recipient: ${phone.ifBlank { "not configured" }}", style = MaterialTheme.typography.bodySmall)
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            Button({ sendNow() }, Modifier.fillMaxWidth()) { Text(if (result == null) "Send SMS" else "Send again") }
        }
    }
}

private fun Map<String, String>.firstPresent(vararg keys: String): String = keys.firstNotNullOfOrNull { key -> get(key)?.trim()?.takeIf(String::isNotBlank) }.orEmpty()

private fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

@Suppress("DEPRECATION")
private fun smsManager(context: Context): SmsManager =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
    else SmsManager.getDefault()

private fun failedSmsValues(phone: String, message: String, error: String): Map<String, String> = mapOf(
    SmsFields.PHONE to phone,
    SmsFields.MESSAGE to message,
    SmsFields.MESSAGE_SHA256 to if (message.isBlank()) "" else sha256Hex(message),
    SmsFields.STATUS to "failed",
    SmsFields.PARTS to "0",
    SmsFields.ERROR to error
)

private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
