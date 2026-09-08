package com.example.methodmesh.modules.texttools

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object TextToolsModule : MethodMeshModule {
    override val moduleId = "texttools"
    override val displayName = "Text tools"
    override val summary = "Clean, transform, inspect, extract, truncate, slug, encode, split and join text locally."
    override val iconKey = "tool"

    override fun as100Methods() = listOf(
        As100TextCleanMethod,
        As100TextCaseMethod,
        As100TextReplaceMethod,
        As100TextLinesMethod,
        As100TextSplitJoinMethod,
        As100TextCountMethod,
        As100TextExtractMethod,
        As100TextTruncateMethod,
        As100TextSlugMethod,
        As100TextEncodeMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("clean text", As100TextCleanMethod.ID, "Normalize whitespace, line endings and Unicode"),
        RilBinding("change text case", As100TextCaseMethod.ID, "Change upper/lower/title/sentence/toggle case"),
        RilBinding("replace text", As100TextReplaceMethod.ID, "Find and replace literal text or regex matches"),
        RilBinding("process text lines", As100TextLinesMethod.ID, "Sort, deduplicate, filter or subset lines"),
        RilBinding("split text", As100TextSplitJoinMethod.ID, "Split or join delimited text"),
        RilBinding("count text", As100TextCountMethod.ID, "Count words, characters, lines and bytes"),
        RilBinding("extract text", As100TextExtractMethod.ID, "Extract structural parts or syntactic patterns"),
        RilBinding("truncate text", As100TextTruncateMethod.ID, "Restrict text to a deterministic length"),
        RilBinding("make text slug", As100TextSlugMethod.ID, "Create a filename- or identifier-friendly slug"),
        RilBinding("encode text", As100TextEncodeMethod.ID, "Encode or decode Base64, URL, hex or HTML entities")
    )

    override fun capabilityScreens() = listOf(
        TextCleanCapabilityScreen,
        TextCaseCapabilityScreen,
        TextReplaceCapabilityScreen,
        TextLinesCapabilityScreen,
        TextSplitJoinCapabilityScreen,
        TextCountCapabilityScreen,
        TextExtractCapabilityScreen,
        TextTruncateCapabilityScreen,
        TextSlugCapabilityScreen,
        TextEncodeCapabilityScreen
    )

    override fun capabilitySettings(): Map<String, List<MethodSetting>> = mapOf(
        As100TextCleanMethod.ID to listOf(
            textInput(),
            MethodSetting.MultiChoiceSetting(
                "operations",
                "Cleanup operations",
                defaultValue = "trim|normalize_line_endings|collapse_blank_lines",
                choices = listOf(
                    "trim",
                    "normalize_whitespace",
                    "normalize_tabs",
                    "normalize_line_endings",
                    "collapse_blank_lines",
                    "remove_blank_lines",
                    "trim_lines"
                )
            ),
            MethodSetting.ChoiceSetting(
                "unicode_normalization",
                "Unicode normalization",
                defaultValue = "none",
                choices = listOf("none", "nfc", "nfd", "nfkc", "nfkd")
            )
        ),
        As100TextCaseMethod.ID to listOf(
            textInput(),
            MethodSetting.ChoiceSetting(
                "mode",
                "Case",
                defaultValue = "lowercase",
                choices = listOf("lowercase", "uppercase", "title_case", "sentence_case", "toggle_case")
            ),
            MethodSetting.ChoiceSetting(
                "locale_policy",
                "Locale policy",
                description = "ROOT is reproducible across devices; device uses the phone locale.",
                defaultValue = "root",
                choices = listOf("root", "device")
            )
        ),
        As100TextReplaceMethod.ID to listOf(
            textInput(),
            MethodSetting.TextSetting("search", "Find", defaultValue = ""),
            MethodSetting.TextSetting("replacement", "Replace with", defaultValue = ""),
            MethodSetting.ChoiceSetting("mode", "Search mode", defaultValue = "literal", choices = listOf("literal", "regex")),
            MethodSetting.ChoiceSetting("scope", "Replace", defaultValue = "all", choices = listOf("all", "first")),
            MethodSetting.BooleanSetting("case_sensitive", "Case sensitive", defaultValue = true)
        ),
        As100TextLinesMethod.ID to listOf(
            textInput(),
            MethodSetting.ChoiceSetting(
                "operation",
                "Line operation",
                defaultValue = "deduplicate_preserve_order",
                choices = listOf(
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
            ),
            MethodSetting.BooleanSetting("trim_lines", "Trim each line", defaultValue = true),
            MethodSetting.BooleanSetting("case_sensitive", "Case sensitive", defaultValue = true),
            MethodSetting.IntSetting("n", "Number of lines", defaultValue = 10, minimum = 0, maximum = 100000)
        ),
        As100TextSplitJoinMethod.ID to listOf(
            textInput(),
            MethodSetting.ChoiceSetting("operation", "Operation", defaultValue = "split", choices = listOf("split", "join")),
            MethodSetting.ChoiceSetting(
                "delimiter",
                "Delimiter",
                defaultValue = "comma",
                choices = listOf("comma", "comma_space", "semicolon", "tab", "pipe", "space", "newline", "custom")
            ),
            MethodSetting.TextSetting("custom_delimiter", "Custom delimiter", defaultValue = ""),
            MethodSetting.BooleanSetting("trim_values", "Trim values", defaultValue = true),
            MethodSetting.BooleanSetting("discard_empty", "Discard empty values", defaultValue = true)
        ),
        As100TextCountMethod.ID to listOf(textInput()),
        As100TextExtractMethod.ID to listOf(
            textInput(),
            MethodSetting.ChoiceSetting(
                "operation",
                "Extraction",
                defaultValue = "pattern",
                choices = listOf(
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
            ),
            MethodSetting.ChoiceSetting(
                "pattern",
                "Pattern",
                defaultValue = "email",
                choices = listOf("email", "url", "ipv4", "number", "integer", "decimal", "custom_regex")
            ),
            MethodSetting.TextSetting("regex", "Custom regular expression", defaultValue = ""),
            MethodSetting.TextSetting("start_marker", "Start / marker", defaultValue = ""),
            MethodSetting.TextSetting("end_marker", "End marker", defaultValue = ""),
            MethodSetting.IntSetting("n", "How many", defaultValue = 1, minimum = 0, maximum = 100000)
        ),
        As100TextTruncateMethod.ID to listOf(
            textInput(),
            MethodSetting.IntSetting("limit", "Limit", defaultValue = 100, minimum = 0, maximum = 10000000),
            MethodSetting.ChoiceSetting("unit", "Unit", defaultValue = "characters", choices = listOf("characters", "words", "lines", "utf8_bytes")),
            MethodSetting.ChoiceSetting("retain", "Keep", defaultValue = "start", choices = listOf("start", "end")),
            MethodSetting.ChoiceSetting("suffix", "Truncation marker", defaultValue = "ellipsis", choices = listOf("none", "ellipsis", "custom")),
            MethodSetting.TextSetting("custom_suffix", "Custom marker", defaultValue = "")
        ),
        As100TextSlugMethod.ID to listOf(
            textInput(),
            MethodSetting.ChoiceSetting("separator", "Separator", defaultValue = "hyphen", choices = listOf("hyphen", "underscore")),
            MethodSetting.BooleanSetting("lowercase", "Lowercase", defaultValue = true),
            MethodSetting.BooleanSetting("ascii_only", "ASCII only", description = "Remove characters that cannot be represented as ASCII.", defaultValue = true)
        ),
        As100TextEncodeMethod.ID to listOf(
            textInput(),
            MethodSetting.ChoiceSetting(
                "operation",
                "Encoding",
                defaultValue = "base64_encode",
                choices = listOf(
                    "base64_encode",
                    "base64_decode",
                    "url_encode",
                    "url_decode",
                    "hex_encode",
                    "hex_decode",
                    "html_encode",
                    "html_decode"
                )
            )
        )
    )

    private fun textInput() = MethodSetting.TextSetting(
        "text",
        "Text",
        description = "Runtime text. ODK callers supply this as input_text.",
        defaultValue = ""
    )
}
