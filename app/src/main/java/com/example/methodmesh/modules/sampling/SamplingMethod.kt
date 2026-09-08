package com.example.methodmesh.modules.sampling

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
import org.json.JSONObject

object SamplingFields {
    const val STATUS = "sampling_status"
    const val MAIN_VALUE = "sampling_value"
    const val RESULT_URI = "sampling_result_uri"
    const val MANIFEST_URI = "sampling_manifest_uri"
    const val SELECTED_ID = "sampling_selected_id"
    const val SELECTED_IDS = "sampling_selected_ids"
    const val SELECTED_LABEL = "sampling_selected_label"
    const val SELECTED_LABELS = "sampling_selected_labels"
    const val SELECTED_COUNT = "sampling_selected_count"
    const val POPULATION_COUNT = "sampling_population_count"
    const val ELIGIBLE_COUNT = "sampling_eligible_count"
    const val RESULT_JSON = "sampling_result_json"
    const val AUDIT_JSON = "sampling_audit_json"
    const val INPUT_FILE_SHA256 = "sampling_input_file_sha256"
    const val POPULATION_SHA256 = "sampling_population_sha256"
    const val RESULT_SHA256 = "sampling_result_sha256"
    const val OUTPUT_FILE_SHA256 = "sampling_output_file_sha256"
    const val PROVENANCE_PAYLOAD_SHA256 = "sampling_provenance_payload_sha256"
    const val ATTESTATION_METHOD_ID = "sampling_attestation_method_id"
    const val ATTESTATION_EVENT_PAYLOAD_HASH = "sampling_attestation_event_payload_hash"
    const val ERROR = "sampling_error"

    val outputs = listOf(
        STATUS, MAIN_VALUE, RESULT_URI, MANIFEST_URI, SELECTED_ID, SELECTED_IDS, SELECTED_LABEL, SELECTED_LABELS,
        SELECTED_COUNT, POPULATION_COUNT, ELIGIBLE_COUNT,
        RESULT_JSON, AUDIT_JSON,
        INPUT_FILE_SHA256, POPULATION_SHA256, RESULT_SHA256, OUTPUT_FILE_SHA256,
        PROVENANCE_PAYLOAD_SHA256, ATTESTATION_METHOD_ID, ATTESTATION_EVENT_PAYLOAD_HASH,
        ERROR
    )
}

object As100SamplingMethod : As100Method {
    const val ID = "sampling.run"
    const val VERSION = "0.1.1"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Reproducible sampling")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Sampling",
        version = VERSION,
        description = "Construct a population and perform reproducible sampling, shuffling or random partitioning.",
        outputs = SamplingFields.outputs,
        graphOutputs = listOf("sampling.run"),
        parameters = mapOf("category" to "Randomisation", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val invocation = InvocationContext.from(request.context)
        val values = runCatching {
            val structured = parseStructuredItems(request.context)
            val run = SamplingEngine.run(
                settings = request.context,
                csvText = contextValue(request.context, "csv_text"),
                structuredHeaders = structured?.first,
                structuredRows = structured?.second,
                sourceFileName = if (structured != null) "odk-structured-items" else null
            )
            valuesFromRun(run)
        }.getOrElse { failureValues(it.message ?: "Sampling failed.") }
        return result(request, values, invocation)
    }

    fun valuesFromRun(
        run: SamplingRun,
        resultUri: String = "",
        manifestUri: String = "",
        outputFileSha256: String? = null
    ): Map<String, String> {
        val populationJson = SamplingProvenance.canonicalJson(SamplingProvenance.populationPayload(run.population))
        val populationSha = SamplingProvenance.sha256(populationJson)
        val resultPayloadJson = SamplingProvenance.canonicalJson(SamplingProvenance.resultPayload(run))
        val resultSha = SamplingProvenance.sha256(resultPayloadJson)
        val manifest = SamplingProvenance.buildManifest(
            run = run,
            inputFileSha256 = run.inputFileSha256,
            outputFileSha256 = outputFileSha256,
            resultSha256 = resultSha,
            populationSha256 = populationSha
        )
        val selected = run.selectedIdentifiers
        val labelField = run.population.mapping.label
        val labels = run.draws.sortedBy { it.drawOrder }.map { draw ->
            val record = run.population.records[draw.sourceIndex]
            labelField?.let { record.values[it].orEmpty() }?.takeIf { it.isNotBlank() } ?: record.identifier
        }
        return linkedMapOf(
            SamplingFields.STATUS to "succeeded",
            // Compact beef-first value for scalar/list workflows. File-oriented native
            // workflows still expose sampling_result_uri as the useful attachment.
            SamplingFields.MAIN_VALUE to selected.firstOrNull().orEmpty(),
            SamplingFields.RESULT_URI to resultUri,
            SamplingFields.MANIFEST_URI to manifestUri,
            SamplingFields.SELECTED_ID to selected.firstOrNull().orEmpty(),
            SamplingFields.SELECTED_IDS to selected.joinToString("\n"),
            SamplingFields.SELECTED_LABEL to labels.firstOrNull().orEmpty(),
            SamplingFields.SELECTED_LABELS to labels.joinToString("\n"),
            SamplingFields.SELECTED_COUNT to run.draws.size.toString(),
            SamplingFields.POPULATION_COUNT to run.population.records.size.toString(),
            SamplingFields.ELIGIBLE_COUNT to run.population.records.count { it.eligible }.toString(),
            SamplingFields.RESULT_JSON to run.resultJson(),
            SamplingFields.AUDIT_JSON to manifest.manifestJson,
            SamplingFields.INPUT_FILE_SHA256 to run.inputFileSha256.orEmpty(),
            SamplingFields.POPULATION_SHA256 to populationSha,
            SamplingFields.RESULT_SHA256 to resultSha,
            SamplingFields.OUTPUT_FILE_SHA256 to outputFileSha256.orEmpty(),
            SamplingFields.PROVENANCE_PAYLOAD_SHA256 to manifest.provenancePayloadSha256,
            SamplingFields.ATTESTATION_METHOD_ID to "attestation.create",
            SamplingFields.ATTESTATION_EVENT_PAYLOAD_HASH to manifest.provenancePayloadSha256,
            SamplingFields.ERROR to ""
        )
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[SamplingFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("sampling:${System.currentTimeMillis()}"),
            "SamplingResult",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.sampling", ID, VERSION)
        val observation = Observation(
            phenomenon = "sampling.run",
            subject = invocation?.subjectRef(),
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
            diagnostics = if (ok) emptyMap() else mapOf(SamplingFields.ERROR to values[SamplingFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun failureValues(error: String): Map<String, String> = linkedMapOf(
        SamplingFields.STATUS to "failed",
        SamplingFields.MAIN_VALUE to "",
        SamplingFields.RESULT_URI to "",
        SamplingFields.MANIFEST_URI to "",
        SamplingFields.SELECTED_ID to "",
        SamplingFields.SELECTED_IDS to "",
        SamplingFields.SELECTED_LABEL to "",
        SamplingFields.SELECTED_LABELS to "",
        SamplingFields.SELECTED_COUNT to "0",
        SamplingFields.POPULATION_COUNT to "0",
        SamplingFields.ELIGIBLE_COUNT to "0",
        SamplingFields.RESULT_JSON to "{}",
        SamplingFields.AUDIT_JSON to "{}",
        SamplingFields.INPUT_FILE_SHA256 to "",
        SamplingFields.POPULATION_SHA256 to "",
        SamplingFields.RESULT_SHA256 to "",
        SamplingFields.OUTPUT_FILE_SHA256 to "",
        SamplingFields.PROVENANCE_PAYLOAD_SHA256 to "",
        SamplingFields.ATTESTATION_METHOD_ID to "attestation.create",
        SamplingFields.ATTESTATION_EVENT_PAYLOAD_HASH to "",
        SamplingFields.ERROR to error
    )

    internal fun parseStructuredItems(context: Map<String, String>): Pair<List<String>, List<LinkedHashMap<String, String>>>? {
        val raw = contextValue(context, "sampling_items_json") ?: contextValue(context, "items_json") ?: return null
        val root = raw.trim()
        val array = when {
            root.startsWith("[") -> JSONArray(root)
            root.startsWith("{") -> JSONObject(root).optJSONArray("items")
                ?: throw IllegalArgumentException("Structured sampling JSON object must contain an 'items' array.")
            else -> throw IllegalArgumentException("Structured sampling items must be a JSON array or an object containing an 'items' array.")
        }
        val headers = linkedSetOf<String>()
        val rows = mutableListOf<LinkedHashMap<String, String>>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: throw IllegalArgumentException("Structured sampling item ${i + 1} is not a JSON object.")
            val row = linkedMapOf<String, String>()
            val keys = obj.keys().asSequence().toList().sorted()
            keys.forEach { key ->
                headers += key
                val value = obj.opt(key)
                row[key] = when (value) {
                    null, JSONObject.NULL -> ""
                    is String -> value
                    else -> value.toString()
                }
            }
            rows += row
        }
        // Ensure each row has every header so canonicalisation and CSV-style
        // projection behave identically regardless of JSON property presence.
        val normalised = rows.map { row -> linkedMapOf<String, String>().apply { headers.forEach { put(it, row[it].orEmpty()) } } }
        return headers.toList() to normalised
    }

    internal fun contextValue(context: Map<String, String>, key: String): String? =
        (context[key] ?: context["input_$key"])?.takeIf { it.isNotBlank() }
}
