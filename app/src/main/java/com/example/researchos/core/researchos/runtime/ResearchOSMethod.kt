package com.example.researchos.core.researchos.runtime

import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.core.researchos.Signal
import com.example.researchos.settings.SettingsState

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
