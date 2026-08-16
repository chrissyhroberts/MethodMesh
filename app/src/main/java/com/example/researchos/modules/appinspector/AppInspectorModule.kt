package com.example.researchos.modules.appinspector

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

object AppInspectorModule : ResearchOSModule {
    override val moduleId = "appinspector"
    override val displayName = "Android app inspector"
    override val summary = "Inspect public app components and test authorised Android intents."
    override fun as100Methods() = listOf(As100AppInspectorMethod)
    override fun rilBindings() = listOf(RilBinding("inspect android app", As100AppInspectorMethod.ID, "Inspect public Android app interfaces"))
    override fun capabilityScreens() = listOf(AppInspectorCapabilityScreen)
    override fun capabilitySettings() = mapOf(As100AppInspectorMethod.ID to listOf(
        MethodSetting.TextSetting("package_name", "Package name", defaultValue = ""),
        MethodSetting.ChoiceSetting(
            "test_action",
            "Test intent action",
            defaultValue = "android.intent.action.MAIN",
            choices = listOf(
                "android.intent.action.MAIN",
                "android.intent.action.VIEW",
                "android.intent.action.SEND",
                "android.intent.action.SENDTO",
                "android.intent.action.GET_CONTENT",
                "android.intent.action.EDIT"
            )
        ),
        MethodSetting.TextSetting("test_uri", "Test URI", defaultValue = "")
    ))
}
