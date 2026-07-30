package com.example.researchos.modules.bluetoothinspector

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object BluetoothInspectorModule : ResearchOSModule {
    override val moduleId = "bluetoothinspector"
    override val displayName = "Bluetooth device inspector"
    override val summary = "Discover nearby Bluetooth devices and inspect authorised BLE endpoints."
    override fun as100Methods() = listOf(As100BluetoothInspectorMethod)
    override fun rilBindings() = listOf(RilBinding("inspect bluetooth devices", As100BluetoothInspectorMethod.ID, "Discover and assay Bluetooth devices"))
    override fun capabilityScreens() = listOf(BluetoothInspectorCapabilityScreen)
}
