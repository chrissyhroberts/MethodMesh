package com.example.researchos.modules.nfc

import com.example.researchos.core.MethodOutput
import com.example.researchos.core.Observation
import com.example.researchos.core.Provenance

object NfcObservationMapper {

    fun fromFields(
        fields: Map<String, Any?>,
        methodId: String = "method:nfc-read",
        methodVersion: String = "1.0.0"
    ): Observation {
        return Observation(
            output = MethodOutput(fields = fields),
            provenance = Provenance(
                methodId = methodId,
                methodVersion = methodVersion
            )
        )
    }

    fun fromReadBundle(
        bundle: NfcReadEvidenceBundle
    ): Observation {
        return fromFields(
            fields = bundle.evidence.values,
            methodId = As100NfcReadMethod.ID,
            methodVersion = As100NfcReadMethod.VERSION
        )
    }

    fun fromWriteBundle(
        bundle: NfcWriteEvidenceBundle
    ): Observation {
        val fields = linkedMapOf<String, Any?>().apply {
            put(NfcWriteFields.WRITE_SUCCESS, bundle.writeSuccess.toString())
            put(NfcWriteFields.WRITE_MESSAGE, bundle.writeMessage)
            put(NfcWriteFields.WRITE_RECORD_TYPE, bundle.intervention.inputs["record_type"].orEmpty())
            put(NfcWriteFields.WRITE_SIZE_BYTES, bundle.writeSizeBytes.toString())
            putAll(bundle.postWriteRead.evidence.values)
        }

        return fromFields(
            fields = fields,
            methodId = As100NfcWriteMethod.ID,
            methodVersion = As100NfcWriteMethod.VERSION
        )
    }

    /**
     * Backwards-compatible alias retained for the read migration slice.
     */
    fun fromBundle(
        bundle: NfcReadEvidenceBundle
    ): Observation = fromReadBundle(bundle)
}
