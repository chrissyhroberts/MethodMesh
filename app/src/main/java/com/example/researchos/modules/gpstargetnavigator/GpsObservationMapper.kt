package com.example.researchos.modules.gpstargetnavigator

import com.example.researchos.core.MethodOutput
import com.example.researchos.core.Observation
import com.example.researchos.core.Provenance

object GpsObservationMapper {

    fun fromFields(
        fields: Map<String, Any?>,
        methodId: String = As100LocateTargetMethod.ID,
        methodVersion: String = As100LocateTargetMethod.VERSION
    ): Observation {
        return Observation(
            output = MethodOutput(fields = fields),
            provenance = Provenance(
                methodId = methodId,
                methodVersion = methodVersion
            )
        )
    }

    fun fromOutput(
        output: MethodOutput,
        methodId: String = As100LocateTargetMethod.ID,
        methodVersion: String = As100LocateTargetMethod.VERSION
    ): Observation {
        return Observation(
            output = output,
            provenance = Provenance(
                methodId = methodId,
                methodVersion = methodVersion
            )
        )
    }
}
