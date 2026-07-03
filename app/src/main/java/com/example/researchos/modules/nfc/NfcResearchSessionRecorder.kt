package com.example.researchos.modules.nfc

import com.example.researchos.core.Entity
import com.example.researchos.core.ObjectRef
import com.example.researchos.core.Observation
import com.example.researchos.core.RelationshipType
import com.example.researchos.core.ResearchRuntime

/**
 * Records NFC observations into the live ResearchOS runtime session.
 *
 * This keeps the existing NFC UI and legacy bundle APIs working while ensuring
 * that NFC read/write operations also contribute canonical Observation objects
 * to the active ResearchGraph.
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

    fun recordReadBundle(bundle: NfcReadEvidenceBundle): Observation =
        record(NfcObservationMapper.fromReadBundle(bundle))

    fun recordWriteBundle(bundle: NfcWriteEvidenceBundle): Observation =
        record(NfcObservationMapper.fromWriteBundle(bundle))
}
