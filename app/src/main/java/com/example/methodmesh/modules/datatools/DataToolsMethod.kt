package com.example.methodmesh.modules.datatools

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

object DataToolsFields {
    const val STATUS = "datatool_status"
    const val OUTPUT = "datatool_output"
    const val VALID = "datatool_valid"
    const val DETECTED_TYPE = "datatool_detected_type"
    const val METADATA_JSON = "datatool_metadata_json"
    const val ERROR = "datatool_error"
    val outputs = listOf(STATUS, OUTPUT, VALID, DETECTED_TYPE, METADATA_JSON, ERROR)
}

object As100DataToolsMethod : As100Method {
    const val ID = "data.tools"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Data encoding tools")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation,
        name = "Data tools", version = VERSION,
        description = "Offline JSON, Base64, URL, hex, timestamp and CSV/JSON transformations.",
        outputs = DataToolsFields.outputs, graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Data", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = result(request, transform(request.context), InvocationContext.from(request.context))

    fun transform(settings: Map<String, String>): Map<String, String> = runCatching {
        val operation = settings.value("operation") ?: "json_pretty"
        val input = settings.value("data").orEmpty()
        val output: String
        var valid = true
        var detected = "text"
        when (operation) {
            "json_pretty" -> {
                val parsed = JSONTokener(input).nextValue()
                when (parsed) {
                    is JSONArray -> { detected = "json_array"; output = parsed.toString(2) }
                    is JSONObject -> { detected = "json_object"; output = parsed.toString(2) }
                    else -> { detected = "json_scalar"; output = jsonScalar(parsed) }
                }
            }
            "json_minify" -> {
                val parsed = JSONTokener(input).nextValue()
                detected = when (parsed) { is JSONArray -> "json_array"; is JSONObject -> "json_object"; else -> "json_scalar" }
                output = if (parsed is JSONObject || parsed is JSONArray) parsed.toString() else jsonScalar(parsed)
            }
            "json_validate" -> { runCatching { JSONTokener(input).nextValue() }.onFailure { valid = false }; output = if (valid) "valid" else "invalid"; detected = "json" }
            "base64_encode" -> { output = Base64.getEncoder().encodeToString(input.toByteArray(StandardCharsets.UTF_8)); detected = "base64" }
            "base64_decode" -> { output = String(Base64.getDecoder().decode(input.trim()), StandardCharsets.UTF_8); detected = "text" }
            "url_encode" -> { output = URLEncoder.encode(input, StandardCharsets.UTF_8.name()); detected = "url_encoded" }
            "url_decode" -> { output = URLDecoder.decode(input, StandardCharsets.UTF_8.name()); detected = "text" }
            "hex_encode" -> { output = input.toByteArray(StandardCharsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 0xff) }; detected = "hex" }
            "hex_decode" -> { val clean = input.replace(Regex("\\s+"), ""); require(clean.length % 2 == 0) { "Hex input must contain an even number of characters." }; output = String(ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }, StandardCharsets.UTF_8); detected = "text" }
            "unix_to_iso" -> { val raw = input.trim().toLong(); val instant = if (raw > 10_000_000_000L) Instant.ofEpochMilli(raw) else Instant.ofEpochSecond(raw); output = instant.toString(); detected = "iso_time" }
            "iso_to_unix" -> { output = Instant.parse(input.trim()).epochSecond.toString(); detected = "unix_seconds" }
            "csv_to_json" -> { output = csvToJson(input).toString(2); detected = "json_array" }
            "json_to_csv" -> { output = jsonToCsv(JSONArray(input)); detected = "csv" }
            else -> error("Unsupported data operation: $operation")
        }
        val metadata = JSONObject().apply { put("operation", operation); put("offline", true); put("input_length", input.length); put("output_length", output.length) }
        linkedMapOf(DataToolsFields.STATUS to "succeeded", DataToolsFields.OUTPUT to output, DataToolsFields.VALID to valid.toString(), DataToolsFields.DETECTED_TYPE to detected, DataToolsFields.METADATA_JSON to metadata.toString(), DataToolsFields.ERROR to "")
    }.getOrElse { linkedMapOf(DataToolsFields.STATUS to "failed", DataToolsFields.OUTPUT to "", DataToolsFields.VALID to "false", DataToolsFields.DETECTED_TYPE to "", DataToolsFields.METADATA_JSON to "{}", DataToolsFields.ERROR to (it.message ?: "Data operation failed.")) }

    private fun jsonScalar(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is String -> JSONObject.quote(value)
        is Boolean, is Number -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun csvToJson(csv: String): JSONArray {
        val rows = parseCsv(csv)
        if (rows.isEmpty()) return JSONArray()
        val headers = rows.first()
        return JSONArray().apply { rows.drop(1).forEach { row -> put(JSONObject().apply { headers.forEachIndexed { i, h -> put(h, row.getOrElse(i) { "" }) } }) } }
    }

    private fun jsonToCsv(array: JSONArray): String {
        val objects = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        if (objects.isEmpty()) return ""
        val headers = linkedSetOf<String>().apply {
            objects.forEach { o ->
                val keys = o.keys()
                while (keys.hasNext()) add(keys.next())
            }
        }.toList()
        return buildString {
            append(headers.joinToString(",") { csvEscape(it) }).append('\n')
            objects.forEach { o -> append(headers.joinToString(",") { h -> csvEscape(if (o.isNull(h)) "" else o.opt(h)?.toString().orEmpty()) }).append('\n') }
        }.trimEnd('\n')
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        fun endCell() { row.add(cell.toString()); cell.setLength(0) }
        fun endRow() { endCell(); rows.add(row); row = mutableListOf() }
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> endCell()
                (c == '\n' || c == '\r') && !quoted -> { if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++; endRow() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
    private fun csvEscape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[DataToolsFields.STATUS] == "succeeded"
        val observation = Observation(phenomenon = ID, subject = null, values = values, temporalContext = request.temporalContext, provenance = ProvenanceContext("methodmesh.datatools", ID, VERSION))
        val transformation = Transformation(action = ID, method = ref, outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)), status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed, temporalContext = request.temporalContext, provenance = ProvenanceContext("methodmesh.datatools", ID, VERSION))
        return As100ExecutionEngine.complete(request, if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed, observations = listOf(observation), transformations = listOf(transformation), diagnostics = if (ok) emptyMap() else mapOf(DataToolsFields.ERROR to values[DataToolsFields.ERROR].orEmpty())).withInvocationContext(invocation)
    }
    private fun Map<String, String>.value(key: String) = (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }
}
