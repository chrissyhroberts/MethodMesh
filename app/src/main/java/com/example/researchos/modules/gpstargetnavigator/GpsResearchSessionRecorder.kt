package com.example.researchos.modules.gpstargetnavigator

import com.example.researchos.core.Observation
import com.example.researchos.core.ResearchRuntime
import com.example.researchos.settings.SettingsState

/**
 * Records GPS capability outputs into the live ResearchOS session.
 *
 * The legacy GPS UI still owns permissions and live device interaction. This
 * recorder now writes both the compatibility Observation and the canonical
 * AS/ResearchOS ExecutionResult produced by As100LocateTargetMethod.
 */
object GpsResearchSessionRecorder {

    fun recordFromSettings(settingsState: SettingsState): Observation {
        val legacyObservation = As100LocateTargetMethod.buildObservation(settingsState)
        ResearchRuntime.session.add(legacyObservation)

        val result = As100LocateTargetMethod.execute(
            request = As100LocateTargetMethod.request(),
            settingsState = settingsState
        )
        ResearchRuntime.session.record(result)

        return legacyObservation
    }

    fun recordNavigationOutcome(fields: Map<String, Any?>): Observation {
        val legacyObservation = GpsObservationMapper.fromFields(fields)
        ResearchRuntime.session.add(legacyObservation)
        return legacyObservation
    }
}
