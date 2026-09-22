package com.example.methodmesh.core

import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100MethodRegistry
import com.example.methodmesh.modules.chance.As100DiceSimulationMethod
import org.junit.Assert.*
import org.junit.Test

class CapabilityCompositionTest {
    @Test fun `registered dice composition preserves fixed seed results and errors`() {
        val previous = As100MethodRegistry.all()
        try {
            As100MethodRegistry.install(listOf(As100DiceSimulationMethod))
            for (expression in listOf("3d6", "not-dice")) {
                val inputs = mapOf("expression" to expression, "rng_mode" to "fixed_seed", "seed" to "release-regression")
                val direct = As100DiceSimulationMethod.generate(inputs)
                val composed = As100ExecutionEngine.observationValues("dice.simulate", inputs)
                for (key in listOf("dice_status", "dice_total", "dice_last_values_csv", "dice_error")) {
                    assertEquals("$expression: $key", direct[key], composed[key])
                }
            }
        } finally { As100MethodRegistry.install(previous) }
    }
}
