package com.example.researchos

import android.app.Application
import com.example.researchos.modules.ResearchOSModuleDiscovery
import com.example.researchos.modules.ResearchOSModuleRegistry

class ResearchOSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ResearchOSModuleRegistry.install(ResearchOSModuleDiscovery.discover(this))
    }
}
