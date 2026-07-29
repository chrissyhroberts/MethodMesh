package com.example.researchos

import android.app.Application
import com.example.researchos.modules.ResearchOSModuleDiscovery
import com.example.researchos.modules.ResearchOSModuleRegistry
import com.example.researchos.core.scheduling.SchedulerRepository

class ResearchOSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ResearchOSModuleRegistry.install(ResearchOSModuleDiscovery.discover(this))
        // Re-arm persisted alarms after process restart, app update, or device reboot.
        SchedulerRepository.rescheduleAll(this)
    }
}
