package com.example.methodmesh.modules.sensorfirmwareinstaller

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding

object SensorFirmwareInstallerModule : MethodMeshModule {
    override val moduleId = "sensorfirmwareinstaller"
    override val displayName = "ESP32 sensor framework"
    override val summary = "Install and manage bundled MethodMesh MicroPython firmware for ESP32-C3 sensor nodes."
    override fun as100Methods() = listOf(As100SensorFirmwareInstallerMethod)
    override fun rilBindings() = listOf(
        RilBinding("install sensor firmware", As100SensorFirmwareInstallerMethod.ID, "Install MethodMesh firmware to a MicroPython ESP32-C3"),
        RilBinding("flash esp32 sensor firmware", As100SensorFirmwareInstallerMethod.ID, "Install the bundled ESP32-C3 MethodMesh sensor-node firmware")
    )
    override fun capabilityScreens() = listOf(SensorFirmwareInstallerCapabilityScreen)
}
