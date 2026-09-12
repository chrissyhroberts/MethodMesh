package com.example.methodmesh.modules.espmesh

import android.content.Context
import com.example.methodmesh.core.transport.MethodMeshTransportProvider
import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding

object EspMeshModule : MethodMeshModule {
    override val moduleId = "espmesh"
    override val displayName = "ESP mesh field network"
    override val summary = "Persistent encrypted BLE↔ESP-NOW field transport with durable store-and-forward data and ephemeral live voice."
    override val workbenchTool = true
    override fun overlays(): List<com.example.methodmesh.modules.ModuleOverlaySpec> = listOf(EspMeshVoiceOverlay)
    override val iconKey = "network"

    override fun as100Methods() = listOf(As100EspMeshMessageMethod, As100EspMeshGatewayMethod, As100EspMeshDiagnosticsMethod)
    override fun rilBindings() = listOf(
        RilBinding("test ESP mesh transport", As100EspMeshMessageMethod.ID, "Send a diagnostic message through the persistent encrypted field transport"),
        RilBinding("configure ESP mesh transport", As100EspMeshGatewayMethod.ID, "Configure persistent BLE gateway, radio network and phone E2E key"),
        RilBinding("inspect ESP mesh diagnostics", As100EspMeshDiagnosticsMethod.ID, "Inspect gateway and durable transport state")
    )
    override fun capabilityScreens() = listOf(EspMeshCapabilityScreen, EspMeshGatewayCapabilityScreen, EspMeshDiagnosticsCapabilityScreen)
    override fun transportProviders(context: Context): List<MethodMeshTransportProvider> = listOf(EspMeshTransportProvider.create(context))
}
