package com.example.methodmesh.modules.clinicalinstruments

import java.security.MessageDigest

/**
 * Deliberately small YAML reader for MethodMesh clinical-instrument definitions.
 *
 * It accepts the constrained, auditable schema used by this module: scalar root
 * fields and list-of-map sections. It is not intended to be a general YAML parser.
 * Keeping the grammar narrow makes imported definitions predictable and avoids a
 * new Gradle dependency for the drop-in prototype.
 */
object ClinicalInstrumentYaml {
    private val listSections = setOf("questions", "derived", "scores", "classifications", "tests")
    private val rootFields = setOf(
        "schema", "id", "name", "version", "status", "type", "category", "tags",
        "summary", "source_url", "citation", "rights_status", "rights_note"
    )

    fun parse(yaml: String, forceStatus: InstrumentStatus? = null): ClinicalInstrumentDefinition {
        val root = linkedMapOf<String, String>()
        val sections = linkedMapOf<String, MutableList<MutableMap<String, String>>>()
        var section: String? = null
        var current: MutableMap<String, String>? = null

        yaml.lineSequence().forEachIndexed { index, original ->
            val line = stripComment(original).trimEnd()
            if (line.isBlank()) return@forEachIndexed
            val indent = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val text = line.trim()

            if (indent == 0) {
                current = null
                if (text.endsWith(":")) {
                    val key = text.dropLast(1).trim()
                    when {
                        key in listSections -> {
                            section = key
                            sections.getOrPut(key) { mutableListOf() }
                        }
                        key in rootFields -> {
                            // YAML permits an empty scalar as `key:`. Treat known
                            // root metadata this way rather than misreading it as
                            // a list section (for example `source_url:`).
                            section = null
                            root[key] = ""
                        }
                        else -> error("Unsupported YAML section '$key' at line ${index + 1}.")
                    }
                } else {
                    section = null
                    val pair = splitKeyValue(text, index)
                    require(pair.first in rootFields) { "Unsupported YAML root field '${pair.first}' at line ${index + 1}." }
                    root[pair.first] = parseScalar(pair.second)
                }
            } else {
                val activeSection = section ?: error("Indented value without a section at line ${index + 1}.")
                if (text.startsWith("- ")) {
                    current = linkedMapOf()
                    sections.getValue(activeSection).add(current!!)
                    val remainder = text.removePrefix("- ").trim()
                    if (remainder.isNotBlank()) {
                        val pair = splitKeyValue(remainder, index)
                        current!![pair.first] = parseScalar(pair.second)
                    }
                } else {
                    val target = current ?: error("Section '$activeSection' requires list items starting with '- ' at line ${index + 1}.")
                    val pair = splitKeyValue(text, index)
                    target[pair.first] = parseScalar(pair.second)
                }
            }
        }

        val id = root.required("id")
        val status = forceStatus ?: when (root["status"]?.lowercase()) {
            "local" -> InstrumentStatus.LOCAL
            else -> InstrumentStatus.CORE
        }
        val questions = sections["questions"].orEmpty().map(::parseQuestion)
        val derived = sections["derived"].orEmpty().map(::parseExpression)
        val scores = sections["scores"].orEmpty().map(::parseExpression)
        val classifications = sections["classifications"].orEmpty().map {
            ClinicalClassification(
                whenExpression = it.required("when"),
                value = it.required("value"),
                label = it["label"].orEmpty().ifBlank { it.required("value") }
            )
        }
        val tests = sections["tests"].orEmpty().map {
            ClinicalDefinitionTest(
                name = it.required("name"),
                inputJson = it.required("input_json"),
                expectJson = it.required("expect_json")
            )
        }

        return ClinicalInstrumentDefinition(
            schema = root["schema"] ?: "methodmesh.clinical-instrument.v1",
            id = id,
            name = root.required("name"),
            version = root["version"] ?: "0.1.0",
            status = status,
            type = root["type"] ?: "structured_assessment",
            category = root["category"] ?: "other",
            tags = parseList(root["tags"].orEmpty()),
            summary = root["summary"].orEmpty(),
            sourceUrl = root["source_url"].orEmpty(),
            citation = root["citation"].orEmpty(),
            rightsStatus = root["rights_status"] ?: "unspecified",
            rightsNote = root["rights_note"].orEmpty(),
            questions = questions,
            derived = derived,
            scores = scores,
            classifications = classifications,
            tests = tests,
            rawYaml = yaml.trim() + "\n",
            definitionSha256 = sha256(yaml.trim() + "\n")
        )
    }

    fun validate(yaml: String, forceStatus: InstrumentStatus? = null): DefinitionValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val definition = runCatching { parse(yaml, forceStatus) }
            .onFailure { errors += (it.message ?: "Definition could not be parsed.") }
            .getOrNull()
            ?: return DefinitionValidation(false, errors, warnings)

        if (!definition.id.matches(Regex("[A-Za-z0-9_.-]+"))) errors += "Instrument id may contain only letters, numbers, '.', '_' and '-'."
        if (!definition.version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?"))) warnings += "Version is not semantic-version shaped (for example 1.0.0)."
        val ids = definition.questions.map { it.id } + definition.derived.map { it.id } + definition.scores.map { it.id }
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { errors += "Duplicate field id '$it'." }
        definition.questions.forEach { q ->
            if (!q.id.matches(Regex("[A-Za-z_][A-Za-z0-9_.-]*"))) errors += "Invalid question id '${q.id}'."
            if (q.type == QuestionType.SELECT_ONE && q.choices.isEmpty()) errors += "Question '${q.id}' is select_one but has no choices."
            if (q.minimum != null && q.maximum != null && q.minimum > q.maximum) errors += "Question '${q.id}' has minimum greater than maximum."
            if (q.notTestableValue != null && q.type == QuestionType.SELECT_ONE && q.choices.none { it.value == q.notTestableValue }) {
                errors += "Question '${q.id}' declares not_testable_value '${q.notTestableValue}' but that value is not in choices."
            }
        }
        val questionIds = definition.questions.map { it.id }.toSet()
        (definition.derived + definition.scores).forEach { expression ->
            expression.requiresTestable.filter { it !in questionIds }.forEach { missing ->
                errors += "Expression '${expression.id}' requires unknown testable question '$missing'."
            }
            expression.requiresTestable.forEach { requiredId ->
                val q = definition.questions.first { it.id == requiredId }
                if (q.notTestableValue == null) errors += "Expression '${expression.id}' requires '$requiredId' to be testable, but that question has no not_testable_value."
            }
        }
        if (definition.sourceUrl.isBlank()) warnings += "No source_url supplied."
        if (definition.citation.isBlank()) warnings += "No citation supplied."
        if (definition.rightsStatus == "unspecified") warnings += "No rights_status supplied."
        return DefinitionValidation(errors.isEmpty(), errors, warnings)
    }

    fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun parseQuestion(map: Map<String, String>): ClinicalQuestion {
        val type = when (map.required("type").lowercase()) {
            "integer" -> QuestionType.INTEGER
            "decimal", "number" -> QuestionType.DECIMAL
            "boolean", "yes_no" -> QuestionType.BOOLEAN
            "select_one", "choice" -> QuestionType.SELECT_ONE
            "text" -> QuestionType.TEXT
            else -> error("Unsupported question type '${map["type"]}' for '${map["id"]}'.")
        }
        require(map["visible_if"].isNullOrBlank()) { "Question '${map["id"]}' uses visible_if; branching/relevance is not supported by Clinical Instruments v0.1." }
        return ClinicalQuestion(
            id = map.required("id"),
            label = map.required("label"),
            hint = map["hint"].orEmpty(),
            type = type,
            unit = map["unit"].orEmpty(),
            required = map["required"]?.toBooleanStrictOrNull() ?: true,
            minimum = map["min"]?.toDoubleOrNull(),
            maximum = map["max"]?.toDoubleOrNull(),
            choices = parseChoices(map["choices"].orEmpty()),
            notTestableValue = map["not_testable_value"]?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseExpression(map: Map<String, String>) = ClinicalExpression(
        id = map.required("id"),
        expression = map.required("expression"),
        label = map["label"].orEmpty(),
        requiresTestable = parseList(map["requires_testable"].orEmpty())
    )

    private fun parseChoices(raw: String): List<ClinicalChoice> = parseList(raw).map { token ->
        val parts = token.split('|', limit = 2)
        ClinicalChoice(parts[0].trim(), parts.getOrElse(1) { parts[0] }.trim())
    }

    private fun parseList(raw: String): List<String> {
        val text = raw.trim()
        if (text.isBlank()) return emptyList()
        val inner = if (text.startsWith('[') && text.endsWith(']')) text.substring(1, text.length - 1) else text
        return splitCommaAware(inner).map(::parseScalar).filter { it.isNotBlank() }
    }

    private fun splitCommaAware(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        text.forEach { ch ->
            when {
                quote != null && ch == quote -> { quote = null; current.append(ch) }
                quote != null -> current.append(ch)
                ch == '\'' || ch == '"' -> { quote = ch; current.append(ch) }
                ch == ',' -> { out += current.toString().trim(); current.clear() }
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) out += current.toString().trim()
        return out
    }

    private fun stripComment(line: String): String {
        var quote: Char? = null
        line.forEachIndexed { index, ch ->
            when {
                quote != null && ch == quote -> quote = null
                quote == null && (ch == '\'' || ch == '"') -> quote = ch
                quote == null && ch == '#' -> return line.substring(0, index)
            }
        }
        return line
    }

    private fun splitKeyValue(text: String, index: Int): Pair<String, String> {
        val colon = text.indexOf(':')
        require(colon > 0) { "Expected key: value at line ${index + 1}." }
        return text.substring(0, colon).trim() to text.substring(colon + 1).trim()
    }

    private fun parseScalar(raw: String): String {
        val value = raw.trim()
        if (value.length >= 2 && ((value.first() == '"' && value.last() == '"') || (value.first() == '\'' && value.last() == '\''))) {
            return value.substring(1, value.length - 1)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\'", "'")
        }
        return value
    }

    private fun Map<String, String>.required(key: String): String = this[key]?.takeIf { it.isNotBlank() }
        ?: error("Required field '$key' is missing.")
}
