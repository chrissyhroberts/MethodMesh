package com.example.methodmesh.modules.countertracker

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object CounterTrackerModule : MethodMeshModule {
    override val moduleId = "countertracker"
    override val displayName = "Counter / Tracker"
    override val summary = "Interactive named counters, HP/EXP/resource values, status flags and snapshot capture."
    override val iconKey = "counter"

    override fun as100Methods() = listOf(As100CounterTrackerMethod)
    override fun rilBindings() = listOf(
        RilBinding("open counter tracker", As100CounterTrackerMethod.ID, "Open the counter and status workspace"),
        RilBinding("capture counter snapshot", As100CounterTrackerMethod.ID, "Capture current counter/status state")
    )
    override fun capabilityScreens() = listOf(CounterTrackerCapabilityScreen)
    override fun capabilitySettings() = mapOf(
        As100CounterTrackerMethod.ID to listOf(
            MethodSetting.BooleanSetting("persist_state", "Persist workspace", defaultValue = true),
            MethodSetting.BooleanSetting("persist_history", "Keep change history", defaultValue = false),
            MethodSetting.BooleanSetting("interactive_capture", "Interactive capture", defaultValue = true),
            MethodSetting.TextSetting("snapshot_json", "Snapshot JSON", defaultValue = "")
        )
    )
}
