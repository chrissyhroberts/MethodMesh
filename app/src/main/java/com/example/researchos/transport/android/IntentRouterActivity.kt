package com.example.researchos.transport.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.researchos.core.MethodRegistry
import com.example.researchos.core.MethodRuntime
import com.example.researchos.core.MethodExecutionRequest
import com.example.researchos.core.ResearchContext
import com.example.researchos.settings.MethodSetting
import com.example.researchos.settings.SettingsState
import com.example.researchos.transport.LaunchConfigParser
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.OutputFormatter

/**
 * Minimal transport adapter for ODK/Android callers.
 *
 * This activity intentionally contains no research logic. It parses launch configuration, resolves
 * a method, executes it through the runtime, serialises the output, and returns it to the caller.
 */
class IntentRouterActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
    }

    private fun route(intent: Intent) {
        val parsed = intent.dataString
            ?.let { LaunchConfigParser.parse(it) }
            ?: parseExtras(intent)

        val methodId = parsed.methodId
        if (methodId == null) {
            finishWithError("No method id supplied.")
            return
        }

        val method = MethodRegistry.find(methodId)
        if (method == null) {
            finishWithError("Unknown method: $methodId")
            return
        }

        val settingsState = SettingsState(method.settings)
        applyParameters(settingsState, method.settings, parsed.settings)

        val result = MethodRuntime.execute(
            method = method,
            request = MethodExecutionRequest(
                methodId = methodId,
                context = ResearchContext(parsed.context),
                parameters = parsed.settings,
                transport = parsed.source ?: "android_intent"
            ),
            settingsState = settingsState
        )

        if (!result.success || result.artifact == null) {
            finishWithError(result.errorMessage ?: "Method execution failed.")
            return
        }

        val returnMode = parsed.returnMode ?: ReturnMode.Json
        val output = OutputFormatter.format(
            artifact = result.artifact,
            returnMode = returnMode,
            includeProvenance = true
        )

        val data = Intent().apply {
            putExtra("value", output)
            putExtra("return_mode", returnMode.id)
            result.artifact.toRecord(includeProvenance = true).forEach { (key, value) ->
                putExtra(key, value?.toString())
            }
        }

        setResult(RESULT_OK, data)
        finish()
    }

    private fun parseExtras(intent: Intent) = LaunchConfigParser.parse(
        intent.extras
            ?.keySet()
            ?.joinToString(";") { key -> "$key=${intent.extras?.get(key)}" }
            ?: ""
    )

    private fun applyParameters(
        settingsState: SettingsState,
        settings: List<MethodSetting>,
        parameters: Map<String, String>
    ) {
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

    private fun finishWithError(message: String) {
        setResult(
            RESULT_CANCELED,
            Intent().apply { putExtra("error", message) }
        )
        finish()
    }
}
