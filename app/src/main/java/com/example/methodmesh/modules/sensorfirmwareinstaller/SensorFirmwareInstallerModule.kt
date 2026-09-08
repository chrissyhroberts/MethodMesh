package com.example.methodmesh.modules.sensorfirmwareinstaller

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object SensorFirmwareInstallerModule : MethodMeshModule {
    override val moduleId = "sensorfirmwareinstaller"
    override val displayName = "ESP32 sensor framework"
    override val summary = "Install bundled MethodMesh ESP32-C3 sensor images."
    override fun as100Methods() = listOf(
        As100Esp32SensorProfileInstallMethod
    )
    override fun rilBindings() = listOf(
        RilBinding("install esp32 sensor image", As100Esp32SensorProfileInstallMethod.id, "Erase and install a complete MethodMesh ESP32-C3 sensor image")
    )
    override fun capabilityScreens() = listOf(
        Esp32SensorProfileInstallCapabilityScreen
    )
    override fun capabilitySettings() = mapOf(
        As100Esp32SensorProfileInstallMethod.id to listOf(
            MethodSetting.ChoiceSetting(
                id = "sensor_profile",
                label = "Attached sensor",
                group = "Sensor",
                defaultValue = "aht20",
                choices = listOf("aht20", "ld2410c")
            )
        )
    )
}
