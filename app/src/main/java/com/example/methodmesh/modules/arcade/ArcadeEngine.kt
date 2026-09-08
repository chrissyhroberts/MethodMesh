package com.example.methodmesh.modules.arcade

import com.example.methodmesh.modules.chance.As100DiceSimulationMethod
import com.example.methodmesh.modules.chance.DiceSimulationFields
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

object ArcadeEngine {
    const val LAUNCHER = "launcher"
    const val SNAKE = "snake"
    const val PONG = "pong"
    const val BREAKOUT = "breakout"
    const val DODGE = "lane_dodge"

    fun newState(
        game: String,
        rngMode: String = "secure_random",
        seed: String = "",
        snakeSpeedCps: Int = ArcadeSnakeSpeed.DEFAULT_CPS,
        pongCpuDifficulty: String = "standard",
        breakoutStartLevel: Int = 1,
        dodgeDifficulty: String = "normal"
    ): String = when (game) {
        SNAKE -> snakeInitial(rngMode, seed, snakeSpeedCps)
        PONG -> pongInitial(pongCpuDifficulty)
        BREAKOUT -> breakoutInitial(breakoutStartLevel)
        DODGE -> dodgeInitial(dodgeDifficulty)
        else -> JSONObject()
            .put("game", LAUNCHER)
            .put("score", 0)
            .put("lives", 0)
            .put("tick", 0L)
            .put("started", false)
            .put("finished", false)
            .put("winner", "")
            .toString()
    }

    fun summary(stateJson: String): ArcadeSummary {
        val s = runCatching { JSONObject(stateJson) }.getOrElse { JSONObject() }
        val game = s.optString("game", LAUNCHER)
        val detail = when (game) {
            SNAKE -> {
                val length = s.optJSONArray("body")?.length() ?: 0
                "Score ${s.optInt("score", 0)} • length $length"
            }
            PONG -> "P1 ${s.optInt("p1_score", 0)} : ${s.optInt("p2_score", 0)} P2"
            BREAKOUT -> "Level ${s.optInt("level", 1)}/${s.optInt("total_levels", 5)} • score ${s.optInt("score", 0)} • lives ${s.optInt("lives", 0)}"
            DODGE -> "Dodged ${s.optInt("score", 0)} • ${s.optLong("tick", 0L) / 62L}s"
            else -> ""
        }
        return ArcadeSummary(
            game = game,
            score = s.optInt("score", 0),
            tick = s.optLong("tick", 0L),
            finished = s.optBoolean("finished", false),
            winner = s.optString("winner", ""),
            detail = detail
        )
    }

    // -----------------------------------------------------------------
    // Snake Sprint
    // -----------------------------------------------------------------

    const val SNAKE_COLS = 18
    const val SNAKE_ROWS = 30
    private const val SNAKE_CELLS = SNAKE_COLS * SNAKE_ROWS

    private fun snakeInitial(
        rngMode: String,
        seed: String,
        speedCps: Int
    ): String {
        val row = SNAKE_ROWS / 2
        val headCol = SNAKE_COLS / 2
        val body = JSONArray(
            listOf(
                row * SNAKE_COLS + headCol,
                row * SNAKE_COLS + headCol - 1,
                row * SNAKE_COLS + headCol - 2,
                row * SNAKE_COLS + headCol - 3
            )
        )
        return JSONObject()
            .put("game", SNAKE)
            .put("body", body)
            .put("dir", 1)
            .put("queued_dir", 1)
            .put("food_index", 0)
            .put("speed_cps", ArcadeSnakeSpeed.normalizeCps(speedCps))
            .put("food", chanceCell(rngMode, seed, 0, body))
            .put("score", 0)
            .put("lives", 1)
            .put("tick", 0L)
            .put("started", false)
            .put("finished", false)
            .put("winner", "")
            .put("reason", "")
            .toString()
    }

    fun snakeSetSpeed(stateJson: String, speedCps: Int): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("finished", false)) return stateJson
        s.put("speed_cps", ArcadeSnakeSpeed.normalizeCps(speedCps))
        return s.toString()
    }

    fun snakeStart(stateJson: String): String {
        val s = JSONObject(stateJson)
        if (!s.optBoolean("finished", false)) s.put("started", true)
        return s.toString()
    }

    /**
     * Queue a turn without changing the committed direction until the next step.
     * This prevents two rapid swipes between ticks from producing a hidden 180° turn.
     */
    fun snakeTurn(stateJson: String, requestedDir: Int): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("finished", false) || requestedDir !in 0..3) return stateJson

        val committed = s.optInt("dir", 1).coerceIn(0, 3)
        val opposite = (committed + 2) % 4
        if (requestedDir != opposite) s.put("queued_dir", requestedDir)
        return s.toString()
    }

    fun snakeStep(
        stateJson: String,
        rngMode: String = "secure_random",
        seed: String = ""
    ): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("finished", false) || !s.optBoolean("started", false)) {
            return stateJson
        }

        val bodyJson = s.getJSONArray("body")
        val body = MutableList(bodyJson.length()) { bodyJson.optInt(it) }
        if (body.isEmpty()) {
            return s.put("finished", true).put("reason", "invalid_body").toString()
        }

        val dir = s.optInt("queued_dir", s.optInt("dir", 1)).coerceIn(0, 3)
        val head = body.first()
        val row = head / SNAKE_COLS
        val col = head % SNAKE_COLS
        val next = when (dir) {
            0 -> (row - 1) to col
            1 -> row to (col + 1)
            2 -> (row + 1) to col
            else -> row to (col - 1)
        }

        val nextTick = s.optLong("tick", 0L) + 1L
        if (next.first !in 0 until SNAKE_ROWS || next.second !in 0 until SNAKE_COLS) {
            return s
                .put("dir", dir)
                .put("queued_dir", dir)
                .put("tick", nextTick)
                .put("started", false)
                .put("finished", true)
                .put("winner", "failed")
                .put("reason", "wall")
                .toString()
        }

        val cell = next.first * SNAKE_COLS + next.second
        val food = s.optInt("food", -1)
        val grows = cell == food
        val collisionBody = if (grows) body else body.dropLast(1)
        if (cell in collisionBody) {
            return s
                .put("dir", dir)
                .put("queued_dir", dir)
                .put("tick", nextTick)
                .put("started", false)
                .put("finished", true)
                .put("winner", "failed")
                .put("reason", "self")
                .toString()
        }

        body.add(0, cell)
        if (grows) {
            val score = s.optInt("score", 0) + 10
            val nextFoodIndex = s.optInt("food_index", 0) + 1
            s.put("score", score).put("food_index", nextFoodIndex)

            if (body.size >= SNAKE_CELLS) {
                return s
                    .put("body", JSONArray(body))
                    .put("dir", dir)
                    .put("queued_dir", dir)
                    .put("tick", nextTick)
                    .put("started", false)
                    .put("finished", true)
                    .put("winner", "cleared")
                    .put("reason", "board_cleared")
                    .toString()
            }
            s.put("food", chanceCell(rngMode, seed, nextFoodIndex, JSONArray(body)))
        } else {
            body.removeAt(body.lastIndex)
        }

        return s
            .put("body", JSONArray(body))
            .put("dir", dir)
            .put("queued_dir", dir)
            .put("tick", nextTick)
            .toString()
    }

    private fun chanceCell(
        rngMode: String,
        seed: String,
        foodIndex: Int,
        occupied: JSONArray
    ): Int {
        val used = (0 until occupied.length()).map { occupied.optInt(it) }.toSet()
        val free = (0 until SNAKE_CELLS).filter { it !in used }
        if (free.isEmpty()) return 0

        val mode = if (rngMode.equals("fixed_seed", ignoreCase = true)) {
            "fixed_seed"
        } else {
            "secure_random"
        }

        val out = As100DiceSimulationMethod.generate(
            mapOf(
                "expression" to "d${free.size}",
                "roll_count" to "1",
                "player_count" to "1",
                "history_output" to "false",
                "rng_mode" to mode,
                "seed" to if (mode == "fixed_seed") "$seed|arcade-snake|food|$foodIndex" else "",
                "animation_mode" to "off"
            )
        )
        val index = ((out[DiceSimulationFields.TOTAL]?.toIntOrNull() ?: 1) - 1)
            .coerceIn(0, free.lastIndex)
        return free[index]
    }

    // -----------------------------------------------------------------
    // Pong Table
    // -----------------------------------------------------------------

    private fun pongInitial(cpuDifficulty: String = "standard"): String = JSONObject()
        .put("game", PONG)
        .put("ball_x", .5)
        .put("ball_y", .5)
        .put("vx", .20)
        .put("vy", .48)
        .put("p1", .5)
        .put("p2", .5)
        .put("p1_v", 0.0)
        .put("p2_v", 0.0)
        .put("p1_score", 0)
        .put("p2_score", 0)
        .put("score", 0)
        .put("lives", 0)
        .put("tick", 0L)
        .put("started", false)
        .put("finished", false)
        .put("winner", "")
        .put("cpu", true)
        .put("cpu_difficulty", normalizePongCpuDifficulty(cpuDifficulty))
        .put("serve_ticks", 0)
        .put("last_point", 0)
        .put("rally", 0)
        .toString()

    private fun normalizePongCpuDifficulty(value: String): String =
        value.lowercase().takeIf { it in setOf("casual", "standard", "sharp") } ?: "standard"

    fun pongSetCpuDifficulty(stateJson: String, difficulty: String): String {
        val s = JSONObject(stateJson)
        if (!s.optBoolean("started", false) &&
            s.optInt("p1_score", 0) == 0 &&
            s.optInt("p2_score", 0) == 0
        ) {
            s.put("cpu_difficulty", normalizePongCpuDifficulty(difficulty))
        }
        return s.toString()
    }

    fun pongSetCpu(stateJson: String, cpu: Boolean): String {
        val s = JSONObject(stateJson)
        if (!s.optBoolean("started", false) &&
            s.optInt("p1_score", 0) == 0 &&
            s.optInt("p2_score", 0) == 0
        ) s.put("cpu", cpu)
        return s.toString()
    }

    fun pongStart(stateJson: String): String {
        val s = JSONObject(stateJson)
        if (!s.optBoolean("finished", false)) {
            s.put("started", true).put("serve_ticks", 42)
        }
        return s.toString()
    }

    fun pongPaddle(stateJson: String, player: Int, x: Float): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("finished", false)) return stateJson
        if (player == 2 && s.optBoolean("cpu", true)) return stateJson

        val key = if (player == 2) "p2" else "p1"
        val velocityKey = if (player == 2) "p2_v" else "p1_v"
        val next = x.coerceIn(.14f, .86f).toDouble()
        val old = s.optDouble(key, .5)
        s.put(key, next)
        s.put(velocityKey, (next - old).coerceIn(-.12, .12))
        return s.toString()
    }

    fun pongStep(
        stateJson: String,
        dt: Float = ArcadeFixedStep.STEP_SECONDS
    ): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("finished", false) || !s.optBoolean("started", false)) {
            return stateJson
        }

        var x = s.optDouble("ball_x", .5)
        var y = s.optDouble("ball_y", .5)
        var vx = s.optDouble("vx", .20)
        var vy = s.optDouble("vy", .48)
        val p1 = s.optDouble("p1", .5)
        var p2 = s.optDouble("p2", .5)
        var p1v = s.optDouble("p1_v", 0.0)
        var p2v = s.optDouble("p2_v", 0.0)
        val nextTick = s.optLong("tick", 0L) + 1L
        var rally = s.optInt("rally", 0)

        if (s.optBoolean("cpu", true)) {
            // Predict the intercept at the CPU paddle, including side-wall reflections.
            // Difficulty changes reaction rate and prediction error, not the ball rules.
            val target = if (vy < -0.02) {
                val time = ((y - .07) / -vy).coerceAtLeast(0.0)
                reflectedCoordinate(x + vx * time, .04, .96)
            } else {
                .5
            }
            val difficulty = normalizePongCpuDifficulty(
                s.optString("cpu_difficulty", "standard")
            )
            val maxStep = when (difficulty) {
                "casual" -> .009
                "sharp" -> .020
                else -> .014
            }
            val errorAmplitude = when (difficulty) {
                "casual" -> .055
                "sharp" -> .006
                else -> .022
            }
            val old = p2
            val error = if (rally < 2) {
                0.0
            } else {
                errorAmplitude * sin(nextTick * .071)
            }
            val desired = (target + error).coerceIn(.14, .86)
            p2 += (desired - p2).coerceIn(-maxStep, maxStep)
            p2v = (p2 - old).coerceIn(-.06, .06)
        }

        val serveTicks = s.optInt("serve_ticks", 0)
        if (serveTicks > 0) {
            return s
                .put("p2", p2)
                .put("p1_v", p1v * .55)
                .put("p2_v", p2v * .55)
                .put("serve_ticks", serveTicks - 1)
                .put("tick", nextTick)
                .toString()
        }

        x += vx * dt
        y += vy * dt

        if (x < .04) {
            x = .04
            vx = abs(vx)
        } else if (x > .96) {
            x = .96
            vx = -abs(vx)
        }

        val paddleHalf = .14
        if (y > .900 && y < .965 && vy > 0 && abs(x - p1) < paddleHalf) {
            y = .910
            val bounced = paddleBounce(
                incomingVx = vx,
                incomingVy = vy,
                hitOffset = ((x - p1) / paddleHalf).coerceIn(-1.0, 1.0),
                paddleVelocity = p1v,
                verticalDirection = -1.0,
                speedBoost = 1.035,
                maxSpeed = 1.08
            )
            vx = bounced.first
            vy = bounced.second
            rally += 1
        }

        if (y < .100 && y > .035 && vy < 0 && abs(x - p2) < paddleHalf) {
            y = .090
            val bounced = paddleBounce(
                incomingVx = vx,
                incomingVy = vy,
                hitOffset = ((x - p2) / paddleHalf).coerceIn(-1.0, 1.0),
                paddleVelocity = p2v,
                verticalDirection = 1.0,
                speedBoost = 1.035,
                maxSpeed = 1.08
            )
            vx = bounced.first
            vy = bounced.second
            rally += 1
        }

        p1v *= .72
        p2v *= .72

        var p1Score = s.optInt("p1_score", 0)
        var p2Score = s.optInt("p2_score", 0)
        var lastPoint = 0

        if (y < 0) {
            p1Score++
            lastPoint = 1
            x = .5
            y = .5
            vx = -.18
            vy = .48
            rally = 0
        } else if (y > 1) {
            p2Score++
            lastPoint = 2
            x = .5
            y = .5
            vx = .18
            vy = -.48
            rally = 0
        }

        val finished = p1Score >= 7 || p2Score >= 7
        val winner = when {
            p1Score >= 7 -> "1"
            p2Score >= 7 -> "2"
            else -> ""
        }

        return s
            .put("ball_x", x)
            .put("ball_y", y)
            .put("vx", vx)
            .put("vy", vy)
            .put("p1", p1)
            .put("p2", p2)
            .put("p1_v", p1v)
            .put("p2_v", p2v)
            .put("p1_score", p1Score)
            .put("p2_score", p2Score)
            .put("score", p1Score)
            .put("last_point", lastPoint)
            .put("rally", rally)
            .put("serve_ticks", if (!finished && lastPoint != 0) 42 else 0)
            .put("tick", nextTick)
            .put("started", !finished)
            .put("finished", finished)
            .put("winner", winner)
            .toString()
    }

    // -----------------------------------------------------------------
    // Breakout / Wall Break
    // -----------------------------------------------------------------

    private data class BreakoutLevel(
        val name: String,
        val rows: Int,
        val cols: Int,
        val bricks: List<Int>
    )

    private val breakoutLevels: List<BreakoutLevel> by lazy {
        listOf(
            makeBreakoutLevel("WALL", 5, 6) { _, _ -> 1 },
            makeBreakoutLevel("CHECKER", 6, 7) { r, c -> if ((r + c) % 2 == 0 || r == 0) 1 else 0 },
            makeBreakoutLevel("FORTRESS", 6, 8) { r, c ->
                when {
                    r == 0 || r == 5 || c == 0 || c == 7 -> 2
                    r in 2..3 && c in 2..5 -> 1
                    else -> 0
                }
            },
            makeBreakoutLevel("CHEVRON", 7, 8) { r, c ->
                val centre = 3.5
                val distance = abs(c - centre)
                if (abs(distance - (r % 4)) < 1.1) if (r >= 4) 2 else 1 else 0
            },
            makeBreakoutLevel("CROWN", 7, 9) { r, c ->
                when {
                    r == 0 && c in listOf(0, 2, 4, 6, 8) -> 2
                    r == 1 && c % 2 == 0 -> 2
                    r in 2..3 && c in 1..7 -> if (c in 3..5) 2 else 1
                    r in 4..6 && c in 2..6 -> if (r == 6) 2 else 1
                    else -> 0
                }
            }
        )
    }

    private fun makeBreakoutLevel(
        name: String,
        rows: Int,
        cols: Int,
        hp: (Int, Int) -> Int
    ): BreakoutLevel = BreakoutLevel(
        name = name,
        rows = rows,
        cols = cols,
        bricks = List(rows * cols) { index -> hp(index / cols, index % cols) }
    )

    private fun breakoutStateForLevel(
        levelNumber: Int,
        score: Int = 0,
        lives: Int = 3,
        tick: Long = 0L,
        startLevel: Int = levelNumber
    ): JSONObject {
        val levelIndex = (levelNumber - 1).coerceIn(0, breakoutLevels.lastIndex)
        val level = breakoutLevels[levelIndex]
        return JSONObject()
            .put("game", BREAKOUT)
            .put("level", levelIndex + 1)
            .put("start_level", startLevel.coerceIn(1, breakoutLevels.size))
            .put("total_levels", breakoutLevels.size)
            .put("level_name", level.name)
            .put("rows", level.rows)
            .put("cols", level.cols)
            .put("ball_x", .5)
            .put("ball_y", .76)
            .put("vx", .15)
            .put("vy", -.50)
            .put("paddle", .5)
            .put("paddle_v", 0.0)
            .put("bricks", JSONArray(level.bricks))
            .put("score", score)
            .put("lives", lives)
            .put("tick", tick)
            .put("started", false)
            .put("finished", false)
            .put("winner", "")
            .put("serve_ticks", 0)
    }

    private fun breakoutInitial(startLevel: Int = 1): String {
        val normalized = startLevel.coerceIn(1, breakoutLevels.size)
        return breakoutStateForLevel(
            levelNumber = normalized,
            startLevel = normalized
        ).toString()
    }

    fun breakoutStart(stateJson: String): String {
        val s = JSONObject(stateJson)
        if (!s.optBoolean("finished", false)) {
            s.put("started", true).put("serve_ticks", 34)
        }
        return s.toString()
    }

    fun breakoutPaddle(stateJson: String, x: Float): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("finished", false)) return stateJson
        val next = x.coerceIn(.15f, .85f).toDouble()
        val old = s.optDouble("paddle", .5)
        s.put("paddle", next)
        s.put("paddle_v", (next - old).coerceIn(-.12, .12))
        return s.toString()
    }

    fun breakoutStep(
        stateJson: String,
        dt: Float = ArcadeFixedStep.STEP_SECONDS
    ): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("finished", false) || !s.optBoolean("started", false)) {
            return stateJson
        }

        val nextTick = s.optLong("tick", 0L) + 1L
        val serveTicks = s.optInt("serve_ticks", 0)
        if (serveTicks > 0) {
            return s
                .put("paddle_v", s.optDouble("paddle_v", 0.0) * .55)
                .put("serve_ticks", serveTicks - 1)
                .put("tick", nextTick)
                .toString()
        }

        var x = s.optDouble("ball_x", .5)
        var y = s.optDouble("ball_y", .76)
        var vx = s.optDouble("vx", .15)
        var vy = s.optDouble("vy", -.50)
        val previousX = x
        val previousY = y
        val paddle = s.optDouble("paddle", .5)
        var paddleV = s.optDouble("paddle_v", 0.0)
        var score = s.optInt("score", 0)
        var lives = s.optInt("lives", 3)
        val rows = s.optInt("rows", 5).coerceAtLeast(1)
        val cols = s.optInt("cols", 6).coerceAtLeast(1)
        val brickArray = s.getJSONArray("bricks")
        val bricks = MutableList(rows * cols) { brickArray.optInt(it, 0) }

        x += vx * dt
        y += vy * dt

        val radius = ArcadeBreakoutRules.BALL_RADIUS
        if (x - radius < .02) {
            x = .02 + radius
            vx = abs(vx)
        } else if (x + radius > .98) {
            x = .98 - radius
            vx = -abs(vx)
        }
        if (y - radius < .02) {
            y = .02 + radius
            vy = abs(vy)
        }

        val paddleTop = ArcadeBreakoutRules.PADDLE_Y
        val paddleBottom = paddleTop + ArcadeBreakoutRules.PADDLE_HEIGHT
        if (
            vy > 0 &&
            y + radius >= paddleTop &&
            previousY + radius < paddleTop + .018 &&
            y - radius <= paddleBottom &&
            abs(x - paddle) <= ArcadeBreakoutRules.PADDLE_HALF + radius
        ) {
            y = paddleTop - radius - .001
            val bounced = paddleBounce(
                incomingVx = vx,
                incomingVy = vy,
                hitOffset = ((x - paddle) / ArcadeBreakoutRules.PADDLE_HALF).coerceIn(-1.0, 1.0),
                paddleVelocity = paddleV,
                verticalDirection = -1.0,
                speedBoost = 1.018,
                maxSpeed = 1.12
            )
            vx = bounced.first
            vy = bounced.second
        }

        // Circle-vs-rectangle collision. The closest point gives corner-aware
        // contact; previous position is used to choose the reflection axis when
        // the centre has moved into the brick rectangle during a fast step.
        var hitBrick = -1
        for (index in bricks.indices) {
            if (bricks[index] <= 0) continue
            val rect = ArcadeBreakoutRules.brickRect(index, rows, cols)
            val closestX = x.coerceIn(rect.left, rect.right)
            val closestY = y.coerceIn(rect.top, rect.bottom)
            val dx = x - closestX
            val dy = y - closestY
            if (dx * dx + dy * dy <= radius * radius) {
                hitBrick = index
                val cameFromLeft = previousX + radius <= rect.left
                val cameFromRight = previousX - radius >= rect.right
                val cameFromTop = previousY + radius <= rect.top
                val cameFromBottom = previousY - radius >= rect.bottom

                when {
                    cameFromLeft -> {
                        x = rect.left - radius - .001
                        vx = -abs(vx)
                    }
                    cameFromRight -> {
                        x = rect.right + radius + .001
                        vx = abs(vx)
                    }
                    cameFromTop -> {
                        y = rect.top - radius - .001
                        vy = -abs(vy)
                    }
                    cameFromBottom -> {
                        y = rect.bottom + radius + .001
                        vy = abs(vy)
                    }
                    abs(dx) > abs(dy) -> vx = -vx
                    else -> vy = -vy
                }
                break
            }
        }

        if (hitBrick >= 0) {
            bricks[hitBrick] = (bricks[hitBrick] - 1).coerceAtLeast(0)
            score += 10
            val speed = sqrt(vx * vx + vy * vy).coerceAtLeast(.45)
            val boosted = (speed * 1.006).coerceAtMost(1.12)
            vx *= boosted / speed
            vy *= boosted / speed
        }

        paddleV *= .72

        var finished = false
        var winner = ""
        var serve = 0

        if (bricks.none { it > 0 }) {
            val currentLevel = s.optInt("level", 1)
            if (currentLevel < breakoutLevels.size) {
                val next = breakoutStateForLevel(
                    levelNumber = currentLevel + 1,
                    score = score + 100,
                    lives = (lives + 1).coerceAtMost(5),
                    tick = nextTick,
                    startLevel = s.optInt("start_level", 1)
                )
                return next
                    .put("started", true)
                    .put("serve_ticks", 52)
                    .toString()
            } else {
                finished = true
                winner = "cleared"
                score += 500
            }
        } else if (y - radius > 1.0) {
            lives -= 1
            if (lives <= 0) {
                finished = true
                winner = "failed"
            } else {
                x = .5
                y = .76
                vx = if (lives % 2 == 0) -.15 else .15
                vy = -.50
                serve = 34
            }
        }

        return s
            .put("ball_x", x)
            .put("ball_y", y)
            .put("vx", vx)
            .put("vy", vy)
            .put("paddle_v", paddleV)
            .put("bricks", JSONArray(bricks))
            .put("score", score)
            .put("lives", lives.coerceAtLeast(0))
            .put("tick", nextTick)
            .put("serve_ticks", serve)
            .put("started", !finished)
            .put("finished", finished)
            .put("winner", winner)
            .toString()
    }

    // -----------------------------------------------------------------
    // Lane Dodge
    // -----------------------------------------------------------------

    private fun normalizeDodgeDifficulty(value: String): String =
        value.lowercase().takeIf { it in setOf("easy", "normal", "hard") } ?: "normal"

    private fun dodgeInitial(difficulty: String = "normal"): String = JSONObject()
        .put("game", DODGE)
        .put("difficulty", normalizeDodgeDifficulty(difficulty))
        .put("lane", 2)
        .put("hazards", JSONArray())
        .put("spawn_index", 0)
        .put("score", 0)
        .put("lives", 1)
        .put("tick", 0L)
        .put("started", false)
        .put("finished", false)
        .put("winner", "")
        .toString()

    fun dodgeStart(stateJson: String): String {
        val s = JSONObject(stateJson)
        if (!s.optBoolean("finished", false)) s.put("started", true)
        return s.toString()
    }

    fun dodgeLane(stateJson: String, lane: Int): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("finished", false) || lane !in 0..4) return stateJson
        s.put("lane", lane)
        return s.toString()
    }

    fun dodgeStep(
        stateJson: String,
        rngMode: String = "secure_random",
        seed: String = ""
    ): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("finished", false) || !s.optBoolean("started", false)) {
            return stateJson
        }

        val tick = s.optLong("tick", 0L) + 1L
        val lane = s.optInt("lane", 2).coerceIn(0, 4)
        var score = s.optInt("score", 0)
        var spawnIndex = s.optInt("spawn_index", 0)

        // Difficulty changes the starting pressure; the game still becomes harder
        // through play. Easy starts at level 1, Normal at 3, Hard at 5.
        val difficulty = normalizeDodgeDifficulty(s.optString("difficulty", "normal"))
        val difficultyOffset = when (difficulty) {
            "easy" -> 0
            "hard" -> 4
            else -> 2
        }
        val level = (1 + difficultyOffset + (tick / 500L).toInt()).coerceAtMost(10)
        val speedPerTick = (.0056 + (level - 1) * .00072).coerceAtMost(.0121)

        val hazards = mutableListOf<JSONObject>()
        val old = s.optJSONArray("hazards") ?: JSONArray()
        for (i in 0 until old.length()) {
            val h = old.optJSONObject(i) ?: continue
            val nextY = h.optDouble("y", 0.0) + speedPerTick
            if (nextY > 1.08) {
                score += 1
            } else {
                hazards += JSONObject()
                    .put("lane", h.optInt("lane", 0).coerceIn(0, 4))
                    .put("y", nextY)
            }
        }

        // Start forgiving and tighten in obvious level-sized steps.
        val interval = (64 - (level - 1) * 4).coerceAtLeast(28)
        if (tick % interval.toLong() == 0L) {
            val hazardLane = chanceInt(
                bound = 5,
                rngMode = rngMode,
                seed = "$seed|arcade-dodge|spawn|$spawnIndex"
            )
            hazards += JSONObject().put("lane", hazardLane).put("y", -.08)
            spawnIndex += 1
        }

        val playerTop = .88
        val playerBottom = .925
        val hazardHeight = .055
        val collision = hazards.any { h ->
            if (h.optInt("lane") != lane) {
                false
            } else {
                val hazardTop = h.optDouble("y")
                val hazardBottom = hazardTop + hazardHeight
                hazardBottom > playerTop && hazardTop < playerBottom
            }
        }

        val hazardJson = JSONArray()
        hazards.forEach { hazardJson.put(it) }

        return s
            .put("hazards", hazardJson)
            .put("spawn_index", spawnIndex)
            .put("score", score)
            .put("level", level)
            .put("tick", tick)
            .put("started", !collision)
            .put("finished", collision)
            .put("winner", if (collision) "failed" else "")
            .toString()
    }

    private fun chanceInt(
        bound: Int,
        rngMode: String,
        seed: String
    ): Int {
        if (bound <= 1) return 0
        val mode = if (rngMode.equals("fixed_seed", ignoreCase = true)) {
            "fixed_seed"
        } else {
            "secure_random"
        }
        val out = As100DiceSimulationMethod.generate(
            mapOf(
                "expression" to "d$bound",
                "roll_count" to "1",
                "player_count" to "1",
                "history_output" to "false",
                "rng_mode" to mode,
                "seed" to if (mode == "fixed_seed") seed else "",
                "animation_mode" to "off"
            )
        )
        return ((out[DiceSimulationFields.TOTAL]?.toIntOrNull() ?: 1) - 1)
            .coerceIn(0, bound - 1)
    }


    /**
     * Deliberately game-like paddle physics rather than mirror reflection.
     * Impact position aims the shot; paddle motion adds "english". The outgoing
     * speed is conserved/boosted then capped, with a minimum vertical component.
     */
    private fun paddleBounce(
        incomingVx: Double,
        incomingVy: Double,
        hitOffset: Double,
        paddleVelocity: Double,
        verticalDirection: Double,
        speedBoost: Double,
        maxSpeed: Double
    ): Pair<Double, Double> {
        val incomingSpeed = sqrt(incomingVx * incomingVx + incomingVy * incomingVy)
            .coerceAtLeast(.42)
        val speed = (incomingSpeed * speedBoost + abs(paddleVelocity) * 1.15)
            .coerceAtMost(maxSpeed)
        val english = (paddleVelocity * 4.5).coerceIn(-.48, .48)
        val aim = (hitOffset + english).coerceIn(-1.0, 1.0)
        val angle = aim * (64.0 * PI / 180.0)
        var outVx = sin(angle) * speed
        var outVy = verticalDirection * cos(angle) * speed

        // Prevent near-horizontal dead rallies while preserving the aim direction.
        val minVertical = speed * .42
        if (abs(outVy) < minVertical) {
            outVy = verticalDirection * minVertical
            val horizontal = sqrt((speed * speed - outVy * outVy).coerceAtLeast(0.0))
            outVx = if (outVx < 0) -horizontal else horizontal
        }
        return outVx to outVy
    }

    /** Reflect a projected coordinate between two side walls (triangle wave). */
    private fun reflectedCoordinate(value: Double, minimum: Double, maximum: Double): Double {
        val width = maximum - minimum
        if (width <= 0.0) return minimum
        val period = width * 2.0
        var t = (value - minimum) % period
        if (t < 0.0) t += period
        return if (t <= width) minimum + t else maximum - (t - width)
    }

}
