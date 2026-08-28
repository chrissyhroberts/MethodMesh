package com.example.methodmesh.core.methodmesh.runtime

import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.settings.SettingsState

/**
 * AS1.00 executable method.
 *
 * This is the sole runtime-facing executable method abstraction.
 */
interface As100Method {
    val id: String
    val ref: ArchitectureRef
    val descriptor: MethodDescriptor
    val contract: MethodContract

    fun request(
        action: String = id,
        context: Map<String, String> = emptyMap(),
        signals: List<Signal> = emptyList(),
        inputs: List<ArchitectureRef> = emptyList()
    ): ExecutionRequest

    fun execute(
        request: ExecutionRequest = request(),
        settingsState: SettingsState? = null,
        transport: String? = null
    ): ExecutionResult
}
