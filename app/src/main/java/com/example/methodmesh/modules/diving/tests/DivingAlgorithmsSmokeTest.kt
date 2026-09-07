package com.example.methodmesh.modules.diving

import kotlin.math.abs

private fun assertNear(actual: Double, expected: Double, tolerance: Double, label: String) {
    check(abs(actual - expected) <= tolerance) { "$label: expected $expected +/- $tolerance, got $actual" }
}

fun main() {
    val ean32 = DivingAlgorithms.GasMix(0.32, 0.0)

    // Conventional 10 m seawater approximation is close to 2 ATA.
    val p10 = DivingAlgorithms.ambientPressureBar(10.0)
    assertNear(p10, 2.0188, 0.01, "ambient at 10 m")

    // EAN32 MOD at ppO2 1.4 is approximately 33-34 m depending on pressure assumptions.
    val mod = DivingAlgorithms.modM(ean32, 1.4)
    check(mod in 33.0..34.5) { "EAN32 MOD at 1.4 should be about 33-34 m; got $mod" }

    // At 30 m, EAN32 EAD is about 24-25 m under the exact hydrostatic model used here.
    val ead = DivingAlgorithms.equivalentAirDepthM(ean32, 30.0)
    check(ead in 24.0..25.5) { "EAN32 EAD at 30 m unexpected: $ead" }

    // 12 L cylinder, 200 -> 50 bar contains nominally 1800 usable surface litres.
    val cap = DivingAlgorithms.cylinderCapacity(12.0, 200.0, 50.0)
    assertNear(cap.usableGasL, 1800.0, 1e-9, "cylinder usable gas")

    // 12 L, 50 bar consumed over 20 min at ~20 m => RMV about 10 L/min.
    val rmv = DivingAlgorithms.sacRmv(12.0, 200.0, 150.0, 20.0, 20.0)
    check(rmv.rmvLMin in 9.5..10.5) { "RMV sanity check failed: ${rmv.rmvLMin}" }

    // Best mix at 30 m for ppO2 1.4 should be around 35%.
    val best = DivingAlgorithms.bestMixFo2(30.0, 1.4)
    check(best in 0.34..0.36) { "Best mix sanity check failed: $best" }

    // Helium lowers density substantially versus air at the same depth.
    val airDensity = DivingAlgorithms.gasDensity(DivingAlgorithms.GasMix(0.2095), 40.0).densityGL
    val trimixDensity = DivingAlgorithms.gasDensity(DivingAlgorithms.GasMix(0.18, 0.45), 40.0).densityGL
    check(trimixDensity < airDensity) { "Helium mix should be less dense than air." }

    // Gas-plan reserve pass/fail logic.
    val plan = DivingAlgorithms.gasPlan(
        baseRmvLMin = 18.0,
        cylinderWaterVolumeL = 12.0,
        startPressureBar = 220.0,
        reservePressureBar = 50.0,
        segments = listOf(
            DivingAlgorithms.Segment("bottom", 20.0, 20.0),
            DivingAlgorithms.Segment("stop", 5.0, 3.0)
        )
    )
    check(plan.totalSurfaceGasL > 0.0)
    check(plan.requiredStartPressureBar > 50.0)

    // Reciprocal headings.
    assertNear(DivingAlgorithms.reciprocalHeadingDeg(25.0), 205.0, 1e-9, "reciprocal")
    assertNear(DivingAlgorithms.reciprocalHeadingDeg(270.0), 90.0, 1e-9, "reciprocal wrap")

    println("DivingAlgorithms smoke tests passed")
}
