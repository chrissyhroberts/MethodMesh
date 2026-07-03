package com.example.xlsformlab.core.researchos.runtime

import com.example.xlsformlab.core.researchos.ArchitectureRef
import com.example.xlsformlab.core.researchos.MethodContract
import com.example.xlsformlab.core.researchos.ExecutionRequest
import com.example.xlsformlab.core.researchos.ExecutionResult
import com.example.xlsformlab.core.researchos.MethodDescriptor
import com.example.xlsformlab.core.researchos.Signal
import com.example.xlsformlab.settings.SettingsState

/**
 * AS1.00 executable method.
 *
 * This is now the runtime-facing abstraction. Legacy Method classes may
 * still exist behind adapters during migration, but callers should depend on
 * As100Method rather than Method.
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
