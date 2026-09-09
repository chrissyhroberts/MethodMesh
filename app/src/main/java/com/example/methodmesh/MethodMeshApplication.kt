package com.example.methodmesh

import android.app.Application
import com.example.methodmesh.core.onlinedata.ApiDefinitionRepository
import com.example.methodmesh.core.scheduling.SchedulePlanRuntime
import com.example.methodmesh.core.scheduling.SchedulerRepository
import com.example.methodmesh.core.transport.MethodMeshTransportRuntime
import com.example.methodmesh.modules.MethodMeshModuleDiscovery
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import org.maplibre.android.MapLibre

class MethodMeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        ApiDefinitionRepository.initialise(this)
        val modules = MethodMeshModuleDiscovery.discover(this)
        MethodMeshModuleRegistry.install(modules)
        val transportRuntime = MethodMeshTransportRuntime.initialise(this)
        modules.flatMap { it.transportProviders(this) }.forEach(transportRuntime::registerProvider)
        transportRuntime.start()
        // Re-arm persisted alarms after process restart, app update, or device reboot.
        SchedulePlanRuntime.rescheduleAll(this)
        SchedulerRepository.rescheduleAll(this)
    }
}
