package com.example.methodmesh.modules.espmesh

import android.content.Context
import com.example.methodmesh.core.transport.MethodMeshTransportProvider
import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding

object EspMeshModule : MethodMeshModule {
    override val moduleId = "espmesh"
    override val displayName = "ESP mesh field network"
    override val summary = "Queue MethodMesh messages for resilient ESP gateway and mesh transport."
    override val iconKey = "network"

    override fun as100Methods() = listOf(As100EspMeshMessageMethod)
    override fun rilBindings() = listOf(
        RilBinding("send ESP mesh message", As100EspMeshMessageMethod.ID, "Queue an opaque message for the field network")
    )
    override fun capabilityScreens() = listOf(EspMeshCapabilityScreen)
    override fun transportProviders(context: Context): List<MethodMeshTransportProvider> = listOf(EspMeshTransportProvider.create(context))
}
