package com.example.researchos.modules.sensorread

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

object SensorReadModule : ResearchOSModule {
    override val moduleId = "sensorread"
    override val displayName = "ESP32 sensor framework"
    override val summary = "Read single, trace, or averaged measurements from registered ResearchOS BLE sensor nodes."

    override fun as100Methods() = listOf(As100SensorReadMethod)

    override fun rilBindings() = listOf(
        RilBinding("read sensor", As100SensorReadMethod.ID, "Read a registered or nearby ResearchOS sensor"),
        RilBinding("sample sensor", As100SensorReadMethod.ID, "Collect a single, trace, or averaged sensor sample")
    )

    override fun capabilityScreens() = listOf(SensorReadCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100SensorReadMethod.ID to listOf(
            MethodSetting.TextSetting("device_id", "Registered sensor", group = "Sensor", defaultValue = ""),
            MethodSetting.ChoiceSetting("sensor_read_mode", "Read mode", group = "Sampling", defaultValue = "single", choices = listOf("single", "trace", "average", "discover")),
            MethodSetting.ChoiceSetting("device_match_policy", "Device match policy", group = "Fallback", defaultValue = "fallback", choices = listOf("fallback", "strict", "any_nearby"))
        )
    )
}
