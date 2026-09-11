package com.example.methodmesh.modules.espmesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Notification control for the persistent walkie-talkie receive path. */
class EspMeshVoiceActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TOGGLE_LISTEN) return
        val controller = EspMeshWalkieTalkieController.get(context.applicationContext)
        controller.toggleListening()
        EspMeshTransportProvider.ensureServiceRunning(context.applicationContext)
    }

    companion object {
        const val ACTION_TOGGLE_LISTEN = "com.example.methodmesh.espmesh.TOGGLE_VOICE_LISTEN"
    }
}
