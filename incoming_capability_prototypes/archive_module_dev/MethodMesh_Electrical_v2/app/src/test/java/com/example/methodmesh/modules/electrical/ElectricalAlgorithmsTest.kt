package com.example.methodmesh.modules.electrical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectricalAlgorithmsTest {
    @Test fun ohmsLaw_solvesFromVoltageAndResistance() {
        val r = ElectricalAlgorithms.ohmsLaw(230.0, null, 46.0, null)
        assertEquals(5.0, r.currentA, 1e-9)
        assertEquals(1150.0, r.powerW, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun ohmsLaw_rejectsInconsistentSuppliedValues() {
        ElectricalAlgorithms.ohmsLaw(10.0, 2.0, 10.0, null)
    }

    @Test fun threePhasePower_usesRootThree() {
        val r = ElectricalAlgorithms.acPower("three_phase", 400.0, 10.0, 0.8)
        assertEquals(5542.5626, r.realPowerW, 0.001)
        assertEquals(6928.2032, r.apparentPowerVa, 0.001)
    }

    @Test fun voltageDrop_singlePhaseCopper() {
        val r = ElectricalAlgorithms.voltageDrop(230.0, 10.0, 20.0, 2.5)
        assertEquals(2.75856, r.dropV, 1e-5)
        assertEquals(1.19937, r.dropPercent, 1e-4)
    }

    @Test fun resistanceParallel() {
        val r = ElectricalAlgorithms.equivalentResistance(listOf(100.0, 100.0), "parallel")
        assertEquals(50.0, r.equivalent, 1e-9)
    }

    @Test fun rcTimeConstant() {
        val r = ElectricalAlgorithms.rcTimeConstant(1000.0, 1e-6)
        assertEquals(0.001, r.tauSeconds, 1e-12)
        assertEquals(159.154943, r.cutoffHz!!, 1e-6)
    }

    @Test fun fourBandResistor() {
        val r = ElectricalAlgorithms.resistorBands(listOf("brown", "black", "red", "gold"))
        assertEquals(1000.0, r.resistanceOhm, 1e-9)
        assertEquals(5.0, r.tolerancePercent, 1e-9)
        assertTrue(r.label.contains("1 kΩ"))
    }
}
