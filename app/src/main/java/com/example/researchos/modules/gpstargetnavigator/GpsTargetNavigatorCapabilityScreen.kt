package com.example.researchos.modules.gpstargetnavigator

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.settings.MethodSetting
import com.example.researchos.settings.SettingsState
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown

object GpsTargetNavigatorCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100LocateTargetMethod.ID
    override val title: String = "GPS target navigation"
    override val description: String = "Navigate to a configured target and confirm the navigation result."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val action = context.action
        val request = context.request
        val interaction = remember { GpsTargetNavigatorInteraction() }
        val settings = remember(action.settings) {
            SettingsState(interaction.settings).also { applyParameters(it, interaction.settings, action.settings) }
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun refreshResult() {
            val execution = As100LocateTargetMethod.execute(
                request = As100LocateTargetMethod.request(
                    action = action.canonicalId,
                    context = request.invocationContext.asMap(action.canonicalId) + action.settings
                ),
                settingsState = settings,
                transport = request.source
            ).withInvocationContext(request.invocationContext)
            result = execution
        }

        LaunchedEffect(context.isExternalInvocation) {
            if (context.isExternalInvocation) refreshResult()
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { refreshResult() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Navigate to the configured target, then review and confirm the saved navigation result.")
            Spacer(Modifier.height(10.dp))
            interaction.Render(settings)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { refreshResult() }) { Text(if (result == null) "Review GPS result" else "Refresh GPS result") }

            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100LocateTargetMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Basic GPS navigation",
                        description = "Navigate to default target",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100LocateTargetMethod.ID}')"
                    ),
                    IntentExample(
                        label = "With target coordinates",
                        description = "Specify target latitude and longitude",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100LocateTargetMethod.ID}',target_latitude='-1.28',target_longitude='36.81')"
                    ),
                    IntentExample(
                        label = "With arrival radius",
                        description = "Specify distance threshold for arrival detection",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100LocateTargetMethod.ID}',target_latitude='-1.28',target_longitude='36.81',arrival_radius='50')"
                    )
                )
            )
        }
    }

    private fun applyParameters(settingsState: SettingsState, settings: List<MethodSetting>, parameters: Map<String, String>) {
        settings.forEach { setting ->
            val raw = parameters[setting.id] ?: return@forEach
            when (setting) {
                is MethodSetting.BooleanSetting -> settingsState.setBoolean(setting.id, raw.toBooleanStrictOrNull() ?: raw == "1")
                is MethodSetting.IntSetting -> raw.toIntOrNull()?.let { settingsState.setInt(setting.id, it) }
                is MethodSetting.FloatSetting -> raw.toFloatOrNull()?.let { settingsState.setFloat(setting.id, it) }
                is MethodSetting.TextSetting -> settingsState.setString(setting.id, raw)
                is MethodSetting.ChoiceSetting -> settingsState.setString(setting.id, raw)
            }
        }
    }
}
