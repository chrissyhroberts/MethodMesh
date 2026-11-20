package com.example.methodmesh.modules.screentools

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object ScreenToolsModule : MethodMeshModule {
    override val moduleId = "screentools"
    override val displayName = "Screen Tools"
    override val version = "0.1.0"
    override val summary = "Capture a user-authorised screen and read text from volatile frames."
    override val iconKey = "document"

    override fun as100Methods() = listOf(ScreenCaptureMethod, ScreenOcrMethod, ScreenVisualSelectMethod, ScreenMemoryMethod)

    override fun rilBindings() = listOf(
        RilBinding("capture screen", ScreenCaptureMethod.ID, "Capture the permitted visible screen into volatile memory")
        ,RilBinding("read screen text", ScreenOcrMethod.ID, "Recognise text and word regions from a captured frame")
        ,RilBinding("select screen regions", ScreenVisualSelectMethod.ID, "Name normalized regions on a captured frame")
        ,RilBinding("remember screen regions", ScreenMemoryMethod.ID, "Encrypt and version reusable visual selections")
    )

    override fun capabilityScreens() = listOf(ScreenCaptureCapabilityScreen, ScreenOcrCapabilityScreen, ScreenVisualSelectCapabilityScreen, ScreenMemoryCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        ScreenCaptureMethod.ID to listOf(
            MethodSetting.BooleanSetting(
                id = "clear_previous_frames",
                label = "Clear previous frames",
                description = "Start a new volatile capture session.",
                defaultValue = true
            )
        ),
        ScreenOcrMethod.ID to listOf(
            MethodSetting.TextSetting(
                id = "frame_ref",
                label = "Frame reference",
                description = "Optional screen-session reference; blank uses the latest volatile frame.",
                defaultValue = ""
            )
        ),
        ScreenVisualSelectMethod.ID to listOf(
            MethodSetting.TextSetting(
                id = "frame_ref",
                label = "Frame reference",
                description = "Optional screen-session reference; blank uses the latest volatile frame.",
                defaultValue = ""
            )
        ),
        ScreenMemoryMethod.ID to listOf(
            MethodSetting.TextSetting("memory_id", "Selection memory ID", defaultValue = ""),
            MethodSetting.TextSetting("memory_name", "Selection memory name", defaultValue = "Screen selections")
        )
    )
}
