package com.example.methodmesh.modules.gamedeck

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object GameDeckModule : MethodMeshModule {
    override val moduleId = "gamedeck"
    override val displayName = "GameDeck"
    override val summary = "A lightweight game shelf with shared MethodMesh randomness and reproducible GameLab scenarios."

    override fun as100Methods() = listOf(As100GameDeckMethod)
    override fun capabilityScreens() = listOf(GameDeckCapabilityScreen)

    override fun rilBindings() = listOf(
        RilBinding("open gamedeck", As100GameDeckMethod.ID, "Open the GameDeck game shelf"),
        RilBinding("play game", As100GameDeckMethod.ID, "Open GameDeck")
    )

    override fun capabilitySettings() = mapOf(
        As100GameDeckMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                "game", "Game", defaultValue = GameDeckEngine.LAUNCHER,
                choices = listOf(
                    GameDeckEngine.LAUNCHER, GameDeckEngine.CONNECT_FOUR, GameDeckEngine.SNAKES_AND_LADDERS,
                    GameDeckEngine.MANCALA, GameDeckEngine.MINESWEEPER,
                    GameDeckExtraEngine.TIC_TAC_TOE, GameDeckExtraEngine.REVERSI, GameDeckExtraEngine.NIM,
                    GameDeckExtraEngine.LIGHTS_OUT, GameDeckExtraEngine.FIFTEEN, GameDeckExtraEngine.MEMORY,
                    GameDeckExtraEngine.SHUT_THE_BOX, GameDeckExtraEngine.CODEBREAKER,
                    GameDeckExtraEngine.TWENTY_FORTY_EIGHT, GameDeckExtraEngine.DOTS_AND_BOXES,
                    GameDeckExtraEngine.SUDOKU, GameDeckExtraEngine.TAKUZU,
                    GameDeckExtraEngine.CHESS, GameDeckExtraEngine.GO_9X9
                )
            ),
            MethodSetting.ChoiceSetting(
                "rng_mode", "Random source", defaultValue = "secure_random",
                choices = listOf("secure_random", "fixed_seed")
            ),
            MethodSetting.TextSetting("seed", "Fixed seed", defaultValue = ""),
            MethodSetting.BooleanSetting("sound", "8-bit sounds", defaultValue = true)
        )
    )
}
