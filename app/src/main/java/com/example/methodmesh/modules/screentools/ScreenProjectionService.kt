package com.example.methodmesh.modules.screentools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class ScreenProjectionService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var capturing = false
    private var stoppedBySystem = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stoppedBySystem = true
            ScreenToolsSession.failure = "projection_expired"
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "MethodMesh screen capture", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProjection(intent)
            ACTION_CAPTURE -> capture()
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startProjection(intent: Intent) {
        if (projection != null) return
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val consent = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode == 0 || consent == null) {
            ScreenToolsSession.failure = "projection_not_authorised"
            stopSelf()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    notification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, consent)
                ?: error("MediaProjection was unavailable")
            projection!!.registerCallback(projectionCallback, handler)

            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            display = projection!!.createVirtualDisplay(
                "MethodMesh Screen Capture",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface,
                null,
                handler
            )
            ScreenToolsSession.failure = ""
        } catch (error: Exception) {
            ScreenToolsSession.failure = "projection_failed:${error.javaClass.simpleName}"
            stopSelf()
        }
    }

    private fun capture() {
        val source = reader ?: return
        if (capturing) return
        capturing = true
        source.acquireLatestImage()?.close()
        handler.postDelayed({
            try {
                val image = source.acquireLatestImage() ?: error("No projection frame available")
                image.use {
                    val plane = it.planes.firstOrNull() ?: error("No projection plane available")
                    val pixelStride = plane.pixelStride
                    val rowPadding = (plane.rowStride - pixelStride * it.width).coerceAtLeast(0)
                    val paddedWidth = it.width + rowPadding / pixelStride
                    val padded = Bitmap.createBitmap(paddedWidth, it.height, Bitmap.Config.ARGB_8888)
                    plane.buffer.rewind()
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val bitmap = if (paddedWidth == it.width) padded else Bitmap.createBitmap(
                        padded, 0, 0, it.width, it.height
                    ).also { padded.recycle() }
                    ScreenToolsSession.add(bitmap)
                }
            } catch (error: Exception) {
                ScreenToolsSession.failure = "capture_failed:${error.javaClass.simpleName}"
            } finally {
                capturing = false
            }
        }, 250L)
    }

    private fun notification(): Notification {
        fun action(label: String, action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
            this,
            requestCode,
            Intent(this, ScreenProjectionService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("MethodMesh screen capture")
            .setContentText("Capture the current screen or stop the session.")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_camera, "Capture", action("Capture", ACTION_CAPTURE, 1))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", action("Stop", ACTION_STOP, 2))
            .build()
    }

    override fun onDestroy() {
        display?.release()
        display = null
        reader?.close()
        reader = null
        projection?.unregisterCallback(projectionCallback)
        if (!stoppedBySystem) projection?.stop()
        projection = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.methodmesh.screentools.START"
        const val ACTION_CAPTURE = "com.example.methodmesh.screentools.CAPTURE"
        const val ACTION_STOP = "com.example.methodmesh.screentools.STOP"
        const val EXTRA_RESULT_CODE = "screen_projection_result_code"
        const val EXTRA_DATA = "screen_projection_result_data"
        private const val CHANNEL = "methodmesh_screen_capture_v001"
        private const val NOTIFICATION_ID = 7712
    }
}
