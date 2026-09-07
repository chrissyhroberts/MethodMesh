package com.example.methodmesh.modules.electrical

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

object ElectricalAlgorithms {
    data class OhmsResult(val voltageV: Double, val currentA: Double, val resistanceOhm: Double, val powerW: Double)
    data class AcPowerResult(val realPowerW: Double, val apparentPowerVa: Double, val reactivePowerVar: Double, val currentA: Double)
    data class VoltageDropResult(val dropV: Double, val dropPercent: Double, val loadVoltageV: Double, val conductorResistanceOhm: Double)
    data class NetworkResult(val equivalent: Double)
    data class EnergyResult(val powerW: Double, val energyWh: Double, val currentA: Double?, val runtimeHours: Double?)
    data class TimeConstantResult(val tauSeconds: Double, val cutoffHz: Double?)
    data class ResistorCodeResult(val resistanceOhm: Double, val tolerancePercent: Double, val label: String)

    fun ohmsLaw(voltageV: Double?, currentA: Double?, resistanceOhm: Double?, powerW: Double?): OhmsResult {
        val known = listOf(voltageV, currentA, resistanceOhm, powerW).count { it != null }
        require(known >= 2) { "Enter at least two of voltage, current, resistance and power." }
        voltageV?.let { require(it >= 0.0) }
        currentA?.let { require(it >= 0.0) }
        resistanceOhm?.let { require(it > 0.0) }
        powerW?.let { require(it >= 0.0) }

        var v = voltageV
        var i = currentA
        var r = resistanceOhm
        var p = powerW

        if (v != null && i != null) {
            if (r == null) r = if (i == 0.0) Double.POSITIVE_INFINITY else v / i
            if (p == null) p = v * i
        } else if (v != null && r != null) {
            if (i == null) i = v / r
            if (p == null) p = v * v / r
        } else if (i != null && r != null) {
            if (v == null) v = i * r
            if (p == null) p = i * i * r
        } else if (v != null && p != null) {
            if (i == null) i = if (v == 0.0) 0.0 else p / v
            if (r == null) r = if (p == 0.0) Double.POSITIVE_INFINITY else v * v / p
        } else if (i != null && p != null) {
            if (v == null) v = if (i == 0.0) 0.0 else p / i
            if (r == null) r = if (i == 0.0) Double.POSITIVE_INFINITY else p / (i * i)
        } else if (r != null && p != null) {
            if (i == null) i = sqrt(p / r)
            if (v == null) v = sqrt(p * r)
        }
        require(v != null && i != null && r != null && p != null) { "Unable to solve from supplied values." }
        fun consistent(supplied: Double?, solved: Double): Boolean {
            if (supplied == null) return true
            if (supplied.isInfinite() || solved.isInfinite()) return supplied == solved
            val scale = maxOf(1.0, abs(supplied), abs(solved))
            return abs(supplied - solved) <= 1e-6 * scale
        }
        require(consistent(voltageV, v) && consistent(currentA, i) && consistent(resistanceOhm, r) && consistent(powerW, p)) {
            "Supplied electrical values are inconsistent with Ohm's law / P = VI."
        }
        return OhmsResult(v, i, r, p)
    }

    fun acPower(
        phase: String,
        voltageV: Double,
        currentA: Double,
        powerFactor: Double
    ): AcPowerResult {
        require(voltageV >= 0 && currentA >= 0)
        require(powerFactor in 0.0..1.0)
        val apparent = when (phase.lowercase()) {
            "three_phase", "3", "3phase" -> sqrt(3.0) * voltageV * currentA
            else -> voltageV * currentA
        }
        val real = apparent * powerFactor
        val reactive = apparent * sqrt((1.0 - powerFactor * powerFactor).coerceAtLeast(0.0))
        return AcPowerResult(real, apparent, reactive, currentA)
    }

    fun voltageDrop(
        supplyVoltageV: Double,
        currentA: Double,
        lengthM: Double,
        conductorAreaMm2: Double,
        phase: String = "single_phase",
        material: String = "copper",
        temperatureC: Double = 20.0,
        powerFactor: Double = 1.0
    ): VoltageDropResult {
        require(supplyVoltageV > 0 && currentA >= 0 && lengthM >= 0 && conductorAreaMm2 > 0)
        require(powerFactor in 0.0..1.0)
        val rho20 = when (material.lowercase()) {
            "aluminium", "aluminum", "al" -> 0.0282
            else -> 0.017241
        } // ohm*mm2/m at 20 C, engineering approximation
        val alpha = when (material.lowercase()) {
            "aluminium", "aluminum", "al" -> 0.00403
            else -> 0.00393
        }
        val rhoT = rho20 * (1.0 + alpha * (temperatureC - 20.0))
        val routeFactor = when (phase.lowercase()) {
            "three_phase", "3", "3phase" -> sqrt(3.0)
            else -> 2.0
        }
        val resistance = rhoT * lengthM * routeFactor / conductorAreaMm2
        val drop = currentA * resistance * powerFactor
        return VoltageDropResult(drop, 100.0 * drop / supplyVoltageV, supplyVoltageV - drop, resistance)
    }

    fun equivalentResistance(valuesOhm: List<Double>, arrangement: String): NetworkResult {
        require(valuesOhm.isNotEmpty())
        require(valuesOhm.all { it > 0.0 })
        return if (arrangement.lowercase() == "parallel") {
            NetworkResult(1.0 / valuesOhm.sumOf { 1.0 / it })
        } else NetworkResult(valuesOhm.sum())
    }

    fun equivalentCapacitance(valuesFarad: List<Double>, arrangement: String): NetworkResult {
        require(valuesFarad.isNotEmpty())
        require(valuesFarad.all { it > 0.0 })
        return if (arrangement.lowercase() == "series") {
            NetworkResult(1.0 / valuesFarad.sumOf { 1.0 / it })
        } else NetworkResult(valuesFarad.sum())
    }

    fun energy(
        voltageV: Double?,
        currentA: Double?,
        powerW: Double?,
        durationHours: Double?,
        capacityAh: Double?
    ): EnergyResult {
        var p = powerW
        if (p == null && voltageV != null && currentA != null) p = voltageV * currentA
        require(p != null && p >= 0) { "Provide power, or voltage and current." }
        val energyWh = durationHours?.let { p * it } ?: capacityAh?.let { ah -> require(voltageV != null); voltageV * ah } ?: 0.0
        val inferredCurrent = currentA ?: if (voltageV != null && voltageV > 0) p / voltageV else null
        val runtime = if (capacityAh != null && inferredCurrent != null && inferredCurrent > 0) capacityAh / inferredCurrent else durationHours
        return EnergyResult(p, energyWh, inferredCurrent, runtime)
    }

    fun rcTimeConstant(resistanceOhm: Double, capacitanceFarad: Double): TimeConstantResult {
        require(resistanceOhm > 0 && capacitanceFarad > 0)
        val tau = resistanceOhm * capacitanceFarad
        return TimeConstantResult(tau, 1.0 / (2.0 * PI * tau))
    }

    fun rlTimeConstant(inductanceHenry: Double, resistanceOhm: Double): TimeConstantResult {
        require(inductanceHenry > 0 && resistanceOhm > 0)
        return TimeConstantResult(inductanceHenry / resistanceOhm, null)
    }

    private val digit = mapOf(
        "black" to 0, "brown" to 1, "red" to 2, "orange" to 3, "yellow" to 4,
        "green" to 5, "blue" to 6, "violet" to 7, "grey" to 8, "gray" to 8, "white" to 9
    )
    private val multiplier = digit.mapValues { 10.0.pow(it.value) } + mapOf("gold" to 0.1, "silver" to 0.01)
    private val tolerance = mapOf(
        "brown" to 1.0, "red" to 2.0, "green" to 0.5, "blue" to 0.25, "violet" to 0.1,
        "grey" to 0.05, "gray" to 0.05, "gold" to 5.0, "silver" to 10.0, "none" to 20.0
    )

    fun resistorBands(bands: List<String>): ResistorCodeResult {
        require(bands.size in 4..5) { "Use four or five resistor bands." }
        val b = bands.map { it.trim().lowercase() }
        val significantCount = if (b.size == 5) 3 else 2
        val significant = b.take(significantCount).fold(0) { acc, colour -> acc * 10 + (digit[colour] ?: error("Unknown digit band: $colour")) }
        val mult = multiplier[b[significantCount]] ?: error("Unknown multiplier band: ${b[significantCount]}")
        val tol = tolerance[b[significantCount + 1]] ?: error("Unknown tolerance band: ${b[significantCount + 1]}")
        val resistance = significant * mult
        return ResistorCodeResult(resistance, tol, formatEngineeringOhms(resistance) + " ±" + trim(tol) + "%")
    }

    fun formatEngineeringOhms(value: Double): String = when {
        abs(value) >= 1_000_000 -> "${trim(value / 1_000_000)} MΩ"
        abs(value) >= 1_000 -> "${trim(value / 1_000)} kΩ"
        else -> "${trim(value)} Ω"
    }

    fun parseNumberList(raw: String): List<Double> = raw.split(',', ';', ' ', '\n', '\t')
        .mapNotNull { it.trim().takeIf(String::isNotBlank)?.toDoubleOrNull() }

    private fun trim(value: Double): String = if (abs(value - value.toLong()) < 1e-10) value.toLong().toString()
        else String.format(java.util.Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
}
