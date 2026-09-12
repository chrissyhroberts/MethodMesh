package com.example.methodmesh.modules.sensorfirmwareinstaller

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object SensorFirmwareInstallerModule : MethodMeshModule {
    override val moduleId = "sensorfirmwareinstaller"
    override val displayName = "ESP32 sensor framework"
    override val summary = "Install bundled MethodMesh ESP32-C3 sensor and ESP-NOW node images."

    override fun as100Methods() = listOf(As100Esp32SensorProfileInstallMethod)

    override fun rilBindings() = listOf(
        RilBinding("install esp32 sensor image", As100Esp32SensorProfileInstallMethod.id, "Erase and install a complete MethodMesh ESP32-C3 image"),
        RilBinding("install esp32 image", As100Esp32SensorProfileInstallMethod.id, "Choose and install a bundled MethodMesh ESP32-C3 image"),
        RilBinding("install esp mesh node", As100Esp32SensorProfileInstallMethod.id, "Choose the ESP-NOW mesh-node image in the MethodMesh ESP32 installer")
    )

    override fun capabilityScreens() = listOf(Esp32SensorProfileInstallCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100Esp32SensorProfileInstallMethod.id to listOf(
            MethodSetting.ChoiceSetting(
                id = "sensor_profile",
                label = "ESP32 image",
                group = "Firmware",
                defaultValue = "aht20",
                choices = listOf("aht20", "ld2410c", "espnow_mesh")
            )
        )
    )
}
