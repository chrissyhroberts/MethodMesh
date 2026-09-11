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


internal enum class PaperRegistrationType {
    BULLSEYE4,
    QR4,
    APRILTAG8;

    companion object {
        fun parse(raw: String): PaperRegistrationType = when (raw.trim().lowercase()) {
            "apriltag8", "apriltag", "tag36h11", "april_tags" -> APRILTAG8
            "qr4", "qr", "qr_fiducials" -> QR4
            "bullseye4", "bullseye", "anchors", "legacy" -> BULLSEYE4
            else -> throw IllegalArgumentException("Unknown paper registration type '$raw'.")
        }
    }
}

internal data class PaperRegistrationSpec(
    val type: PaperRegistrationType = PaperRegistrationType.BULLSEYE4,
    val schemaKey: String = "",
    val markerSizeFraction: Float = 0.075f,
    val centres: List<Pair<Float, Float>> = PaperAnchorSpec().targets,
    val markerIds: List<Int> = emptyList()
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
    BARCODE,
    IMAGE;

    companion object {
        fun parse(raw: String): PaperFieldType = when (raw.trim().lowercase()) {
            "omr_single" -> OMR_SINGLE
            "omr_multiple" -> OMR_MULTIPLE
            "ocr_text" -> OCR_TEXT
            "ocr_integer" -> OCR_INTEGER
            "ocr_decimal" -> OCR_DECIMAL
            "barcode" -> BARCODE
            "image", "picture", "photo" -> IMAGE
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
    val requiredExpression: String? = null,
    val relevanceExpression: String? = null,
    val constraintExpression: String? = null,
    val constraintMessage: String? = null,
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
    val registration: PaperRegistrationSpec,
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
            val registration = parseRegistration(root.optJSONObject("registration"), templateId, anchors)
            when (registration.type) {
                PaperRegistrationType.QR4 -> root.put("registration", JSONObject()
                    .put("type", "qr4")
                    .put("schema_key", registration.schemaKey)
                    .put("marker_size_fraction", registration.markerSizeFraction)
                    .put("centres", JSONArray().apply {
                        registration.centres.forEach { (x, y) -> put(JSONArray().put(x.toDouble()).put(y.toDouble())) }
                    }))
                PaperRegistrationType.APRILTAG8 -> root.put("registration", JSONObject()
                    .put("type", "apriltag8")
                    .put("family", PaperAprilTagFiducial.FAMILY)
                    .put("schema_key", registration.schemaKey)
                    .put("marker_size_fraction", registration.markerSizeFraction)
                    .put("ids", JSONArray(registration.markerIds))
                    .put("centres", JSONArray().apply {
                        registration.centres.forEach { (x, y) -> put(JSONArray().put(x.toDouble()).put(y.toDouble())) }
                    }))
                PaperRegistrationType.BULLSEYE4 -> Unit
            }
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

            return PaperTemplate(schema, templateId, version, title, width, height, anchors, registration, fields, root.toString())
        }

        private fun parseRegistration(json: JSONObject?, templateId: String, anchors: PaperAnchorSpec): PaperRegistrationSpec {
            if (json == null) return PaperRegistrationSpec(
                type = PaperRegistrationType.BULLSEYE4,
                centres = anchors.targets
            )
            val type = PaperRegistrationType.parse(json.optString("type", "bullseye4"))
            if (type == PaperRegistrationType.BULLSEYE4) {
                return PaperRegistrationSpec(type = type, centres = anchors.targets)
            }
            val key = json.optString("schema_key").trim().ifBlank { PaperQrFiducial.deterministicSchemaKey(templateId) }
            require(Regex("^[A-Za-z0-9]{4,11}$").matches(key)) { "registration schema_key must contain 4-11 letters or digits." }
            if (type == PaperRegistrationType.APRILTAG8) {
                val defaults = PaperAprilTagFiducial.defaultRegistration(key)
                val centresArray = json.optJSONArray("centres")
                val centres = if (centresArray != null) {
                    require(centresArray.length() == 8) { "APRILTAG8 requires exactly eight centre coordinates." }
                    (0 until 8).map { index ->
                        val pair = centresArray.getJSONArray(index)
                        require(pair.length() == 2) { "APRILTAG8 centre must be [x,y]." }
                        pair.getDouble(0).toFloat() to pair.getDouble(1).toFloat()
                    }
                } else defaults.centres
                centres.forEach { (x, y) ->
                    require(x in 0.03f..0.97f && y in 0.03f..0.97f) { "APRILTAG8 centres must remain inside printable page bounds." }
                }
                val idsArray = json.optJSONArray("ids")
                val ids = if (idsArray != null) {
                    require(idsArray.length() == 8) { "APRILTAG8 requires exactly eight marker IDs." }
                    (0 until 8).map { idsArray.getInt(it) }
                } else defaults.markerIds
                require(ids.distinct().size == 8 && ids.all { it in 0..7 }) { "Paper Bridge APRILTAG8 currently requires unique tag36h11 IDs 0..7." }
                return PaperRegistrationSpec(
                    type = type,
                    schemaKey = key.uppercase(),
                    markerSizeFraction = json.optDouble("marker_size_fraction", 0.065).toFloat().coerceIn(0.050f, 0.090f),
                    centres = centres,
                    markerIds = ids
                )
            }

            val centresArray = json.optJSONArray("centres")
            val centres = if (centresArray != null) {
                require(centresArray.length() == 4) { "QR4 requires exactly four centre coordinates." }
                (0 until 4).map { index ->
                    val pair = centresArray.getJSONArray(index)
                    require(pair.length() == 2) { "QR4 centre must be [x,y]." }
                    pair.getDouble(0).toFloat() to pair.getDouble(1).toFloat()
                }
            } else PaperQrFiducial.defaultRegistration(key).centres
            centres.forEach { (x, y) ->
                require(x in 0.02f..0.20f || x in 0.80f..0.98f) { "QR4 x centres must remain near page corners." }
                require(y in 0.02f..0.20f || y in 0.80f..0.98f) { "QR4 y centres must remain near page corners." }
            }
            return PaperRegistrationSpec(
                type = type,
                schemaKey = key.uppercase(),
                markerSizeFraction = json.optDouble("marker_size_fraction", 0.075).toFloat().coerceIn(0.045f, 0.12f),
                centres = centres
            )
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
                requiredExpression = json.optString("required_expression").trim().ifBlank { null },
                relevanceExpression = json.optString("relevance").trim().ifBlank { null },
                constraintExpression = json.optString("constraint_expression").trim().ifBlank { null },
                constraintMessage = json.optString("constraint_message").trim().ifBlank { null },
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
                "ROI values must be normalised to the template canonical coordinate frame (0..1)."
            }
            require(roi.left < roi.right && roi.top < roi.bottom) { "ROI edges are inverted or empty." }
            return roi
        }

        private fun JSONArray.toStringSet(): Set<String> = (0 until length()).map { getString(it) }.toSet()
    }
}

internal object PaperTemplateSamples {
    val DEMO_MANIFEST_JSON: String = """
        {"schema":"methodmesh.paper.v1","template_id":"paperbridge_designer_demo","version":"1","title":"Paper Bridge designer demo","page":{"width_px":1760,"height_px":2423},"anchors":{"search_fraction":0.24,"threshold_luma":150,"minimum_score":0.28},"fields":[{"name":"participant_id","label":"Participant ID","type":"ocr_text","roi":[0.183232,0.161693,0.519798,0.198446],"required":true,"regex":"[A-Za-z0-9 _-]{1,40}","normalise":"trim","auto_accept":false},{"name":"visit_number","label":"Visit number","type":"ocr_integer","roi":[0.717777,0.161693,0.816768,0.198446],"required":true,"min":1,"max":12,"normalise":"trim","auto_accept":false},{"name":"eaten_today","label":"Have you eaten today?","type":"omr_single","required":true,"threshold":0.18,"min_separation":0.06,"options":[{"value":"none","label":"Nothing","roi":[0.183232,0.299033,0.20798,0.322245]},{"value":"little","label":"A little","roi":[0.183232,0.33772,0.20798,0.360932]},{"value":"some","label":"Some","roi":[0.183232,0.376408,0.20798,0.39962]},{"value":"full","label":"A full meal","roi":[0.183232,0.415095,0.20798,0.438307]}],"allowed_values":["none","little","some","full"]},{"name":"symptoms","label":"Symptoms today","type":"omr_multiple","required":false,"separator":" ","threshold":0.18,"min_separation":0.06,"options":[{"value":"fever","label":"Fever","roi":[0.183232,0.514714,0.20798,0.537926]},{"value":"cough","label":"Cough","roi":[0.519798,0.514714,0.544545,0.537926]},{"value":"diarrhoea","label":"Diarrhoea","roi":[0.183232,0.556302,0.20798,0.579515]},{"value":"headache","label":"Headache","roi":[0.519798,0.556302,0.544545,0.579515]}],"allowed_values":["fever","cough","diarrhoea","headache"]},{"name":"temperature_c","label":"Temperature (C)","type":"ocr_decimal","roi":[0.183232,0.666562,0.341616,0.709118],"required":true,"min":34.0,"max":43.0,"normalise":"trim","auto_accept":false},{"name":"specimen_barcode","label":"Specimen barcode","type":"barcode","roi":[0.539596,0.650119,0.836566,0.746837],"required":false},{"name":"site_sketch","label":"Site sketch / drawing","type":"image","roi":[0.119225,0.743123,0.313533,0.787881],"required":false,"auto_accept":true}],"coordinate_frame":"canonical_page","authoring_profile":{"schema":"methodmesh.paper.colour.v1","scalar_envelope":"#D81B60","select_one_envelope":"#00897B","select_multiple_envelope":"#EF6C00","barcode_envelope":"#5E35B1","choice_label":"#1565C0","question_text":"#000000","response_geometry":"#000000","authoring_text_style":"bold_recommended","runtime_colour_required":false,"image_envelope":"#7CB342"},"registration":{"type":"apriltag8","family":"tag36h11","schema_key":"DEMO01","marker_size_fraction":0.065,"ids":[0,1,2,3,4,5,6,7],"centres":[[0.06,0.06],[0.5,0.06],[0.94,0.06],[0.06,0.5],[0.94,0.5],[0.06,0.94],[0.5,0.94],[0.94,0.94]]}}
    """.trimIndent().replace("\n", "")
}
