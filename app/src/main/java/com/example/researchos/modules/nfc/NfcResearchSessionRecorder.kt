package com.example.researchos.modules.nfc

import com.example.researchos.core.Entity
import com.example.researchos.core.ObjectRef
import com.example.researchos.core.Observation
import com.example.researchos.core.RelationshipType
import com.example.researchos.core.ResearchRuntime

/**
 * Records NFC results into the live ResearchOS runtime session.
 *
 * NFC now contributes canonical AS/ResearchOS observations, transformations and
 * execution results. Legacy observations are retained only to keep current UI
 * screens and export previews working during migration.
 */
object NfcResearchSessionRecorder {

    private const val DEFAULT_PARTICIPANT_ID = "participant:001"
    private const val DEFAULT_PARTICIPANT_LABEL = "P001"

    fun record(observation: Observation): Observation {
        val session = ResearchRuntime.session

        val participant = session.entities
            .firstOrNull { it.type == "Participant" }
            ?: Entity(
                id = DEFAULT_PARTICIPANT_ID,
                type = "Participant",
                label = DEFAULT_PARTICIPANT_LABEL
            ).also { session.add(it) }

        session.add(observation)

        val relationshipAlreadyExists = session.graph().relationships.any {
            it.source.id == participant.id &&
                it.target.id == observation.id &&
                it.type == RelationshipType.Observes
        }

        if (!relationshipAlreadyExists) {
            session.relate(
                source = ObjectRef(
                    id = participant.id,
                    type = "Entity",
                    label = participant.label
                ),
                type = RelationshipType.Observes,
                target = ObjectRef(
                    id = observation.id,
                    type = "Observation"
                )
            )
        }

        return observation
    }

    fun recordReadBundle(bundle: NfcReadEvidenceBundle): Observation {
        ResearchRuntime.session.record(bundle.executionResult)
        return record(NfcObservationMapper.fromReadBundle(bundle))
    }

    fun recordWriteBundle(bundle: NfcWriteEvidenceBundle): Observation {
        ResearchRuntime.session.record(bundle.postWriteRead.executionResult)
        return record(NfcObservationMapper.fromWriteBundle(bundle))
    }
}
