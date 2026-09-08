package com.example.methodmesh.modules.sampling

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object SamplingProvenance {
    const val HASH_ALGORITHM = "SHA-256"
    const val CANONICAL_JSON_VERSION = "methodmesh.canonical-json.v1"
    const val POPULATION_CANONICALISATION_VERSION = "methodmesh.sampling.population.v1"

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance(HASH_ALGORITHM).digest(bytes).toHex()

    fun sha256(text: String): String = sha256(text.toByteArray(StandardCharsets.UTF_8))

    fun canonicalJson(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean -> if (value) "true" else "false"
        is Byte, is Short, is Int, is Long -> value.toString()
        is Float -> canonicalDecimal(value.toDouble())
        is Double -> canonicalDecimal(value)
        is Map<*, *> -> value.entries
            .map { it.key.toString() to it.value }
            .sortedBy { it.first }
            .joinToString(prefix = "{", separator = ",", postfix = "}") { (key, nested) ->
                "${quote(key)}:${canonicalJson(nested)}"
            }
        is Iterable<*> -> value.joinToString(prefix = "[", separator = ",", postfix = "]") { canonicalJson(it) }
        is Array<*> -> value.asIterable().joinToString(prefix = "[", separator = ",", postfix = "]") { canonicalJson(it) }
        else -> quote(value.toString())
    }

    fun populationPayload(population: SamplingPopulation): Map<String, Any?> = linkedMapOf(
        "schema" to "methodmesh.sampling.population",
        "schema_version" to POPULATION_CANONICALISATION_VERSION,
        "source_type" to population.sourceType.wireValue,
        "headers" to population.headers,
        "field_mapping" to linkedMapOf(
            "identifier" to population.mapping.identifier,
            "label" to population.mapping.label,
            "weight" to population.mapping.weight,
            "stratum" to population.mapping.stratum,
            "eligibility" to population.mapping.eligibility
        ),
        "records" to population.records.map { record ->
            linkedMapOf(
                "source_index" to record.sourceIndex,
                "identifier" to record.identifier,
                "eligible" to record.eligible,
                "weight" to record.weight,
                "stratum" to record.stratum,
                "values" to record.values
            )
        },
        "generation" to population.generationMetadata
    )

    fun resultPayload(run: SamplingRun): Map<String, Any?> = linkedMapOf(
        "schema" to "methodmesh.sampling.result",
        "schema_version" to "1.0",
        "operation" to run.config.operation.wireValue,
        "draws" to run.draws.map { draw ->
            linkedMapOf(
                "source_index" to draw.sourceIndex,
                "identifier" to draw.identifier,
                "draw_order" to draw.drawOrder,
                "group" to draw.group
            )
        },
        "selected_identifiers" to run.selectedIdentifiers,
        "selected_unique_n" to run.selectedUniqueCount,
        "draw_n" to run.draws.size
    )

    fun buildManifest(
        run: SamplingRun,
        inputFileSha256: String?,
        outputFileSha256: String?,
        resultSha256: String,
        populationSha256: String
    ): SamplingManifest {
        val config = run.config
        val population = run.population
        val provenancePayload = linkedMapOf<String, Any?>(
            "schema" to "methodmesh.sampling.provenance",
            "schema_version" to "1.0",
            "method" to linkedMapOf(
                "method_id" to As100SamplingMethod.ID,
                "method_version" to As100SamplingMethod.VERSION
            ),
            "operation" to linkedMapOf(
                "type" to config.operation.wireValue,
                "sampling_algorithm" to run.samplingAlgorithm,
                "sampling_algorithm_version" to run.samplingAlgorithmVersion,
                "rng_algorithm" to SamplingRandom.ALGORITHM,
                "rng_algorithm_version" to SamplingRandom.ALGORITHM_VERSION,
                "seed" to run.seedHex,
                "population_generation_algorithm" to population.generationMetadata["algorithm"],
                "population_generation_algorithm_version" to population.generationMetadata["algorithm_version"]
            ),
            "population" to linkedMapOf(
                "source_type" to population.sourceType.wireValue,
                "source_name" to population.sourceName,
                "population_n" to population.records.size,
                "eligible_n" to population.records.count { it.eligible },
                "excluded_n" to population.records.count { !it.eligible },
                "field_mapping" to linkedMapOf(
                    "identifier" to population.mapping.identifier,
                    "label" to population.mapping.label,
                    "weight" to population.mapping.weight,
                    "stratum" to population.mapping.stratum,
                    "eligibility" to population.mapping.eligibility
                ),
                "input_file_sha256" to inputFileSha256,
                "population_sha256" to populationSha256,
                "population_canonicalisation_version" to POPULATION_CANONICALISATION_VERSION,
                "generation" to population.generationMetadata
            ),
            "parameters" to linkedMapOf(
                "sample_mode" to config.sampleMode.wireValue,
                "manual_separator" to config.manualSeparator,
                "sample_n" to config.sampleSize,
                "sample_fraction" to config.sampleFraction,
                "sample_fraction_rounding" to if (config.sampleMode == SamplingSampleMode.FRACTION) "ceiling" else null,
                "replacement" to config.replacement,
                "output_order" to config.outputOrder.wireValue,
                "stratum_allocation" to config.stratumAllocation.wireValue,
                "stratum_sizes" to config.stratumSizes,
                "partition_groups" to config.partitionGroups,
                "systematic_interval" to config.systematicInterval
            ),
            "output" to linkedMapOf(
                "format" to config.outputFormat.wireValue,
                "mode" to config.outputMode.wireValue,
                "field_names" to linkedMapOf(
                    "selected" to config.selectedField,
                    "count" to config.countField,
                    "order" to config.orderField,
                    "group" to config.groupField
                ),
                "draw_n" to run.draws.size,
                "selected_unique_n" to run.selectedUniqueCount,
                "result_sha256" to resultSha256,
                "output_file_sha256" to outputFileSha256
            ),
            "hashing" to linkedMapOf(
                "algorithm" to HASH_ALGORITHM,
                "canonical_json_version" to CANONICAL_JSON_VERSION
            )
        )
        val payloadJson = canonicalJson(provenancePayload)
        val payloadHash = sha256(payloadJson)
        val manifest = linkedMapOf<String, Any?>(
            "schema" to "methodmesh.sampling.manifest",
            "schema_version" to "1.0",
            "provenance" to provenancePayload,
            "provenance_payload_sha256" to payloadHash,
            "attestation_request" to linkedMapOf(
                "method_id" to "attestation.create",
                "event_payload_hash" to payloadHash,
                "event_type" to "sampling_result",
                "trusted_timestamp" to "required",
                "note" to "Chain this hash into the existing attestation capability. The TSA timestamp is created by attestation, not by sampling."
            )
        )
        return SamplingManifest(
            provenancePayload = provenancePayload,
            provenancePayloadJson = payloadJson,
            provenancePayloadSha256 = payloadHash,
            manifest = manifest,
            manifestJson = canonicalJson(manifest)
        )
    }

    private fun canonicalDecimal(value: Double): String {
        require(value.isFinite()) { "Non-finite numbers cannot be canonicalised." }
        if (value == 0.0) return "0"
        val text = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        return if (text == "-0") "0" else text
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

data class SamplingManifest(
    val provenancePayload: Map<String, Any?>,
    val provenancePayloadJson: String,
    val provenancePayloadSha256: String,
    val manifest: Map<String, Any?>,
    val manifestJson: String
)
