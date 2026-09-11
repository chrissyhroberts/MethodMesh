package com.example.methodmesh.modules.espmesh

import android.app.Notification
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.methodmesh.R
import com.example.methodmesh.core.transport.MethodMeshTransportRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Persistent control plane for the battery/USB-independent mesh edge.
 * The service exists so BLE connection/reconciliation does not depend on a UI.
 */
class EspMeshGatewayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "MethodMesh mesh transport", NotificationManager.IMPORTANCE_LOW))
        // Start only as connectedDevice. Android 15+ forbids launching a
        // mediaPlayback foreground service directly from BOOT_COMPLETED. The
        // service is promoted to mediaPlayback only while speech is actually
        // being received, then downgraded again afterwards.
        publishForeground("Starting persistent mesh transport…", listening = true, mediaPlayback = false)
        MethodMeshTransportRuntime.get(this).start()
        val provider = EspMeshTransportProvider.get(this)
        val walkie = EspMeshWalkieTalkieController.get(this)
        notificationJob = scope.launch {
            combine(provider.snapshot, walkie.state) { snapshot, voice -> snapshot to voice }
                .collectLatest { (snapshot, voice) ->
                    val transport = when {
                        !snapshot.enabled -> "Paused"
                        snapshot.connected -> "Connected · ${snapshot.outboxPending} queued"
                        snapshot.gatewayAddress.isBlank() -> "Waiting for a configured gateway"
                        else -> "Gateway out of range · ${snapshot.outboxPending} queued"
                    }
                    val voiceText = when {
                        voice.transmitting -> "transmitting ${voice.channel}"
                        voice.receiving -> "receiving ${voice.channel}"
                        voice.listening -> "listening ${voice.channel}"
                        else -> "voice muted"
                    }
                    publishForeground("$transport · $voiceText", voice.listening, mediaPlayback = voice.receiving)
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!EspMeshTransportProvider.persistentEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        // Do not stop the shared transport runtime here: process/app lifetime owns it.
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publishForeground(text: String, listening: Boolean, mediaPlayback: Boolean) {
        val types = if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                (if (mediaPlayback) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        } else 0
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification(text, listening), types)
        } else {
            startForeground(NOTIFICATION_ID, notification(text, listening))
        }
    }

    private fun notification(text: String, listening: Boolean = true): Notification {
        val toggleIntent = Intent(this, EspMeshVoiceActionReceiver::class.java).setAction(EspMeshVoiceActionReceiver.ACTION_TOGGLE_LISTEN)
        val togglePending = PendingIntent.getBroadcast(
            this,
            4202,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("MethodMesh mesh")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_launcher_foreground, if (listening) "Mute voice" else "Listen", togglePending)
            .build()
    }

    companion object {
        private const val CHANNEL = "espmesh_gateway"
        private const val NOTIFICATION_ID = 4201
    }
}
