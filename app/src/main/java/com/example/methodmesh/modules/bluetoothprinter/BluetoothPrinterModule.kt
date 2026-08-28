package com.example.methodmesh.modules.bluetoothprinter

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding

object BluetoothPrinterModule : MethodMeshModule {
    override val moduleId = "bluetoothprinter"
    override val displayName = "Bluetooth printer"
    override val summary = "Send a text or raw thermal payload to a paired Bluetooth printer."
    override fun as100Methods() = listOf(As100BluetoothPrinterMethod)
    override fun rilBindings() = listOf(RilBinding("print over Bluetooth", As100BluetoothPrinterMethod.ID, "Send a print payload to a paired Bluetooth thermal printer"))
    override fun capabilityScreens() = listOf(BluetoothPrinterCapabilityScreen)
}
