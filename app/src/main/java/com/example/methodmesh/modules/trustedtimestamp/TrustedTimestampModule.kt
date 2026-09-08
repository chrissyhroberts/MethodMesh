package com.example.methodmesh.modules.trustedtimestamp

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object TrustedTimestampModule : MethodMeshModule {
    override val moduleId = "trustedtimestamp"
    override val displayName = "Trusted timestamp"
    override val summary = "Create portable RFC 3161 proof-of-existence bundles without uploading the source content."

    val maturityTag = TrustedTimestampContractMetadata.MATURITY
    val connectivityTag = TrustedTimestampContractMetadata.CONNECTIVITY

    override fun as100Methods() = listOf(As100TrustedTimestampMethod)

    override fun rilBindings() = listOf(
        RilBinding("prove existence", As100TrustedTimestampMethod.ID, "Create an RFC 3161 proof of existence"),
        RilBinding("trusted timestamp", As100TrustedTimestampMethod.ID, "Timestamp exact content with a trusted authority")
    )

    override fun capabilityScreens() = listOf(TrustedTimestampCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100TrustedTimestampMethod.ID to listOf(
            MethodSetting.TextSetting(
                "input_text",
                "Text to timestamp",
                defaultValue = ""
            ),
            MethodSetting.TextSetting(
                "input_file",
                "Source file URI/path (advanced)",
                defaultValue = ""
            ),
            MethodSetting.TextSetting(
                "tsa_url",
                "Timestamp authority URL",
                defaultValue = TrustedTimestampAuthorities.FREETSA.endpoint
            ),
            MethodSetting.IntSetting(
                "timeout_ms",
                "Network timeout (ms)",
                defaultValue = 10000,
                minimum = 1000,
                maximum = 30000
            ),
            MethodSetting.BooleanSetting(
                "include_full_json",
                "Expose capability metadata JSON in runtime",
                defaultValue = false
            )
        )
    )
}
