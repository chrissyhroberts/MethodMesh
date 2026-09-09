package com.example.methodmesh.modules.espmesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.methodmesh.R
import com.example.methodmesh.core.transport.MethodMeshTransportRuntime

/** Keeps a provisioned BLE gateway alive when no capability screen is open. */
class EspMeshGatewayService : Service() {
    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "ESP mesh gateway", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION_ID, notification())
        MethodMeshTransportRuntime.get(this).start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        MethodMeshTransportRuntime.get(this).stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("MethodMesh field network")
        .setContentText("Gateway listening for queued and incoming messages")
        .setOngoing(true)
        .build()

    companion object {
        private const val CHANNEL = "espmesh_gateway"
        private const val NOTIFICATION_ID = 4201
    }
}
