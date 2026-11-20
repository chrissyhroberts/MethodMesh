package com.example.methodmesh.modules.externaldisplay

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

object ExternalDisplayCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ExternalDisplayMethod.ID
    override val title = "External display"
    override val description = "Present MethodMesh content on a projector or TV, or hand off whole-device casting to Android."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        var refresh by rememberSaveable { mutableStateOf(0) }
        var state by remember(refresh) { mutableStateOf(ExternalDisplayManager.currentState(androidContext)) }
        val supplied = remember(context.request.settings, context.action.settings) { context.request.settings + context.action.settings }
        val mode = supplied["mode"].orEmpty().ifBlank { "present" }

        fun finish(values: Map<String, String>) {
            val request = As100ExternalDisplayMethod.request(As100ExternalDisplayMethod.ID, context.request.invocationContext.asMap(As100ExternalDisplayMethod.ID) + supplied, emptyList(), emptyList())
            onConfirmed(As100ExternalDisplayMethod.result(request, values, context.request.invocationContext))
        }

        LaunchedEffect(context.presentationMode, mode) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && context.submitsImmediately) {
                if (mode == "system_handoff") handoff(androidContext) else state.display?.let { ExternalDisplayManager.startPresentation(androidContext, it.displayId) }
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(description, style = MaterialTheme.typography.bodyLarge)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val display = state.display
                    Text(display?.name ?: "No suitable external display", style = MaterialTheme.typography.titleLarge)
                    Text(state.message)
                    Text(if (display == null) "Native wired video is device-dependent: the phone, USB-C port, adapter and display must all support video output." else "${display.width} × ${display.height} · display ${display.displayId}")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val display = state.display
                    if (display == null) finish(mapOf(ExternalDisplayFields.STATUS to "unavailable", ExternalDisplayFields.CONNECTION to "none", ExternalDisplayFields.NATIVE_VIDEO to "unknown", ExternalDisplayFields.SYSTEM_HANDOFF to "false", ExternalDisplayFields.MESSAGE to state.message, ExternalDisplayFields.ERROR to "No suitable external display is available."))
                    else if (ExternalDisplayManager.startPresentation(androidContext, display.displayId)) {
                        state = ExternalDisplayManager.currentState(androidContext)
                        val startedDisplay = display
                        finish(mapOf(ExternalDisplayFields.STATUS to "presenting", ExternalDisplayFields.DISPLAY_ID to startedDisplay.displayId.toString(), ExternalDisplayFields.DISPLAY_NAME to startedDisplay.name, ExternalDisplayFields.CONNECTION to "android_display", ExternalDisplayFields.NATIVE_VIDEO to "available", ExternalDisplayFields.SYSTEM_HANDOFF to "false", ExternalDisplayFields.BLANKED to "false", ExternalDisplayFields.MESSAGE to "Presentation started."))
                    }
                }) { Text("Present MethodMesh") }
                OutlinedButton(onClick = {
                    handoff(androidContext)
                    finish(mapOf(ExternalDisplayFields.STATUS to "system_handoff", ExternalDisplayFields.CONNECTION to "system_managed", ExternalDisplayFields.NATIVE_VIDEO to "device_dependent", ExternalDisplayFields.SYSTEM_HANDOFF to "true", ExternalDisplayFields.MESSAGE to "Android system casting controls opened."))
                }) { Text("System casting") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { ExternalDisplayManager.setBlanked(androidContext, true); state = ExternalDisplayManager.currentState(androidContext) }, enabled = state.mode != null) { Text("Blank") }
                OutlinedButton(onClick = { ExternalDisplayManager.stop(androidContext); refresh++ }, enabled = state.mode != null) { Text("Stop") }
                OutlinedButton(onClick = { refresh++ }) { Text("Refresh") }
            }
            Text("Whole-device mirroring and third-party apps such as YouTube remain OS-managed. MethodMesh does not claim to force a mirror or bypass Android privacy restrictions.", style = MaterialTheme.typography.bodySmall)
        }
    }

    private fun handoff(context: android.content.Context) {
        try { context.startActivity(ExternalDisplayManager.systemDisplaySettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: ActivityNotFoundException) { context.startActivity(ExternalDisplayManager.systemDisplaySettingsFallbackIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
