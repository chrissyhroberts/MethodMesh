package com.example.methodmesh.modules.chance

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Random

/** Pure Kotlin randomisation primitives for non-dice Chance capabilities. */
object ChanceSelectionEngine {
    const val ENGINE_VERSION = "0.2.0"
    const val FIXED_ALGORITHM_VERSION = "java.util.Random-sha256-seed-v1"
    const val SECURE_ALGORITHM_VERSION = "java.security.SecureRandom-v1"

    data class PrimitiveDraw(val sequence: Int, val bound: Int, val value: Int)

    data class Metadata(
        val rngMode: String,
        val seed: String?,
        val algorithm: String,
        val algorithmVersion: String,
        val generatedTimeIso: String,
        val primitiveDraws: List<PrimitiveDraw>
    )

    data class CardDraw(
        val cards: List<String>,
        val metadata: Metadata
    )

    data class PickPreparation(
        val setType: String,
        val arrangement: List<String>,
        val metadata: Metadata
    ) {
        fun toJson(): String = JSONObject()
            .put("set_type", setType)
            .put("arrangement", JSONArray(arrangement))
            .put("rng_mode", metadata.rngMode)
            .put("seed", metadata.seed ?: JSONObject.NULL)
            .put("algorithm", metadata.algorithm)
            .put("algorithm_version", metadata.algorithmVersion)
            .put("generated_time_iso", metadata.generatedTimeIso)
            .put("primitive_draws", primitiveDrawsJson(metadata.primitiveDraws))
            .toString()
    }

    data class Choice(
        val index: Int,
        val label: String,
        val metadata: Metadata
    )

    data class WeightedOption(val label: String, val weight: Double)

    data class WeightedChoice(
        val index: Int,
        val label: String,
        val weight: Double,
        val probability: Double,
        val totalWeight: Double,
        val options: List<WeightedOption>,
        val metadata: Metadata
    )

    private interface IntSource {
        val mode: String
        val seed: String?
        val algorithm: String
        val algorithmVersion: String
        fun nextInt(bound: Int): Int
    }

    private class SecureSource : IntSource {
        private val random = SecureRandom()
        override val mode = "secure_random"
        override val seed: String? = null
        override val algorithm = "java.security.SecureRandom"
        override val algorithmVersion = SECURE_ALGORITHM_VERSION
        override fun nextInt(bound: Int): Int = random.nextInt(bound)
    }

    private class FixedSource(seedText: String) : IntSource {
        private val actualSeed = seedText.ifBlank { "methodmesh-chance" }
        private val random = Random(seedLong(actualSeed))
        override val mode = "fixed_seed"
        override val seed: String = actualSeed
        override val algorithm = "java.util.Random"
        override val algorithmVersion = FIXED_ALGORITHM_VERSION
        override fun nextInt(bound: Int): Int = random.nextInt(bound)
    }

    private class RecordingSource(private val delegate: IntSource) {
        private val draws = mutableListOf<PrimitiveDraw>()
        fun nextInt(bound: Int): Int {
            require(bound > 0) { "Random bound must be positive." }
            val value = delegate.nextInt(bound)
            draws += PrimitiveDraw(draws.size + 1, bound, value)
            return value
        }
        fun metadata(): Metadata = Metadata(
            rngMode = delegate.mode,
            seed = delegate.seed,
            algorithm = delegate.algorithm,
            algorithmVersion = delegate.algorithmVersion,
            generatedTimeIso = Instant.now().toString(),
            primitiveDraws = draws.toList()
        )
    }

    fun drawCards(drawCount: Int, includeJokers: Boolean, rngMode: String, seed: String): CardDraw {
        val deck = standardDeck(includeJokers).toMutableList()
        require(drawCount in 1..deck.size) { "Draw count must be between 1 and ${deck.size}." }
        val source = recordingSource(rngMode, seed)
        val cards = buildList {
            repeat(drawCount) {
                val index = source.nextInt(deck.size)
                add(deck.removeAt(index))
            }
        }
        return CardDraw(cards, source.metadata())
    }

    fun preparePick(setType: String, itemCount: Int, customItems: String, rngMode: String, seed: String): PickPreparation {
        val source = recordingSource(rngMode, seed)
        val normalized = setType.trim().lowercase()
        val values = when (normalized) {
            "numbers" -> {
                require(itemCount in 2..12) { "Numbers supports 2 to 12 choices." }
                (1..itemCount).map(Int::toString)
            }
            "colours" -> selectDistinct(COLOURS, itemCount, source, "Colours")
            "suits" -> {
                require(itemCount in 2..4) { "Suits supports 2 to 4 choices." }
                SUITS.take(itemCount)
            }
            "abstract" -> selectDistinct(ABSTRACT, itemCount, source, "Abstract symbols")
            "classic_cards" -> {
                require(itemCount in 2..12) { "Classic cards supports 2 to 12 choices." }
                val deck = standardDeck(false).toMutableList()
                buildList {
                    repeat(itemCount) { add(deck.removeAt(source.nextInt(deck.size))) }
                }
            }
            "custom" -> {
                val parsed = parseItems(customItems)
                require(parsed.size in 2..12) { "Custom Pick one needs 2 to 12 non-empty choices." }
                parsed
            }
            else -> throw IllegalArgumentException("Unknown Pick one set '$setType'.")
        }
        val shuffled = shuffled(values, source)
        return PickPreparation(normalized, shuffled, source.metadata())
    }

    fun parsePickPreparation(json: String): PickPreparation {
        val obj = JSONObject(json)
        val arrangementArray = obj.getJSONArray("arrangement")
        val arrangement = (0 until arrangementArray.length()).map { arrangementArray.getString(it) }
        val drawArray = obj.optJSONArray("primitive_draws") ?: JSONArray()
        val draws = (0 until drawArray.length()).map { i ->
            val d = drawArray.getJSONObject(i)
            PrimitiveDraw(d.optInt("sequence", i + 1), d.getInt("bound"), d.getInt("value"))
        }
        return PickPreparation(
            setType = obj.optString("set_type", "custom"),
            arrangement = arrangement,
            metadata = Metadata(
                rngMode = obj.optString("rng_mode", "secure_random"),
                seed = obj.optString("seed", "").takeIf { it.isNotBlank() && it != "null" },
                algorithm = obj.optString("algorithm", ""),
                algorithmVersion = obj.optString("algorithm_version", ""),
                generatedTimeIso = obj.optString("generated_time_iso", ""),
                primitiveDraws = draws
            )
        )
    }

    fun spin(itemsText: String, rngMode: String, seed: String): Choice {
        val items = parseItems(itemsText)
        require(items.size in 2..60) { "Spinner needs 2 to 60 non-empty labels." }
        val source = recordingSource(rngMode, seed)
        val index = source.nextInt(items.size)
        return Choice(index, items[index], source.metadata())
    }

    fun weightedChoose(weightedText: String, rngMode: String, seed: String): WeightedChoice {
        val options = parseWeightedItems(weightedText)
        require(options.size in 2..60) { "Weighted choice needs 2 to 60 labelled options." }
        val total = options.sumOf { it.weight }
        require(total > 0.0 && total.isFinite()) { "Total weight must be finite and greater than zero." }

        // Build a 53-bit uniform fraction from two bounded integer draws. This
        // matches the useful precision of a Double without requiring weights to
        // sum to 1, 100, or any bounded integer total.
        val source = recordingSource(rngMode, seed)
        val high = source.nextInt(1 shl 26).toLong()
        val low = source.nextInt(1 shl 27).toLong()
        val unit = ((high shl 27) + low).toDouble() / (1L shl 53).toDouble()
        val target = unit * total
        var cumulative = 0.0
        var selected = options.lastIndex
        for (i in options.indices) {
            cumulative += options[i].weight
            if (target < cumulative) {
                selected = i
                break
            }
        }
        val option = options[selected]
        return WeightedChoice(
            index = selected,
            label = option.label,
            weight = option.weight,
            probability = option.weight / total,
            totalWeight = total,
            options = options,
            metadata = source.metadata()
        )
    }

    fun parseItems(text: String): List<String> = text
        .split('\n', '|', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    fun parseWeightedItems(text: String): List<WeightedOption> = text
        .lines()
        .flatMap { line -> line.split(';', '|') }
        .mapNotNull { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@mapNotNull null
            val splitAt = line.lastIndexOf(':')
            require(splitAt > 0 && splitAt < line.lastIndex) { "Weighted choices use 'Label:weight', one per line." }
            val label = line.substring(0, splitAt).trim()
            val weight = line.substring(splitAt + 1).trim().toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid weight for '$label'.")
            require(label.isNotBlank()) { "Weighted choice labels cannot be blank." }
            require(weight > 0.0 && weight.isFinite()) { "Weight for '$label' must be greater than zero." }
            WeightedOption(label, weight)
        }

    fun standardDeck(includeJokers: Boolean): List<String> = buildList {
        val ranks = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
        val suits = listOf("♠", "♥", "♦", "♣")
        suits.forEach { suit -> ranks.forEach { rank -> add("$rank$suit") } }
        if (includeJokers) {
            add("Joker ★")
            add("Joker ☆")
        }
    }

    fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun primitiveDrawsJson(draws: List<PrimitiveDraw>): JSONArray = JSONArray().apply {
        draws.forEach { draw -> put(JSONObject().put("sequence", draw.sequence).put("bound", draw.bound).put("value", draw.value)) }
    }

    private fun recordingSource(rngMode: String, seed: String): RecordingSource = when (rngMode) {
        "secure_random" -> RecordingSource(SecureSource())
        "fixed_seed" -> RecordingSource(FixedSource(seed))
        else -> throw IllegalArgumentException("Random source must be secure_random or fixed_seed.")
    }

    private fun <T> shuffled(values: List<T>, source: RecordingSource): List<T> {
        val out = values.toMutableList()
        for (i in out.lastIndex downTo 1) {
            val j = source.nextInt(i + 1)
            val tmp = out[i]
            out[i] = out[j]
            out[j] = tmp
        }
        return out
    }

    private fun selectDistinct(values: List<String>, count: Int, source: RecordingSource, label: String): List<String> {
        require(count in 2..values.size) { "$label supports 2 to ${values.size} choices." }
        val pool = values.toMutableList()
        return buildList {
            repeat(count) { add(pool.removeAt(source.nextInt(pool.size))) }
        }
    }


    private fun seedLong(seed: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        var value = 0L
        repeat(8) { i -> value = (value shl 8) or (digest[i].toLong() and 0xffL) }
        return value
    }

    private val COLOURS = listOf(
        "Red", "Orange", "Yellow", "Green", "Teal", "Blue", "Indigo", "Purple", "Pink", "Brown", "Grey", "Black"
    )
    private val SUITS = listOf("♠ Spades", "♥ Hearts", "♦ Diamonds", "♣ Clubs")
    private val ABSTRACT = listOf(
        "Single wave", "Double wave", "Zigzag", "Loop", "Three dots", "Parallel lines",
        "Crosshatch", "Spiral", "Chevron", "Arc", "Mesh", "Broken line"
    )
}
