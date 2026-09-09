package com.example.methodmesh.modules.paperbridge

import org.json.JSONArray
import org.json.JSONObject

internal data class NormalisedRoi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) { "ROI coordinates must be between 0 and 1." }
        require(right > left && bottom > top) { "ROI must have positive width and height." }
    }
}

internal data class PaperAnchorSpec(
    val searchFraction: Float = 0.24f,
    val minComponentFraction: Float = 0.0004f,
    val maxComponentFraction: Float = 0.035f,
    val maxAspectError: Float = 0.55f,
    val minimumScore: Float = 0.28f,
    val thresholdLuma: Int = 150,
    val targets: List<Pair<Float, Float>> = listOf(
        0.05f to 0.04f,
        0.95f to 0.04f,
        0.95f to 0.96f,
        0.05f to 0.96f
    )
)

internal data class PaperOption(
    val value: String,
    val label: String,
    val roi: NormalisedRoi
)

internal enum class PaperFieldType {
    OMR_SINGLE,
    OMR_MULTIPLE,
    OCR_TEXT,
    OCR_INTEGER,
    OCR_DECIMAL,
    BARCODE;

    companion object {
        fun parse(raw: String): PaperFieldType = when (raw.trim().lowercase()) {
            "omr_single" -> OMR_SINGLE
            "omr_multiple" -> OMR_MULTIPLE
            "ocr_text" -> OCR_TEXT
            "ocr_integer" -> OCR_INTEGER
            "ocr_decimal" -> OCR_DECIMAL
            "barcode" -> BARCODE
            else -> throw IllegalArgumentException("Unknown paper field type '$raw'.")
        }
    }
}

internal data class PaperFieldSpec(
    val name: String,
    val label: String,
    val type: PaperFieldType,
    val roi: NormalisedRoi? = null,
    val options: List<PaperOption> = emptyList(),
    val required: Boolean = false,
    val autoAccept: Boolean? = null,
    val threshold: Float = 0.18f,
    val minimumSeparation: Float = 0.06f,
    val separator: String = " ",
    val regex: String? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val normalise: String = "collapse_whitespace",
    val allowedValues: Set<String> = emptySet()
)

internal data class PaperTemplate(
    val schema: String,
    val templateId: String,
    val version: String,
    val title: String,
    val pageWidthPx: Int,
    val pageHeightPx: Int,
    val anchors: PaperAnchorSpec,
    val fields: List<PaperFieldSpec>,
    val rawJson: String
) {
    val dynamicFieldNames: List<String> get() = fields.map { it.name }

    companion object {
        private val odkName = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

        fun parse(raw: String): PaperTemplate {
            require(raw.isNotBlank()) { "Paper template manifest is blank." }
            val root = JSONObject(raw)
            val schema = root.optString("schema", "methodmesh.paper.v1")
            require(schema == "methodmesh.paper.v1") { "Unsupported paper manifest schema '$schema'." }
            val templateId = root.getString("template_id").trim()
            require(templateId.isNotBlank()) { "template_id is required." }
            val version = root.optString("version", "1").trim().ifBlank { "1" }
            val title = root.optString("title", templateId).trim().ifBlank { templateId }
            val page = root.optJSONObject("page") ?: JSONObject()
            val width = page.optInt("width_px", 1600)
            val height = page.optInt("height_px", 2263)
            require(width in 600..4000 && height in 600..6000) { "Canonical paper page dimensions are outside the supported range." }

            val anchors = parseAnchors(root.optJSONObject("anchors"))
            val fieldsArray = root.optJSONArray("fields") ?: throw IllegalArgumentException("fields array is required.")
            require(fieldsArray.length() > 0) { "Paper template contains no fields." }
            val fields = (0 until fieldsArray.length()).map { index ->
                parseField(fieldsArray.getJSONObject(index), index)
            }
            require(fields.map { it.name }.distinct().size == fields.size) { "Paper template field names must be unique." }
            fields.forEach { field ->
                require(odkName.matches(field.name)) { "Field '${field.name}' is not a safe XLSForm/ODK node name." }
                require(!field.name.startsWith("methodmesh_") && !field.name.startsWith("paper_")) {
                    "Field '${field.name}' collides with reserved MethodMesh/Paper Bridge return names."
                }
            }

            return PaperTemplate(schema, templateId, version, title, width, height, anchors, fields, root.toString())
        }

        private fun parseAnchors(json: JSONObject?): PaperAnchorSpec {
            if (json == null) return PaperAnchorSpec()
            val targetArray = json.optJSONArray("targets")
            val targets = if (targetArray != null) {
                require(targetArray.length() == 4) { "Exactly four anchor targets are required." }
                (0 until 4).map { index ->
                    val pair = targetArray.getJSONArray(index)
                    require(pair.length() == 2) { "Anchor target must be [x,y]." }
                    pair.getDouble(0).toFloat() to pair.getDouble(1).toFloat()
                }
            } else PaperAnchorSpec().targets
            targets.forEach { (x, y) -> require(x in 0f..1f && y in 0f..1f) { "Anchor targets must be normalised." } }
            return PaperAnchorSpec(
                searchFraction = json.optDouble("search_fraction", 0.24).toFloat().coerceIn(0.12f, 0.40f),
                minComponentFraction = json.optDouble("min_component_fraction", 0.0004).toFloat().coerceIn(0.00005f, 0.02f),
                maxComponentFraction = json.optDouble("max_component_fraction", 0.035).toFloat().coerceIn(0.002f, 0.15f),
                maxAspectError = json.optDouble("max_aspect_error", 0.55).toFloat().coerceIn(0.05f, 1f),
                minimumScore = json.optDouble("minimum_score", 0.28).toFloat().coerceIn(0.05f, 1f),
                thresholdLuma = json.optInt("threshold_luma", 150).coerceIn(40, 230),
                targets = targets
            )
        }

        private fun parseField(json: JSONObject, index: Int): PaperFieldSpec {
            val name = json.getString("name").trim()
            val label = json.optString("label", name).trim().ifBlank { name }
            val type = PaperFieldType.parse(json.getString("type"))
            val roi = json.optJSONArray("roi")?.let(::parseRoi)
            val options = json.optJSONArray("options")?.let { array ->
                (0 until array.length()).map { optionIndex ->
                    val option = array.getJSONObject(optionIndex)
                    val value = option.getString("value")
                    PaperOption(value, option.optString("label", value), parseRoi(option.getJSONArray("roi")))
                }
            }.orEmpty()
            when (type) {
                PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE -> {
                    require(options.isNotEmpty()) { "Field '$name' requires options with ROIs." }
                    require(options.map { it.value }.distinct().size == options.size) { "Field '$name' has duplicate option values." }
                }
                else -> require(roi != null) { "Field '$name' requires roi." }
            }
            val declaredAllowed = json.optJSONArray("allowed_values")?.toStringSet().orEmpty()
            val allowed = if (declaredAllowed.isEmpty() && type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE)) {
                options.map { it.value }.toSet()
            } else declaredAllowed
            if (type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE) && allowed.isNotEmpty()) {
                require(allowed.all { candidate -> options.any { it.value == candidate } }) { "Field '$name' allowed_values contains an option that is not declared." }
            }
            val regex = json.optString("regex").trim().ifBlank { null }
            regex?.let { runCatching { Regex(it) }.getOrElse { error -> throw IllegalArgumentException("Invalid regex for '$name': ${error.message}") } }
            return PaperFieldSpec(
                name = name,
                label = label,
                type = type,
                roi = roi,
                options = options,
                required = json.optBoolean("required", false),
                autoAccept = if (json.has("auto_accept")) json.optBoolean("auto_accept") else null,
                threshold = json.optDouble("threshold", 0.18).toFloat().coerceIn(0.01f, 0.95f),
                minimumSeparation = json.optDouble("min_separation", 0.06).toFloat().coerceIn(0f, 0.80f),
                separator = json.optString("separator", " "),
                regex = regex,
                minimum = if (json.has("min")) json.optDouble("min") else null,
                maximum = if (json.has("max")) json.optDouble("max") else null,
                normalise = json.optString("normalise", "collapse_whitespace"),
                allowedValues = allowed
            )
        }

        private fun parseRoi(array: JSONArray): NormalisedRoi {
            require(array.length() == 4) { "ROI must be [left,top,right,bottom]." }
            val roi = NormalisedRoi(
                array.getDouble(0).toFloat(), array.getDouble(1).toFloat(),
                array.getDouble(2).toFloat(), array.getDouble(3).toFloat()
            )
            require(roi.left in 0f..1f && roi.top in 0f..1f && roi.right in 0f..1f && roi.bottom in 0f..1f) {
                "ROI values must be normalised to the page (0..1)."
            }
            require(roi.left < roi.right && roi.top < roi.bottom) { "ROI edges are inverted or empty." }
            return roi
        }

        private fun JSONArray.toStringSet(): Set<String> = (0 until length()).map { getString(it) }.toSet()
    }
}

internal object PaperTemplateSamples {
    val DEMO_MANIFEST_JSON: String = """
        {"schema":"methodmesh.paper.v1","template_id":"paperbridge_designer_demo","version":"1","title":"Paper Bridge designer demo","page":{"width_px":1600,"height_px":2263},"anchors":{"search_fraction":0.24,"threshold_luma":150,"minimum_score":0.28},"fields":[{"name":"participant_id","label":"Participant ID","type":"ocr_text","roi":[0.18,0.15,0.52,0.188],"required":true,"regex":"[A-Za-z0-9_-]{1,24}","normalise":"trim","auto_accept":false},{"name":"visit_number","label":"Visit number","type":"ocr_integer","roi":[0.72,0.15,0.82,0.188],"required":true,"min":1,"max":12,"normalise":"trim","auto_accept":false},{"name":"eaten_today","label":"Have you eaten today?","type":"omr_single","required":true,"threshold":0.18,"min_separation":0.06,"options":[{"value":"none","label":"Nothing","roi":[0.18,0.292,0.205,0.316]},{"value":"little","label":"A little","roi":[0.18,0.332,0.205,0.356]},{"value":"some","label":"Some","roi":[0.18,0.372,0.205,0.396]},{"value":"full","label":"A full meal","roi":[0.18,0.412,0.205,0.436]}],"allowed_values":["none","little","some","full"]},{"name":"symptoms","label":"Symptoms today","type":"omr_multiple","required":false,"separator":" ","threshold":0.18,"min_separation":0.06,"options":[{"value":"fever","label":"Fever","roi":[0.18,0.515,0.205,0.539]},{"value":"cough","label":"Cough","roi":[0.52,0.515,0.545,0.539]},{"value":"diarrhoea","label":"Diarrhoea","roi":[0.18,0.558,0.205,0.582]},{"value":"headache","label":"Headache","roi":[0.52,0.558,0.545,0.582]}],"allowed_values":["fever","cough","diarrhoea","headache"]},{"name":"temperature_c","label":"Temperature (C)","type":"ocr_decimal","roi":[0.18,0.672,0.34,0.716],"required":true,"min":34.0,"max":43.0,"normalise":"trim","auto_accept":false},{"name":"specimen_barcode","label":"Specimen barcode","type":"barcode","roi":[0.54,0.655,0.84,0.755],"required":false}]}
    """.trimIndent().replace("\n", "")
}
