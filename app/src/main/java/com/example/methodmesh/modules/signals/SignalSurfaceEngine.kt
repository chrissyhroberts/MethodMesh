package com.example.methodmesh.modules.signals

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/** High-rate accelerometer vibration envelope for phone-to-surface tabletop transfer. */
class SignalSurfaceReceiverEngine(private val appContext: Context) : SensorEventListener {
    data class Window(
        val timestampMs: Long,
        val vibrationRms: Double,
        val samples: Int
    )

    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var manager: SensorManager? = null
    private var sensorThread: HandlerThread? = null
    private var sampleSeen = AtomicBoolean(false)
    private var previous: FloatArray? = null
    private var windowStartNs: Long = 0L
    private var sum2 = 0.0
    private var count = 0
    private var callback: ((Window) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    fun start(onWindow: (Window) -> Unit, onError: (String) -> Unit): Boolean {
        if (!running.compareAndSet(false, true)) return true
        callback = onWindow
        errorCallback = onError
        return runCatching {
            val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                ?: error("Android did not provide a SensorManager.")
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                ?: error("No accelerometer is available for tabletop reception.")
            manager = sensorManager
            previous = null
            windowStartNs = 0L
            sum2 = 0.0
            count = 0
            sampleSeen.set(false)
            val thread = HandlerThread("MethodMesh-Signals-SurfaceSensor").also { it.start() }
            sensorThread = thread
            val sensorHandler = Handler(thread.looper)
            // Explicit 10 ms (100 Hz) request stays below Android's >200 Hz high-rate
            // sensor permission boundary while still giving several samples per modem bit.
            val ok = sensorManager.registerListener(this, accelerometer, 10_000, sensorHandler)
            if (!ok) error("The accelerometer could not be started.")
            mainHandler.postDelayed({
                if (running.get() && !sampleSeen.get()) failSafely("Accelerometer started but no samples arrived. Try removing battery optimisation or test another device sensor.")
            }, 1200L)
            true
        }.getOrElse { failure ->
            running.set(false)
            manager?.unregisterListener(this)
            manager = null
            runCatching { sensorThread?.quitSafely() }
            sensorThread = null
            callback = null
            val message = failure.message ?: "Tabletop accelerometer receiver could not start."
            mainHandler.post { onError(message) }
            false
        }
    }

    fun stop() {
        running.set(false)
        runCatching { manager?.unregisterListener(this) }
        manager = null
        runCatching { sensorThread?.quitSafely() }
        sensorThread = null
        callback = null
        errorCallback = null
        previous = null
        sum2 = 0.0
        count = 0
        windowStartNs = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running.get() || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        sampleSeen.set(true)
        runCatching {
            val current = event.values
            val prev = previous
            previous = current.copyOf()
            if (prev == null || current.size < 3 || prev.size < 3) return
            val dx = current[0] - prev[0]
            val dy = current[1] - prev[1]
            val dz = current[2] - prev[2]
            val delta = sqrt((dx * dx + dy * dy + dz * dz).toDouble())
            if (!delta.isFinite()) return
            if (windowStartNs == 0L) windowStartNs = event.timestamp
            sum2 += delta * delta
            count++
            val elapsedNs = event.timestamp - windowStartNs
            if (elapsedNs >= 20_000_000L) {
                val rms = sqrt(sum2 / count.coerceAtLeast(1))
                val window = Window(System.currentTimeMillis(), rms, count)
                sum2 = 0.0
                count = 0
                windowStartNs = event.timestamp
                mainHandler.post {
                    if (!running.get()) return@post
                    runCatching { callback?.invoke(window) }
                        .onFailure { failSafely(it.message ?: "Tabletop receiver callback failed.") }
                }
            }
        }.onFailure { failSafely(it.message ?: "Accelerometer processing failed.") }
    }

    private fun failSafely(message: String) {
        if (!running.getAndSet(false)) return
        runCatching { manager?.unregisterListener(this) }
        manager = null
        runCatching { sensorThread?.quitSafely() }
        sensorThread = null
        previous = null
        val handler = errorCallback
        callback = null
        errorCallback = null
        mainHandler.post { handler?.invoke(message) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
