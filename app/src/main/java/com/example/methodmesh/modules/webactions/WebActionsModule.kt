package com.example.methodmesh.modules.webactions

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object WebActionsModule : MethodMeshModule {
    override val moduleId = "webactions"
    override val displayName = "Web Actions"
    override val summary = "Run web workflows and fetch useful online data streams, returning complete JSON to ODK or readable results for people."
    override val iconKey = "web"

    override fun as100Methods() = listOf(
        As100WebActionsDashboardMethod,
        As100WebActionsWorkflowsDashboardMethod,
        As100OdkWebFormsRoundtripMethod,
        As100OdkEnketoRoundtripMethod,
        As100KoboEnketoRoundtripMethod,
        As100OdkCentralRoundtripMethod,
        As100EnketoRoundtripMethod,
        As100WebRoundtripMethod,
        As100WebOpenMethod
    ) + WebApiMethods.all

    override fun rilBindings() = listOf(
        RilBinding("open web actions dashboard", As100WebActionsDashboardMethod.ID, "Choose any Web Actions workflow or online data capability"),
        RilBinding("open web workflows dashboard", As100WebActionsWorkflowsDashboardMethod.ID, "Choose an ODK form, Enketo session, web workflow, or browser page"),
        RilBinding(
            "complete ODK Web Forms form",
            As100OdkWebFormsRoundtripMethod.ID,
            "Paste an ODK Central Web Forms link, complete the submission, and return after confirmed submission"
        ),
        RilBinding(
            "complete ODK Enketo form",
            As100OdkEnketoRoundtripMethod.ID,
            "Paste an ODK Central link for a form configured to use Enketo, complete the submission, and return after confirmed submission"
        ),
        RilBinding(
            "complete Kobo Enketo form",
            As100KoboEnketoRoundtripMethod.ID,
            "Paste a KoboToolbox Enketo link, complete the submission, and return after confirmed submission"
        ),
        RilBinding(
            "complete ODK Central form",
            As100OdkCentralRoundtripMethod.ID,
            "Compatibility route for older saved ODK Central web-form presets"
        ),
        RilBinding(
            "create precooked Enketo session",
            As100EnketoRoundtripMethod.ID,
            "Advanced: create a fresh Enketo single-submit session with runtime prefill and return after explicit completion"
        ),
        // Development compatibility alias: v0.03 exposed this advanced
        // capability as web.enketo_roundtrip. canonicalAction() also resolves
        // RIL phrases, so old presets/intents continue to route to the renamed
        // precooked capability without keeping the misleading ID in the catalogue.
        RilBinding(
            "web.enketo_roundtrip",
            As100EnketoRoundtripMethod.ID,
            "Compatibility alias for the v0.03 precooked Enketo capability ID"
        ),
        RilBinding("run web roundtrip", As100WebRoundtripMethod.ID, "Run a web workflow with a one-shot return URL"),
        RilBinding("open web page", As100WebOpenMethod.ID, "Open an HTTP or HTTPS page in the Android browser")
    ) + WebApiMethods.all.map { method ->
        RilBinding(
            "fetch ${method.definition.name.lowercase()}",
            method.id,
            "Fetch the complete ${method.definition.name} data stream"
        )
    }

    override fun capabilityScreens() = listOf(
        WebActionsDashboardCapabilityScreen,
        WebActionsWorkflowsDashboardCapabilityScreen,
        OdkWebFormsRoundtripCapabilityScreen,
        OdkEnketoRoundtripCapabilityScreen,
        KoboEnketoRoundtripCapabilityScreen,
        OdkCentralRoundtripCapabilityScreen,
        EnketoRoundtripCapabilityScreen,
        WebRoundtripCapabilityScreen,
        WebOpenCapabilityScreen
    ) + WebApiCapabilityScreens.all

    override fun capabilitySettings() = mapOf(
        As100OdkCentralRoundtripMethod.ID to listOf(
            MethodSetting.TextSetting(
                id = "url",
                label = "Central web-form link",
                description = "Paste the link copied from ODK Central. Public Access and Data Collector links are supported.",
                defaultValue = "",
                group = "ODK Central"
            ),
            MethodSetting.IntSetting(
                id = "timeout_seconds",
                label = "Timeout",
                description = "0 waits indefinitely.",
                defaultValue = 0,
                minimum = 0,
                maximum = 86400,
                step = 60,
                unit = "s",
                group = "Session"
            ),
            MethodSetting.BooleanSetting(
                id = "allow_insecure_http",
                label = "Allow HTTP",
                description = "Off by default. Enable only for a trusted local/test Central deployment.",
                defaultValue = false,
                group = "Security"
            ),
            MethodSetting.BooleanSetting(
                id = "disposable_online_session",
                label = "Disposable online session",
                description = "Removes Central offline/cached-form routes, adds single-submit return controls, and clears browser storage on exit.",
                defaultValue = true,
                group = "Session"
            ),
            MethodSetting.BooleanSetting(
                id = "cache_buster",
                label = "Fresh browser URL",
                description = "Adds a per-run MethodMesh query value so the WebView/provider does not reuse a cached form page.",
                defaultValue = true,
                group = "Session"
            )
        ),
        As100OdkWebFormsRoundtripMethod.ID to hostedFormSettings(
            label = "ODK Web Forms link",
            description = "Paste an ODK Central Web Forms link. Public Access and Data Collector links are supported.",
            group = "ODK Web Forms"
        ),
        As100OdkEnketoRoundtripMethod.ID to hostedFormSettings(
            label = "ODK Enketo link",
            description = "Paste an ODK Central Public Access link for a form configured to use Enketo. Legacy /-/ links are also supported.",
            group = "ODK Enketo"
        ),
        As100KoboEnketoRoundtripMethod.ID to hostedFormSettings(
            label = "Kobo Enketo link",
            description = "Paste a KoboToolbox Enketo link, usually containing /x/ or /single/.",
            group = "Kobo Enketo"
        ),
        As100EnketoRoundtripMethod.ID to listOf(
            MethodSetting.TextSetting(
                id = "api_base_url",
                label = "Enketo API base URL",
                description = "Usually ends in /api/v2.",
                defaultValue = "https://enke.to/api/v2",
                group = "Enketo"
            ),
            MethodSetting.TextSetting(
                id = "server_url",
                label = "Form server URL",
                defaultValue = "",
                group = "Enketo"
            ),
            MethodSetting.TextSetting(
                id = "form_id",
                label = "Form ID",
                defaultValue = "",
                group = "Enketo"
            ),
            MethodSetting.ChoiceSetting(
                id = "single_mode",
                label = "Submission mode",
                defaultValue = "single",
                choices = listOf("single", "single_once"),
                group = "Enketo"
            ),
            MethodSetting.TextSetting(
                id = "prefill_bindings_json",
                label = "Prefill mappings",
                description = "Flat JSON mapping form node paths to fixed text or {{runtime_field}} values.",
                defaultValue = "{}",
                group = "Prefill"
            ),
            MethodSetting.TextSetting(
                id = "defaults_json",
                label = "Literal defaults JSON",
                description = "Backwards-compatible flat JSON object mapping XPath/node paths to literal values.",
                defaultValue = "{}",
                group = "Prefill"
            ),
            MethodSetting.TextSetting(
                id = "theme",
                label = "Theme",
                description = "Optional Enketo theme override.",
                defaultValue = "",
                group = "Display"
            ),
            MethodSetting.IntSetting(
                id = "timeout_seconds",
                label = "Timeout",
                description = "0 waits indefinitely.",
                defaultValue = 0,
                minimum = 0,
                maximum = 86400,
                step = 60,
                unit = "s",
                group = "Session"
            ),
            MethodSetting.BooleanSetting(
                id = "allow_insecure_http",
                label = "Allow HTTP",
                description = "Off by default. Enable only for a trusted local/test deployment.",
                defaultValue = false,
                group = "Security"
            ),
            MethodSetting.BooleanSetting(
                id = "cache_buster",
                label = "Fresh browser URL",
                description = "Adds a per-run MethodMesh query value to the issued Enketo URL before opening it.",
                defaultValue = true,
                group = "Session"
            )
        ),
        As100WebRoundtripMethod.ID to listOf(
            MethodSetting.TextSetting(
                id = "url",
                label = "Workflow URL",
                defaultValue = "",
                group = "Web workflow"
            ),
            MethodSetting.TextSetting(
                id = "callback_parameter",
                label = "Return URL parameter",
                description = "Query parameter used when no placeholder is present.",
                defaultValue = "return_url",
                group = "Completion"
            ),
            MethodSetting.TextSetting(
                id = "callback_placeholder",
                label = "Return URL placeholder",
                description = "If present in the URL, this literal is replaced with the MethodMesh return URL.",
                defaultValue = "{METHODMESH_RETURN_URL}",
                group = "Completion"
            ),
            MethodSetting.IntSetting(
                id = "timeout_seconds",
                label = "Timeout",
                description = "0 waits indefinitely.",
                defaultValue = 0,
                minimum = 0,
                maximum = 86400,
                step = 60,
                unit = "s",
                group = "Session"
            ),
            MethodSetting.BooleanSetting(
                id = "allow_insecure_http",
                label = "Allow HTTP",
                description = "Off by default. Enable only for a trusted local/test deployment.",
                defaultValue = false,
                group = "Security"
            )
        ),
        As100WebOpenMethod.ID to listOf(
            MethodSetting.TextSetting(
                id = "url",
                label = "Web address",
                defaultValue = "",
                group = "Page"
            ),
            MethodSetting.BooleanSetting(
                id = "allow_insecure_http",
                label = "Allow HTTP",
                description = "Off by default. Enable only for a trusted local/test deployment.",
                defaultValue = false,
                group = "Security"
            )
        )
    ) + WebApiMethods.all.associate { method -> method.id to webApiSettings(method) }

    private fun webApiSettings(method: WebApiMethod): List<MethodSetting> = buildList {
        if (method.definition.inputs.any { it.id == "latitude" } && method.definition.inputs.any { it.id == "longitude" }) {
            add(
                MethodSetting.ChoiceSetting(
                    id = "location_mode",
                    label = "Location source",
                    description = "Choose current GPS or enter latitude and longitude manually. GPS never prevents manual entry.",
                    defaultValue = "manual",
                    choices = listOf("gps", "manual"),
                    group = "Location"
                )
            )
        }
        method.definition.inputs.forEach { input ->
            add(
                MethodSetting.TextSetting(
                    id = input.id,
                    label = input.name,
                    description = input.description.ifBlank { "Input sent to ${method.definition.attribution.providerName}." },
                    defaultValue = input.defaultValue,
                    group = "Request"
                )
            )
        }
        add(
            MethodSetting.MultiChoiceSetting(
                id = "result_paths",
                label = "Returned fields",
                description = "Useful values shown in the compact result. The full response remains available in methodmesh_full_json for ODK.",
                defaultValue = method.definition.response.expectedPaths.joinToString("|"),
                choices = method.definition.response.expectedPaths,
                delimiter = "|",
                group = "Return"
            )
        )
        add(
            MethodSetting.TextSetting(
                id = "fallback_value",
                label = "Fallback value",
                description = "Optional value if a selected field is missing.",
                defaultValue = "",
                group = "Return"
            )
        )
    }


    private fun hostedFormSettings(label: String, description: String, group: String) = listOf(
        MethodSetting.TextSetting(
            id = "url",
            label = label,
            description = description,
            defaultValue = "",
            group = group
        ),
        MethodSetting.IntSetting(
            id = "timeout_seconds",
            label = "Timeout",
            description = "0 waits indefinitely.",
            defaultValue = 0,
            minimum = 0,
            maximum = 86400,
            step = 60,
            unit = "s",
            group = "Session"
        ),
        MethodSetting.BooleanSetting(
            id = "allow_insecure_http",
            label = "Allow HTTP",
            description = "Off by default. Enable only for a trusted local/test deployment.",
            defaultValue = false,
            group = "Security"
        ),
        MethodSetting.BooleanSetting(
            id = "disposable_online_session",
            label = "Disposable online session",
            description = "Clears draft/cache state on exit and keeps the hosted form as a one-run session.",
            defaultValue = true,
            group = "Session"
        ),
        MethodSetting.BooleanSetting(
            id = "cache_buster",
            label = "Fresh browser URL",
            description = "Adds a per-run MethodMesh query value where the provider route can safely accept it.",
            defaultValue = true,
            group = "Session"
        ),
        MethodSetting.BooleanSetting(
            id = "chrome_user_agent",
            label = "Chrome browser identity",
            description = "For ODK Enketo troubleshooting: present the WebView as Chrome rather than Android WebView.",
            defaultValue = true,
            group = "Session"
        )
    )

}
