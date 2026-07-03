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
            fields = bundle.outputFields(),
            methodId = As100NfcReadMethod.ID,
            methodVersion = As100NfcReadMethod.VERSION
        )
    }

    fun fromWriteBundle(
        bundle: NfcWriteEvidenceBundle
    ): Observation {
        return fromFields(
            fields = bundle.outputFields(),
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
