package com.example.methodmesh.modules.nfc

/** The field commitment policy shared conceptually with the standalone compiler. */
internal object NfcOdkCommitPolicy {
    val allowedOverrides = setOf("", "auto", "value", "sha256", "exclude")

    data class Decision(val mode: Mode, val transform: String, val reason: String)
    enum class Mode { VALUE, SHA256, EXCLUDE }

    private val structural = setOf(
        "begin group", "begin_group", "end group", "end_group", "begin repeat", "begin_repeat",
        "end repeat", "end_repeat", "note"
    )
    private val metadata = setOf("start", "end", "today", "deviceid", "username", "phonenumber", "start-geopoint", "audit")
    private val media = setOf("image", "audio", "video", "file")
    private val direct = setOf("integer", "decimal", "date", "range")
    private val lexical = setOf("text", "datetime", "datetime", "dateTime", "time", "geopoint", "geotrace", "geoshape", "barcode", "acknowledge", "rank", "hidden")

    fun decide(type: String, rawOverride: String, fieldName: String, insideExcludedRepeat: Boolean = false): Decision {
        val t = type.trim()
        val lower = t.lowercase()
        val override = rawOverride.trim().lowercase()
        require(override in allowedOverrides) {
            "$fieldName: mm_commit must be one of auto, value, sha256, exclude; got '$override'."
        }
        val effective = override.ifBlank { "auto" }
        if (insideExcludedRepeat) {
            require(effective == "auto" || effective == "exclude") {
                "$fieldName: field is inside a repeat excluded from the commitment but mm_commit=$effective."
            }
            return Decision(Mode.EXCLUDE, "none", "inside explicitly excluded repeat")
        }
        if (lower in structural || lower in metadata) {
            if (effective == "auto" || effective == "exclude") return Decision(Mode.EXCLUDE, "none", "structural/metadata field")
            if (lower in metadata && effective == "sha256") return Decision(Mode.SHA256, "odk-lexical-utf8-sha256", "explicit metadata commitment")
            error("$fieldName: type '$t' cannot use mm_commit=$effective.")
        }
        if (lower == "calculate") {
            if (effective == "auto" || effective == "exclude") return Decision(Mode.EXCLUDE, "none", "calculate excluded by default")
            if (effective == "sha256") return Decision(Mode.SHA256, "odk-lexical-utf8-sha256", "explicit calculate commitment")
            error("$fieldName: calculate fields may be explicitly sha256 or exclude; value is ambiguous.")
        }
        val head = lower.substringBefore(' ')
        if (head in media) {
            require(effective == "exclude") {
                "$fieldName: binary/media field '$t' cannot be safely content-hashed by ODK digest(); set mm_commit=exclude."
            }
            return Decision(Mode.EXCLUDE, "none", "binary/media explicitly excluded")
        }
        if (effective == "exclude") return Decision(Mode.EXCLUDE, "none", "explicitly excluded by author")
        if (isSelectOne(t)) {
            return if (effective == "auto" || effective == "value") Decision(Mode.VALUE, "stored-choice-name", "single select commits stable stored choice name")
            else Decision(Mode.SHA256, "odk-lexical-utf8-sha256", "explicit sha256")
        }
        if (isSelectMultiple(t)) {
            require(effective == "auto" || effective == "sha256") {
                "$fieldName: select_multiple cannot use mm_commit=value; use auto/sha256 or exclude."
            }
            return Decision(Mode.SHA256, "odk-selection-order-lexical-utf8-sha256", "multi-select commits stored selection-order representation")
        }
        if (head in direct) {
            return if (effective == "auto" || effective == "value") Decision(Mode.VALUE, if (head == "date") "yyyy-mm-dd" else "odk-canonical-scalar", "deterministic scalar value")
            else Decision(Mode.SHA256, "odk-lexical-utf8-sha256", "explicit sha256")
        }
        if (head in lexical || head == "datetime") {
            require(effective == "auto" || effective == "sha256") {
                "$fieldName: type '$t' is not safe for raw ordered-kv insertion; use auto/sha256 or exclude."
            }
            return Decision(Mode.SHA256, "odk-lexical-utf8-sha256", "text/complex lexical value hashed")
        }
        require(effective == "sha256") {
            "$fieldName: unsupported or ambiguous XLSForm type '$t'. Set mm_commit=sha256/exclude or extend the policy."
        }
        return Decision(Mode.SHA256, "odk-lexical-utf8-sha256", "explicit sha256 for otherwise unknown type")
    }

    fun isSelectOne(type: String): Boolean {
        val lower = type.trim().lowercase()
        return lower.startsWith("select_one ") || lower.startsWith("select one ") || lower.startsWith("select_one_from_file ")
    }

    fun isSelectMultiple(type: String): Boolean {
        val lower = type.trim().lowercase()
        return lower.startsWith("select_multiple ") || lower.startsWith("select multiple ") || lower.startsWith("select_multiple_from_file ")
    }

    fun isBeginRepeat(type: String) = type.trim().lowercase() in setOf("begin repeat", "begin_repeat")
    fun isEndRepeat(type: String) = type.trim().lowercase() in setOf("end repeat", "end_repeat")
    fun typeHead(type: String) = type.trim().substringBefore(' ').lowercase()

    fun choiceList(type: String): String? {
        val parts = type.trim().split(Regex("\\s+"), limit = 2)
        return if ((isSelectOne(type) || isSelectMultiple(type)) && parts.size == 2) parts[1].trim() else null
    }
}
