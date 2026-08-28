package com.example.methodmesh.platform.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

object PhoneSensorRepository : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var started = false
    private var hasRotationVector = false

    private var latestAccelerometer: FloatArray? = null
    private var latestMagnetometer: FloatArray? = null

    val readings = mutableStateMapOf<String, SensorReading>()

    var headingDegrees by mutableStateOf<Float?>(null)
        private set

    /** Phone tilt relative to the horizon, derived from the rotation vector. */
    var pitchDegrees by mutableStateOf<Float?>(null)
        private set

    /** Roll angle, useful for detecting a tilted measurement posture. */
    var rollDegrees by mutableStateOf<Float?>(null)
        private set

    var status by mutableStateOf("Stopped")
        private set

    fun start(context: Context) {
        if (started) return

        val manager = context.applicationContext
            .getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorManager = manager
        started = true
        status = "Running"

        registerSensor(manager, Sensor.TYPE_ACCELEROMETER, "accelerometer", "Accelerometer", "m/s²")
        registerSensor(manager, Sensor.TYPE_GYROSCOPE, "gyroscope", "Gyroscope", "rad/s")
        registerSensor(manager, Sensor.TYPE_MAGNETIC_FIELD, "magnetometer", "Magnetometer", "µT")
        registerSensor(manager, Sensor.TYPE_ROTATION_VECTOR, "rotation_vector", "Rotation vector", null)
        registerSensor(manager, Sensor.TYPE_LIGHT, "light", "Light", "lx")
        registerSensor(manager, Sensor.TYPE_PRESSURE, "pressure", "Pressure", "hPa")
        registerSensor(manager, Sensor.TYPE_PROXIMITY, "proximity", "Proximity", "cm")
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        started = false
        status = "Stopped"
        pitchDegrees = null
        rollDegrees = null
    }

    private fun registerSensor(
        manager: SensorManager,
        sensorType: Int,
        id: String,
        label: String,
        unit: String?
    ) {
        val sensor = manager.getDefaultSensor(sensorType)

        readings[id] = SensorReading(
            id = id,
            label = label,
            available = sensor != null,
            unit = unit
        )

        if (sensor != null) {
            manager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                latestAccelerometer = event.values.copyOf()
                updateReading("accelerometer", "Accelerometer", true, event.values.toList(), "m/s²", event.accuracy)
                updateHeadingFromAccelerometerAndMagnetometerIfNeeded()
            }

            Sensor.TYPE_GYROSCOPE -> {
                updateReading("gyroscope", "Gyroscope", true, event.values.toList(), "rad/s", event.accuracy)
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                latestMagnetometer = event.values.copyOf()
                updateReading("magnetometer", "Magnetometer", true, event.values.toList(), "µT", event.accuracy)
                updateHeadingFromAccelerometerAndMagnetometerIfNeeded()
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                hasRotationVector = true
                updateReading("rotation_vector", "Rotation vector", true, event.values.toList(), null, event.accuracy)
                headingDegrees = headingFromRotationVector(event.values)
            }

            Sensor.TYPE_LIGHT -> {
                updateReading("light", "Light", true, event.values.toList(), "lx", event.accuracy)
            }

            Sensor.TYPE_PRESSURE -> {
                updateReading("pressure", "Pressure", true, event.values.toList(), "hPa", event.accuracy)
            }

            Sensor.TYPE_PROXIMITY -> {
                updateReading("proximity", "Proximity", true, event.values.toList(), "cm", event.accuracy)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val id = when (sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> "accelerometer"
            Sensor.TYPE_GYROSCOPE -> "gyroscope"
            Sensor.TYPE_MAGNETIC_FIELD -> "magnetometer"
            Sensor.TYPE_ROTATION_VECTOR -> "rotation_vector"
            Sensor.TYPE_LIGHT -> "light"
            Sensor.TYPE_PRESSURE -> "pressure"
            Sensor.TYPE_PROXIMITY -> "proximity"
            else -> null
        }

        if (id != null) {
            readings[id]?.let {
                readings[id] = it.copy(accuracy = accuracy)
            }
        }
    }

    private fun updateReading(
        id: String,
        label: String,
        available: Boolean,
        values: List<Float>,
        unit: String?,
        accuracy: Int?
    ) {
        readings[id] = SensorReading(
            id = id,
            label = label,
            available = available,
            values = values,
            unit = unit,
            accuracy = accuracy
        )
    }

    private fun updateHeadingFromAccelerometerAndMagnetometerIfNeeded() {
        if (hasRotationVector) return

        val accelerometer = latestAccelerometer ?: return
        val magnetometer = latestMagnetometer ?: return
        val rotationMatrix = FloatArray(9)
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometer,
            magnetometer
        )

        if (success) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            headingDegrees = normaliseDegrees(Math.toDegrees(orientation[0].toDouble()).toFloat())
            pitchDegrees = normaliseSignedDegrees(Math.toDegrees(orientation[1].toDouble()).toFloat())
            rollDegrees = normaliseSignedDegrees(Math.toDegrees(orientation[2].toDouble()).toFloat())
        }
    }

    private fun headingFromRotationVector(values: FloatArray): Float {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        pitchDegrees = normaliseSignedDegrees(Math.toDegrees(orientation[1].toDouble()).toFloat())
        rollDegrees = normaliseSignedDegrees(Math.toDegrees(orientation[2].toDouble()).toFloat())
        return normaliseDegrees(Math.toDegrees(orientation[0].toDouble()).toFloat())
    }

    private fun normaliseDegrees(value: Float): Float {
        return ((value % 360f) + 360f) % 360f
    }

    private fun normaliseSignedDegrees(value: Float): Float = ((value + 180f) % 360f + 360f) % 360f - 180f

    fun formattedHeading(): String {
        val heading = headingDegrees ?: return "waiting"
        return "${heading.roundToInt()}°"
    }
}
