package com.example.methodmesh.modules.webactions

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object WebActionsModule : MethodMeshModule {
    override val moduleId = "webactions"
    override val displayName = "Web Actions"
    override val summary = "Run ODK Central and other web workflows with explicit completion; create precooked Enketo sessions when runtime prefill is needed."
    override val iconKey = "web"

    override fun as100Methods() = listOf(
        As100OdkCentralRoundtripMethod,
        As100EnketoRoundtripMethod,
        As100WebRoundtripMethod,
        As100WebOpenMethod
    )

    override fun rilBindings() = listOf(
        RilBinding(
            "complete ODK Central form",
            As100OdkCentralRoundtripMethod.ID,
            "Paste a Central web-form link, complete the submission, and return after Central's explicit return URL"
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
    )

    override fun capabilityScreens() = listOf(
        OdkCentralRoundtripCapabilityScreen,
        EnketoRoundtripCapabilityScreen,
        WebRoundtripCapabilityScreen,
        WebOpenCapabilityScreen
    )

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
            )
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
    )
}
