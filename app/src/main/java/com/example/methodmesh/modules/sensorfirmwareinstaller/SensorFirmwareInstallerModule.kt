package com.example.methodmesh.modules.sensorfirmwareinstaller

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object SensorFirmwareInstallerModule : MethodMeshModule {
    override val moduleId = "sensorfirmwareinstaller"
    override val displayName = "ESP32 sensor framework"
    override val summary = "Install and manage bundled MethodMesh MicroPython firmware for ESP32-C3 sensor nodes."
    override fun as100Methods() = listOf(
        As100Esp32BoardWipeMethod,
        As100Esp32RuntimeInstallMethod,
        As100Esp32SensorProfileInstallMethod
    )
    override fun rilBindings() = listOf(
        RilBinding("wipe esp32 board", As100Esp32BoardWipeMethod.id, "Erase an ESP32-C3 and verify old firmware is gone"),
        RilBinding("install methodmesh esp32 runtime", As100Esp32RuntimeInstallMethod.id, "Write the bundled board-level MicroPython image"),
        RilBinding("install esp32 sensor profile", As100Esp32SensorProfileInstallMethod.id, "Install or replace the configured ESP32 sensor profile")
    )
    override fun capabilityScreens() = listOf(
        Esp32BoardWipeCapabilityScreen,
        Esp32RuntimeInstallCapabilityScreen,
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
