package com.example.methodmesh.modules.chance

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Pure Kotlin dice-expression engine.
 *
 * The engine deliberately separates outcome generation from presentation. Native animation must
 * never decide a result: it receives an already-computed DiceSimulation and only visualises it.
 *
 * Supported v0.2 grammar (no parentheses yet):
 *   d20
 *   6d6+d20
 *   2d6+4
 *   4d6kh3 / kl / dh / dl
 *   d20r1          (reroll once when exactly 1)
 *   d6rr<3         (reroll repeatedly while < 3)
 *   d6!            (explode on maximum)
 *   d6!>=5         (explode on condition)
 *   8d6cs>=5       (count retained dice meeting condition)
 *   dF             (Fate/Fudge: -1, 0, +1)
 *   d%             (percentile alias for d100)
 *   d20+5>=15      (top-level roll-over/under comparison)
 */
object DiceSimulatorEngine {
    const val ENGINE_VERSION = "0.2.0"
    const val FIXED_ALGORITHM = "methodmesh.sha256_counter_u32_rejection"
    const val FIXED_ALGORITHM_VERSION = "2.0.0"
    const val SECURE_ALGORITHM = "java.security.SecureRandom.nextInt(bound)"

    const val MAX_DICE = 100
    const val MAX_SIDES = 1000
    const val MAX_ROUNDS = 1000
    const val MAX_PRIMITIVE_DRAWS_PER_ROUND = 10_000
    private const val MAX_CHAIN_DRAWS_PER_DIE = 1000

    enum class CompareOp(val symbol: String) {
        LT("<"), LTE("<="), EQ("="), GTE(">="), GT(">");

        fun matches(value: Int, target: Int): Boolean = when (this) {
            LT -> value < target
            LTE -> value <= target
            EQ -> value == target
            GTE -> value >= target
            GT -> value > target
        }
    }

    data class Condition(val op: CompareOp, val target: Int) {
        fun matches(value: Int): Boolean = op.matches(value, target)
        override fun toString(): String = "${op.symbol}$target"
    }

    enum class KeepDropKind(val code: String) {
        KEEP_HIGHEST("kh"), KEEP_LOWEST("kl"), DROP_HIGHEST("dh"), DROP_LOWEST("dl")
    }

    data class KeepDropRule(val kind: KeepDropKind, val count: Int)
    data class RerollRule(val repeat: Boolean, val condition: Condition)
    data class ExplodeRule(val condition: Condition?, val onMaximum: Boolean)
    data class SuccessRule(val condition: Condition)

    sealed interface TermSpec {
        val sign: Int
        val source: String
    }

    data class ConstantTerm(
        override val sign: Int,
        val value: Int,
        override val source: String
    ) : TermSpec

    data class DiceTerm(
        override val sign: Int,
        val count: Int,
        val sides: Int?,
        val fate: Boolean,
        val keepDrop: KeepDropRule?,
        val reroll: RerollRule?,
        val explode: ExplodeRule?,
        val success: SuccessRule?,
        override val source: String
    ) : TermSpec {
        val sidesLabel: String get() = if (fate) "F" else sides.toString()
        val minimumFace: Int get() = if (fate) -1 else 1
        val maximumFace: Int get() = if (fate) 1 else requireNotNull(sides)
    }

    data class ParsedExpression(
        val original: String,
        val canonical: String,
        val terms: List<TermSpec>,
        val comparison: Condition?
    ) {
        val diceCount: Int = terms.filterIsInstance<DiceTerm>().sumOf { it.count }
    }

    enum class DrawKind { INITIAL, REROLL, EXPLOSION }

    data class PrimitiveDraw(
        val sequence: Int,
        val termIndex: Int,
        val dieIndex: Int,
        val kind: DrawKind,
        val value: Int
    )

    data class DieOutcome(
        val termIndex: Int,
        val dieIndex: Int,
        val sidesLabel: String,
        val initialValue: Int,
        val rerolledValues: List<Int>,
        val acceptedBaseValue: Int,
        val explosionValues: List<Int>,
        val total: Int,
        val retained: Boolean
    )

    data class TermOutcome(
        val termIndex: Int,
        val source: String,
        val sign: Int,
        val dice: List<DieOutcome>,
        val successCount: Int?,
        val unsignedValue: Int,
        val signedValue: Int
    )

    data class SimulationRound(
        val index: Int,
        val terms: List<TermOutcome>,
        val total: Int,
        val comparison: Condition?,
        val comparisonPassed: Boolean?,
        val primitiveDraws: List<PrimitiveDraw>
    ) {
        val allDice: List<DieOutcome> get() = terms.flatMap { it.dice }
    }

    data class DiceSimulation(
        val expression: String,
        val canonicalExpression: String,
        val rounds: List<SimulationRound>,
        val rngMode: String,
        val algorithm: String,
        val algorithmVersion: String,
        val seed: String?
    )

    data class CoinResult(
        val tosses: List<Boolean>,
        val rngMode: String,
        val algorithm: String,
        val algorithmVersion: String,
        val seed: String?
    ) {
        val heads: Int = tosses.count { it }
        val tails: Int = tosses.size - heads
    }

    fun parse(expression: String): ParsedExpression {
        val original = expression.trim()
        require(original.isNotBlank()) { "Enter a dice expression or add at least one die." }
        val compact = original.replace(" ", "")

        val comparisonSplit = splitTopLevelComparison(compact)
        val arithmetic = comparisonSplit.first
        val comparison = comparisonSplit.second
        val signedTokens = splitTerms(arithmetic)
        require(signedTokens.isNotEmpty()) { "The expression contains no terms." }

        val terms = signedTokens.mapIndexed { index, (sign, token) ->
            parseTerm(sign, token, index)
        }
        val diceCount = terms.filterIsInstance<DiceTerm>().sumOf { it.count }
        require(diceCount in 1..MAX_DICE) { "An expression must contain between 1 and $MAX_DICE dice." }

        val canonical = buildCanonical(terms, comparison)
        return ParsedExpression(original, canonical, terms, comparison)
    }

    fun simulate(
        expression: String,
        rollCount: Int = 1,
        rngMode: String = "secure_random",
        seed: String? = null
    ): DiceSimulation {
        require(rollCount in 1..MAX_ROUNDS) { "Number of simulations must be between 1 and $MAX_ROUNDS." }
        val parsed = parse(expression)
        val source = randomSource(rngMode, seed)
        val rounds = (1..rollCount).map { roundIndex -> executeRound(parsed, roundIndex, source) }
        return DiceSimulation(
            expression = expression,
            canonicalExpression = parsed.canonical,
            rounds = rounds,
            rngMode = source.mode,
            algorithm = source.algorithm,
            algorithmVersion = source.algorithmVersion,
            seed = source.seed
        )
    }

    fun tossCoins(count: Int, rngMode: String, seed: String?): CoinResult {
        require(count in 1..10_000) { "Number of tosses must be between 1 and 10000." }
        val source = randomSource(rngMode, seed)
        val tosses = List(count) { source.nextInt(2) == 1 }
        return CoinResult(tosses, source.mode, source.algorithm, source.algorithmVersion, source.seed)
    }

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Expand original dice only, for the native tray. Constants are intentionally omitted. */
    fun visualDice(expression: String): List<Pair<String, Int>> {
        val parsed = parse(expression)
        return parsed.terms.filterIsInstance<DiceTerm>().flatMap { term ->
            List(term.count) { term.sidesLabel to (term.sides ?: 3) }
        }
    }

    private fun executeRound(parsed: ParsedExpression, roundIndex: Int, source: IntRandomSource): SimulationRound {
        val primitive = mutableListOf<PrimitiveDraw>()
        var sequence = 0

        fun draw(termIndex: Int, dieIndex: Int, term: DiceTerm, kind: DrawKind): Int {
            require(primitive.size < MAX_PRIMITIVE_DRAWS_PER_ROUND) {
                "The dice rules generated too many primitive draws. Check repeated reroll/explosion conditions."
            }
            val value = if (term.fate) source.nextInt(3) - 1 else source.nextInt(requireNotNull(term.sides)) + 1
            primitive += PrimitiveDraw(++sequence, termIndex, dieIndex, kind, value)
            return value
        }

        val outcomes = parsed.terms.mapIndexed { termIndex, spec ->
            when (spec) {
                is ConstantTerm -> TermOutcome(
                    termIndex = termIndex,
                    source = spec.source,
                    sign = spec.sign,
                    dice = emptyList(),
                    successCount = null,
                    unsignedValue = spec.value,
                    signedValue = spec.sign * spec.value
                )
                is DiceTerm -> executeDiceTerm(termIndex, spec, ::draw)
            }
        }
        val total = outcomes.sumOf { it.signedValue }
        val passed = parsed.comparison?.matches(total)
        return SimulationRound(roundIndex, outcomes, total, parsed.comparison, passed, primitive.toList())
    }

    private fun executeDiceTerm(
        termIndex: Int,
        term: DiceTerm,
        draw: (Int, Int, DiceTerm, DrawKind) -> Int
    ): TermOutcome {
        var dice = (1..term.count).map { dieIndex ->
            var accepted = draw(termIndex, dieIndex, term, DrawKind.INITIAL)
            val initial = accepted
            val rerolled = mutableListOf<Int>()

            term.reroll?.let { rule ->
                if (rule.repeat) {
                    var chainCount = 0
                    while (rule.condition.matches(accepted)) {
                        require(chainCount++ < MAX_CHAIN_DRAWS_PER_DIE) { "Repeated reroll did not terminate." }
                        rerolled += accepted
                        accepted = draw(termIndex, dieIndex, term, DrawKind.REROLL)
                    }
                } else if (rule.condition.matches(accepted)) {
                    rerolled += accepted
                    accepted = draw(termIndex, dieIndex, term, DrawKind.REROLL)
                }
            }

            val explosions = mutableListOf<Int>()
            term.explode?.let { rule ->
                var triggerValue = accepted
                var chainCount = 0
                while (explodeMatches(term, rule, triggerValue)) {
                    require(chainCount++ < MAX_CHAIN_DRAWS_PER_DIE) { "Explosion chain did not terminate." }
                    var next = draw(termIndex, dieIndex, term, DrawKind.EXPLOSION)
                    term.reroll?.let { rerollRule ->
                        if (rerollRule.repeat) {
                            var rerolls = 0
                            while (rerollRule.condition.matches(next)) {
                                require(rerolls++ < MAX_CHAIN_DRAWS_PER_DIE) { "Repeated reroll did not terminate inside explosion chain." }
                                rerolled += next
                                next = draw(termIndex, dieIndex, term, DrawKind.REROLL)
                            }
                        } else if (rerollRule.condition.matches(next)) {
                            rerolled += next
                            next = draw(termIndex, dieIndex, term, DrawKind.REROLL)
                        }
                    }
                    explosions += next
                    triggerValue = next
                }
            }

            DieOutcome(
                termIndex = termIndex,
                dieIndex = dieIndex,
                sidesLabel = term.sidesLabel,
                initialValue = initial,
                rerolledValues = rerolled,
                acceptedBaseValue = accepted,
                explosionValues = explosions,
                total = accepted + explosions.sum(),
                retained = true
            )
        }

        term.keepDrop?.let { rule ->
            val ordered = when (rule.kind) {
                KeepDropKind.KEEP_HIGHEST, KeepDropKind.DROP_HIGHEST -> dice.sortedWith(compareByDescending<DieOutcome> { it.total }.thenBy { it.dieIndex })
                KeepDropKind.KEEP_LOWEST, KeepDropKind.DROP_LOWEST -> dice.sortedWith(compareBy<DieOutcome> { it.total }.thenBy { it.dieIndex })
            }
            val selected = ordered.take(rule.count).map { it.dieIndex }.toSet()
            dice = dice.map { die ->
                val retained = when (rule.kind) {
                    KeepDropKind.KEEP_HIGHEST, KeepDropKind.KEEP_LOWEST -> die.dieIndex in selected
                    KeepDropKind.DROP_HIGHEST, KeepDropKind.DROP_LOWEST -> die.dieIndex !in selected
                }
                die.copy(retained = retained)
            }
        }

        val retained = dice.filter { it.retained }
        val successCount = term.success?.let { rule -> retained.count { rule.condition.matches(it.total) } }
        val unsigned = successCount ?: retained.sumOf { it.total }
        return TermOutcome(
            termIndex = termIndex,
            source = term.source,
            sign = term.sign,
            dice = dice,
            successCount = successCount,
            unsignedValue = unsigned,
            signedValue = term.sign * unsigned
        )
    }

    private fun parseTerm(sign: Int, token: String, index: Int): TermSpec {
        token.toIntOrNull()?.let { value ->
            require(value >= 0) { "Use + or - between terms rather than a signed constant." }
            return ConstantTerm(sign, value, token)
        }

        val base = Regex("(?i)^(\\d*)d(%|f|\\d+)(.*)$").matchEntire(token)
            ?: throw IllegalArgumentException("Invalid term '$token'. Examples: d20, 2d6+4, 4d6kh3, d6!, 8d6cs>=5.")
        val count = base.groupValues[1].ifBlank { "1" }.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid dice count in '$token'.")
        require(count in 1..MAX_DICE) { "Dice groups must contain between 1 and $MAX_DICE dice." }
        val sidesToken = base.groupValues[2].lowercase()
        val fate = sidesToken == "f"
        val sides = when (sidesToken) {
            "f" -> null
            "%" -> 100
            else -> sidesToken.toIntOrNull()
        }
        if (!fate) require(sides in 2..MAX_SIDES) { "Dice must have between 2 and $MAX_SIDES sides." }

        var suffix = base.groupValues[3]
        var keepDrop: KeepDropRule? = null
        var reroll: RerollRule? = null
        var explode: ExplodeRule? = null
        var success: SuccessRule? = null

        while (suffix.isNotEmpty()) {
            val kd = Regex("(?i)^(kh|kl|dh|dl)(\\d+)(.*)$").matchEntire(suffix)
            if (kd != null) {
                require(keepDrop == null) { "Only one keep/drop rule is allowed per dice term." }
                val n = kd.groupValues[2].toInt()
                require(n in 1..count) { "Keep/drop count must be between 1 and the dice count ($count)." }
                val kind = when (kd.groupValues[1].lowercase()) {
                    "kh" -> KeepDropKind.KEEP_HIGHEST
                    "kl" -> KeepDropKind.KEEP_LOWEST
                    "dh" -> KeepDropKind.DROP_HIGHEST
                    else -> KeepDropKind.DROP_LOWEST
                }
                keepDrop = KeepDropRule(kind, n)
                suffix = kd.groupValues[3]
                continue
            }

            val rr = Regex("(?i)^(rr|r)(<=|>=|<|>|=)?(\\d+)(.*)$").matchEntire(suffix)
            if (rr != null) {
                require(reroll == null) { "Only one reroll rule is allowed per dice term." }
                val repeat = rr.groupValues[1].equals("rr", true)
                val op = parseCompareOp(rr.groupValues[2].ifBlank { "=" })
                val condition = Condition(op, rr.groupValues[3].toInt())
                if (repeat) require(!matchesAllFaces(fate, sides, condition)) {
                    "Repeated reroll condition $condition matches every face and could never terminate."
                }
                reroll = RerollRule(repeat, condition)
                suffix = rr.groupValues[4]
                continue
            }

            val ex = Regex("^!(?:(<=|>=|<|>|=)?(\\d+))?(.*)$").matchEntire(suffix)
            if (ex != null) {
                require(explode == null) { "Only one explosion rule is allowed per dice term." }
                val threshold = ex.groupValues[2]
                explode = if (threshold.isBlank()) {
                    ExplodeRule(condition = null, onMaximum = true)
                } else {
                    val condition = Condition(parseCompareOp(ex.groupValues[1].ifBlank { "=" }), threshold.toInt())
                    require(!matchesAllFaces(fate, sides, condition)) {
                        "Explosion condition $condition matches every face and could never terminate."
                    }
                    ExplodeRule(condition, onMaximum = false)
                }
                suffix = ex.groupValues[3]
                continue
            }

            val cs = Regex("(?i)^cs(<=|>=|<|>|=)(\\d+)(.*)$").matchEntire(suffix)
            if (cs != null) {
                require(success == null) { "Only one success-count rule is allowed per dice term." }
                success = SuccessRule(Condition(parseCompareOp(cs.groupValues[1]), cs.groupValues[2].toInt()))
                suffix = cs.groupValues[3]
                continue
            }

            throw IllegalArgumentException("Could not parse dice operator '$suffix' in term ${index + 1} ('$token').")
        }

        return DiceTerm(sign, count, sides, fate, keepDrop, reroll, explode, success, token)
    }

    private fun splitTopLevelComparison(compact: String): Pair<String, Condition?> {
        val match = Regex("^(.+?)(<=|>=|<|>|=)(\\d+)$").matchEntire(compact) ?: return compact to null
        val left = match.groupValues[1]
        val lower = left.lowercase()
        // These comparator forms belong to an immediately preceding dice operator, not the expression.
        if (lower.endsWith("cs") || lower.endsWith("!") || lower.endsWith("r") || lower.endsWith("rr")) {
            return compact to null
        }
        return left to Condition(parseCompareOp(match.groupValues[2]), match.groupValues[3].toInt())
    }

    private fun splitTerms(arithmetic: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var sign = 1
        var start = 0
        var i = 0
        if (arithmetic.startsWith('+')) { start = 1; i = 1 }
        else if (arithmetic.startsWith('-')) { sign = -1; start = 1; i = 1 }

        while (i <= arithmetic.length) {
            val atEnd = i == arithmetic.length
            val separator = !atEnd && (arithmetic[i] == '+' || arithmetic[i] == '-')
            if (atEnd || separator) {
                val token = arithmetic.substring(start, i)
                require(token.isNotBlank()) { "Missing term near position ${i + 1}." }
                out += sign to token
                if (!atEnd) {
                    sign = if (arithmetic[i] == '+') 1 else -1
                    start = i + 1
                }
            }
            i++
        }
        return out
    }

    private fun buildCanonical(terms: List<TermSpec>, comparison: Condition?): String {
        val body = terms.mapIndexed { index, term ->
            val signText = when {
                index == 0 && term.sign < 0 -> "-"
                index == 0 -> ""
                term.sign < 0 -> "-"
                else -> "+"
            }
            signText + when (term) {
                is ConstantTerm -> term.value.toString()
                is DiceTerm -> buildString {
                    append(term.count)
                    append('d')
                    append(if (term.fate) "F" else term.sides)
                    term.keepDrop?.let { append(it.kind.code).append(it.count) }
                    term.reroll?.let {
                        append(if (it.repeat) "rr" else "r")
                        if (it.condition.op != CompareOp.EQ) append(it.condition.op.symbol)
                        append(it.condition.target)
                    }
                    term.explode?.let {
                        append('!')
                        if (!it.onMaximum) append(requireNotNull(it.condition))
                    }
                    term.success?.let { append("cs").append(it.condition) }
                }
            }
        }.joinToString("")
        return body + (comparison?.toString() ?: "")
    }

    private fun parseCompareOp(value: String): CompareOp = when (value) {
        "<" -> CompareOp.LT
        "<=" -> CompareOp.LTE
        "=", "==" -> CompareOp.EQ
        ">=" -> CompareOp.GTE
        ">" -> CompareOp.GT
        else -> throw IllegalArgumentException("Unknown comparison operator '$value'.")
    }

    private fun matchesAllFaces(fate: Boolean, sides: Int?, condition: Condition): Boolean {
        val min = if (fate) -1 else 1
        val max = if (fate) 1 else requireNotNull(sides)
        return (min..max).all(condition::matches)
    }

    private fun explodeMatches(term: DiceTerm, rule: ExplodeRule, value: Int): Boolean =
        if (rule.onMaximum) value == term.maximumFace else requireNotNull(rule.condition).matches(value)

    private interface IntRandomSource {
        val mode: String
        val algorithm: String
        val algorithmVersion: String
        val seed: String?
        fun nextInt(bound: Int): Int
    }

    private fun randomSource(mode: String, seed: String?): IntRandomSource = when (mode) {
        "secure_random" -> SecureRandomSource()
        "fixed_seed" -> {
            val supplied = seed?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Fixed-seed mode requires a non-empty seed.")
            Sha256CounterSource(supplied)
        }
        else -> throw IllegalArgumentException("Unknown random source '$mode'.")
    }

    private class SecureRandomSource : IntRandomSource {
        private val random = SecureRandom()
        override val mode = "secure_random"
        override val algorithm = SECURE_ALGORITHM
        override val algorithmVersion = "platform"
        override val seed: String? = null
        override fun nextInt(bound: Int): Int {
            require(bound > 0)
            return random.nextInt(bound)
        }
    }

    /** Versioned deterministic source for tests/replay; rejection sampling avoids modulo bias. */
    private class Sha256CounterSource(seedText: String) : IntRandomSource {
        private val seedBytes = MessageDigest.getInstance("SHA-256").digest(seedText.toByteArray(Charsets.UTF_8))
        private var counter = 0L
        override val mode = "fixed_seed"
        override val algorithm = FIXED_ALGORITHM
        override val algorithmVersion = FIXED_ALGORITHM_VERSION
        override val seed: String = seedText

        override fun nextInt(bound: Int): Int {
            require(bound > 0)
            val modulus = 1L shl 32
            val limit = modulus - (modulus % bound.toLong())
            while (true) {
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update("MethodMesh.DiceSimulator.v2".toByteArray(Charsets.UTF_8))
                digest.update(seedBytes)
                digest.update(ByteBuffer.allocate(8).putLong(counter++).array())
                val bytes = digest.digest()
                val unsigned = ((bytes[0].toLong() and 0xffL) shl 24) or
                    ((bytes[1].toLong() and 0xffL) shl 16) or
                    ((bytes[2].toLong() and 0xffL) shl 8) or
                    (bytes[3].toLong() and 0xffL)
                if (unsigned < limit) return (unsigned % bound.toLong()).toInt()
            }
        }
    }
}
