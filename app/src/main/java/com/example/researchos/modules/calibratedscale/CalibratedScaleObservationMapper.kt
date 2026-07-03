package com.example.researchos.modules.calibratedscale

import com.example.researchos.core.MethodOutput
import com.example.researchos.core.Observation
import com.example.researchos.core.Provenance

object CalibratedScaleObservationMapper {

    fun fromFields(
        fields: Map<String, Any?>,
        methodId: String = As100CalibratedScaleMethod.ID,
        methodVersion: String = As100CalibratedScaleMethod.VERSION
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
        methodId: String = As100CalibratedScaleMethod.ID,
        methodVersion: String = As100CalibratedScaleMethod.VERSION
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
