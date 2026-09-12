package com.example.methodmesh.modules.espmesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Best-effort resurrection after reboot/Bluetooth restoration; USB is never required. */
class EspMeshBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (EspMeshTransportProvider.persistentEnabled(context)) {
            EspMeshTransportProvider.ensureServiceRunning(context)
        }
    }
}
