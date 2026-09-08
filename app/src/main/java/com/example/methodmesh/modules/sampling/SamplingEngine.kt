package com.example.methodmesh.modules.sampling

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.ceil
import kotlin.math.floor

private const val MAX_POPULATION_SIZE = 100_000

enum class SamplingSourceType(val wireValue: String) {
    MANUAL("manual"), CSV("csv"), SEQUENCE("sequence"), RANDOM_WORDS("random_words"), STRUCTURED("structured");
    companion object { fun from(value: String?) = values().firstOrNull { it.wireValue == value?.lowercase() } ?: MANUAL }
}

enum class SamplingOperation(val wireValue: String) {
    SIMPLE_SAMPLE("simple_sample"), SHUFFLE("shuffle"), WEIGHTED_SAMPLE("weighted_sample"),
    STRATIFIED_SAMPLE("stratified_sample"), SYSTEMATIC_SAMPLE("systematic_sample"),
    PARTITION("partition"), POPULATION_ONLY("population_only");
    companion object { fun from(value: String?) = values().firstOrNull { it.wireValue == value?.lowercase() } ?: SIMPLE_SAMPLE }
}

enum class SamplingSampleMode(val wireValue: String) {
    N("n"), FRACTION("fraction");
    companion object { fun from(value: String?) = values().firstOrNull { it.wireValue == value?.lowercase() } ?: N }
}

enum class SamplingOutputOrder(val wireValue: String) {
    DRAW("draw"), INPUT("input"), SORTED("sorted");
    companion object { fun from(value: String?) = values().firstOrNull { it.wireValue == value?.lowercase() } ?: DRAW }
}

enum class SamplingOutputMode(val wireValue: String) {
    ANNOTATED("annotated"), SELECTED_ONLY("selected_only");
    companion object { fun from(value: String?) = values().firstOrNull { it.wireValue == value?.lowercase() } ?: ANNOTATED }
}

enum class SamplingOutputFormat(val wireValue: String) {
    CSV("csv"), JSON("json");
    companion object { fun from(value: String?) = values().firstOrNull { it.wireValue == value?.lowercase() } ?: CSV }
}

enum class SamplingStratumAllocation(val wireValue: String) {
    EQUAL_N_PER_STRATUM("equal_n_per_stratum"), PROPORTIONAL_TOTAL("proportional_total"), SPECIFIED("specified");
    companion object { fun from(value: String?) = values().firstOrNull { it.wireValue == value?.lowercase() } ?: PROPORTIONAL_TOTAL }
}

data class SamplingFieldMapping(
    val identifier: String,
    val label: String?,
    val weight: String?,
    val stratum: String?,
    val eligibility: String?
)

data class SamplingConfig(
    val sourceType: SamplingSourceType,
    val operation: SamplingOperation,
    val sampleMode: SamplingSampleMode,
    val manualSeparator: String,
    val sampleSize: Int,
    val sampleFraction: Double,
    val replacement: Boolean,
    val outputOrder: SamplingOutputOrder,
    val outputMode: SamplingOutputMode,
    val outputFormat: SamplingOutputFormat,
    val mapping: SamplingFieldMapping,
    val selectedField: String,
    val countField: String,
    val orderField: String,
    val groupField: String,
    val stratumAllocation: SamplingStratumAllocation,
    val stratumSizes: String,
    val partitionGroups: Int,
    val systematicInterval: Double?,
    val seedMode: String,
    val requestedSeed: String,
    val sortField: String?
) {
    companion object {
        fun from(settings: Map<String, String>): SamplingConfig {
            fun value(key: String): String? = (settings[key] ?: settings["input_$key"])?.trim()?.takeIf { it.isNotEmpty() }
            fun boolean(key: String, default: Boolean): Boolean = when (value(key)?.lowercase()) {
                "true", "1", "yes", "y" -> true
                "false", "0", "no", "n" -> false
                else -> default
            }
            val idField = value("id_field") ?: "item_id"
            return SamplingConfig(
                sourceType = SamplingSourceType.from(value("source_type")),
                operation = SamplingOperation.from(value("operation")),
                sampleMode = SamplingSampleMode.from(value("sample_mode")),
                manualSeparator = value("manual_separator")?.lowercase() ?: "newline",
                sampleSize = value("sample_size")?.toIntOrNull()?.coerceAtLeast(0) ?: 1,
                sampleFraction = value("sample_fraction")?.toDoubleOrNull() ?: 0.1,
                replacement = boolean("replacement", false),
                outputOrder = SamplingOutputOrder.from(value("output_order")),
                outputMode = SamplingOutputMode.from(value("output_mode")),
                outputFormat = SamplingOutputFormat.from(value("output_format")),
                mapping = SamplingFieldMapping(
                    identifier = idField,
                    label = value("label_field")?.takeUnless { it == "(none)" },
                    weight = value("weight_field")?.takeUnless { it == "(none)" },
                    stratum = value("stratum_field")?.takeUnless { it == "(none)" },
                    eligibility = value("eligibility_field")?.takeUnless { it == "(none)" }
                ),
                selectedField = value("selected_field") ?: "sampling_selected",
                countField = value("count_field") ?: "sampling_count",
                orderField = value("order_field") ?: "sampling_order",
                groupField = value("group_field") ?: "sampling_group",
                stratumAllocation = SamplingStratumAllocation.from(value("stratum_allocation")),
                stratumSizes = value("stratum_sizes").orEmpty(),
                partitionGroups = value("partition_groups")?.toIntOrNull()?.coerceAtLeast(1) ?: 2,
                systematicInterval = value("systematic_interval")?.toDoubleOrNull()?.takeIf { it > 0.0 },
                seedMode = value("seed_mode")?.lowercase() ?: "auto",
                requestedSeed = value("seed").orEmpty(),
                sortField = value("sort_field")
            )
        }
    }
}

data class SamplingRecord(
    val sourceIndex: Int,
    val values: LinkedHashMap<String, String>,
    val identifier: String,
    val eligible: Boolean,
    val weight: Double,
    val stratum: String?
)

data class SamplingPopulation(
    val sourceType: SamplingSourceType,
    val headers: List<String>,
    val records: List<SamplingRecord>,
    val mapping: SamplingFieldMapping,
    val generationMetadata: Map<String, Any?> = emptyMap(),
    val sourceName: String? = null
)

data class SamplingDraw(
    val sourceIndex: Int,
    val identifier: String,
    val drawOrder: Int,
    val group: String? = null
)

data class SamplingRun(
    val config: SamplingConfig,
    val population: SamplingPopulation,
    val seedHex: String,
    val draws: List<SamplingDraw>,
    val outputHeaders: List<String>,
    val outputRows: List<LinkedHashMap<String, String>>,
    val selectedIdentifiers: List<String>,
    val selectedUniqueCount: Int,
    val samplingAlgorithm: String,
    val samplingAlgorithmVersion: String,
    val inputFileSha256: String? = null,
    val sourceFileName: String? = null
) {
    fun resultJson(): String = SamplingProvenance.canonicalJson(
        linkedMapOf(
            "selected_ids" to selectedIdentifiers,
            "population_n" to population.records.size,
            "eligible_n" to population.records.count { it.eligible },
            "draw_n" to draws.size,
            "selected_unique_n" to selectedUniqueCount,
            "records" to outputRows
        )
    )
}

class SamplingRandom private constructor(
    val seedHex: String,
    private val seedBytes: ByteArray
) {
    companion object {
        const val ALGORITHM = "methodmesh.sha256_counter"
        const val ALGORITHM_VERSION = "1.0.0"
        private val DOMAIN = "MethodMesh Sampling RNG v1\u0000".toByteArray(StandardCharsets.UTF_8)

        fun create(seedMode: String, requestedSeed: String): SamplingRandom {
            val seed = if (seedMode == "fixed" || seedMode == "fixed_seed") {
                require(requestedSeed.isNotBlank()) { "A fixed seed was requested but no seed was supplied." }
                normalizeSeed(requestedSeed)
            } else {
                ByteArray(32).also { SecureRandom().nextBytes(it) }
            }
            return SamplingRandom(seed.toHex(), seed)
        }

        private fun normalizeSeed(value: String): ByteArray {
            val trimmed = value.trim()
            if (trimmed.matches(Regex("[0-9a-fA-F]{64}"))) return trimmed.hexToBytes()
            return MessageDigest.getInstance("SHA-256").digest(trimmed.toByteArray(StandardCharsets.UTF_8))
        }

        private fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private var counter = 0L
    private var buffer = ByteArray(0)
    private var offset = 0

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "Random bound must be positive." }
        val space = 1L shl 32
        val limit = space - (space % bound.toLong())
        var candidate: Long
        do candidate = nextUInt32() while (candidate >= limit)
        return (candidate % bound).toInt()
    }

    fun nextDouble(): Double {
        val a = nextUInt32() ushr 5
        val b = nextUInt32() ushr 6
        return (a * 67_108_864.0 + b) / 9_007_199_254_740_992.0
    }

    fun <T> shuffle(values: MutableList<T>) {
        for (i in values.lastIndex downTo 1) {
            val j = nextInt(i + 1)
            if (i != j) {
                val tmp = values[i]
                values[i] = values[j]
                values[j] = tmp
            }
        }
    }

    private fun nextUInt32(): Long {
        val bytes = nextBytes(4)
        return ((bytes[0].toLong() and 0xff) shl 24) or
            ((bytes[1].toLong() and 0xff) shl 16) or
            ((bytes[2].toLong() and 0xff) shl 8) or
            (bytes[3].toLong() and 0xff)
    }

    private fun nextBytes(count: Int): ByteArray {
        val out = ByteArray(count)
        var written = 0
        while (written < count) {
            if (offset >= buffer.size) refill()
            val copy = minOf(count - written, buffer.size - offset)
            buffer.copyInto(out, written, offset, offset + copy)
            written += copy
            offset += copy
        }
        return out
    }

    private fun refill() {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN)
        digest.update(seedBytes)
        digest.update(ByteBuffer.allocate(8).putLong(counter++).array())
        buffer = digest.digest()
        offset = 0
    }
}

object SamplingEngine {
    fun run(
        settings: Map<String, String>,
        csvText: String? = null,
        inputFileSha256: String? = null,
        sourceFileName: String? = null,
        structuredHeaders: List<String>? = null,
        structuredRows: List<LinkedHashMap<String, String>>? = null
    ): SamplingRun {
        val config = SamplingConfig.from(settings)
        validateOutputFieldNames(config)
        val rng = SamplingRandom.create(config.seedMode, config.requestedSeed)
        val population = when {
            structuredRows != null -> buildStructuredPopulation(config, structuredHeaders.orEmpty(), structuredRows, sourceFileName)
            config.sourceType == SamplingSourceType.CSV -> buildCsvPopulation(config, requireNotNull(csvText) { "No CSV input was supplied." }, sourceFileName)
            config.sourceType == SamplingSourceType.SEQUENCE -> buildSequencePopulation(config, settings)
            config.sourceType == SamplingSourceType.RANDOM_WORDS -> buildRandomWordPopulation(config, settings, rng)
            else -> buildManualPopulation(config, settings)
        }
        require(population.records.isNotEmpty()) { "Population contains no records." }
        require(population.records.size <= MAX_POPULATION_SIZE) { "Population exceeds $MAX_POPULATION_SIZE records." }
        validateOutputCollisions(population, config)

        val eligible = population.records.filter { it.eligible }
        require(eligible.isNotEmpty()) { "Population contains no eligible records." }

        val selection = select(population, config, rng)
        val output = buildOutput(population, config, selection.draws)
        return SamplingRun(
            config = config,
            population = population,
            seedHex = rng.seedHex,
            draws = selection.draws,
            outputHeaders = output.first,
            outputRows = output.second,
            selectedIdentifiers = selection.draws.sortedBy { it.drawOrder }.map { it.identifier },
            selectedUniqueCount = selection.draws.map { it.sourceIndex }.distinct().size,
            samplingAlgorithm = selection.algorithm,
            samplingAlgorithmVersion = "1.0.0",
            inputFileSha256 = inputFileSha256,
            sourceFileName = sourceFileName
        )
    }

    private data class Selection(val draws: List<SamplingDraw>, val algorithm: String)

    private fun select(population: SamplingPopulation, config: SamplingConfig, rng: SamplingRandom): Selection {
        val eligible = population.records.filter { it.eligible }
        return when (config.operation) {
            SamplingOperation.POPULATION_ONLY -> Selection(
                eligible.mapIndexed { index, record -> SamplingDraw(record.sourceIndex, record.identifier, index + 1) },
                "population_only"
            )
            SamplingOperation.SHUFFLE -> {
                val shuffled = eligible.toMutableList().also(rng::shuffle)
                Selection(shuffled.mapIndexed { index, r -> SamplingDraw(r.sourceIndex, r.identifier, index + 1) }, "fisher_yates_shuffle")
            }
            SamplingOperation.SIMPLE_SAMPLE -> {
                val n = requestedSampleSize(eligible.size, config)
                Selection(sampleUniform(eligible, n, config.replacement, rng), if (config.replacement) "uniform_draw_with_replacement" else "fisher_yates_without_replacement")
            }
            SamplingOperation.WEIGHTED_SAMPLE -> {
                val n = requestedSampleSize(eligible.size, config)
                Selection(sampleWeighted(eligible, n, config.replacement, rng), "sequential_weighted_draw")
            }
            SamplingOperation.STRATIFIED_SAMPLE -> Selection(sampleStratified(eligible, config, rng), "stratified_uniform_draw")
            SamplingOperation.SYSTEMATIC_SAMPLE -> Selection(sampleSystematic(eligible, config, rng), "systematic_random_start")
            SamplingOperation.PARTITION -> Selection(partition(eligible, config.partitionGroups, rng), "fisher_yates_balanced_partition")
        }
    }

    private fun sampleUniform(records: List<SamplingRecord>, n: Int, replacement: Boolean, rng: SamplingRandom): List<SamplingDraw> {
        require(n >= 0) { "Sample size must be zero or greater." }
        if (n == 0) return emptyList()
        if (replacement) {
            return List(n) { order ->
                val record = records[rng.nextInt(records.size)]
                SamplingDraw(record.sourceIndex, record.identifier, order + 1)
            }
        }
        require(n <= records.size) { "Sample size $n exceeds the ${records.size} eligible records without replacement." }
        val shuffled = records.toMutableList().also(rng::shuffle)
        return shuffled.take(n).mapIndexed { order, record -> SamplingDraw(record.sourceIndex, record.identifier, order + 1) }
    }

    private fun sampleWeighted(records: List<SamplingRecord>, n: Int, replacement: Boolean, rng: SamplingRandom): List<SamplingDraw> {
        require(records.all { it.weight.isFinite() && it.weight >= 0.0 }) { "Weights must be finite and non-negative." }
        require(records.any { it.weight > 0.0 }) { "At least one eligible record must have positive weight." }
        if (!replacement) require(n <= records.count { it.weight > 0.0 }) { "Sample size exceeds records with positive weight." }
        val remaining = records.toMutableList()
        val draws = mutableListOf<SamplingDraw>()
        repeat(n) { order ->
            val pool = if (replacement) records else remaining
            val positive = pool.filter { it.weight > 0.0 }
            require(positive.isNotEmpty()) { "No positive weights remain." }
            val total = positive.sumOf { it.weight }
            val target = rng.nextDouble() * total
            var cumulative = 0.0
            var chosen = positive.last()
            for (record in positive) {
                cumulative += record.weight
                if (target < cumulative) { chosen = record; break }
            }
            draws += SamplingDraw(chosen.sourceIndex, chosen.identifier, order + 1)
            if (!replacement) remaining.removeAll { it.sourceIndex == chosen.sourceIndex }
        }
        return draws
    }

    private fun sampleStratified(records: List<SamplingRecord>, config: SamplingConfig, rng: SamplingRandom): List<SamplingDraw> {
        require(records.all { !it.stratum.isNullOrBlank() }) { "Every eligible record needs a stratum for stratified sampling." }
        val groups = records.groupBy { it.stratum!! }.toSortedMap()
        val allocation: Map<String, Int> = when (config.stratumAllocation) {
            SamplingStratumAllocation.EQUAL_N_PER_STRATUM -> groups.keys.associateWith { config.sampleSize }
            SamplingStratumAllocation.SPECIFIED -> parseSpecifiedAllocation(config.stratumSizes, groups.keys)
            SamplingStratumAllocation.PROPORTIONAL_TOTAL -> proportionalAllocation(groups.mapValues { it.value.size }, requestedSampleSize(records.size, config))
        }
        val draws = mutableListOf<SamplingDraw>()
        groups.forEach { (stratum, rows) ->
            val n = allocation[stratum] ?: 0
            val stratumDraws = sampleUniform(rows, n, config.replacement, rng)
            stratumDraws.forEach { draw -> draws += draw.copy(drawOrder = draws.size + 1, group = stratum) }
        }
        return draws
    }

    private fun sampleSystematic(records: List<SamplingRecord>, config: SamplingConfig, rng: SamplingRandom): List<SamplingDraw> {
        require(!config.replacement) { "Systematic sampling does not support sampling with replacement." }
        val n = requestedSampleSize(records.size, config)
        require(n in 1..records.size) { "Systematic sample size must be between 1 and the eligible population size." }
        val interval = config.systematicInterval ?: records.size.toDouble() / n.toDouble()
        require(interval >= 1.0) { "Systematic interval must be at least 1 for sampling without replacement." }
        val maxStart = minOf(interval, records.size - ((n - 1) * interval)).coerceAtLeast(0.0000001)
        val start = rng.nextDouble() * maxStart
        val positions = (0 until n).map { index -> floor(start + index * interval).toInt() }
        require(positions.distinct().size == n && positions.all { it in records.indices }) {
            "The requested systematic interval cannot produce $n distinct in-range records."
        }
        return positions.mapIndexed { order, position ->
            val record = records[position]
            SamplingDraw(record.sourceIndex, record.identifier, order + 1)
        }
    }

    private fun partition(records: List<SamplingRecord>, groups: Int, rng: SamplingRandom): List<SamplingDraw> {
        require(groups in 1..records.size) { "Number of groups must be between 1 and the eligible population size." }
        val shuffled = records.toMutableList().also(rng::shuffle)
        return shuffled.mapIndexed { index, record ->
            val groupNumber = (index % groups) + 1
            SamplingDraw(record.sourceIndex, record.identifier, index + 1, groupNumber.toString())
        }
    }

    private fun requestedSampleSize(eligibleN: Int, config: SamplingConfig): Int = when (config.sampleMode) {
        SamplingSampleMode.N -> config.sampleSize
        SamplingSampleMode.FRACTION -> {
            require(config.sampleFraction in 0.0..1.0) { "Sample fraction must be between 0 and 1." }
            ceil(eligibleN * config.sampleFraction).toInt()
        }
    }

    private fun proportionalAllocation(sizes: Map<String, Int>, totalN: Int): Map<String, Int> {
        val population = sizes.values.sum()
        require(totalN <= population) { "Requested stratified sample exceeds the eligible population." }
        val exact = sizes.mapValues { (_, size) -> totalN * size.toDouble() / population.toDouble() }
        val base = exact.mapValues { floor(it.value).toInt() }.toMutableMap()
        var remaining = totalN - base.values.sum()
        exact.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value - floor(it.value) }.thenBy { it.key }).forEach { entry ->
            if (remaining > 0 && base.getValue(entry.key) < sizes.getValue(entry.key)) {
                base[entry.key] = base.getValue(entry.key) + 1
                remaining -= 1
            }
        }
        require(remaining == 0) { "Could not allocate the requested proportional sample." }
        return base
    }

    private fun parseSpecifiedAllocation(text: String, strata: Set<String>): Map<String, Int> {
        require(text.isNotBlank()) { "Specified stratum allocation is empty." }
        val result = linkedMapOf<String, Int>()
        text.split(';', '\n', '|').map { it.trim() }.filter { it.isNotBlank() }.forEach { part ->
            val pieces = part.split('=', limit = 2)
            require(pieces.size == 2) { "Specified allocation must use stratum=n entries, e.g. A=5;B=8." }
            val key = pieces[0].trim()
            val n = pieces[1].trim().toIntOrNull()
            require(key in strata) { "Unknown stratum '$key' in specified allocation." }
            require(n != null && n >= 0) { "Invalid sample size for stratum '$key'." }
            result[key] = n
        }
        require(result.keys.containsAll(strata)) { "Specified allocation must include every stratum." }
        return result
    }

    private fun buildManualPopulation(config: SamplingConfig, settings: Map<String, String>): SamplingPopulation {
        val text = setting(settings, "manual_items").orEmpty()
        val separator = when (config.manualSeparator) {
            "pipe" -> Regex("\\|")
            "comma" -> Regex(",")
            "semicolon" -> Regex(";")
            else -> Regex("\\r?\\n")
        }
        val items = text.split(separator).map { it.trim() }.filter { it.isNotBlank() }
        require(items.isNotEmpty()) { "Enter at least one population item." }
        require(items.distinct().size == items.size) { "Manual list contains duplicate identifiers." }
        val headers = listOf(config.mapping.identifier, config.mapping.label ?: "item_label").distinct()
        val rows = items.map { item -> linkedMapOf(config.mapping.identifier to item, (config.mapping.label ?: "item_label") to item) }
        val mapping = config.mapping.copy(label = config.mapping.label ?: "item_label", weight = null, stratum = null, eligibility = null)
        return populationFromRows(
            SamplingSourceType.MANUAL, headers, rows, mapping,
            linkedMapOf("separator" to config.manualSeparator), null
        )
    }

    private fun buildCsvPopulation(config: SamplingConfig, csvText: String, sourceFileName: String?): SamplingPopulation {
        val table = SamplingCsv.parse(csvText)
        return populationFromRows(SamplingSourceType.CSV, table.headers, table.rows, config.mapping, emptyMap(), sourceFileName)
    }

    private fun buildStructuredPopulation(config: SamplingConfig, headers: List<String>, rows: List<LinkedHashMap<String, String>>, sourceName: String?): SamplingPopulation {
        val actualHeaders = if (headers.isNotEmpty()) headers else rows.flatMap { it.keys }.distinct()
        return populationFromRows(SamplingSourceType.STRUCTURED, actualHeaders, rows, config.mapping, emptyMap(), sourceName)
    }

    private fun buildSequencePopulation(config: SamplingConfig, settings: Map<String, String>): SamplingPopulation {
        val start = setting(settings, "sequence_start")?.toBigDecimalOrNull() ?: BigDecimal.ONE
        val end = setting(settings, "sequence_end")?.toBigDecimalOrNull() ?: BigDecimal("100")
        val step = setting(settings, "sequence_step")?.toBigDecimalOrNull() ?: BigDecimal.ONE
        require(step.compareTo(BigDecimal.ZERO) != 0) { "Sequence step cannot be zero." }
        require((end >= start && step > BigDecimal.ZERO) || (end <= start && step < BigDecimal.ZERO)) {
            "Sequence step direction does not move from start toward end."
        }
        val values = mutableListOf<BigDecimal>()
        var current = start
        fun within(value: BigDecimal) = if (step > BigDecimal.ZERO) value <= end else value >= end
        while (within(current)) {
            require(values.size < MAX_POPULATION_SIZE) { "Sequence would exceed $MAX_POPULATION_SIZE values." }
            values += current
            current = current.add(step)
        }
        val idField = config.mapping.identifier
        val rows = values.map { value -> linkedMapOf(idField to value.stripTrailingZeros().toPlainString()) }
        return populationFromRows(
            SamplingSourceType.SEQUENCE,
            listOf(idField),
            rows,
            config.mapping.copy(label = null, weight = null, stratum = null, eligibility = null),
            linkedMapOf("algorithm" to "decimal_sequence", "algorithm_version" to "1.0.0", "start" to start.toPlainString(), "end" to end.toPlainString(), "step" to step.toPlainString()),
            null
        )
    }

    private fun buildRandomWordPopulation(config: SamplingConfig, settings: Map<String, String>, rng: SamplingRandom): SamplingPopulation {
        val count = setting(settings, "word_count")?.toIntOrNull()?.coerceIn(1, MAX_POPULATION_SIZE) ?: 8
        val minLength = setting(settings, "word_min_length")?.toIntOrNull()?.coerceAtLeast(1) ?: 3
        val maxLength = setting(settings, "word_max_length")?.toIntOrNull()?.coerceAtLeast(minLength) ?: 12
        val unique = parseBoolean(setting(settings, "word_unique"), true)
        val pool = SamplingDictionary.words.filter { it.length in minLength..maxLength }
        require(pool.isNotEmpty()) { "No dictionary words satisfy the requested length filter." }
        if (unique) require(count <= pool.size) { "Requested $count unique words but only ${pool.size} dictionary words satisfy the filter." }
        val generated = if (unique) {
            pool.toMutableList().also(rng::shuffle).take(count)
        } else {
            List(count) { pool[rng.nextInt(pool.size)] }
        }
        val idField = config.mapping.identifier
        val labelField = config.mapping.label ?: "item_label"
        val rows = generated.mapIndexed { index, word -> linkedMapOf(idField to (index + 1).toString(), labelField to word) }
        return populationFromRows(
            SamplingSourceType.RANDOM_WORDS,
            listOf(idField, labelField),
            rows,
            config.mapping.copy(label = labelField, weight = null, stratum = null, eligibility = null),
            linkedMapOf(
                "algorithm" to if (unique) "dictionary_uniform_without_replacement" else "dictionary_uniform_with_replacement",
                "algorithm_version" to "1.0.0",
                "dictionary_id" to SamplingDictionary.ID,
                "dictionary_version" to SamplingDictionary.VERSION,
                "dictionary_sha256" to SamplingDictionary.sha256,
                "dictionary_size" to SamplingDictionary.words.size,
                "candidate_pool_size" to pool.size,
                "word_count" to count,
                "minimum_word_length" to minLength,
                "maximum_word_length" to maxLength,
                "unique_words" to unique
            ),
            null
        )
    }

    private fun populationFromRows(
        sourceType: SamplingSourceType,
        headers: List<String>,
        rows: List<LinkedHashMap<String, String>>,
        mapping: SamplingFieldMapping,
        generationMetadata: Map<String, Any?>,
        sourceName: String?
    ): SamplingPopulation {
        require(mapping.identifier in headers) { "Identifier field '${mapping.identifier}' is not present in the population." }
        mapping.label?.let { require(it in headers) { "Label field '$it' is not present in the population." } }
        mapping.weight?.let { require(it in headers) { "Weight field '$it' is not present in the population." } }
        mapping.stratum?.let { require(it in headers) { "Stratum field '$it' is not present in the population." } }
        mapping.eligibility?.let { require(it in headers) { "Eligibility field '$it' is not present in the population." } }

        val records = rows.mapIndexed { index, source ->
            val identifier = source[mapping.identifier].orEmpty().trim()
            require(identifier.isNotBlank()) { "Row ${index + 1} has a blank identifier in '${mapping.identifier}'." }
            val eligible = mapping.eligibility?.let { field -> parseEligibility(source[field].orEmpty(), index + 1, field) } ?: true
            val weight = mapping.weight?.let { field ->
                source[field].orEmpty().trim().toDoubleOrNull()
                    ?: throw IllegalArgumentException("Row ${index + 1} has invalid weight '${source[field].orEmpty()}' in '$field'.")
            } ?: 1.0
            require(weight.isFinite() && weight >= 0.0) { "Row ${index + 1} has invalid weight in '${mapping.weight}'." }
            val stratum = mapping.stratum?.let { source[it].orEmpty().trim() }
            SamplingRecord(index, LinkedHashMap(source), identifier, eligible, weight, stratum)
        }
        val duplicateIds = records.groupingBy { it.identifier }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "Identifier field '${mapping.identifier}' is not unique. Duplicate example: ${duplicateIds.first()}." }
        return SamplingPopulation(sourceType, headers, records, mapping, generationMetadata, sourceName)
    }

    private fun buildOutput(
        population: SamplingPopulation,
        config: SamplingConfig,
        draws: List<SamplingDraw>
    ): Pair<List<String>, List<LinkedHashMap<String, String>>> {
        val byIndex = draws.groupBy { it.sourceIndex }
        val generatedFields = when (config.operation) {
            SamplingOperation.POPULATION_ONLY -> emptyList()
            SamplingOperation.PARTITION -> listOf(config.selectedField, config.countField, config.orderField, config.groupField)
            else -> listOf(config.selectedField, config.countField, config.orderField)
        }.distinct()
        val headers = (population.headers + generatedFields).distinct()

        fun annotated(record: SamplingRecord, selectedDraws: List<SamplingDraw>): LinkedHashMap<String, String> =
            LinkedHashMap(record.values).apply {
                if (config.operation != SamplingOperation.POPULATION_ONLY) {
                    put(config.selectedField, selectedDraws.isNotEmpty().toString())
                    put(config.countField, selectedDraws.size.toString())
                    put(config.orderField, selectedDraws.sortedBy { it.drawOrder }.joinToString(";") { it.drawOrder.toString() })
                    if (config.operation == SamplingOperation.PARTITION) {
                        put(config.groupField, selectedDraws.firstOrNull()?.group.orEmpty())
                    }
                }
            }

        val rows = if (config.outputMode == SamplingOutputMode.ANNOTATED) {
            population.records.map { record -> annotated(record, byIndex[record.sourceIndex].orEmpty()) }
        } else {
            val drawOrdered = draws.sortedBy { it.drawOrder }
            val selectedRows = drawOrdered.map { draw ->
                val record = population.records[draw.sourceIndex]
                LinkedHashMap(record.values).apply {
                    if (config.operation != SamplingOperation.POPULATION_ONLY) {
                        put(config.selectedField, "true")
                        put(config.countField, byIndex[draw.sourceIndex].orEmpty().size.toString())
                        put(config.orderField, draw.drawOrder.toString())
                        if (config.operation == SamplingOperation.PARTITION) put(config.groupField, draw.group.orEmpty())
                    }
                }
            }
            when (config.outputOrder) {
                SamplingOutputOrder.DRAW -> selectedRows
                SamplingOutputOrder.INPUT -> selectedRows.sortedBy { row -> population.records.first { it.identifier == row[population.mapping.identifier] }.sourceIndex }
                SamplingOutputOrder.SORTED -> {
                    val field = config.sortField?.takeIf { it in headers } ?: population.mapping.identifier
                    selectedRows.sortedWith { left, right -> compareSortValues(left[field].orEmpty(), right[field].orEmpty()) }
                }
            }
        }
        return headers to rows
    }


    private fun compareSortValues(left: String, right: String): Int {
        val leftNumber = left.trim().toBigDecimalOrNull()
        val rightNumber = right.trim().toBigDecimalOrNull()
        if (leftNumber != null && rightNumber != null) {
            val numeric = leftNumber.compareTo(rightNumber)
            if (numeric != 0) return numeric
        }
        val folded = left.compareTo(right, ignoreCase = true)
        return if (folded != 0) folded else left.compareTo(right)
    }

    private fun validateOutputFieldNames(config: SamplingConfig) {
        val names = listOf(config.selectedField, config.countField, config.orderField, config.groupField)
        require(names.none { it.isBlank() }) { "Sampling output field names cannot be blank." }
        require(names.distinct().size == names.size) { "Sampling output field names must be distinct." }
    }

    private fun validateOutputCollisions(population: SamplingPopulation, config: SamplingConfig) {
        if (config.operation == SamplingOperation.POPULATION_ONLY) return
        val used = mutableListOf(config.selectedField, config.countField, config.orderField)
        if (config.operation == SamplingOperation.PARTITION) used += config.groupField
        val collisions = used.filter { it in population.headers }
        require(collisions.isEmpty()) { "Output field already exists in input: ${collisions.joinToString()}. Choose different output field name(s)." }
    }

    private fun parseEligibility(value: String, rowNumber: Int, field: String): Boolean = when (value.trim().lowercase()) {
        "true", "1", "yes", "y" -> true
        "false", "0", "no", "n" -> false
        else -> throw IllegalArgumentException("Row $rowNumber has unrecognised eligibility '$value' in '$field'.")
    }

    private fun parseBoolean(value: String?, default: Boolean): Boolean = when (value?.trim()?.lowercase()) {
        "true", "1", "yes", "y" -> true
        "false", "0", "no", "n" -> false
        else -> default
    }

    private fun setting(settings: Map<String, String>, key: String): String? =
        (settings[key] ?: settings["input_$key"])?.trim()?.takeIf { it.isNotEmpty() }
}
