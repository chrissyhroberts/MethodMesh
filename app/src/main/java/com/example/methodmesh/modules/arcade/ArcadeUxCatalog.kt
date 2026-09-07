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
                "Swipe the board or use the arrow controls to turn.",
                "Eat the gold food to grow. Hitting a wall or your own body ends the run."
            ),
            tip = "A queued turn is applied on the next movement step, so rapid taps cannot secretly reverse the snake."
        ),
        ArcadeGameInfo(
            id = ArcadeEngine.BREAKOUT,
            title = "Brick Breaker",
            subtitle = "Keep the ball alive and clear the wall",
            badge = "1P",
            objective = "Clear all thirty bricks before you run out of balls.",
            steps = listOf(
                "Press START when you are ready.",
                "Drag or tap across the court to position the paddle.",
                "Bounce the ball through the brick wall. Missing the paddle costs a life."
            ),
            tip = "Where the ball hits the paddle changes its horizontal direction."
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
            tip = "The ball pauses briefly at centre after each point so both players can reset."
        )
    )

    fun info(game: String): ArcadeGameInfo? = games.firstOrNull { it.id == game }
}
