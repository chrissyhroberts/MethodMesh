package com.example.methodmesh.modules.sensorread

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object SensorReadModule : MethodMeshModule {
    override val moduleId = "sensorread"
    override val displayName = "ESP32 sensor framework"
    override val summary = "Read single, trace, averaged, or interactive live measurements from registered MethodMesh BLE sensor nodes."

    override fun as100Methods() = listOf(As100SensorReadMethod)

    override fun rilBindings() = listOf(
        RilBinding("read sensor", As100SensorReadMethod.ID, "Read a registered or nearby MethodMesh sensor"),
        RilBinding("sample sensor", As100SensorReadMethod.ID, "Collect a single, trace, or averaged sensor sample")
    )

    override fun capabilityScreens() = listOf(SensorReadCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100SensorReadMethod.ID to listOf(
            MethodSetting.TextSetting(
                "device_id",
                "Registered sensor",
                group = "Sensor",
                defaultValue = ""
            ),
            MethodSetting.ChoiceSetting(
                "sensor_read_mode",
                "Read mode",
                group = "Sampling",
                defaultValue = "single",
                choices = listOf("single", "trace", "average", "discover")
            ),
            MethodSetting.ChoiceSetting(
                "device_match_policy",
                "Device match policy",
                group = "Fallback",
                defaultValue = "fallback",
                choices = listOf("fallback", "strict", "any_nearby")
            )
        )
    )
}
