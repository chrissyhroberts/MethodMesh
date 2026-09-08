package com.example.methodmesh.modules.sampling

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject

object SamplingCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SamplingMethod.ID
    override val title = "Sampling"
    override val description = "Reproducible random selection, shuffling and partitioning with CSV/JSON provenance."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        fun initial(key: String, default: String) = context.action.settings[key]
            ?: context.action.settings["input_$key"]
            ?: default
        fun setSetting(key: String, value: String) = context.onSettingsChanged(mapOf(key to value))
        fun showSetting(key: String) = context.settingShouldBeShown(key)

        var sourceType by rememberSaveable { mutableStateOf(initial("source_type", "manual")) }
        var manualItems by rememberSaveable { mutableStateOf(initial("manual_items", "")) }
        var manualSeparator by rememberSaveable { mutableStateOf(initial("manual_separator", "newline")) }
        var sequenceStart by rememberSaveable { mutableStateOf(initial("sequence_start", "1")) }
        var sequenceEnd by rememberSaveable { mutableStateOf(initial("sequence_end", "100")) }
        var sequenceStep by rememberSaveable { mutableStateOf(initial("sequence_step", "1")) }
        var wordCount by rememberSaveable { mutableStateOf(initial("word_count", "8")) }
        var wordMinLength by rememberSaveable { mutableStateOf(initial("word_min_length", "3")) }
        var wordMaxLength by rememberSaveable { mutableStateOf(initial("word_max_length", "12")) }
        var wordUnique by rememberSaveable { mutableStateOf(initial("word_unique", "true").equals("true", true)) }

        var operation by rememberSaveable { mutableStateOf(initial("operation", "simple_sample")) }
        var sampleMode by rememberSaveable { mutableStateOf(initial("sample_mode", "n")) }
        var sampleSize by rememberSaveable { mutableStateOf(initial("sample_size", "1")) }
        var sampleFraction by rememberSaveable { mutableStateOf(initial("sample_fraction", "0.1")) }
        var replacement by rememberSaveable { mutableStateOf(initial("replacement", "false").equals("true", true)) }
        var outputOrder by rememberSaveable { mutableStateOf(initial("output_order", "draw")) }
        var sortField by rememberSaveable { mutableStateOf(initial("sort_field", "")) }

        var idField by rememberSaveable { mutableStateOf(initial("id_field", "item_id")) }
        var labelField by rememberSaveable { mutableStateOf(initial("label_field", "item_label")) }
        var weightField by rememberSaveable { mutableStateOf(initial("weight_field", "weight")) }
        var stratumField by rememberSaveable { mutableStateOf(initial("stratum_field", "stratum")) }
        var eligibilityField by rememberSaveable { mutableStateOf(initial("eligibility_field", "eligible")) }

        var stratumAllocation by rememberSaveable { mutableStateOf(initial("stratum_allocation", "proportional_total")) }
        var stratumSizes by rememberSaveable { mutableStateOf(initial("stratum_sizes", "")) }
        var partitionGroups by rememberSaveable { mutableStateOf(initial("partition_groups", "2")) }
        var systematicInterval by rememberSaveable { mutableStateOf(initial("systematic_interval", "0")) }

        var outputMode by rememberSaveable { mutableStateOf(initial("output_mode", "annotated")) }
        var outputFormat by rememberSaveable { mutableStateOf(initial("output_format", "csv")) }
        var selectedField by rememberSaveable { mutableStateOf(initial("selected_field", "sampling_selected")) }
        var countField by rememberSaveable { mutableStateOf(initial("count_field", "sampling_count")) }
        var orderField by rememberSaveable { mutableStateOf(initial("order_field", "sampling_order")) }
        var groupField by rememberSaveable { mutableStateOf(initial("group_field", "sampling_group")) }

        var seedMode by rememberSaveable { mutableStateOf(initial("seed_mode", "auto")) }
        var seed by rememberSaveable { mutableStateOf(initial("seed", "")) }

        var csvUriString by rememberSaveable { mutableStateOf(initial("csv_uri", "")) }
        var inlineCsvText by rememberSaveable { mutableStateOf(initial("csv_text", "")) }
        var structuredItemsJson by rememberSaveable { mutableStateOf(initial("sampling_items_json", "")) }
        var csvText by remember { mutableStateOf<String?>(null) }
        var csvFileName by rememberSaveable { mutableStateOf("") }
        var csvSha256 by rememberSaveable { mutableStateOf("") }
        var csvHeaders by remember { mutableStateOf<List<String>>(emptyList()) }
        var fileStatus by rememberSaveable { mutableStateOf("") }
        var templateStatus by rememberSaveable { mutableStateOf("") }
        var showAdvanced by rememberSaveable { mutableStateOf(false) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var valuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }

        fun currentSettings(): Map<String, String> = linkedMapOf(
            "source_type" to sourceType,
            "manual_items" to manualItems,
            "manual_separator" to manualSeparator,
            "sequence_start" to sequenceStart,
            "sequence_end" to sequenceEnd,
            "sequence_step" to sequenceStep,
            "word_count" to wordCount,
            "word_min_length" to wordMinLength,
            "word_max_length" to wordMaxLength,
            "word_unique" to wordUnique.toString(),
            "operation" to operation,
            "sample_mode" to sampleMode,
            "sample_size" to sampleSize,
            "sample_fraction" to sampleFraction,
            "replacement" to replacement.toString(),
            "output_order" to outputOrder,
            "sort_field" to sortField,
            "id_field" to idField,
            "label_field" to labelField,
            "weight_field" to weightField,
            "stratum_field" to stratumField,
            "eligibility_field" to eligibilityField,
            "stratum_allocation" to stratumAllocation,
            "stratum_sizes" to stratumSizes,
            "partition_groups" to partitionGroups,
            "systematic_interval" to systematicInterval,
            "output_mode" to outputMode,
            "output_format" to outputFormat,
            "selected_field" to selectedField,
            "count_field" to countField,
            "order_field" to orderField,
            "group_field" to groupField,
            "seed_mode" to seedMode,
            "seed" to seed,
            "csv_uri" to csvUriString,
            "csv_text" to inlineCsvText,
            "sampling_items_json" to structuredItemsJson
        )

        fun requestFor(settings: Map<String, String>) = As100SamplingMethod.request(
            action = capabilityId,
            context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + settings,
            signals = emptyList(),
            inputs = emptyList()
        )

        fun encodeValues(values: Map<String, String>): String = JSONObject(values).toString()
        fun decodeValues(json: String): Map<String, String> {
            if (json.isBlank()) return emptyMap()
            val obj = JSONObject(json)
            return obj.keys().asSequence().associateWith { key -> obj.optString(key, "") }
        }

        val result: ExecutionResult? = remember(valuesJson, context.action.canonicalId) {
            if (valuesJson.isBlank()) null else {
                val values = decodeValues(valuesJson)
                As100SamplingMethod.result(
                    requestFor(currentSettings()),
                    values,
                    context.request.invocationContext
                )
            }
        }

        fun adoptCsv(input: SamplingCsvInput) {
            val table = SamplingCsv.parse(input.text)
            csvText = input.text
            csvFileName = input.displayName
            csvSha256 = input.sha256
            csvHeaders = table.headers
            fileStatus = "${input.displayName}: ${table.rows.size} rows · SHA-256 ${input.sha256.take(12)}…"

            fun choose(key: String, current: String, default: String, optional: Boolean): String {
                if (!showSetting(key)) return current
                if (current in table.headers) return current
                if (default in table.headers) return default
                return if (optional) "(none)" else table.headers.firstOrNull().orEmpty()
            }
            idField = choose("id_field", idField, "item_id", optional = false).also { setSetting("id_field", it) }
            labelField = choose("label_field", labelField, "item_label", optional = true).also { setSetting("label_field", it) }
            weightField = choose("weight_field", weightField, "weight", optional = true).also { setSetting("weight_field", it) }
            stratumField = choose("stratum_field", stratumField, "stratum", optional = true).also { setSetting("stratum_field", it) }
            eligibilityField = choose("eligibility_field", eligibilityField, "eligible", optional = true).also { setSetting("eligibility_field", it) }
        }

        val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                csvUriString = uri.toString()
                setSetting("csv_uri", csvUriString)
                adoptCsv(SamplingFiles.readCsv(appContext, uri))
            }.onFailure { error -> fileStatus = "CSV error: ${error.message ?: "could not read file"}" }
        }

        val templateCreator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching { SamplingFiles.writeTemplate(appContext, uri) }
                .onSuccess { templateStatus = "Template created." }
                .onFailure { templateStatus = "Template error: ${it.message ?: "could not write file"}" }
        }

        LaunchedEffect(csvUriString) {
            if (csvUriString.isNotBlank() && csvText == null) {
                runCatching { SamplingFiles.readCsv(appContext, Uri.parse(csvUriString)) }
                    .onSuccess(::adoptCsv)
                    .onFailure { fileStatus = "CSV needs to be selected again: ${it.message ?: "file unavailable"}" }
            }
        }

        fun runSampling() {
            val settings = context.action.settings + currentSettings()
            val request = requestFor(settings)
            val executionValues = runCatching {
                val structured = As100SamplingMethod.parseStructuredItems(settings)
                val inlineCsv = As100SamplingMethod.contextValue(settings, "csv_text")
                val activeCsv = csvText ?: inlineCsv
                val activeCsvHash = when {
                    csvSha256.isNotBlank() -> csvSha256
                    inlineCsv != null -> SamplingProvenance.sha256(inlineCsv)
                    else -> null
                }
                val run = SamplingEngine.run(
                    settings = settings,
                    csvText = activeCsv,
                    inputFileSha256 = activeCsvHash,
                    sourceFileName = csvFileName.ifBlank { if (inlineCsv != null) "sampling_input.csv" else "" }.takeIf { it.isNotBlank() },
                    structuredHeaders = structured?.first,
                    structuredRows = structured?.second
                )
                val config = run.config
                // ODK's common JSON-roster route returns JSON directly. CSV output,
                // and all native file-oriented runs, produce cache attachments.
                val shouldCreateFiles = config.outputFormat == SamplingOutputFormat.CSV ||
                    context.presentationMode != CapabilityPresentationMode.IntentLaunch
                val artifacts = if (shouldCreateFiles) SamplingFiles.writeRun(appContext, run) else null
                As100SamplingMethod.valuesFromRun(
                    run = run,
                    resultUri = artifacts?.resultUri.orEmpty(),
                    manifestUri = artifacts?.manifestUri.orEmpty(),
                    outputFileSha256 = artifacts?.outputFileSha256
                )
            }.getOrElse { As100SamplingMethod.failureValues(it.message ?: "Sampling failed.") }
            valuesJson = encodeValues(executionValues)
            // capturedResult is reconstructed from valuesJson. CapabilityScreenScaffold
            // owns AutomaticReturn close-out, so do not deliver the same execution twice.
        }

        LaunchedEffect(context.presentationMode, context.completionMode, csvText, csvUriString, context.action.settings) {
            // ODK/external automatic-return calls should execute immediately. Native
            // preset runs use IntentLaunch presentation too, but must first expose any
            // declared runtime inputs rather than silently running with placeholders.
            if (context.submitsImmediately && context.presentationMode == CapabilityPresentationMode.IntentLaunch && !launched) {
                val requestedSource = SamplingSourceType.from(initial("source_type", "manual"))
                val inlineCsvPresent = As100SamplingMethod.contextValue(context.action.settings, "csv_text") != null
                val ready = requestedSource != SamplingSourceType.CSV || csvText != null || inlineCsvPresent
                if (ready) {
                    launched = true
                    runSampling()
                }
            }
        }

        // Random words are useful as a generated list in their own right. Make
        // population-only the natural initial operation when the source switches.
        LaunchedEffect(sourceType) {
            if (!context.isNativePresetRun && showSetting("operation") && sourceType == "random_words" && operation == "simple_sample") {
                operation = "population_only"
                setSetting("operation", operation)
            }
        }

        val preview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty()
        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = preview,
            onBack = onBack,
            onRetry = { valuesJson = "" },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Build a population, then sample, shuffle or partition it. Every random run records a stable algorithm and seed.")
            Spacer(Modifier.height(12.dp))

            if (context.settingShouldBeShown("source_type")) {
                SamplingChoiceDropdown(
                    label = "Population source",
                    value = sourceType,
                    options = listOf("manual", "csv", "sequence", "random_words"),
                    labels = mapOf("manual" to "Paste / enter list", "csv" to "CSV file", "sequence" to "Numeric sequence", "random_words" to "Random words"),
                    onSelected = { sourceType = it; setSetting("source_type", it) }
                )
                Spacer(Modifier.height(8.dp))
            }

            when (sourceType) {
                "csv" -> {
                    if (showSetting("csv_uri")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "application/vnd.ms-excel")) },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (csvUriString.isBlank()) "Select CSV" else "Change CSV") }
                            OutlinedButton(
                                onClick = { templateCreator.launch("methodmesh_sampling_template.csv") },
                                modifier = Modifier.weight(1f)
                            ) { Text("Download template") }
                        }
                    } else {
                        // A fixed preset CSV URI is configuration, not an operational prompt.
                        // Keep it hidden and reload it from saved state/context automatically.
                        OutlinedButton(
                            onClick = { templateCreator.launch("methodmesh_sampling_template.csv") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Download CSV template") }
                    }
                    if (showSetting("csv_text")) {
                        OutlinedTextField(
                            inlineCsvText,
                            { inlineCsvText = it; setSetting("csv_text", it) },
                            label = { Text("Inline CSV text · optional alternative to a file") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = if (context.isNativePresetRun) 4 else 2
                        )
                    }
                    if (fileStatus.isNotBlank()) Text(fileStatus, style = MaterialTheme.typography.bodySmall)
                    if (templateStatus.isNotBlank()) Text(templateStatus, style = MaterialTheme.typography.bodySmall)
                    if (csvHeaders.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("CSV field mapping", style = MaterialTheme.typography.titleSmall)
                        if (showSetting("id_field")) SamplingChoiceDropdown("Identifier field", idField, csvHeaders, onSelected = { idField = it; setSetting("id_field", it) })
                        if (showSetting("label_field")) SamplingChoiceDropdown("Label field", labelField, listOf("(none)") + csvHeaders, onSelected = { labelField = it; setSetting("label_field", it) })
                        if (showSetting("weight_field")) SamplingChoiceDropdown("Weight field", weightField, listOf("(none)") + csvHeaders, onSelected = { weightField = it; setSetting("weight_field", it) })
                        if (showSetting("stratum_field")) SamplingChoiceDropdown("Stratum field", stratumField, listOf("(none)") + csvHeaders, onSelected = { stratumField = it; setSetting("stratum_field", it) })
                        if (showSetting("eligibility_field")) SamplingChoiceDropdown("Eligibility field", eligibilityField, listOf("(none)") + csvHeaders, onSelected = { eligibilityField = it; setSetting("eligibility_field", it) })
                    }
                }
                "sequence" -> {
                    if (showSetting("sequence_start")) OutlinedTextField(sequenceStart, { sequenceStart = it; setSetting("sequence_start", it) }, label = { Text("Start") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (showSetting("sequence_end")) OutlinedTextField(sequenceEnd, { sequenceEnd = it; setSetting("sequence_end", it) }, label = { Text("End") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (showSetting("sequence_step")) OutlinedTextField(sequenceStep, { sequenceStep = it; setSetting("sequence_step", it) }, label = { Text("Step") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Example: 1, 100, 5 generates 1, 6, 11 … 96.", style = MaterialTheme.typography.bodySmall)
                }
                "random_words" -> {
                    if (showSetting("word_count")) OutlinedTextField(wordCount, { wordCount = it.filter(Char::isDigit); setSetting("word_count", wordCount) }, label = { Text("Number of words") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (showSetting("word_min_length") || showSetting("word_max_length")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (showSetting("word_min_length")) OutlinedTextField(wordMinLength, { wordMinLength = it.filter(Char::isDigit); setSetting("word_min_length", wordMinLength) }, label = { Text("Min length") }, modifier = Modifier.weight(1f), singleLine = true)
                            if (showSetting("word_max_length")) OutlinedTextField(wordMaxLength, { wordMaxLength = it.filter(Char::isDigit); setSetting("word_max_length", wordMaxLength) }, label = { Text("Max length") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                    }
                    if (showSetting("word_unique")) SamplingCheckbox("Unique words", wordUnique) { wordUnique = it; setSetting("word_unique", it.toString()) }
                    Text("Bundled dictionary: ${SamplingDictionary.ID} v${SamplingDictionary.VERSION} · ${SamplingDictionary.words.size} words.", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    if (showSetting("manual_separator")) {
                        SamplingChoiceDropdown(
                            "Input separator", manualSeparator, listOf("newline", "pipe", "comma", "semicolon"),
                            mapOf("newline" to "One item per line", "pipe" to "Pipe |", "comma" to "Comma ,", "semicolon" to "Semicolon ;")
                        ) { manualSeparator = it; setSetting("manual_separator", it) }
                    }
                    if (showSetting("manual_items")) {
                        OutlinedTextField(
                            manualItems,
                            { manualItems = it; setSetting("manual_items", it) },
                            label = { Text(if (manualSeparator == "newline") "Population · one item per line" else "Population items") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = if (manualSeparator == "newline") 6 else 3
                        )
                    }
                    if (context.isNativePresetRun && context.settingIsRuntimeInput("sampling_items_json")) {
                        OutlinedTextField(
                            structuredItemsJson,
                            { structuredItemsJson = it; setSetting("sampling_items_json", it) },
                            label = { Text("Structured population JSON") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))

            if (context.settingShouldBeShown("operation")) {
                SamplingChoiceDropdown(
                    "Operation",
                    operation,
                    listOf("simple_sample", "shuffle", "weighted_sample", "stratified_sample", "systematic_sample", "partition", "population_only"),
                    mapOf(
                        "simple_sample" to "Simple random sample",
                        "shuffle" to "Shuffle population",
                        "weighted_sample" to "Weighted sample",
                        "stratified_sample" to "Stratified sample",
                        "systematic_sample" to "Systematic sample",
                        "partition" to "Random partition",
                        "population_only" to "Build population only"
                    ),
                    onSelected = { operation = it; setSetting("operation", it) }
                )
            }

            if (operation in setOf("simple_sample", "weighted_sample", "stratified_sample", "systematic_sample")) {
                if (showSetting("sample_mode")) {
                    SamplingChoiceDropdown("Sample size", sampleMode, listOf("n", "fraction"), mapOf("n" to "Fixed n", "fraction" to "Fraction")) {
                        sampleMode = it; setSetting("sample_mode", it)
                    }
                }
                if (sampleMode == "fraction") {
                    if (showSetting("sample_fraction")) OutlinedTextField(sampleFraction, { sampleFraction = it; setSetting("sample_fraction", it) }, label = { Text("Fraction (0–1)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                } else {
                    val label = if (operation == "stratified_sample" && stratumAllocation == "equal_n_per_stratum") "n per stratum" else "Sample n"
                    if (showSetting("sample_size")) OutlinedTextField(sampleSize, { sampleSize = it.filter(Char::isDigit); setSetting("sample_size", sampleSize) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }

            if (operation in setOf("simple_sample", "weighted_sample", "stratified_sample")) {
                if (showSetting("replacement")) SamplingCheckbox("Sample with replacement", replacement) { replacement = it; setSetting("replacement", it.toString()) }
            }

            if (operation == "stratified_sample") {
                if (showSetting("stratum_allocation")) SamplingChoiceDropdown(
                    "Stratum allocation", stratumAllocation,
                    listOf("proportional_total", "equal_n_per_stratum", "specified"),
                    mapOf("proportional_total" to "Proportional total", "equal_n_per_stratum" to "Same n per stratum", "specified" to "Specify n by stratum")
                ) { stratumAllocation = it; setSetting("stratum_allocation", it) }
                if (stratumAllocation == "specified" && showSetting("stratum_sizes")) {
                    OutlinedTextField(stratumSizes, { stratumSizes = it; setSetting("stratum_sizes", it) }, label = { Text("Stratum sizes · A=5;B=8") }, modifier = Modifier.fillMaxWidth())
                }
            }

            if (operation == "partition" && showSetting("partition_groups")) {
                OutlinedTextField(partitionGroups, { partitionGroups = it.filter(Char::isDigit); setSetting("partition_groups", partitionGroups) }, label = { Text("Number of groups") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            if (operation == "systematic_sample" && showSetting("systematic_interval")) {
                OutlinedTextField(systematicInterval, { systematicInterval = it; setSetting("systematic_interval", it) }, label = { Text("Systematic interval · 0 = derive from n") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            if (operation != "population_only") {
                if (showSetting("output_order")) SamplingChoiceDropdown("Selected-record order", outputOrder, listOf("draw", "input", "sorted"), mapOf("draw" to "Draw order", "input" to "Original input order", "sorted" to "Sorted")) {
                    outputOrder = it; setSetting("output_order", it)
                }
                if (outputOrder == "sorted" && showSetting("sort_field")) {
                    OutlinedTextField(sortField, { sortField = it; setSetting("sort_field", it) }, label = { Text("Sort field · blank = identifier") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }

            Spacer(Modifier.height(12.dp))
            if (showSetting("output_mode")) SamplingChoiceDropdown("Output records", outputMode, listOf("annotated", "selected_only"), mapOf("annotated" to "Full population + sampling fields", "selected_only" to "Selected records only")) {
                outputMode = it; setSetting("output_mode", it)
            }
            if (showSetting("output_format")) SamplingChoiceDropdown("Output format", outputFormat, listOf("csv", "json"), mapOf("csv" to "CSV file", "json" to "JSON")) {
                outputFormat = it; setSetting("output_format", it)
            }

            Spacer(Modifier.height(10.dp))
            if (showSetting("seed_mode")) SamplingChoiceDropdown("Seed", seedMode, listOf("auto", "fixed"), mapOf("auto" to "Generate automatically", "fixed" to "Use fixed seed")) {
                seedMode = it; setSetting("seed_mode", it)
            }
            if (seedMode == "fixed" && showSetting("seed")) {
                OutlinedTextField(seed, { seed = it; setSetting("seed", it) }, label = { Text("Fixed seed · text or 64 hex characters") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            val advancedFieldsVisible = listOf("selected_field", "count_field", "order_field", "group_field").any(::showSetting)
            if (advancedFieldsVisible) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (showAdvanced) "▼ Advanced output fields" else "▶ Advanced output fields",
                    modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced }.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            if (showAdvanced && advancedFieldsVisible) {
                if (showSetting("selected_field")) OutlinedTextField(selectedField, { selectedField = it; setSetting("selected_field", it) }, label = { Text("Selected field") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (showSetting("count_field")) OutlinedTextField(countField, { countField = it; setSetting("count_field", it) }, label = { Text("Selection count field") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (showSetting("order_field")) OutlinedTextField(orderField, { orderField = it; setSetting("order_field", it) }, label = { Text("Selection order field") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (operation == "partition" && showSetting("group_field")) {
                    OutlinedTextField(groupField, { groupField = it; setSetting("group_field", it) }, label = { Text("Group field") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                Text("Existing input columns are never overwritten silently. A field-name collision stops the run.", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Provenance records the input/population hashes, seed, RNG and sampling algorithm versions, result hash and a provenance payload hash. Chain that hash to Traceable attestation to obtain the existing TSA-backed trusted timestamp.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { runSampling() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (valuesJson.isBlank()) "Run sampling" else "Run again")
            }
        }
    }
}

@Composable
private fun SamplingCheckbox(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 3.dp)) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun SamplingChoiceDropdown(
    label: String,
    value: String,
    options: List<String>,
    labels: Map<String, String> = emptyMap(),
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = labels[value] ?: value.ifBlank { "Choose…" }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(shown) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.distinct().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(labels[option] ?: option) },
                        onClick = { expanded = false; onSelected(option) }
                    )
                }
            }
        }
    }
}
