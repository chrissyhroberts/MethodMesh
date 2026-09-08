package com.example.methodmesh.modules.astronomy

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sqrt

class MountStabilitySampler(context: Context) : SensorEventListener {
    data class Sample(val tNs: Long, val ax: Double, val ay: Double, val az: Double, val gx: Double?, val gy: Double?, val gz: Double?)
    data class Summary(val samples: Int, val accelRmsMs2: Double, val gyroRmsRadS: Double?, val peakAccelDeltaMs2: Double, val label: String)

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val samples = CopyOnWriteArrayList<Sample>()
    @Volatile private var lastGyro: Triple<Double, Double, Double>? = null

    fun start() {
        samples.clear()
        accel?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyro?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = manager.unregisterListener(this)
    fun clear() = samples.clear()

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> lastGyro = Triple(event.values[0].toDouble(), event.values[1].toDouble(), event.values[2].toDouble())
            Sensor.TYPE_ACCELEROMETER -> {
                val g = lastGyro
                samples += Sample(event.timestamp, event.values[0].toDouble(), event.values[1].toDouble(), event.values[2].toDouble(), g?.first, g?.second, g?.third)
                if (samples.size > 5000) samples.removeAt(0)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun summary(): Summary {
        if (samples.size < 5) return Summary(samples.size, Double.NaN, null, Double.NaN, "INSUFFICIENT DATA")
        val magnitudes = samples.map { sqrt(it.ax * it.ax + it.ay * it.ay + it.az * it.az) }
        val mean = magnitudes.average()
        val accelRms = sqrt(magnitudes.map { (it - mean) * (it - mean) }.average())
        val peak = magnitudes.maxOf { kotlin.math.abs(it - mean) }
        val gyroMagnitudes = samples.mapNotNull { s -> if (s.gx == null || s.gy == null || s.gz == null) null else sqrt(s.gx * s.gx + s.gy * s.gy + s.gz * s.gz) }
        val gyroRms = gyroMagnitudes.takeIf { it.isNotEmpty() }?.let { vals -> sqrt(vals.map { it * it }.average()) }
        val label = when {
            accelRms < 0.015 && (gyroRms == null || gyroRms < 0.002) -> "EXCELLENT"
            accelRms < 0.04 && (gyroRms == null || gyroRms < 0.006) -> "GOOD"
            accelRms < 0.10 -> "MARGINAL"
            else -> "UNSTABLE"
        }
        return Summary(samples.size, accelRms, gyroRms, peak, label)
    }
}
