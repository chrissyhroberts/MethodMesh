package com.example.methodmesh.modules.tamagotchi

object TamagotchiCatalog {
    val creatures = listOf(
        CreatureDefinition("mossbit", "Mossbit", "tiny forest optimist", "collects buttons; leaf ears reveal more than Mossbit intends", 0xFFB8DEA2, 0xFFF3A9C8, .72, .58, .85, .52),
        CreatureDefinition("nib", "Nib", "maximum frill wiggle", "aquatic, curious, and operating with approximately three thoughts", 0xFFAADFEA, 0xFFE8A1CE, .66, .45, .78, .55),
        CreatureDefinition("mallow", "Mallow", "possibly fur, possibly cloud", "quietly social; tends to sit closer rather than complain", 0xFFF4E7E9, 0xFFC6B3E6, .91, .36, .67, .66),
        CreatureDefinition("pip", "Pip", "long ears, enormous feet", "bursts of enthusiasm followed by uncompromising naps", 0xFFF0CE9C, 0xFFE99985, .69, .88, .91, .81),
        CreatureDefinition("sprig", "Sprig", "mobile botanical problem", "leans toward light and regards watering as a major event", 0xFF9DDAA6, 0xFFFFD687, .48, .28, .72, .58),
        CreatureDefinition("tumble", "Tumble", "round, stoic, technically ambulatory", "expresses almost everything by changing rolling speed", 0xFFD4C4A8, 0xFF95B8D1, .52, .64, .32, .47)
    )

    val scenarios = listOf(
        ScenarioDefinition(
            id = "foundation_care",
            title = "Foundation Care",
            subtitle = "Learn how repeated care choices shape a changing system.",
            variables = listOf(
                StateVariable("satiety", "Satiety", initial = 68.0, driftPerSimulationHour = -3.6),
                StateVariable("hydration", "Hydration", initial = 76.0, driftPerSimulationHour = -2.8, measurable = true, unit = "%"),
                StateVariable("energy", "Energy", initial = 72.0, driftPerSimulationHour = -2.0),
                StateVariable("mood", "Mood", initial = 75.0, driftPerSimulationHour = -0.8),
                StateVariable("cleanliness", "Cleanliness", initial = 82.0, driftPerSimulationHour = -1.25),
                StateVariable("stress", "Stress", initial = 18.0, driftPerSimulationHour = 0.35),
                StateVariable("resilience", "Resilience", initial = 67.0, driftPerSimulationHour = 0.0, careVisible = false)
            ),
            actions = listOf(
                TamagotchiAction("feed", "Feed", CareGlyphKind.Feed, "Offer a normal meal.", mapOf("satiety" to 24.0, "mood" to 3.0), mapOf("stress" to -2.0), 5.0),
                TamagotchiAction("drink", "Drink", CareGlyphKind.Drink, "Offer fresh water.", mapOf("hydration" to 28.0), mapOf("stress" to -1.0), 3.0),
                TamagotchiAction("play", "Play", CareGlyphKind.Play, "Spend active time together.", mapOf("mood" to 16.0, "energy" to -9.0, "hydration" to -4.0), mapOf("stress" to -8.0), 15.0),
                TamagotchiAction("rest", "Rest", CareGlyphKind.Rest, "Settle somewhere cosy.", mapOf("energy" to 22.0), mapOf("stress" to -5.0), 30.0),
                TamagotchiAction("clean", "Clean", CareGlyphKind.Clean, "Freshen up creature and environment.", mapOf("cleanliness" to 34.0, "mood" to 2.0), emptyMap(), 12.0)
            ),
            generators = listOf(
                ProceduralGenerator(
                    id = "mystery_snacks",
                    labels = listOf("Moonberry puff", "Tiny cloud toast", "Polka-dot dumpling", "Moss biscuit", "Wobbly melon cube", "Star noodles"),
                    count = 3,
                    effectRanges = mapOf("satiety" to 10.0..28.0, "mood" to -2.0..9.0, "hydration" to -5.0..6.0),
                    hiddenEffectRanges = mapOf("stress" to -4.0..4.0),
                    glyph = CareGlyphKind.Sparkle
                )
            ),
            observationVariableIds = listOf("energy", "mood", "stress"),
            measurementVariableIds = listOf("hydration"),
            teachingNote = "The generated snack choices look similar but have seed-reproducible hidden properties."
        ),
        ScenarioDefinition(
            id = "infection_lab",
            title = "Hidden Infection",
            subtitle = "Observe, measure and treat while important state remains latent.",
            variables = listOf(
                StateVariable("hydration", "Hydration", initial = 72.0, driftPerSimulationHour = -3.5, measurable = true, unit = "%"),
                StateVariable("energy", "Energy", initial = 70.0, driftPerSimulationHour = -2.8),
                StateVariable("mood", "Mood", initial = 68.0, driftPerSimulationHour = -1.0),
                StateVariable("temperature", "Temperature", minimum = 35.0, maximum = 42.0, initial = 37.1, driftPerSimulationHour = 0.02, measurable = true, unit = "°C"),
                StateVariable("infection_burden", "Infection burden", initial = 27.0, driftPerSimulationHour = 1.8, careVisible = false),
                StateVariable("drug_susceptibility", "Drug susceptibility", initial = 62.0, driftPerSimulationHour = 0.0, careVisible = false),
                StateVariable("stress", "Stress", initial = 24.0, driftPerSimulationHour = 0.6)
            ),
            actions = listOf(
                TamagotchiAction("drink", "Offer water", CareGlyphKind.Drink, "Support hydration.", mapOf("hydration" to 25.0, "mood" to 2.0), emptyMap(), 5.0),
                TamagotchiAction("rest", "Rest", CareGlyphKind.Rest, "Reduce activity for a while.", mapOf("energy" to 16.0), mapOf("stress" to -5.0, "infection_burden" to -1.5), 45.0),
                TamagotchiAction("medicine_a", "Medicine A", CareGlyphKind.Medicine, "Give the first candidate treatment.", mapOf("mood" to -1.0), mapOf("infection_burden" to -12.0), 4.0),
                TamagotchiAction("medicine_b", "Medicine B", CareGlyphKind.Medicine, "Give the second candidate treatment.", mapOf("mood" to -2.0), mapOf("infection_burden" to -5.5, "hydration" to -2.0), 4.0)
            ),
            observationVariableIds = listOf("energy", "mood", "stress"),
            measurementVariableIds = listOf("temperature", "hydration"),
            teachingNote = "The care interface never directly reveals infection burden or drug susceptibility."
        ),
        ScenarioDefinition(
            id = "sprig_growth",
            title = "Sprig Growth Lab",
            subtitle = "A plant-care system with interacting light, water and nutrient inputs.",
            variables = listOf(
                StateVariable("moisture", "Soil moisture", initial = 62.0, driftPerSimulationHour = -2.4, measurable = true, unit = "%"),
                StateVariable("light", "Light exposure", initial = 48.0, driftPerSimulationHour = -0.25, measurable = true, unit = "%"),
                StateVariable("nutrition", "Nutrition", initial = 55.0, driftPerSimulationHour = -0.65),
                StateVariable("growth", "Growth", minimum = 0.0, maximum = 500.0, initial = 22.0, driftPerSimulationHour = 0.18, measurable = true, unit = "mm"),
                StateVariable("leaf_vigor", "Leaf vigor", initial = 74.0, driftPerSimulationHour = -0.4),
                StateVariable("root_stress", "Root stress", initial = 16.0, driftPerSimulationHour = 0.3, careVisible = false)
            ),
            actions = listOf(
                TamagotchiAction("water", "Water", CareGlyphKind.Drink, "Add water to the pot.", mapOf("moisture" to 30.0), mapOf("root_stress" to -2.0), 4.0),
                TamagotchiAction("sunny_spot", "Move to light", CareGlyphKind.Sparkle, "Put Sprig somewhere brighter.", mapOf("light" to 28.0, "leaf_vigor" to 4.0), emptyMap(), 6.0),
                TamagotchiAction("feed", "Add nutrients", CareGlyphKind.Feed, "Use a small nutrient feed.", mapOf("nutrition" to 26.0), mapOf("root_stress" to 2.0), 5.0)
            ),
            observationVariableIds = listOf("leaf_vigor"),
            measurementVariableIds = listOf("growth", "moisture", "light"),
            teachingNote = "Sprig demonstrates that the same generic engine can model non-animal longitudinal care systems."
        )
    )

    fun creature(id: String?) = creatures.firstOrNull { it.id == id } ?: creatures.first()
    fun scenario(id: String?) = scenarios.firstOrNull { it.id == id } ?: scenarios.first()
}
