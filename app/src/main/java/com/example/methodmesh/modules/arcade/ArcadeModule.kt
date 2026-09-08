package com.example.methodmesh.modules.arcade

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object ArcadeModule : MethodMeshModule {
    override val moduleId = "arcade"
    override val displayName = "Arcade"
    override val summary =
        "Lightweight real-time touch games on a fixed-step local-first runtime."

    override fun as100Methods() = listOf(As100ArcadeMethod)
    override fun capabilityScreens() = listOf(ArcadeCapabilityScreen)

    override fun rilBindings() = listOf(
        RilBinding(
            "open arcade",
            As100ArcadeMethod.ID,
            "Open the Arcade game shelf"
        ),
        RilBinding(
            "play arcade game",
            As100ArcadeMethod.ID,
            "Open Arcade"
        )
    )

    override fun capabilitySettings() = mapOf(
        As100ArcadeMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                "game",
                "Game",
                defaultValue = ArcadeEngine.LAUNCHER,
                choices = listOf(
                    ArcadeEngine.LAUNCHER,
                    ArcadeEngine.SNAKE,
                    ArcadeEngine.PONG,
                    ArcadeEngine.BREAKOUT,
                    ArcadeEngine.DODGE
                )
            ),
            MethodSetting.ChoiceSetting(
                "snake_speed",
                "Snake speed",
                defaultValue = ArcadeSnakeSpeed.RELAXED,
                choices = ArcadeSnakeSpeed.modes
            ),
            MethodSetting.ChoiceSetting(
                "rng_mode",
                "Random source",
                defaultValue = "secure_random",
                choices = listOf(
                    "secure_random",
                    "fixed_seed"
                )
            ),
            MethodSetting.TextSetting(
                "seed",
                "Fixed seed",
                defaultValue = ""
            ),
            MethodSetting.BooleanSetting(
                "sound",
                "8-bit sounds",
                defaultValue = true
            )
        )
    )
}
