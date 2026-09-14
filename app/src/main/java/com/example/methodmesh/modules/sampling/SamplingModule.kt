package com.example.methodmesh.modules.sampling

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleDependency
import com.example.methodmesh.modules.ModuleExample
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object SamplingModule : MethodMeshModule {
    override val moduleId = "sampling"
    override val displayName = "Sampling"
    override val summary = "Build a population, sample or randomise it reproducibly, and return auditable CSV/JSON results."

    override fun as100Methods() = listOf(As100SamplingMethod)

    override fun rilBindings() = listOf(
        RilBinding("sample items", As100SamplingMethod.ID, "Randomly select records from a population"),
        RilBinding("random sample", As100SamplingMethod.ID, "Create a reproducible random sample"),
        RilBinding("shuffle items", As100SamplingMethod.ID, "Randomly permute a population"),
        RilBinding("partition items", As100SamplingMethod.ID, "Randomly divide a population into groups")
    )

    override fun capabilityScreens() = listOf(SamplingCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100SamplingMethod.ID to listOf(
            MethodSetting.ChoiceSetting("source_type", "Population source", defaultValue = "manual", choices = listOf("manual", "csv", "sequence", "random_words")),
            MethodSetting.TextSetting("manual_items", "Population items", defaultValue = ""),
            // File/inline CSV are runtime population inputs. File selection is owned by the
            // capability screen; csv_text is primarily for intent/piped callers.
            MethodSetting.TextSetting("csv_uri", "CSV file URI", defaultValue = ""),
            MethodSetting.TextSetting("csv_text", "Inline CSV text", defaultValue = ""),
            MethodSetting.ChoiceSetting("manual_separator", "Manual list separator", defaultValue = "newline", choices = listOf("newline", "pipe", "comma", "semicolon")),
            MethodSetting.FloatSetting("sequence_start", "Sequence start", defaultValue = 1f),
            MethodSetting.FloatSetting("sequence_end", "Sequence end", defaultValue = 100f),
            MethodSetting.FloatSetting("sequence_step", "Sequence step", defaultValue = 1f),
            MethodSetting.IntSetting("word_count", "Number of random words", defaultValue = 8, minimum = 1, maximum = 100000),
            MethodSetting.IntSetting("word_min_length", "Minimum word length", defaultValue = 3, minimum = 1, maximum = 30),
            MethodSetting.IntSetting("word_max_length", "Maximum word length", defaultValue = 12, minimum = 1, maximum = 30),
            MethodSetting.BooleanSetting("word_unique", "Unique random words", defaultValue = true),

            MethodSetting.ChoiceSetting(
                "operation", "Operation", defaultValue = "simple_sample",
                choices = listOf("simple_sample", "shuffle", "weighted_sample", "stratified_sample", "systematic_sample", "partition", "population_only")
            ),
            MethodSetting.ChoiceSetting("sample_mode", "Sample size mode", defaultValue = "n", choices = listOf("n", "fraction")),
            MethodSetting.IntSetting("sample_size", "Sample size", defaultValue = 1, minimum = 0, maximum = 100000),
            MethodSetting.FloatSetting("sample_fraction", "Sample fraction", defaultValue = 0.1f, minimum = 0f, maximum = 1f),
            MethodSetting.BooleanSetting("replacement", "Sampling with replacement", defaultValue = false),
            MethodSetting.ChoiceSetting("output_order", "Output order", defaultValue = "draw", choices = listOf("draw", "input", "sorted")),
            MethodSetting.TextSetting("sort_field", "Sort field", defaultValue = ""),

            MethodSetting.TextSetting("id_field", "Identifier field", defaultValue = "item_id"),
            MethodSetting.TextSetting("label_field", "Label field", defaultValue = "item_label"),
            MethodSetting.TextSetting("weight_field", "Weight field", defaultValue = "weight"),
            MethodSetting.TextSetting("stratum_field", "Stratum field", defaultValue = "stratum"),
            MethodSetting.TextSetting("eligibility_field", "Eligibility field", defaultValue = "eligible"),

            MethodSetting.ChoiceSetting(
                "stratum_allocation", "Stratum allocation", defaultValue = "proportional_total",
                choices = listOf("proportional_total", "equal_n_per_stratum", "specified")
            ),
            MethodSetting.TextSetting("stratum_sizes", "Specified stratum sizes", defaultValue = ""),
            MethodSetting.IntSetting("partition_groups", "Number of groups", defaultValue = 2, minimum = 1, maximum = 10000),
            MethodSetting.FloatSetting("systematic_interval", "Systematic interval", defaultValue = 0f, minimum = 0f),

            MethodSetting.ChoiceSetting("output_mode", "Output records", defaultValue = "annotated", choices = listOf("annotated", "selected_only")),
            MethodSetting.ChoiceSetting("output_format", "Output format", defaultValue = "csv", choices = listOf("csv", "json")),
            MethodSetting.TextSetting("selected_field", "Selected output field", defaultValue = "sampling_selected"),
            MethodSetting.TextSetting("count_field", "Selection count output field", defaultValue = "sampling_count"),
            MethodSetting.TextSetting("order_field", "Selection order output field", defaultValue = "sampling_order"),
            MethodSetting.TextSetting("group_field", "Group output field", defaultValue = "sampling_group"),

            MethodSetting.ChoiceSetting("seed_mode", "Seed mode", defaultValue = "auto", choices = listOf("auto", "fixed")),
            MethodSetting.TextSetting("seed", "Fixed seed", defaultValue = ""),

            // ODK/intent runtime input. JSON array of records; not normally shown in native use.
            MethodSetting.TextSetting("sampling_items_json", "Structured population JSON", defaultValue = "")
        )
    )

    override fun dependencies() = listOf(
        ModuleDependency(
            "attestation",
            "Sampling returns sampling_provenance_payload_sha256. Chain this to attestation.create as event_payload_hash to obtain the existing TSA-backed trusted timestamp and signed provenance record."
        )
    )

    override fun examples() = listOf(
        ModuleExample(
            title = "Select one eligible household member",
            ril = "WHAT; sample items; RESULT; return sampling_selected_id, sampling_audit_json; format json",
            notes = "Use a structured roster, sample n=1 without replacement, then optionally chain sampling_provenance_payload_sha256 to attestation.create."
        ),
        ModuleExample(
            title = "Sample laboratory specimens",
            ril = "WHAT; random sample; RESULT; return sampling_result_uri, sampling_audit_json; format json",
            notes = "Native CSV input preserves the manifest and appends configurable sampling fields."
        ),
        ModuleExample(
            title = "Shuffle a specimen processing order",
            ril = "WHAT; shuffle items; RESULT; return sampling_result_uri, sampling_audit_json; format json"
        )
    )
}
