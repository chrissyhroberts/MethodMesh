package com.example.researchos.modules.randomnumber

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

object RandomNumberModule : ResearchOSModule {
    override val moduleId = "randomnumber"
    override val displayName = "Random number generator"
    override val summary = "Generate secure or fixed-seed random numbers with count, range and step controls."

    override fun as100Methods() = listOf(As100RandomNumberMethod)

    override fun rilBindings() = listOf(
        RilBinding("generate random number", As100RandomNumberMethod.ID, "Generate a configurable random number"),
        RilBinding("generate random numbers", As100RandomNumberMethod.ID, "Generate a configurable set of random numbers")
    )

    override fun capabilityScreens() = listOf(RandomNumberCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100RandomNumberMethod.ID to listOf(
            MethodSetting.IntSetting("count", "How many numbers", defaultValue = 1, minimum = 1, maximum = 10000),
            MethodSetting.FloatSetting("min", "Minimum", defaultValue = 0f),
            MethodSetting.FloatSetting("max", "Maximum", defaultValue = 1f),
            MethodSetting.FloatSetting("step", "Step", defaultValue = 1f, minimum = 0.000001f),
            MethodSetting.ChoiceSetting("seed_mode", "Seed mode", defaultValue = "secure_random", choices = listOf("secure_random", "fixed_seed")),
            MethodSetting.TextSetting("seed", "Fixed seed", defaultValue = "")
        )
    )
}
