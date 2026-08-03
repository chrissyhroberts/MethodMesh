package com.example.researchos.modules.sensorfirmwareinstaller

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object SensorFirmwareInstallerModule : ResearchOSModule {
    override val moduleId = "sensorfirmwareinstaller"
    override val displayName = "ESP32 sensor framework"
    override val summary = "Install and manage bundled ResearchOS MicroPython firmware for ESP32-C3 sensor nodes."
    override fun as100Methods() = listOf(As100SensorFirmwareInstallerMethod)
    override fun rilBindings() = listOf(
        RilBinding("install sensor firmware", As100SensorFirmwareInstallerMethod.ID, "Install ResearchOS firmware to a MicroPython ESP32-C3"),
        RilBinding("flash esp32 sensor firmware", As100SensorFirmwareInstallerMethod.ID, "Install the bundled ESP32-C3 ResearchOS sensor-node firmware")
    )
    override fun capabilityScreens() = listOf(SensorFirmwareInstallerCapabilityScreen)
}
