package com.example.methodmesh.modules.diving

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure diving-physics and gas-planning calculations.
 *
 * Deliberately excludes decompression scheduling, no-decompression limits,
 * treatment tables and equipment/manufacturer-specific supply settings.
 */
object DivingAlgorithms {
    const val STANDARD_GRAVITY_M_S2 = 9.80665
    const val STANDARD_SURFACE_PRESSURE_BAR = 1.01325
    const val DEFAULT_SEAWATER_DENSITY_KG_M3 = 1025.0
    const val DEFAULT_FRESHWATER_DENSITY_KG_M3 = 1000.0
    const val AIR_NITROGEN_FRACTION = 0.7902

    private const val R_J_MOL_K = 8.314462618
    private const val MOLAR_MASS_O2_G_MOL = 31.9988
    private const val MOLAR_MASS_N2_G_MOL = 28.0134
    private const val MOLAR_MASS_HE_G_MOL = 4.002602

    data class GasMix(
        val fo2: Double,
        val fhe: Double = 0.0
    ) {
        val fn2: Double get() = 1.0 - fo2 - fhe

        init {
            require(fo2 in 0.0..1.0) { "Oxygen fraction must be between 0 and 1." }
            require(fhe in 0.0..1.0) { "Helium fraction must be between 0 and 1." }
            require(fo2 + fhe <= 1.0 + 1e-9) { "Oxygen plus helium fractions cannot exceed 1." }
        }
    }

    data class SacRmvResult(
        val pressureUsedBar: Double,
        val averageAmbientPressureBar: Double,
        val averagePressureRatio: Double,
        val surfaceGasUsedL: Double,
        val sacBarMin: Double,
        val rmvLMin: Double
    )

    data class CylinderCapacityResult(
        val totalGasAtStartL: Double,
        val reserveGasL: Double,
        val usableGasL: Double
    )

    data class Segment(
        val label: String,
        val depthM: Double,
        val minutes: Double,
        val rmvMultiplier: Double = 1.0
    )

    data class SegmentGas(
        val label: String,
        val depthM: Double,
        val minutes: Double,
        val rmvMultiplier: Double,
        val ambientPressureBar: Double,
        val pressureRatio: Double,
        val surfaceGasL: Double
    )

    data class GasPlanResult(
        val segments: List<SegmentGas>,
        val totalSurfaceGasL: Double,
        val plannedPressureDropBar: Double,
        val requiredStartPressureBar: Double,
        val availableUsableGasL: Double,
        val marginGasL: Double,
        val marginPressureBar: Double,
        val meetsReserve: Boolean
    )

    data class BailoutPlanResult(
        val segments: List<SegmentGas>,
        val totalSurfaceGasL: Double,
        val requiredStartPressureBar: Double,
        val availableUsableGasL: Double,
        val marginGasL: Double,
        val marginPressureBar: Double,
        val meetsReserve: Boolean
    )

    data class GasDensityResult(
        val molarMassGmol: Double,
        val densityGL: Double
    )

    data class LiftResult(
        val minimumBagVolumeL: Double,
        val surfaceEquivalentFillGasL: Double,
        val ambientPressureBar: Double
    )

    fun ambientPressureBar(
        depthM: Double,
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): Double {
        require(depthM >= 0.0) { "Depth cannot be negative." }
        require(waterDensityKgM3 > 0.0) { "Water density must be positive." }
        require(surfacePressureBar > 0.0) { "Surface pressure must be positive." }
        val hydrostaticBar = waterDensityKgM3 * STANDARD_GRAVITY_M_S2 * depthM / 100_000.0
        return surfacePressureBar + hydrostaticBar
    }

    fun depthFromAmbientPressureBar(
        ambientPressureBar: Double,
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): Double {
        require(waterDensityKgM3 > 0.0) { "Water density must be positive." }
        require(surfacePressureBar > 0.0) { "Surface pressure must be positive." }
        if (ambientPressureBar <= surfacePressureBar) return 0.0
        return (ambientPressureBar - surfacePressureBar) * 100_000.0 /
            (waterDensityKgM3 * STANDARD_GRAVITY_M_S2)
    }

    fun pressureRatio(
        depthM: Double,
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): Double = ambientPressureBar(depthM, waterDensityKgM3, surfacePressureBar) / surfacePressureBar

    fun sacRmv(
        cylinderWaterVolumeL: Double,
        startPressureBar: Double,
        endPressureBar: Double,
        averageDepthM: Double,
        durationMin: Double,
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): SacRmvResult {
        require(cylinderWaterVolumeL > 0.0) { "Cylinder water volume must be positive." }
        require(startPressureBar >= 0.0 && endPressureBar >= 0.0) { "Cylinder pressure cannot be negative." }
        require(startPressureBar >= endPressureBar) { "Start pressure must be at least end pressure." }
        require(durationMin > 0.0) { "Duration must be positive." }
        val deltaBar = startPressureBar - endPressureBar
        val ambient = ambientPressureBar(averageDepthM, waterDensityKgM3, surfacePressureBar)
        val ratio = ambient / surfacePressureBar
        val surfaceGas = deltaBar * cylinderWaterVolumeL
        val rmv = surfaceGas / durationMin / ratio
        val sac = deltaBar / durationMin / ratio
        return SacRmvResult(deltaBar, ambient, ratio, surfaceGas, sac, rmv)
    }

    /**
     * Nominal cylinder gas volume using the common pressure x water-volume planning approximation.
     * Real-gas compressibility at high pressure is not modelled.
     */
    fun cylinderCapacity(
        cylinderWaterVolumeL: Double,
        startPressureBar: Double,
        reservePressureBar: Double = 0.0
    ): CylinderCapacityResult {
        require(cylinderWaterVolumeL > 0.0) { "Cylinder water volume must be positive." }
        require(startPressureBar >= 0.0) { "Start pressure cannot be negative." }
        require(reservePressureBar >= 0.0) { "Reserve pressure cannot be negative." }
        require(startPressureBar >= reservePressureBar) { "Start pressure must be at least reserve pressure." }
        return CylinderCapacityResult(
            totalGasAtStartL = cylinderWaterVolumeL * startPressureBar,
            reserveGasL = cylinderWaterVolumeL * reservePressureBar,
            usableGasL = cylinderWaterVolumeL * (startPressureBar - reservePressureBar)
        )
    }

    fun ppo2Bar(
        gas: GasMix,
        depthM: Double,
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): Double = gas.fo2 * ambientPressureBar(depthM, waterDensityKgM3, surfacePressureBar)

    fun modM(
        gas: GasMix,
        ppo2LimitBar: Double,
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): Double {
        require(gas.fo2 > 0.0) { "Oxygen fraction must be greater than zero." }
        require(ppo2LimitBar > 0.0) { "ppO2 limit must be positive." }
        val requiredAmbient = ppo2LimitBar / gas.fo2
        return depthFromAmbientPressureBar(requiredAmbient, waterDensityKgM3, surfacePressureBar)
    }

    fun bestMixFo2(
        depthM: Double,
        ppo2LimitBar: Double,
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): Double {
        require(ppo2LimitBar > 0.0) { "ppO2 limit must be positive." }
        val ambient = ambientPressureBar(depthM, waterDensityKgM3, surfacePressureBar)
        return min(1.0, ppo2LimitBar / ambient)
    }

    fun equivalentAirDepthM(
        gas: GasMix,
        depthM: Double,
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): Double {
        require(gas.fhe <= 1e-9) { "Equivalent air depth is defined here for nitrox/air only; helium fraction must be zero." }
        val ambient = ambientPressureBar(depthM, waterDensityKgM3, surfacePressureBar)
        val equivalentAmbient = ambient * gas.fn2 / AIR_NITROGEN_FRACTION
        return max(0.0, depthFromAmbientPressureBar(equivalentAmbient, waterDensityKgM3, surfacePressureBar))
    }

    /**
     * Equivalent narcotic depth under one of two explicit models:
     * - nitrogen_only: only N2 is treated as narcotic;
     * - oxygen_and_nitrogen: O2 and N2 are treated as narcotic and He as non-narcotic.
     *
     * The model name is intentionally explicit because oxygen narcotic potency is a debated assumption.
     */
    fun equivalentNarcoticDepthM(
        gas: GasMix,
        depthM: Double,
        model: String = "oxygen_and_nitrogen",
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): Double {
        val ambient = ambientPressureBar(depthM, waterDensityKgM3, surfacePressureBar)
        val equivalentAmbient = when (model.trim().lowercase()) {
            "nitrogen_only" -> ambient * gas.fn2 / AIR_NITROGEN_FRACTION
            "oxygen_and_nitrogen" -> ambient * (gas.fo2 + gas.fn2)
            else -> error("Unknown END model: $model")
        }
        return max(0.0, depthFromAmbientPressureBar(equivalentAmbient, waterDensityKgM3, surfacePressureBar))
    }

    fun gasDensity(
        gas: GasMix,
        depthM: Double,
        gasTemperatureC: Double = 20.0,
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): GasDensityResult {
        require(gasTemperatureC > -273.15) { "Gas temperature must be above absolute zero." }
        val molarMass = gas.fo2 * MOLAR_MASS_O2_G_MOL +
            gas.fn2 * MOLAR_MASS_N2_G_MOL +
            gas.fhe * MOLAR_MASS_HE_G_MOL
        val pressurePa = ambientPressureBar(depthM, waterDensityKgM3, surfacePressureBar) * 100_000.0
        val kelvin = gasTemperatureC + 273.15
        val densityKgM3 = pressurePa * (molarMass / 1000.0) / (R_J_MOL_K * kelvin)
        return GasDensityResult(molarMassGmol = molarMass, densityGL = densityKgM3)
    }

    fun gasPlan(
        baseRmvLMin: Double,
        cylinderWaterVolumeL: Double,
        startPressureBar: Double,
        reservePressureBar: Double,
        segments: List<Segment>,
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): GasPlanResult {
        require(baseRmvLMin > 0.0) { "RMV must be positive." }
        require(cylinderWaterVolumeL > 0.0) { "Cylinder water volume must be positive." }
        require(startPressureBar >= reservePressureBar) { "Start pressure must be at least reserve pressure." }
        require(reservePressureBar >= 0.0) { "Reserve pressure cannot be negative." }
        require(segments.isNotEmpty()) { "At least one gas-plan segment is required." }

        val computed = segments.map { segment ->
            require(segment.depthM >= 0.0) { "Segment depth cannot be negative." }
            require(segment.minutes >= 0.0) { "Segment duration cannot be negative." }
            require(segment.rmvMultiplier > 0.0) { "RMV multiplier must be positive." }
            val ambient = ambientPressureBar(segment.depthM, waterDensityKgM3, surfacePressureBar)
            val ratio = ambient / surfacePressureBar
            SegmentGas(
                label = segment.label,
                depthM = segment.depthM,
                minutes = segment.minutes,
                rmvMultiplier = segment.rmvMultiplier,
                ambientPressureBar = ambient,
                pressureRatio = ratio,
                surfaceGasL = baseRmvLMin * segment.rmvMultiplier * ratio * segment.minutes
            )
        }
        val total = computed.sumOf { it.surfaceGasL }
        val dropBar = total / cylinderWaterVolumeL
        val requiredStartBar = reservePressureBar + dropBar
        val available = cylinderWaterVolumeL * (startPressureBar - reservePressureBar)
        val marginL = available - total
        return GasPlanResult(
            segments = computed,
            totalSurfaceGasL = total,
            plannedPressureDropBar = dropBar,
            requiredStartPressureBar = requiredStartBar,
            availableUsableGasL = available,
            marginGasL = marginL,
            marginPressureBar = marginL / cylinderWaterVolumeL,
            meetsReserve = marginL >= -1e-9
        )
    }

    fun bailoutPlan(
        maxDepthM: Double,
        problemTimeMin: Double,
        stressedRmvLMin: Double,
        ascentRateMMin: Double,
        stopDepthM: Double,
        stopTimeMin: Double,
        cylinderWaterVolumeL: Double,
        startPressureBar: Double,
        reservePressureBar: Double,
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): BailoutPlanResult {
        require(maxDepthM >= 0.0) { "Maximum depth cannot be negative." }
        require(problemTimeMin >= 0.0) { "Problem-solving time cannot be negative." }
        require(stressedRmvLMin > 0.0) { "Stressed RMV must be positive." }
        require(ascentRateMMin > 0.0) { "Ascent rate must be positive." }
        require(stopDepthM in 0.0..maxDepthM) { "Stop depth must be between surface and maximum depth." }
        require(stopTimeMin >= 0.0) { "Stop time cannot be negative." }

        val segments = mutableListOf<Segment>()
        if (problemTimeMin > 0.0) {
            segments += Segment("problem", maxDepthM, problemTimeMin)
        }
        if (maxDepthM > stopDepthM) {
            val minutes = (maxDepthM - stopDepthM) / ascentRateMMin
            segments += Segment("ascent_to_stop", (maxDepthM + stopDepthM) / 2.0, minutes)
        }
        if (stopDepthM > 0.0 && stopTimeMin > 0.0) {
            segments += Segment("stop", stopDepthM, stopTimeMin)
        }
        if (stopDepthM > 0.0) {
            val minutes = stopDepthM / ascentRateMMin
            segments += Segment("ascent_to_surface", stopDepthM / 2.0, minutes)
        }
        if (segments.isEmpty()) {
            segments += Segment("surface", 0.0, 0.0)
        }

        val plan = gasPlan(
            baseRmvLMin = stressedRmvLMin,
            cylinderWaterVolumeL = cylinderWaterVolumeL,
            startPressureBar = startPressureBar,
            reservePressureBar = reservePressureBar,
            segments = segments,
            waterDensityKgM3 = waterDensityKgM3,
            surfacePressureBar = surfacePressureBar
        )
        return BailoutPlanResult(
            segments = plan.segments,
            totalSurfaceGasL = plan.totalSurfaceGasL,
            requiredStartPressureBar = plan.requiredStartPressureBar,
            availableUsableGasL = plan.availableUsableGasL,
            marginGasL = plan.marginGasL,
            marginPressureBar = plan.marginPressureBar,
            meetsReserve = plan.meetsReserve
        )
    }

    fun reciprocalHeadingDeg(headingDeg: Double): Double = ((normaliseHeading(headingDeg) + 180.0) % 360.0)

    fun travelTimeMin(distanceM: Double, speedMMin: Double): Double {
        require(distanceM >= 0.0) { "Distance cannot be negative." }
        require(speedMMin > 0.0) { "Speed must be positive." }
        return distanceM / speedMMin
    }

    fun travelDistanceM(timeMin: Double, speedMMin: Double): Double {
        require(timeMin >= 0.0) { "Time cannot be negative." }
        require(speedMMin >= 0.0) { "Speed cannot be negative." }
        return timeMin * speedMMin
    }

    fun liftBag(
        liftMassKg: Double,
        depthM: Double,
        waterDensityKgM3: Double = DEFAULT_SEAWATER_DENSITY_KG_M3,
        surfacePressureBar: Double = STANDARD_SURFACE_PRESSURE_BAR
    ): LiftResult {
        require(liftMassKg >= 0.0) { "Lift mass cannot be negative." }
        val waterDensityKgL = waterDensityKgM3 / 1000.0
        val bagVolumeL = if (waterDensityKgL > 0.0) liftMassKg / waterDensityKgL else 0.0
        val ambient = ambientPressureBar(depthM, waterDensityKgM3, surfacePressureBar)
        val ratio = ambient / surfacePressureBar
        return LiftResult(
            minimumBagVolumeL = bagVolumeL,
            surfaceEquivalentFillGasL = bagVolumeL * ratio,
            ambientPressureBar = ambient
        )
    }

    fun parseSegments(text: String): List<Segment> {
        return text.split(';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { index, token ->
                val parts = token.split(':').map { it.trim() }
                require(parts.size in 2..4) {
                    "Segment ${index + 1} must be depth:minutes, depth:minutes:multiplier, or label:depth:minutes:multiplier."
                }
                val hasLabel = parts.size == 4
                val label = if (hasLabel) parts[0].ifBlank { "segment_${index + 1}" } else "segment_${index + 1}"
                val offset = if (hasLabel) 1 else 0
                val depth = parts[offset].toDoubleOrNull() ?: error("Invalid depth in segment ${index + 1}.")
                val minutes = parts[offset + 1].toDoubleOrNull() ?: error("Invalid duration in segment ${index + 1}.")
                val multiplier = parts.getOrNull(offset + 2)?.toDoubleOrNull() ?: 1.0
                Segment(label, depth, minutes, multiplier)
            }
    }

    fun normaliseHeading(value: Double): Double {
        val modulo = value % 360.0
        return if (modulo < 0.0) modulo + 360.0 else modulo
    }

    fun roundUpTo(value: Double, increment: Double): Double {
        require(increment > 0.0) { "Increment must be positive." }
        return ceil(value / increment) * increment
    }

    fun straightLineUmbilicalMinimumM(depthM: Double, horizontalExcursionM: Double): Double {
        require(depthM >= 0.0) { "Depth cannot be negative." }
        require(horizontalExcursionM >= 0.0) { "Horizontal excursion cannot be negative." }
        return sqrt(depthM * depthM + horizontalExcursionM * horizontalExcursionM)
    }
}
