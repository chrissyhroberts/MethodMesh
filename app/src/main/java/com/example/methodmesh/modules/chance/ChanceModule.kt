package com.example.methodmesh.modules.chance

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

/** Umbrella module for visual randomisation and concealed-choice tools. */
object ChanceModule : MethodMeshModule {
    override val moduleId = "chance"
    override val displayName = "Chance"
    override val summary = "Dice, coins, cards, concealed picks, spinners and weighted random choices."
    override val iconKey = "random"

    override fun as100Methods() = listOf(
        As100DiceSimulationMethod,
        As100CoinTossMethod,
        As100CardDrawMethod,
        As100PickOneMethod,
        As100SpinnerMethod,
        As100WeightedChoiceMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("roll dice", As100DiceSimulationMethod.ID, "Roll one or more dice"),
        RilBinding("simulate dice", As100DiceSimulationMethod.ID, "Simulate a simple or advanced dice expression"),
        RilBinding("roll d20", As100DiceSimulationMethod.ID, "Roll a twenty-sided die"),
        RilBinding("roll with advantage", As100DiceSimulationMethod.ID, "Roll two d20 and keep the highest"),
        RilBinding("roll with disadvantage", As100DiceSimulationMethod.ID, "Roll two d20 and keep the lowest"),
        RilBinding("roll percentile", As100DiceSimulationMethod.ID, "Roll percentile dice"),
        RilBinding("toss coin", As100CoinTossMethod.ID, "Toss a fair coin one or more times"),
        RilBinding("flip coin", As100CoinTossMethod.ID, "Toss a fair coin one or more times"),
        RilBinding("draw card", As100CardDrawMethod.ID, "Draw a card from a standard deck"),
        RilBinding("draw cards", As100CardDrawMethod.ID, "Draw one or more cards without replacement"),
        RilBinding("pick one", As100PickOneMethod.ID, "Lay out concealed choices and let the user pick one"),
        RilBinding("choose one", As100PickOneMethod.ID, "Lay out concealed choices and let the user pick one"),
        RilBinding("spin wheel", As100SpinnerMethod.ID, "Spin an equal-segment choice wheel"),
        RilBinding("spin spinner", As100SpinnerMethod.ID, "Spin an equal-segment choice wheel"),
        RilBinding("weighted choice", As100WeightedChoiceMethod.ID, "Choose from labelled options using numeric weights"),
        RilBinding("random weighted choice", As100WeightedChoiceMethod.ID, "Choose from labelled options using numeric weights")
    )

    override fun capabilityScreens() = listOf(
        DiceSimulatorCapabilityScreen,
        CoinTossCapabilityScreen,
        CardDrawCapabilityScreen,
        PickOneCapabilityScreen,
        SpinnerCapabilityScreen,
        WeightedChoiceCapabilityScreen
    )

    override fun capabilitySettings() = mapOf(
        As100DiceSimulationMethod.ID to listOf(
            MethodSetting.TextSetting("expression", "Dice expression", defaultValue = "d6"),
            MethodSetting.IntSetting("roll_count", "Rolls per player", defaultValue = 1, minimum = 1, maximum = 1000),
            MethodSetting.IntSetting("player_count", "Players", defaultValue = 1, minimum = 1, maximum = 20),
            MethodSetting.ChoiceSetting("player_mode", "Player presentation", defaultValue = "take_turns", choices = listOf("take_turns", "around_table")),
            MethodSetting.BooleanSetting("history_output", "Return full simulation series", defaultValue = true),
            MethodSetting.ChoiceSetting("rng_mode", "Random source", defaultValue = "secure_random", choices = listOf("secure_random", "fixed_seed")),
            MethodSetting.TextSetting("seed", "Fixed seed", defaultValue = ""),
            MethodSetting.ChoiceSetting("animation_mode", "Animation", defaultValue = "full", choices = listOf("full", "fast", "off"))
        ),
        As100CoinTossMethod.ID to listOf(
            MethodSetting.IntSetting("toss_count", "Number of tosses", defaultValue = 1, minimum = 1, maximum = 10000),
            MethodSetting.ChoiceSetting("interaction_mode", "Single-coin mode", defaultValue = "record", choices = listOf("record", "just_toss")),
            MethodSetting.BooleanSetting("history_output", "Return full toss series", defaultValue = true),
            MethodSetting.ChoiceSetting("rng_mode", "Random source", defaultValue = "secure_random", choices = listOf("secure_random", "fixed_seed")),
            MethodSetting.TextSetting("seed", "Fixed seed", defaultValue = ""),
            MethodSetting.ChoiceSetting("animation_mode", "Animation", defaultValue = "full", choices = listOf("full", "fast", "off"))
        ),
        As100CardDrawMethod.ID to listOf(
            MethodSetting.IntSetting("draw_count", "Cards per player", defaultValue = 1, minimum = 1, maximum = 54),
            MethodSetting.IntSetting("player_count", "Players", defaultValue = 1, minimum = 1, maximum = 20),
            MethodSetting.ChoiceSetting("player_mode", "Player presentation", defaultValue = "take_turns", choices = listOf("take_turns", "around_table")),
            MethodSetting.BooleanSetting("include_jokers", "Include jokers", defaultValue = false),
            MethodSetting.ChoiceSetting("rng_mode", "Random source", defaultValue = "secure_random", choices = listOf("secure_random", "fixed_seed")),
            MethodSetting.TextSetting("seed", "Fixed seed", defaultValue = ""),
            MethodSetting.ChoiceSetting("animation_mode", "Animation", defaultValue = "full", choices = listOf("full", "fast", "off"))
        ),
        As100PickOneMethod.ID to listOf(
            MethodSetting.ChoiceSetting("set_type", "Choice set", defaultValue = "numbers", choices = listOf("numbers", "colours", "suits", "abstract", "classic_cards", "custom")),
            MethodSetting.IntSetting("item_count", "Number of choices", defaultValue = 6, minimum = 2, maximum = 12),
            MethodSetting.TextSetting("custom_items", "Custom choices", defaultValue = ""),
            MethodSetting.IntSetting("selected_position", "Selected position", defaultValue = 0, minimum = 0, maximum = 12),
            MethodSetting.BooleanSetting("reveal_remaining", "Allow reveal remaining", defaultValue = true),
            MethodSetting.ChoiceSetting("rng_mode", "Random source", defaultValue = "secure_random", choices = listOf("secure_random", "fixed_seed")),
            MethodSetting.TextSetting("seed", "Fixed seed", defaultValue = ""),
            MethodSetting.ChoiceSetting("animation_mode", "Animation", defaultValue = "full", choices = listOf("full", "fast", "off"))
        ),
        As100SpinnerMethod.ID to listOf(
            MethodSetting.TextSetting("items", "Spinner labels", defaultValue = "Yes\nNo"),
            MethodSetting.ChoiceSetting("rng_mode", "Random source", defaultValue = "secure_random", choices = listOf("secure_random", "fixed_seed")),
            MethodSetting.TextSetting("seed", "Fixed seed", defaultValue = ""),
            MethodSetting.ChoiceSetting("animation_mode", "Animation", defaultValue = "full", choices = listOf("full", "fast", "off"))
        ),
        As100WeightedChoiceMethod.ID to listOf(
            MethodSetting.TextSetting("weighted_items", "Weighted choices", defaultValue = "Option A:1\nOption B:1"),
            MethodSetting.ChoiceSetting("rng_mode", "Random source", defaultValue = "secure_random", choices = listOf("secure_random", "fixed_seed")),
            MethodSetting.TextSetting("seed", "Fixed seed", defaultValue = ""),
            MethodSetting.ChoiceSetting("animation_mode", "Animation", defaultValue = "full", choices = listOf("full", "fast", "off"))
        )
    )
}
