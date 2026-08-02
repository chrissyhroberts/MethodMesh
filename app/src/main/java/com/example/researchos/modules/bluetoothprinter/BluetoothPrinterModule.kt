package com.example.researchos.modules.bluetoothprinter

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object BluetoothPrinterModule : ResearchOSModule {
    override val moduleId = "bluetoothprinter"
    override val displayName = "Bluetooth printer"
    override val summary = "Send a text or raw thermal payload to a paired Bluetooth printer."
    override fun as100Methods() = listOf(As100BluetoothPrinterMethod)
    override fun rilBindings() = listOf(RilBinding("print over Bluetooth", As100BluetoothPrinterMethod.ID, "Send a print payload to a paired Bluetooth thermal printer"))
    override fun capabilityScreens() = listOf(BluetoothPrinterCapabilityScreen)
}
