package com.example.methodmesh.transport.workflow

import com.example.methodmesh.core.protocols.PresetResultAction

/** Preserve native Home/Share routing, but only after the host confirms and attempts logging. */
internal object NativePresetCloseoutPolicy {
    fun opensHome(settings: Map<String, String>): Boolean {
        fun enabled(key: String) = settings[key] == "true" || settings["input_$key"] == "true"
        if (!enabled("methodmesh_native_preset_run") || enabled("methodmesh_finish_to_launcher") ||
            enabled("methodmesh_protocol_step_run") || enabled("methodmesh_sequence_step_run") || enabled("methodmesh_headless")) return false
        val action = PresetResultAction.normalize(settings["methodmesh_preset_result_action"] ?: settings["input_methodmesh_preset_result_action"] ?: PresetResultAction.HOME)
        return action != PresetResultAction.SAVE
    }
}
