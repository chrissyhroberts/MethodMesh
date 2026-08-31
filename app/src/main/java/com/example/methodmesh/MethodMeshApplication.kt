package com.example.methodmesh

import android.app.Application
import com.example.methodmesh.modules.MethodMeshModuleDiscovery
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import com.example.methodmesh.core.scheduling.SchedulerRepository
import org.maplibre.android.MapLibre

class MethodMeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        MethodMeshModuleRegistry.install(MethodMeshModuleDiscovery.discover(this))
        // Re-arm persisted alarms after process restart, app update, or device reboot.
        SchedulerRepository.rescheduleAll(this)
    }
}
