package com.example.methodmesh.modules.pluscodecapture

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object PlusCodeCaptureModule : MethodMeshModule {
    override val moduleId = "pluscodecapture"
    override val displayName = "Plus Code capture"
    override val summary = "Capture full offline Open Location Codes using GPS and a selectable local grid."

    override fun as100Methods() = listOf(As100PlusCodeCaptureMethod)

    override fun rilBindings() = listOf(
        RilBinding("capture plus code", As100PlusCodeCaptureMethod.ID, "Capture a full Open Location Code from GPS and a selected grid cell"),
        RilBinding("select plus code", As100PlusCodeCaptureMethod.ID, "Select a local Plus Code cell"),
        RilBinding("olc capture", As100PlusCodeCaptureMethod.ID, "Capture an offline Open Location Code")
    )

    override fun capabilityScreens() = listOf(PlusCodeCaptureCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100PlusCodeCaptureMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "basemap_mode",
                label = "Map mode",
                description = "Choose street, satellite, or grid-only context. Plus Codes are always calculated locally.",
                group = "Map",
                defaultValue = "auto",
                choices = listOf("auto", "satellite", "blank")
            ),
            MethodSetting.BooleanSetting(
                id = "allow_online_tiles",
                label = "Allow online map tiles",
                description = "Allow online street or satellite basemap tiles to load. Plus Code calculation remains local.",
                group = "Map",
                defaultValue = true
            ),
            MethodSetting.IntSetting(
                id = "code_length",
                label = "Plus Code length",
                description = "Full code precision. 10 digits is roughly compound/house scale.",
                group = "Grid",
                defaultValue = 10,
                minimum = 2,
                maximum = 10,
                step = 2
            ),
            MethodSetting.ChoiceSetting(
                id = "grid_span_cells",
                label = "Initial map width",
                description = "How many Plus Code cells to show across the selector when it opens.",
                group = "Grid",
                defaultValue = "129",
                choices = listOf("5", "9", "17", "25", "33", "65", "129")
            ),
            MethodSetting.IntSetting(
                id = "gps_average_seconds",
                label = "GPS averaging",
                description = "Seconds to average GPS fixes before centring the grid.",
                group = "GPS",
                defaultValue = 10,
                minimum = 1,
                maximum = 60,
                step = 1,
                unit = "s"
            )
        )
    )
}
