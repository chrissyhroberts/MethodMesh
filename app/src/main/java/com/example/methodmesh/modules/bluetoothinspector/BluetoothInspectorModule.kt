package com.example.methodmesh.modules.bluetoothinspector

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding

object BluetoothInspectorModule : MethodMeshModule {
    override val moduleId = "bluetoothinspector"
    override val displayName = "Bluetooth device inspector"
    override val summary = "Discover nearby Bluetooth devices and inspect authorised BLE endpoints."
    override fun as100Methods() = listOf(As100BluetoothInspectorMethod)
    override fun rilBindings() = listOf(RilBinding("inspect bluetooth devices", As100BluetoothInspectorMethod.ID, "Discover and assay Bluetooth devices"))
    override fun capabilityScreens() = listOf(BluetoothInspectorCapabilityScreen)
}
