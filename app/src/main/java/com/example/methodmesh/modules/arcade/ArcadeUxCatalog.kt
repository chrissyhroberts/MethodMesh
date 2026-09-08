package com.example.methodmesh.modules.arcade

internal data class ArcadeGameInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val objective: String,
    val steps: List<String>,
    val tip: String
)

internal object ArcadeUxCatalog {
    val games = listOf(
        ArcadeGameInfo(
            id = ArcadeEngine.SNAKE,
            title = "Snake Sprint",
            subtitle = "Grow longer without hitting the wall or yourself",
            badge = "1P",
            objective = "Collect food for points and survive for as long as you can.",
            steps = listOf(
                "Press START when you are ready.",
                "Swipe anywhere on the board to turn.",
                "Eat the gold food to grow. Hitting a wall or your own body ends the run."
            ),
            tip = "Use the speed slider at any time. The 18×30 board gives much finer steering than the original coarse grid."
        ),
        ArcadeGameInfo(
            id = ArcadeEngine.BREAKOUT,
            title = "Wall Break",
            subtitle = "Aim with paddle position and motion across five walls",
            badge = "1P",
            objective = "Clear five different brick layouts before you run out of balls.",
            steps = listOf(
                "Press START when you are ready.",
                "Drag or tap across the court to position the paddle.",
                "Where the ball lands on the paddle sets the return angle; moving the paddle adds extra horizontal 'english'."
            ),
            tip = "Centre hits return steeply; edge hits aim wide. Moving into the ball pushes the shot further in that direction."
        ),
        ArcadeGameInfo(
            id = ArcadeEngine.DODGE,
            title = "Lane Dodge",
            subtitle = "Switch lanes as falling blocks accelerate",
            badge = "1P",
            objective = "Let as many falling blocks pass as possible without being hit.",
            steps = listOf(
                "Press START.",
                "Tap or drag across the five lanes to move the green player.",
                "Red blocks speed up over time. A collision ends the run."
            ),
            tip = "Moving late is often safer than constantly switching lanes."
        ),
        ArcadeGameInfo(
            id = ArcadeEngine.PONG,
            title = "Pong Table",
            subtitle = "Drag the paddles in a first-to-seven match",
            badge = "1P/2P",
            objective = "Be the first side to score seven points.",
            steps = listOf(
                "Before the match, choose CPU or two-player mode and press START MATCH.",
                "Drag or tap your half of the court to move your paddle.",
                "In two-player mode the far player controls the upper half of the screen."
            ),
            tip = "Paddle contact is skill-based: hit position and paddle motion determine the return trajectory. The CPU predicts where that trajectory reaches its end."
        )
    )

    fun info(game: String): ArcadeGameInfo? = games.firstOrNull { it.id == game }
}
