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
            MethodSetting.TextSetting("device_id", "Device ID", group = "Sensor", defaultValue = ""),
            MethodSetting.TextSetting("sensor_id", "Sensor ID", group = "Sensor", defaultValue = ""),
            MethodSetting.TextSetting("sensor_profile", "Sensor profile", group = "Sensor", defaultValue = ""),
            MethodSetting.TextSetting("sensor_read_mode", "Read mode", group = "Sampling", defaultValue = "single"),
            MethodSetting.IntSetting("duration_seconds", "Duration", group = "Sampling", defaultValue = 30, minimum = 1, maximum = 3600, step = 1, unit = "s"),
            MethodSetting.IntSetting("sample_interval_seconds", "Sample interval", group = "Sampling", defaultValue = 5, minimum = 1, maximum = 3600, step = 1, unit = "s"),
            MethodSetting.TextSetting("device_match_policy", "Device match policy", group = "Fallback", defaultValue = "fallback")
        )
    )
}
