package com.example.methodmesh.modules.bluetoothprinter

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding

/**
 * Standalone Qutie-family Bluetooth printer module.
 *
 * This object uses only the standard MethodMesh module contracts already
 * available to every capability: methods, RIL bindings, capability screens and
 * typed capability settings. No dashboard, preset, registry or core UI code is
 * modified by this module.
 */
object BluetoothPrinterModule : MethodMeshModule {
    override val moduleId = "bluetoothprinter"
    override val displayName = "Bluetooth printer"
    override val summary = "Qutie-family FF00/LuckPrinter BLE thermal printing with text, QR and Code 128 composition."

    override fun as100Methods() = listOf(As100BluetoothPrinterMethod)

    override fun rilBindings() = listOf(
        RilBinding(
            "print with Qutie-family Bluetooth printer",
            As100BluetoothPrinterMethod.ID,
            "Print to a Qutie or compatible FF00/LuckPrinter-family BLE thermal printer"
        )
    )

    override fun capabilityScreens() = listOf(BluetoothPrinterCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100BluetoothPrinterMethod.ID to BluetoothPrinterSettings.schema
    )
}
