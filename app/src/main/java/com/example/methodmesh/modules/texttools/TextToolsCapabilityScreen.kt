package com.example.methodmesh.modules.texttools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject

val TextCleanCapabilityScreen = TextToolCapabilityScreen(
    As100TextCleanMethod,
    "Clean text",
    "Normalize whitespace, lines and Unicode."
)
val TextCaseCapabilityScreen = TextToolCapabilityScreen(
    As100TextCaseMethod,
    "Change text case",
    "Convert text case locally."
)
val TextReplaceCapabilityScreen = TextToolCapabilityScreen(
    As100TextReplaceMethod,
    "Find and replace",
    "Replace literal text or explicit regular-expression matches."
)
val TextLinesCapabilityScreen = TextToolCapabilityScreen(
    As100TextLinesMethod,
    "Process lines",
    "Sort, deduplicate, filter or subset line-oriented text."
)
val TextSplitJoinCapabilityScreen = TextToolCapabilityScreen(
    As100TextSplitJoinMethod,
    "Split or join text",
    "Convert between delimited values and one item per line."
)
val TextCountCapabilityScreen = TextToolCapabilityScreen(
    As100TextCountMethod,
    "Count text",
    "Count words, characters, lines, paragraphs and bytes."
)
val TextExtractCapabilityScreen = TextToolCapabilityScreen(
    As100TextExtractMethod,
    "Extract from text",
    "Extract portions of text or syntactic patterns."
)
val TextTruncateCapabilityScreen = TextToolCapabilityScreen(
    As100TextTruncateMethod,
    "Truncate text",
    "Restrict text to a deterministic size."
)
val TextSlugCapabilityScreen = TextToolCapabilityScreen(
    As100TextSlugMethod,
    "Make slug",
    "Create a filename- or identifier-friendly slug."
)
val TextEncodeCapabilityScreen = TextToolCapabilityScreen(
    As100TextEncodeMethod,
    "Encode or decode text",
    "Base64, URL, hexadecimal and basic HTML entity transforms."
)

class TextToolCapabilityScreen(
    private val method: BaseTextToolMethod,
    override val title: String,
    override val description: String
) : CapabilityScreenSpec {
    override val capabilityId: String = method.id

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        fun initial(key: String, fallback: String = "") =
            context.action.settings[key] ?: context.action.settings["input_$key"] ?: fallback

        var text by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("text")) }
        var operations by rememberSaveable { mutableStateOf(initial("operations", "trim|normalize_line_endings|collapse_blank_lines")) }
        var unicodeNormalization by rememberSaveable { mutableStateOf(initial("unicode_normalization", "none")) }
        var mode by rememberSaveable { mutableStateOf(initial("mode", defaultMode(method.id))) }
        var localePolicy by rememberSaveable { mutableStateOf(initial("locale_policy", "root")) }
        var search by rememberSaveable { mutableStateOf(initial("search")) }
        var replacement by rememberSaveable { mutableStateOf(initial("replacement")) }
        var scope by rememberSaveable { mutableStateOf(initial("scope", "all")) }
        var caseSensitive by rememberSaveable { mutableStateOf(initial("case_sensitive", "true").toBoolean()) }
        var lineOperation by rememberSaveable { mutableStateOf(initial("operation", defaultOperation(method.id))) }
        var trimLines by rememberSaveable { mutableStateOf(initial("trim_lines", "true").toBoolean()) }
        var n by rememberSaveable { mutableStateOf(initial("n", defaultN(method.id))) }
        var delimiter by rememberSaveable { mutableStateOf(initial("delimiter", "comma")) }
        var customDelimiter by rememberSaveable { mutableStateOf(initial("custom_delimiter")) }
        var trimValues by rememberSaveable { mutableStateOf(initial("trim_values", "true").toBoolean()) }
        var discardEmpty by rememberSaveable { mutableStateOf(initial("discard_empty", "true").toBoolean()) }
        var extractOperation by rememberSaveable { mutableStateOf(initial("operation", "pattern")) }
        var pattern by rememberSaveable { mutableStateOf(initial("pattern", "email")) }
        var regex by rememberSaveable { mutableStateOf(initial("regex")) }
        var startMarker by rememberSaveable { mutableStateOf(initial("start_marker")) }
        var endMarker by rememberSaveable { mutableStateOf(initial("end_marker")) }
        var limit by rememberSaveable { mutableStateOf(initial("limit", "100")) }
        var unit by rememberSaveable { mutableStateOf(initial("unit", "characters")) }
        var retain by rememberSaveable { mutableStateOf(initial("retain", "start")) }
        var suffix by rememberSaveable { mutableStateOf(initial("suffix", "ellipsis")) }
        var customSuffix by rememberSaveable { mutableStateOf(initial("custom_suffix")) }
        var separator by rememberSaveable { mutableStateOf(initial("separator", "hyphen")) }
        var lowercase by rememberSaveable { mutableStateOf(initial("lowercase", "true").toBoolean()) }
        var asciiOnly by rememberSaveable { mutableStateOf(initial("ascii_only", "true").toBoolean()) }
        var encodeOperation by rememberSaveable { mutableStateOf(initial("operation", "base64_encode")) }

        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var liveResult by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable { mutableStateOf("Ready.") }

        fun currentSettings(): Map<String, String> {
            val values = linkedMapOf("text" to text)
            when (method.id) {
                As100TextCleanMethod.ID -> {
                    values["operations"] = operations
                    values["unicode_normalization"] = unicodeNormalization
                }
                As100TextCaseMethod.ID -> {
                    values["mode"] = mode
                    values["locale_policy"] = localePolicy
                }
                As100TextReplaceMethod.ID -> {
                    values["search"] = search
                    values["replacement"] = replacement
                    values["mode"] = mode
                    values["scope"] = scope
                    values["case_sensitive"] = caseSensitive.toString()
                }
                As100TextLinesMethod.ID -> {
                    values["operation"] = lineOperation
                    values["trim_lines"] = trimLines.toString()
                    values["case_sensitive"] = caseSensitive.toString()
                    values["n"] = n
                }
                As100TextSplitJoinMethod.ID -> {
                    values["operation"] = lineOperation
                    values["delimiter"] = delimiter
                    values["custom_delimiter"] = customDelimiter
                    values["trim_values"] = trimValues.toString()
                    values["discard_empty"] = discardEmpty.toString()
                }
                As100TextExtractMethod.ID -> {
                    values["operation"] = extractOperation
                    values["pattern"] = pattern
                    values["regex"] = regex
                    values["start_marker"] = startMarker
                    values["end_marker"] = endMarker
                    values["n"] = n
                }
                As100TextTruncateMethod.ID -> {
                    values["limit"] = limit
                    values["unit"] = unit
                    values["retain"] = retain
                    values["suffix"] = suffix
                    values["custom_suffix"] = customSuffix
                }
                As100TextSlugMethod.ID -> {
                    values["separator"] = separator
                    values["lowercase"] = lowercase.toString()
                    values["ascii_only"] = asciiOnly.toString()
                }
                As100TextEncodeMethod.ID -> values["operation"] = encodeOperation
            }
            return values
        }

        val settingsSnapshot = currentSettings()

        val restoredResult = remember(resultValuesJson) {
            resultValuesJson
                ?.let(::valuesFromJson)
                ?.let { values ->
                    val request = method.request(
                        action = method.id,
                        context = context.request.invocationContext.asMap(method.id) +
                            context.action.settings + settingsSnapshot,
                        signals = emptyList(),
                        inputs = emptyList()
                    )
                    method.result(request, values, context.request.invocationContext)
                }
        }
        val capturedResult = liveResult ?: restoredResult

        LaunchedEffect(settingsSnapshot) {
            context.onSettingsChanged(settingsSnapshot)
        }

        fun runTool() {
            val settings = currentSettings()
            val values = method.process(settings)
            val request = method.request(
                action = method.id,
                context = context.request.invocationContext.asMap(method.id) +
                    context.action.settings + settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = method.result(request, values, context.request.invocationContext)
            liveResult = execution
            resultValuesJson = valuesToJson(values)
            status = values[TextCommonFields.ERROR].orEmpty()
                .ifBlank { if (values[TextCommonFields.STATUS] == "succeeded") "Done." else "Failed." }
            if (context.submitsImmediately) onConfirmed(execution)
        }

        LaunchedEffect(context.presentationMode, context.startsImmediately, capturedResult) {
            if (
                (context.presentationMode == CapabilityPresentationMode.IntentLaunch || context.startsImmediately) &&
                !launched && capturedResult == null
            ) {
                launched = true
                runTool()
            }
        }

        val preview = capturedResult?.let { execution ->
            val fields = OutputFormatter.fields(execution, includeProvenance = false)
            method.previewFields.mapNotNull { key -> fields[key]?.let { key to it } }.toMap()
        }.orEmpty()

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = capturedResult,
            resultPreview = preview,
            onBack = onBack,
            onRetry = {
                liveResult = null
                resultValuesJson = null
                status = "Ready to repeat."
            },
            onConfirm = { capturedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))

            if (context.settingShouldBeShown("text")) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 16
                )
                Spacer(Modifier.height(10.dp))
            }

            when (method.id) {
                As100TextCleanMethod.ID -> {
                    if (context.settingShouldBeShown("operations")) {
                        Text("Cleanup", style = MaterialTheme.typography.titleSmall)
                        val choices = listOf(
                            "trim" to "Trim ends",
                            "normalize_whitespace" to "Normalize spaces",
                            "normalize_tabs" to "Tabs → spaces",
                            "normalize_line_endings" to "Normalize line endings",
                            "collapse_blank_lines" to "Collapse repeated blank lines",
                            "remove_blank_lines" to "Remove blank lines",
                            "trim_lines" to "Trim each line"
                        )
                        MultiChoiceRows(operations, choices) { operations = it }
                    }
                    if (context.settingShouldBeShown("unicode_normalization")) {
                        ChoiceRows("Unicode normalization", unicodeNormalization, listOf("none", "nfc", "nfd", "nfkc", "nfkd")) {
                            unicodeNormalization = it
                        }
                    }
                }

                As100TextCaseMethod.ID -> {
                    if (context.settingShouldBeShown("mode")) {
                        ChoiceRows("Case", mode, listOf("lowercase", "uppercase", "title_case", "sentence_case", "toggle_case")) {
                            mode = it
                        }
                    }
                    if (context.settingShouldBeShown("locale_policy")) {
                        ChoiceRows("Locale", localePolicy, listOf("root", "device")) { localePolicy = it }
                    }
                }

                As100TextReplaceMethod.ID -> {
                    if (context.settingShouldBeShown("search")) TextField("Find", search) { search = it }
                    if (context.settingShouldBeShown("replacement")) TextField("Replace with", replacement) { replacement = it }
                    if (context.settingShouldBeShown("mode")) ChoiceRows("Mode", mode, listOf("literal", "regex")) { mode = it }
                    if (context.settingShouldBeShown("scope")) ChoiceRows("Replace", scope, listOf("all", "first")) { scope = it }
                    if (context.settingShouldBeShown("case_sensitive")) ToggleRow("Case sensitive", caseSensitive) { caseSensitive = it }
                }

                As100TextLinesMethod.ID -> {
                    if (context.settingShouldBeShown("operation")) {
                        ChoiceRows(
                            "Line operation",
                            lineOperation,
                            listOf(
                                "deduplicate_preserve_order",
                                "unique_sorted",
                                "sort_ascending",
                                "sort_descending",
                                "remove_blank_lines",
                                "reverse",
                                "number_lines",
                                "take_first",
                                "take_last"
                            )
                        ) { lineOperation = it }
                    }
                    if (context.settingShouldBeShown("trim_lines")) ToggleRow("Trim each line", trimLines) { trimLines = it }
                    if (context.settingShouldBeShown("case_sensitive")) ToggleRow("Case sensitive", caseSensitive) { caseSensitive = it }
                    if ((lineOperation == "take_first" || lineOperation == "take_last") && context.settingShouldBeShown("n")) {
                        NumberField("Number of lines", n) { n = it }
                    }
                }

                As100TextSplitJoinMethod.ID -> {
                    if (context.settingShouldBeShown("operation")) ChoiceRows("Operation", lineOperation, listOf("split", "join")) { lineOperation = it }
                    if (context.settingShouldBeShown("delimiter")) {
                        ChoiceRows("Delimiter", delimiter, listOf("comma", "comma_space", "semicolon", "tab", "pipe", "space", "newline", "custom")) {
                            delimiter = it
                        }
                    }
                    if (delimiter == "custom" && context.settingShouldBeShown("custom_delimiter")) {
                        TextField("Custom delimiter", customDelimiter) { customDelimiter = it }
                    }
                    if (context.settingShouldBeShown("trim_values")) ToggleRow("Trim values", trimValues) { trimValues = it }
                    if (context.settingShouldBeShown("discard_empty")) ToggleRow("Discard empty values", discardEmpty) { discardEmpty = it }
                }

                As100TextExtractMethod.ID -> {
                    if (context.settingShouldBeShown("operation")) {
                        ChoiceRows(
                            "Extraction",
                            extractOperation,
                            listOf(
                                "pattern",
                                "first_n_characters",
                                "last_n_characters",
                                "first_n_words",
                                "last_n_words",
                                "first_n_lines",
                                "last_n_lines",
                                "before_marker",
                                "after_marker",
                                "between_markers"
                            )
                        ) { extractOperation = it }
                    }
                    if (extractOperation == "pattern" && context.settingShouldBeShown("pattern")) {
                        ChoiceRows("Pattern", pattern, listOf("email", "url", "ipv4", "number", "integer", "decimal", "custom_regex")) { pattern = it }
                    }
                    if (extractOperation == "pattern" && pattern == "custom_regex" && context.settingShouldBeShown("regex")) {
                        TextField("Regular expression", regex) { regex = it }
                    }
                    if (extractOperation in listOf("before_marker", "after_marker", "between_markers") && context.settingShouldBeShown("start_marker")) {
                        TextField("Marker / start marker", startMarker) { startMarker = it }
                    }
                    if (extractOperation == "between_markers" && context.settingShouldBeShown("end_marker")) {
                        TextField("End marker", endMarker) { endMarker = it }
                    }
                    if (extractOperation.contains("_n_") && context.settingShouldBeShown("n")) {
                        NumberField("How many", n) { n = it }
                    }
                }

                As100TextTruncateMethod.ID -> {
                    if (context.settingShouldBeShown("limit")) NumberField("Limit", limit) { limit = it }
                    if (context.settingShouldBeShown("unit")) ChoiceRows("Unit", unit, listOf("characters", "words", "lines", "utf8_bytes")) { unit = it }
                    if (context.settingShouldBeShown("retain")) ChoiceRows("Keep", retain, listOf("start", "end")) { retain = it }
                    if (context.settingShouldBeShown("suffix")) ChoiceRows("Marker", suffix, listOf("none", "ellipsis", "custom")) { suffix = it }
                    if (suffix == "custom" && context.settingShouldBeShown("custom_suffix")) TextField("Custom marker", customSuffix) { customSuffix = it }
                }

                As100TextSlugMethod.ID -> {
                    if (context.settingShouldBeShown("separator")) ChoiceRows("Separator", separator, listOf("hyphen", "underscore")) { separator = it }
                    if (context.settingShouldBeShown("lowercase")) ToggleRow("Lowercase", lowercase) { lowercase = it }
                    if (context.settingShouldBeShown("ascii_only")) ToggleRow("ASCII only", asciiOnly) { asciiOnly = it }
                }

                As100TextEncodeMethod.ID -> {
                    if (context.settingShouldBeShown("operation")) {
                        ChoiceRows(
                            "Encoding",
                            encodeOperation,
                            listOf("base64_encode", "base64_decode", "url_encode", "url_decode", "hex_encode", "hex_decode", "html_encode", "html_decode")
                        ) { encodeOperation = it }
                    }
                    Text(
                        "Encoding is reversible representation, not encryption.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(onClick = ::runTool, modifier = Modifier.fillMaxWidth()) {
                Text(if (capturedResult == null) actionLabel(method.id) else "Run again")
            }
            Text(status, modifier = Modifier.padding(top = 8.dp))

            val coreValue = resultValuesJson
                ?.let(::valuesFromJson)
                ?.get(method.coreField)
                .orEmpty()
            if (coreValue.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Result", style = MaterialTheme.typography.titleSmall)
                SelectionContainer {
                    Text(coreValue, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun TextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceRows(label: String, selected: String, choices: List<String>, onSelected: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    choices.forEach { choice ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected == choice, onClick = { onSelected(choice) })
            Text(pretty(choice))
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun MultiChoiceRows(
    selectedRaw: String,
    choices: List<Pair<String, String>>,
    onChanged: (String) -> Unit
) {
    val selected = selectedRaw.split('|').filter { it.isNotBlank() }.toMutableSet()
    choices.forEach { (key, label) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = key in selected,
                onCheckedChange = { checked ->
                    val next = selected.toMutableSet()
                    if (checked) next += key else next -= key
                    onChanged(choices.map { it.first }.filter { it in next }.joinToString("|"))
                }
            )
            Text(label)
        }
    }
    Spacer(Modifier.height(6.dp))
}

private fun pretty(value: String): String =
    value.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun defaultMode(methodId: String): String = when (methodId) {
    As100TextCaseMethod.ID -> "lowercase"
    As100TextReplaceMethod.ID -> "literal"
    else -> ""
}

private fun defaultOperation(methodId: String): String = when (methodId) {
    As100TextLinesMethod.ID -> "deduplicate_preserve_order"
    As100TextSplitJoinMethod.ID -> "split"
    else -> ""
}

private fun defaultN(methodId: String): String = when (methodId) {
    As100TextLinesMethod.ID -> "10"
    As100TextExtractMethod.ID -> "1"
    else -> "1"
}

private fun actionLabel(methodId: String): String = when (methodId) {
    As100TextCleanMethod.ID -> "Clean text"
    As100TextCaseMethod.ID -> "Change case"
    As100TextReplaceMethod.ID -> "Replace"
    As100TextLinesMethod.ID -> "Process lines"
    As100TextSplitJoinMethod.ID -> "Split / join"
    As100TextCountMethod.ID -> "Count"
    As100TextExtractMethod.ID -> "Extract"
    As100TextTruncateMethod.ID -> "Truncate"
    As100TextSlugMethod.ID -> "Make slug"
    As100TextEncodeMethod.ID -> "Encode / decode"
    else -> "Process"
}

private fun valuesToJson(values: Map<String, String>): String =
    JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }.toString()

private fun valuesFromJson(json: String): Map<String, String> =
    JSONObject(json).let { obj ->
        buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, obj.optString(key, ""))
            }
        }
    }
