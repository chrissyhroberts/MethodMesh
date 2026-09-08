package com.example.methodmesh.modules.sampling

/**
 * Small RFC-4180-style CSV reader/writer kept inside the capability so sampling
 * does not acquire a heavyweight CSV dependency. It preserves source column
 * order and source values at the logical data level.
 */
data class SamplingCsvTable(
    val headers: List<String>,
    val rows: List<LinkedHashMap<String, String>>
)

object SamplingCsv {
    fun parse(text: String): SamplingCsvTable {
        val records = parseRecords(text.removePrefix("\uFEFF"))
        require(records.isNotEmpty()) { "CSV has no header row." }

        val headers = records.first().map { it.trim() }
        require(headers.isNotEmpty()) { "CSV has no columns." }
        require(headers.none { it.isBlank() }) { "CSV contains a blank column name." }
        require(headers.distinct().size == headers.size) { "CSV contains duplicate column names." }

        val rows = records.drop(1).mapIndexedNotNull { index, values ->
            if (values.size == 1 && values.first().isBlank()) return@mapIndexedNotNull null
            require(values.size <= headers.size) {
                "CSV row ${index + 2} has ${values.size} fields but the header has ${headers.size}."
            }
            linkedMapOf<String, String>().apply {
                headers.forEachIndexed { columnIndex, header ->
                    put(header, values.getOrElse(columnIndex) { "" })
                }
            }
        }
        return SamplingCsvTable(headers, rows)
    }

    fun write(headers: List<String>, rows: List<Map<String, String>>): String = buildString {
        append(headers.joinToString(",") { escape(it) })
        append("\r\n")
        rows.forEach { row ->
            append(headers.joinToString(",") { header -> escape(row[header].orEmpty()) })
            append("\r\n")
        }
    }

    private fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var quotedField = false
        var i = 0

        fun finishField() {
            row.add(field.toString())
            field.setLength(0)
            quotedField = false
        }

        fun finishRow() {
            finishField()
            records.add(row.toList())
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i += 1
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> {
                        require(field.isEmpty()) { "Unexpected quote inside an unquoted CSV field." }
                        inQuotes = true
                        quotedField = true
                    }
                    ',' -> finishField()
                    '\n' -> finishRow()
                    '\r' -> {
                        if (i + 1 < text.length && text[i + 1] == '\n') i += 1
                        finishRow()
                    }
                    else -> {
                        require(!quotedField) { "Unexpected text after a closing CSV quote." }
                        field.append(c)
                    }
                }
            }
            i += 1
        }

        require(!inQuotes) { "CSV ends inside a quoted field." }
        if (field.isNotEmpty() || row.isNotEmpty() || text.endsWith(',')) finishRow()
        return records
    }

    private fun escape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        val requiresQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (requiresQuotes) "\"$escaped\"" else escaped
    }
}
