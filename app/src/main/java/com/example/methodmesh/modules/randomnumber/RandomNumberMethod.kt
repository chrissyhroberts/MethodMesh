package com.example.methodmesh.modules.randomnumber

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import java.security.SecureRandom
import java.time.Instant
import kotlin.math.floor
import kotlin.random.Random

object RandomNumberFields {
    const val STATUS = "random_status"
    const val NUMBERS_JSON = "random_numbers_json"
    const val NUMBERS_CSV = "random_numbers_csv"
    const val FIRST_NUMBER = "random_first_number"
    const val COUNT = "random_count"
    const val MIN = "random_min"
    const val MAX = "random_max"
    const val STEP = "random_step"
    const val SEED_MODE = "random_seed_mode"
    const val SEED = "random_seed"
    const val ALGORITHM = "random_algorithm"
    const val GENERATED_TIME_ISO = "random_generated_time_iso"
    const val ERROR = "random_error"

    val outputs = listOf(STATUS, NUMBERS_JSON, NUMBERS_CSV, FIRST_NUMBER, COUNT, MIN, MAX, STEP, SEED_MODE, SEED, ALGORITHM, GENERATED_TIME_ISO, ERROR)
}

object As100RandomNumberMethod : As100Method {
    const val ID = "random.number.generate"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Random number generation")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Random number generator",
        version = VERSION,
        description = "Generate one or more random numbers with fixed-seed or secure-random modes.",
        outputs = RandomNumberFields.outputs,
        graphOutputs = listOf("random.number.generate"),
        parameters = mapOf("category" to "Randomisation")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, generate(request.context), InvocationContext.from(request.context))

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[RandomNumberFields.STATUS] == "succeeded"
        val entity = Entity(ArchitectureId("random-number:${System.currentTimeMillis()}"), "RandomNumberSet", temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("methodmesh.random", ID, VERSION)
        val observation = Observation(
            phenomenon = "random.number.generate",
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(RandomNumberFields.ERROR to values[RandomNumberFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun generate(settings: Map<String, String>): Map<String, String> {
        val count = settings.value("count")?.toIntOrNull()?.coerceIn(1, 10000) ?: 1
        val min = settings.value("min")?.toDoubleOrNull() ?: 0.0
        val max = settings.value("max")?.toDoubleOrNull() ?: 1.0
        val step = settings.value("step")?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val seedMode = settings.value("seed_mode") ?: "secure_random"
        val seed = settings.value("seed").orEmpty()
        if (max < min) return failure(count, min, max, step, seedMode, seed, "Maximum must be greater than or equal to minimum.")
        val slots = floor(((max - min) / step) + 0.0000001).toLong().coerceAtLeast(0L) + 1L
        if (slots <= 0L) return failure(count, min, max, step, seedMode, seed, "No values are available for this range and step.")
        val secure = SecureRandom()
        val seeded = if (seedMode == "fixed_seed") Random(stableSeed(seed.ifBlank { "methodmesh" })) else null
        val values = List(count) {
            val slot = if (seeded != null) seeded.nextLong(slots) else secure.nextLong(slots)
            val number = min + slot * step
            formatNumber(number)
        }
        return linkedMapOf(
            RandomNumberFields.STATUS to "succeeded",
            RandomNumberFields.NUMBERS_JSON to JSONArray(values).toString(),
            RandomNumberFields.NUMBERS_CSV to values.joinToString(","),
            RandomNumberFields.FIRST_NUMBER to values.firstOrNull().orEmpty(),
            RandomNumberFields.COUNT to count.toString(),
            RandomNumberFields.MIN to formatNumber(min),
            RandomNumberFields.MAX to formatNumber(max),
            RandomNumberFields.STEP to formatNumber(step),
            RandomNumberFields.SEED_MODE to seedMode,
            RandomNumberFields.SEED to if (seedMode == "fixed_seed") seed else "",
            RandomNumberFields.ALGORITHM to if (seedMode == "fixed_seed") "kotlin.Random(stable_seed)" else "java.security.SecureRandom",
            RandomNumberFields.GENERATED_TIME_ISO to Instant.now().toString(),
            RandomNumberFields.ERROR to ""
        )
    }

    private fun Map<String, String>.value(key: String): String? =
        (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

    private fun failure(count: Int, min: Double, max: Double, step: Double, seedMode: String, seed: String, error: String): Map<String, String> = linkedMapOf(
        RandomNumberFields.STATUS to "failed",
        RandomNumberFields.NUMBERS_JSON to "[]",
        RandomNumberFields.NUMBERS_CSV to "",
        RandomNumberFields.FIRST_NUMBER to "",
        RandomNumberFields.COUNT to count.toString(),
        RandomNumberFields.MIN to formatNumber(min),
        RandomNumberFields.MAX to formatNumber(max),
        RandomNumberFields.STEP to formatNumber(step),
        RandomNumberFields.SEED_MODE to seedMode,
        RandomNumberFields.SEED to seed,
        RandomNumberFields.ALGORITHM to "",
        RandomNumberFields.GENERATED_TIME_ISO to Instant.now().toString(),
        RandomNumberFields.ERROR to error
    )

    private fun stableSeed(seed: String): Int = seed.fold(0) { acc, c -> acc * 31 + c.code }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.6f".format(value).trimEnd('0').trimEnd('.')
}
