package com.example.methodmesh.modules.arcade

import com.example.methodmesh.modules.chance.As100DiceSimulationMethod
import com.example.methodmesh.modules.chance.DiceSimulationFields
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

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
        snakeSpeed: String = ArcadeSnakeSpeed.RELAXED
    ): String = when (game) {
        SNAKE -> snakeInitial(rngMode, seed, snakeSpeed)
        PONG -> pongInitial()
        BREAKOUT -> breakoutInitial()
        DODGE -> dodgeInitial()
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
            BREAKOUT -> "Score ${s.optInt("score", 0)} • lives ${s.optInt("lives", 0)}"
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

    private fun snakeInitial(
        rngMode: String,
        seed: String,
        speed: String
    ): String {
        val body = JSONArray(listOf(42, 41, 40))
        return JSONObject()
            .put("game", SNAKE)
            .put("body", body)
            .put("dir", 1)
            .put("queued_dir", 1)
            .put("food_index", 0)
            .put("speed", ArcadeSnakeSpeed.normalize(speed))
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

    fun snakeSetSpeed(stateJson: String, speed: String): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("started", false) || s.optBoolean("finished", false)) {
            return stateJson
        }
        s.put("speed", ArcadeSnakeSpeed.normalize(speed))
        return s.toString()
    }

    fun snakeStart(stateJson: String): String {
        val s = JSONObject(stateJson)
        if (!s.optBoolean("finished", false)) s.put("started", true)
        return s.toString()
    }

    /**
     * Queue a turn without changing the committed direction until the next step.
     * This prevents two rapid taps between ticks from producing a hidden 180° turn.
     */
    fun snakeTurn(stateJson: String, requestedDir: Int): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("finished", false) || requestedDir !in 0..3) return stateJson

        val committed = s.optInt("dir", 1).coerceIn(0, 3)
        val opposite = (committed + 2) % 4
        if (requestedDir != opposite) {
            s.put("queued_dir", requestedDir)
        }
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
        if (body.isEmpty()) return s.put("finished", true).put("reason", "invalid_body").toString()

        val dir = s.optInt("queued_dir", s.optInt("dir", 1)).coerceIn(0, 3)
        val head = body.first()
        val row = head / 10
        val col = head % 10

        val next = when (dir) {
            0 -> (row - 1) to col
            1 -> row to (col + 1)
            2 -> (row + 1) to col
            else -> row to (col - 1)
        }

        val nextTick = s.optLong("tick", 0L) + 1L
        if (next.first !in 0..9 || next.second !in 0..9) {
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

        val cell = next.first * 10 + next.second
        val food = s.optInt("food", -1)
        val grows = cell == food

        // The tail vacates on a normal move, so entering the current tail cell is
        // legal when the snake is not growing.
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

            s.put("score", score)
                .put("food_index", nextFoodIndex)

            if (body.size >= 100) {
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

            s.put(
                "food",
                chanceCell(rngMode, seed, nextFoodIndex, JSONArray(body))
            )
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
        val free = (0 until 100).filter { it !in used }
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

    private fun pongInitial(): String = JSONObject()
        .put("game", PONG)
        .put("ball_x", .5)
        .put("ball_y", .5)
        .put("vx", .30)
        .put("vy", .44)
        .put("p1", .5)
        .put("p2", .5)
        .put("p1_score", 0)
        .put("p2_score", 0)
        .put("score", 0)
        .put("lives", 0)
        .put("tick", 0L)
        .put("started", false)
        .put("finished", false)
        .put("winner", "")
        .put("cpu", true)
        .put("serve_ticks", 0)
        .put("last_point", 0)
        .put("rally", 0)
        .toString()

    fun pongSetCpu(stateJson: String, cpu: Boolean): String {
        val s = JSONObject(stateJson)
        if (!s.optBoolean("started", false) &&
            s.optInt("p1_score", 0) == 0 &&
            s.optInt("p2_score", 0) == 0
        ) {
            s.put("cpu", cpu)
        }
        return s.toString()
    }

    fun pongStart(stateJson: String): String {
        val s = JSONObject(stateJson)
        if (!s.optBoolean("finished", false)) {
            s.put("started", true)
                .put("serve_ticks", 42)
        }
        return s.toString()
    }

    fun pongPaddle(stateJson: String, player: Int, x: Float): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("finished", false)) return stateJson
        if (player == 2 && s.optBoolean("cpu", true)) return stateJson

        val key = if (player == 2) "p2" else "p1"
        s.put(key, x.coerceIn(.14f, .86f).toDouble())
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
        var vx = s.optDouble("vx", .30)
        var vy = s.optDouble("vy", .44)
        val p1 = s.optDouble("p1", .5)
        var p2 = s.optDouble("p2", .5)
        val nextTick = s.optLong("tick", 0L) + 1L
        var rally = s.optInt("rally", 0)

        if (s.optBoolean("cpu", true)) {
            val delta = x - p2
            p2 += delta.coerceIn(-.018, .018)
        }

        val serveTicks = s.optInt("serve_ticks", 0)
        if (serveTicks > 0) {
            return s
                .put("p2", p2)
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
        if (y > .93 && vy > 0 && abs(x - p1) < paddleHalf) {
            y = .93
            rally += 1
            vy = (-abs(vy) * 1.045).coerceAtLeast(-.86)
            vx = ((vx + (x - p1) * .30) * 1.018).coerceIn(-.72, .72)
        }

        if (y < .07 && vy < 0 && abs(x - p2) < paddleHalf) {
            y = .07
            rally += 1
            vy = (abs(vy) * 1.045).coerceAtMost(.86)
            vx = ((vx + (x - p2) * .30) * 1.018).coerceIn(-.72, .72)
        }

        var p1Score = s.optInt("p1_score", 0)
        var p2Score = s.optInt("p2_score", 0)
        var lastPoint = 0

        if (y < 0) {
            p1Score++
            lastPoint = 1
            x = .5
            y = .5
            vx = .24
            vy = .44
            rally = 0
        } else if (y > 1) {
            p2Score++
            lastPoint = 2
            x = .5
            y = .5
            vx = -.24
            vy = -.44
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
    // Breakout
    // -----------------------------------------------------------------

    private fun breakoutInitial(): String = JSONObject()
        .put("game", BREAKOUT)
        .put("ball_x", .5)
        .put("ball_y", .72)
        .put("vx", .27)
        .put("vy", -.40)
        .put("paddle", .5)
        .put("bricks", JSONArray(List(30) { 1 }))
        .put("score", 0)
        .put("lives", 3)
        .put("tick", 0L)
        .put("started", false)
        .put("finished", false)
        .put("winner", "")
        .put("serve_ticks", 0)
        .toString()

    fun breakoutStart(stateJson: String): String {
        val s = JSONObject(stateJson)
        if (!s.optBoolean("finished", false)) {
            s.put("started", true)
                .put("serve_ticks", 34)
        }
        return s.toString()
    }

    fun breakoutPaddle(stateJson: String, x: Float): String {
        val s = JSONObject(stateJson)
        if (s.optBoolean("finished", false)) return stateJson
        s.put("paddle", x.coerceIn(.15f, .85f).toDouble())
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
            return s.put("serve_ticks", serveTicks - 1)
                .put("tick", nextTick)
                .toString()
        }

        var x = s.optDouble("ball_x", .5)
        var y = s.optDouble("ball_y", .72)
        var vx = s.optDouble("vx", .27)
        var vy = s.optDouble("vy", -.40)
        val paddle = s.optDouble("paddle", .5)
        var score = s.optInt("score", 0)
        var lives = s.optInt("lives", 3)
        val brickArray = s.getJSONArray("bricks")
        val bricks = MutableList(30) { brickArray.optInt(it, 1) }

        x += vx * dt
        y += vy * dt

        if (x < .025) {
            x = .025
            vx = abs(vx)
        } else if (x > .975) {
            x = .975
            vx = -abs(vx)
        }
        if (y < .025) {
            y = .025
            vy = abs(vy)
        }

        // Paddle occupies the lower part of the court.
        if (y > .90 && y < .955 && vy > 0 && abs(x - paddle) < .17) {
            y = .90
            vy = (-abs(vy) * 1.025).coerceAtLeast(-.86)
            vx = ((vx + (x - paddle) * .34) * 1.012).coerceIn(-.72, .72)
        }

        // Six columns by five rows. The collision model deliberately uses the
        // ball centre: simple, deterministic and stable at the current speed.
        if (y in 0.11..0.43) {
            val col = (x * 6.0).toInt().coerceIn(0, 5)
            val row = ((y - .11) / .064).toInt().coerceIn(0, 4)
            val index = row * 6 + col
            if (bricks[index] == 1) {
                bricks[index] = 0
                score += 10
                val direction = if (vy > 0) -1.0 else 1.0
                vy = (abs(vy) * 1.012).coerceAtMost(.86) * direction
                vx = (vx * 1.008).coerceIn(-.72, .72)
            }
        }

        var finished = false
        var winner = ""
        var serve = 0

        if (bricks.none { it == 1 }) {
            finished = true
            winner = "cleared"
        } else if (y > 1.02) {
            lives -= 1
            if (lives <= 0) {
                finished = true
                winner = "failed"
            } else {
                x = .5
                y = .72
                vx = if (lives % 2 == 0) -.27 else .27
                vy = -.40
                serve = 34
            }
        }

        return s
            .put("ball_x", x)
            .put("ball_y", y)
            .put("vx", vx)
            .put("vy", vy)
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

    private fun dodgeInitial(): String = JSONObject()
        .put("game", DODGE)
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

        // A visible level increase about every eight seconds. Both fall speed and
        // spawn pressure tighten, so progression can be felt rather than inferred.
        val level = (1 + (tick / 500L).toInt()).coerceAtMost(10)
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

}
