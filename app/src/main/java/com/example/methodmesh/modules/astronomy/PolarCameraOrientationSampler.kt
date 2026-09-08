package com.example.methodmesh.modules.astronomy

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Astronomy-local camera-axis orientation sampler for AR polar sighting.
 * It deliberately does not change the shared phone-sensor service.
 */
class PolarCameraOrientationSampler(context: Context) : SensorEventListener {
    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var cameraAzimuthDegrees by mutableStateOf<Double?>(null)
        private set
    var cameraElevationDegrees by mutableStateOf<Double?>(null)
        private set
    var status by mutableStateOf(if (rotation == null) "Rotation-vector sensor unavailable." else "Ready.")
        private set

    fun start() {
        if (rotation == null) {
            status = "Rotation-vector sensor unavailable."
            return
        }
        manager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME)
        status = "Tracking rear-camera axis."
    }

    fun stop() = manager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val matrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val east = -matrix[2].toDouble()
        val north = -matrix[5].toDouble()
        val up = -matrix[8].toDouble()
        val horizontal = sqrt(east * east + north * north)
        cameraElevationDegrees = Math.toDegrees(atan2(up, horizontal))
        cameraAzimuthDegrees = if (horizontal < 0.05) null else normalise(Math.toDegrees(atan2(east, north)))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun normalise(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
}
