package com.example.methodmesh.modules.texttools

import org.json.JSONArray
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Base64
import java.util.Locale

data class TextProcessOutcome(
    val result: String,
    val extras: Map<String, String> = emptyMap(),
    val error: String? = null
)

object TextProcessing {
    private val wordRegex = Regex("""[\p{L}\p{N}]+(?:['’_-][\p{L}\p{N}]+)*""")
    private val blankLineRegex = Regex("""\n[ \t]*\n(?:[ \t]*\n)+""")
    private val whitespaceRegex = Regex("""[ \t]+""")

    fun clean(settings: Map<String, String>): TextProcessOutcome {
        val input = settings.text()
        val operations = settings.string("operations", "trim|normalize_line_endings|collapse_blank_lines")
            .split('|').filter { it.isNotBlank() }.toSet()
        var value = input

        if ("normalize_line_endings" in operations) {
            value = value.replace("\r\n", "\n").replace('\r', '\n')
        }
        if ("normalize_tabs" in operations) {
            value = value.replace('\t', ' ')
        }
        if ("trim_lines" in operations) {
            value = value.lines().joinToString("\n") { it.trim() }
        }
        if ("normalize_whitespace" in operations) {
            value = value.lines().joinToString("\n") { line ->
                whitespaceRegex.replace(line.trim(), " ")
            }
        }
        if ("remove_blank_lines" in operations) {
            value = value.lines().filter { it.isNotBlank() }.joinToString("\n")
        } else if ("collapse_blank_lines" in operations) {
            value = blankLineRegex.replace(value, "\n\n")
        }
        if ("trim" in operations) {
            value = value.trim()
        }

        val normalization = settings.string("unicode_normalization", "none").lowercase(Locale.ROOT)
        value = when (normalization) {
            "nfc" -> Normalizer.normalize(value, Normalizer.Form.NFC)
            "nfd" -> Normalizer.normalize(value, Normalizer.Form.NFD)
            "nfkc" -> Normalizer.normalize(value, Normalizer.Form.NFKC)
            "nfkd" -> Normalizer.normalize(value, Normalizer.Form.NFKD)
            else -> value
        }

        return TextProcessOutcome(
            result = value,
            extras = linkedMapOf(
                "text_clean_operations" to operations.joinToString("|"),
                "text_unicode_normalization" to normalization,
                "text_characters_removed" to (input.length - value.length).coerceAtLeast(0).toString(),
                "text_lines_before" to lineCount(input).toString(),
                "text_lines_after" to lineCount(value).toString()
            )
        )
    }

    fun changeCase(settings: Map<String, String>): TextProcessOutcome {
        val input = settings.text()
        val mode = settings.string("mode", "lowercase").lowercase(Locale.ROOT)
        val locale = if (settings.string("locale_policy", "root") == "device") Locale.getDefault() else Locale.ROOT

        val result = when (mode) {
            "lowercase" -> input.lowercase(locale)
            "uppercase" -> input.uppercase(locale)
            "title_case" -> titleCase(input, locale)
            "sentence_case" -> sentenceCase(input, locale)
            "toggle_case" -> buildString(input.length) {
                input.forEach { c ->
                    append(
                        when {
                            c.isUpperCase() -> c.lowercaseChar()
                            c.isLowerCase() -> c.uppercaseChar()
                            else -> c
                        }
                    )
                }
            }
            else -> return TextProcessOutcome("", error = "Unsupported case mode: $mode")
        }

        return TextProcessOutcome(
            result,
            linkedMapOf(
                "text_case_mode" to mode,
                "text_case_locale_policy" to settings.string("locale_policy", "root")
            )
        )
    }

    fun replace(settings: Map<String, String>): TextProcessOutcome {
        val input = settings.text()
        val search = settings.raw("search").orEmpty()
        val replacement = settings.raw("replacement").orEmpty()
        val mode = settings.string("mode", "literal")
        val scope = settings.string("scope", "all")
        val caseSensitive = settings.bool("case_sensitive", true)

        if (search.isEmpty()) return TextProcessOutcome("", error = "Search text cannot be empty.")

        return try {
            val regex = if (mode == "regex") {
                Regex(search, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
            } else {
                Regex(
                    Regex.escape(search),
                    if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                )
            }
            val matches = regex.findAll(input).count()
            val escapedReplacement = if (mode == "regex") replacement else Regex.escapeReplacement(replacement)
            val result = if (scope == "first") {
                regex.replaceFirst(input, escapedReplacement)
            } else {
                regex.replace(input, escapedReplacement)
            }
            val replacements = if (scope == "first") minOf(matches, 1) else matches
            TextProcessOutcome(
                result,
                linkedMapOf(
                    "text_matches_found" to matches.toString(),
                    "text_replacements_made" to replacements.toString(),
                    "text_replace_mode" to mode,
                    "text_replace_scope" to scope
                )
            )
        } catch (e: IllegalArgumentException) {
            TextProcessOutcome("", error = "Invalid regular expression: ${e.message ?: "syntax error"}")
        }
    }

    fun lines(settings: Map<String, String>): TextProcessOutcome {
        val input = settings.text().replace("\r\n", "\n").replace('\r', '\n')
        val operation = settings.string("operation", "deduplicate_preserve_order")
        val trimLines = settings.bool("trim_lines", true)
        val caseSensitive = settings.bool("case_sensitive", true)
        val n = settings.int("n", 10).coerceAtLeast(0)

        var values = if (input.isEmpty()) emptyList() else input.split("\n")
        if (trimLines) values = values.map { it.trim() }

        val originalCount = values.size
        val blankCount = values.count { it.isBlank() }
        val comparator = if (caseSensitive) compareBy<String> { it } else compareBy { it.lowercase(Locale.ROOT) }

        val resultLines = when (operation) {
            "remove_blank_lines" -> values.filter { it.isNotBlank() }
            "sort_ascending" -> values.sortedWith(comparator)
            "sort_descending" -> values.sortedWith(comparator.reversed())
            "unique_sorted" -> values.distinctBy { if (caseSensitive) it else it.lowercase(Locale.ROOT) }.sortedWith(comparator)
            "deduplicate_preserve_order" -> values.distinctBy { if (caseSensitive) it else it.lowercase(Locale.ROOT) }
            "reverse" -> values.reversed()
            "number_lines" -> values.mapIndexed { index, line -> "${index + 1}. $line" }
            "take_first" -> values.take(n)
            "take_last" -> values.takeLast(n)
            else -> return TextProcessOutcome("", error = "Unsupported line operation: $operation")
        }

        val duplicates = originalCount - values.distinctBy { if (caseSensitive) it else it.lowercase(Locale.ROOT) }.size
        return TextProcessOutcome(
            resultLines.joinToString("\n"),
            linkedMapOf(
                "text_line_operation" to operation,
                "text_input_line_count" to originalCount.toString(),
                "text_output_line_count" to resultLines.size.toString(),
                "text_duplicate_count" to duplicates.coerceAtLeast(0).toString(),
                "text_blank_line_count" to blankCount.toString()
            )
        )
    }

    fun splitJoin(settings: Map<String, String>): TextProcessOutcome {
        val input = settings.text()
        val operation = settings.string("operation", "split")
        val delimiterMode = settings.string("delimiter", "comma")
        val delimiter = delimiterValue(delimiterMode, settings.raw("custom_delimiter").orEmpty())
        val trimValues = settings.bool("trim_values", true)
        val discardEmpty = settings.bool("discard_empty", true)

        if (delimiterMode == "custom" && delimiter.isEmpty()) {
            return TextProcessOutcome("", error = "Custom delimiter cannot be empty.")
        }

        val items = if (operation == "split") {
            input.split(delimiter).let { parts ->
                parts.map { if (trimValues) it.trim() else it }
                    .let { if (discardEmpty) it.filter(String::isNotEmpty) else it }
            }
        } else {
            input.replace("\r\n", "\n").replace('\r', '\n').split("\n").let { parts ->
                parts.map { if (trimValues) it.trim() else it }
                    .let { if (discardEmpty) it.filter(String::isNotEmpty) else it }
            }
        }

        val result = if (operation == "split") items.joinToString("\n") else items.joinToString(delimiter)
        return TextProcessOutcome(
            result,
            linkedMapOf(
                "text_split_join_operation" to operation,
                "text_item_count" to items.size.toString(),
                "text_delimiter_mode" to delimiterMode
            )
        )
    }

    fun count(settings: Map<String, String>): TextProcessOutcome {
        val input = settings.text()
        val lines = if (input.isEmpty()) emptyList() else input.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        val words = wordRegex.findAll(input).map { it.value }.toList()
        val paragraphs = input.trim().takeIf { it.isNotEmpty() }
            ?.split(Regex("""\n[ \t]*\n+"""))?.count { it.isNotBlank() } ?: 0

        val extras = linkedMapOf(
            "text_character_count" to input.length.toString(),
            "text_codepoint_count" to input.codePointCount(0, input.length).toString(),
            "text_non_whitespace_character_count" to input.count { !it.isWhitespace() }.toString(),
            "text_word_count" to words.size.toString(),
            "text_unique_word_count" to words.map { it.lowercase(Locale.ROOT) }.distinct().size.toString(),
            "text_line_count" to lines.size.toString(),
            "text_non_empty_line_count" to lines.count { it.isNotBlank() }.toString(),
            "text_paragraph_count" to paragraphs.toString(),
            "text_utf8_byte_count" to input.toByteArray(StandardCharsets.UTF_8).size.toString()
        )
        val summary = "${extras["text_word_count"]} words · ${extras["text_character_count"]} characters · ${extras["text_line_count"]} lines"
        return TextProcessOutcome(summary, extras)
    }

    fun extract(settings: Map<String, String>): TextProcessOutcome {
        val input = settings.text()
        val operation = settings.string("operation", "pattern")
        val n = settings.int("n", 1).coerceAtLeast(0)

        val matches: List<String> = when (operation) {
            "first_n_characters" -> listOf(takeCodePoints(input, n, fromEnd = false))
            "last_n_characters" -> listOf(takeCodePoints(input, n, fromEnd = true))
            "first_n_words" -> listOf(wordRegex.findAll(input).take(n).joinToString(" ") { it.value })
            "last_n_words" -> listOf(wordRegex.findAll(input).map { it.value }.toList().takeLast(n).joinToString(" "))
            "first_n_lines" -> listOf(input.lines().take(n).joinToString("\n"))
            "last_n_lines" -> listOf(input.lines().takeLast(n).joinToString("\n"))
            "before_marker" -> {
                val marker = settings.raw("start_marker").orEmpty()
                if (marker.isEmpty()) return TextProcessOutcome("", error = "Marker cannot be empty.")
                listOf(input.substringBefore(marker, missingDelimiterValue = ""))
            }
            "after_marker" -> {
                val marker = settings.raw("start_marker").orEmpty()
                if (marker.isEmpty()) return TextProcessOutcome("", error = "Marker cannot be empty.")
                listOf(input.substringAfter(marker, missingDelimiterValue = ""))
            }
            "between_markers" -> {
                val start = settings.raw("start_marker").orEmpty()
                val end = settings.raw("end_marker").orEmpty()
                if (start.isEmpty() || end.isEmpty()) return TextProcessOutcome("", error = "Both markers are required.")
                val after = input.substringAfter(start, missingDelimiterValue = "")
                listOf(if (after.isEmpty()) "" else after.substringBefore(end, missingDelimiterValue = ""))
            }
            "pattern" -> extractPattern(input, settings)
            else -> return TextProcessOutcome("", error = "Unsupported extraction operation: $operation")
        }

        val nonEmpty = matches.filter { it.isNotEmpty() }
        return TextProcessOutcome(
            nonEmpty.joinToString("\n"),
            linkedMapOf(
                "text_extract_operation" to operation,
                "text_match_count" to nonEmpty.size.toString(),
                "text_matches_json" to JSONArray(nonEmpty).toString()
            )
        )
    }

    fun truncate(settings: Map<String, String>): TextProcessOutcome {
        val input = settings.text()
        val unit = settings.string("unit", "characters")
        val limit = settings.int("limit", 100).coerceAtLeast(0)
        val retain = settings.string("retain", "start")
        val suffixMode = settings.string("suffix", "ellipsis")
        val customSuffix = settings.raw("custom_suffix").orEmpty()
        val suffix = when (suffixMode) {
            "none" -> ""
            "ellipsis" -> "…"
            "custom" -> customSuffix
            else -> ""
        }

        val measured = measure(input, unit)
        if (measured <= limit) {
            return TextProcessOutcome(
                input,
                linkedMapOf(
                    "text_original_length" to measured.toString(),
                    "text_result_length" to measured.toString(),
                    "text_limit" to limit.toString(),
                    "text_limit_unit" to unit,
                    "text_was_truncated" to "false"
                )
            )
        }

        val available = (limit - measure(suffix, unit)).coerceAtLeast(0)
        val body = truncateTo(input, unit, available, retain == "end")
        val result = if (retain == "end") suffix + body else body + suffix
        return TextProcessOutcome(
            result,
            linkedMapOf(
                "text_original_length" to measured.toString(),
                "text_result_length" to measure(result, unit).toString(),
                "text_limit" to limit.toString(),
                "text_limit_unit" to unit,
                "text_was_truncated" to "true"
            )
        )
    }

    fun slug(settings: Map<String, String>): TextProcessOutcome {
        val input = settings.text()
        val separator = if (settings.string("separator", "hyphen") == "underscore") "_" else "-"
        val lowercase = settings.bool("lowercase", true)
        val asciiOnly = settings.bool("ascii_only", true)

        var value = Normalizer.normalize(input, Normalizer.Form.NFKD)
            .replace(Regex("""\p{M}+"""), "")
        if (lowercase) value = value.lowercase(Locale.ROOT)

        val allowed = if (asciiOnly) Regex("""[^A-Za-z0-9]+""") else Regex("""[^\p{L}\p{N}]+""")
        value = allowed.replace(value, separator)
            .replace(Regex(if (separator == "-") "-{2,}" else "_{2,}"), separator)
            .trim(separator.first())

        return TextProcessOutcome(
            value,
            linkedMapOf(
                "text_slug_separator" to separator,
                "text_slug_lowercase" to lowercase.toString(),
                "text_slug_ascii_only" to asciiOnly.toString()
            )
        )
    }

    fun encode(settings: Map<String, String>): TextProcessOutcome {
        val input = settings.text()
        val operation = settings.string("operation", "base64_encode")
        return try {
            val result = when (operation) {
                "base64_encode" -> Base64.getEncoder().encodeToString(input.toByteArray(StandardCharsets.UTF_8))
                "base64_decode" -> String(Base64.getDecoder().decode(input.trim()), StandardCharsets.UTF_8)
                "url_encode" -> URLEncoder.encode(input, StandardCharsets.UTF_8.name())
                "url_decode" -> URLDecoder.decode(input, StandardCharsets.UTF_8.name())
                "hex_encode" -> input.toByteArray(StandardCharsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
                "hex_decode" -> decodeHex(input)
                "html_encode" -> htmlEncode(input)
                "html_decode" -> htmlDecode(input)
                else -> return TextProcessOutcome("", error = "Unsupported encoding operation: $operation")
            }
            TextProcessOutcome(result, linkedMapOf("text_encoding_operation" to operation))
        } catch (e: IllegalArgumentException) {
            TextProcessOutcome("", error = "Could not decode input: ${e.message ?: "invalid input"}")
        }
    }

    private fun extractPattern(input: String, settings: Map<String, String>): List<String> {
        val pattern = settings.string("pattern", "email")
        val source = when (pattern) {
            "email" -> """(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b"""
            "url" -> """(?i)\bhttps?://[^\s<>"']+"""
            "ipv4" -> """\b(?:\d{1,3}\.){3}\d{1,3}\b"""
            "number" -> """[-+]?(?:\d+(?:\.\d+)?|\.\d+)"""
            "integer" -> """[-+]?\d+"""
            "decimal" -> """[-+]?(?:\d+\.\d+|\.\d+)"""
            "custom_regex" -> settings.raw("regex").orEmpty()
            else -> throw IllegalArgumentException("Unsupported pattern: $pattern")
        }
        if (source.isEmpty()) throw IllegalArgumentException("Custom regular expression cannot be empty.")
        val regex = Regex(source)
        return regex.findAll(input).map { it.value }.toList()
    }

    private fun titleCase(input: String, locale: Locale): String {
        val lowered = input.lowercase(locale)
        return wordRegex.replace(lowered) { match ->
            match.value.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase() else c.toString()
            }
        }
    }

    private fun sentenceCase(input: String, locale: Locale): String {
        val lowered = input.lowercase(locale)
        val chars = lowered.toCharArray()
        var capitalizeNext = true
        for (i in chars.indices) {
            val c = chars[i]
            if (capitalizeNext && c.isLetter()) {
                chars[i] = c.uppercaseChar()
                capitalizeNext = false
            }
            if (c == '.' || c == '!' || c == '?' || c == '\n') capitalizeNext = true
        }
        return String(chars)
    }

    private fun delimiterValue(mode: String, custom: String): String = when (mode) {
        "comma" -> ","
        "semicolon" -> ";"
        "tab" -> "\t"
        "pipe" -> "|"
        "space" -> " "
        "newline" -> "\n"
        "comma_space" -> ", "
        "custom" -> custom
        else -> ","
    }

    private fun takeCodePoints(input: String, n: Int, fromEnd: Boolean): String {
        if (n <= 0 || input.isEmpty()) return ""
        val cps = input.codePoints().toArray()
        val picked = if (fromEnd) cps.takeLast(n) else cps.take(n)
        return String(picked.toIntArray(), 0, picked.size)
    }

    private fun measure(input: String, unit: String): Int = when (unit) {
        "words" -> wordRegex.findAll(input).count()
        "lines" -> lineCount(input)
        "utf8_bytes" -> input.toByteArray(StandardCharsets.UTF_8).size
        else -> input.codePointCount(0, input.length)
    }

    private fun truncateTo(input: String, unit: String, limit: Int, fromEnd: Boolean): String {
        if (limit <= 0) return ""
        return when (unit) {
            "words" -> {
                val words = wordRegex.findAll(input).map { it.value }.toList()
                (if (fromEnd) words.takeLast(limit) else words.take(limit)).joinToString(" ")
            }
            "lines" -> (if (fromEnd) input.lines().takeLast(limit) else input.lines().take(limit)).joinToString("\n")
            "utf8_bytes" -> truncateUtf8(input, limit, fromEnd)
            else -> takeCodePoints(input, limit, fromEnd)
        }
    }

    private fun truncateUtf8(input: String, limit: Int, fromEnd: Boolean): String {
        val cps = input.codePoints().toArray()
        val sequence = if (fromEnd) cps.reversedArray().asList() else cps.asList()
        val accepted = mutableListOf<Int>()
        var used = 0
        for (cp in sequence) {
            val bytes = String(intArrayOf(cp), 0, 1).toByteArray(StandardCharsets.UTF_8).size
            if (used + bytes > limit) break
            accepted += cp
            used += bytes
        }
        val ordered = if (fromEnd) accepted.reversed() else accepted
        return String(ordered.toIntArray(), 0, ordered.size)
    }

    private fun decodeHex(input: String): String {
        val compact = input.filterNot(Char::isWhitespace)
        require(compact.length % 2 == 0) { "Hex input must contain an even number of digits." }
        require(compact.matches(Regex("""[0-9A-Fa-f]*"""))) { "Hex input contains non-hex characters." }
        val bytes = ByteArray(compact.length / 2) { i ->
            compact.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun htmlEncode(input: String): String =
        input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")

    private fun htmlDecode(input: String): String =
        input.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")

    private fun lineCount(input: String): Int = if (input.isEmpty()) 0 else input.replace("\r\n", "\n").replace('\r', '\n').count { it == '\n' } + 1

    internal fun Map<String, String>.raw(key: String): String? = this[key] ?: this["input_$key"]
    internal fun Map<String, String>.text(): String = raw("text").orEmpty()
    internal fun Map<String, String>.string(key: String, default: String): String = raw(key)?.takeIf { it.isNotBlank() } ?: default
    internal fun Map<String, String>.bool(key: String, default: Boolean): Boolean =
        raw(key)?.lowercase(Locale.ROOT)?.let { it == "true" || it == "1" || it == "yes" } ?: default
    internal fun Map<String, String>.int(key: String, default: Int): Int = raw(key)?.toIntOrNull() ?: default
}
