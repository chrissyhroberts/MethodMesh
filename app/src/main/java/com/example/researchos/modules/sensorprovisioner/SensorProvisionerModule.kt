package com.example.researchos.modules.sensorprovisioner

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

object SensorProvisionerModule : ResearchOSModule {
    override val moduleId = "sensorprovisioner"
    override val displayName = "ESP32 sensor framework"
    override val summary = "Provision ResearchOS ESP32-C3 BLE sensor nodes and save them into the device registry."

    override fun as100Methods() = listOf(As100SensorProvisionerMethod)

    override fun rilBindings() = listOf(
        RilBinding("provision sensor node", As100SensorProvisionerMethod.ID, "Configure a ResearchOS BLE sensor node"),
        RilBinding("configure environmental sensor", As100SensorProvisionerMethod.ID, "Set device identity and sampling interval for a ResearchOS sensor")
    )

    override fun capabilityScreens() = listOf(SensorProvisionerCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100SensorProvisionerMethod.ID to listOf(
            MethodSetting.TextSetting("sensor_device_id", "Device ID", group = "Provisioning", defaultValue = "clinic_room_01_sensor"),
            MethodSetting.TextSetting("sensor_device_name", "Display name", group = "Provisioning", defaultValue = "Clinic room 01 sensor"),
            MethodSetting.TextSetting("sensor_profile", "Sensor profile", group = "Provisioning", defaultValue = "aht20"),
            MethodSetting.IntSetting("sensor_sample_interval_ms", "Sample interval (ms)", group = "Provisioning", defaultValue = 60000, minimum = 1000, maximum = 3600000, step = 1000, unit = "ms")
        )
    )
}
