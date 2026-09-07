package com.example.methodmesh.modules.aviation

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState
import org.json.JSONObject
import java.time.Instant
import kotlin.math.pow

object AviationEmergencyFields {
    const val VALUE = "aviation_emergency_value"
    const val STATUS = "aviation_emergency_status"
    const val SNAPSHOT_TIME_ISO = "aviation_emergency_snapshot_time_iso"
    const val DEVICE_ATTITUDE_VALID = "aviation_emergency_device_attitude_valid"
    const val DEVICE_PITCH_DEG = "aviation_emergency_device_pitch_deg"
    const val DEVICE_ROLL_DEG = "aviation_emergency_device_roll_deg"
    const val DEVICE_HEADING_DEG = "aviation_emergency_device_heading_deg"
    const val MOUNT_ORIENTATION = "aviation_emergency_mount_orientation"
    const val ATTITUDE_CALIBRATED = "aviation_emergency_attitude_calibrated"
    const val GPS_GROUND_SPEED_KT = "aviation_emergency_gps_ground_speed_kt"
    const val GPS_TRACK_DEG = "aviation_emergency_gps_track_deg"
    const val GPS_ALTITUDE_FT = "aviation_emergency_gps_altitude_ft"
    const val GPS_VERTICAL_SPEED_FPM = "aviation_emergency_gps_vertical_speed_fpm"
    const val GPS_ACCURACY_M = "aviation_emergency_gps_accuracy_m"
    const val GPS_FIX_AGE_S = "aviation_emergency_gps_fix_age_s"
    const val PRESSURE_HPA = "aviation_emergency_pressure_hpa"
    const val PRESSURE_ALTITUDE_FT = "aviation_emergency_pressure_altitude_ft"
    const val ACCELERATION_MAGNITUDE_G = "aviation_emergency_acceleration_magnitude_g"
    const val NEAREST_AIRFIELD_IDENT = "aviation_emergency_nearest_airfield_ident"
    const val NEAREST_AIRFIELD_NAME = "aviation_emergency_nearest_airfield_name"
    const val NEAREST_AIRFIELD_DISTANCE_NM = "aviation_emergency_nearest_airfield_distance_nm"
    const val NEAREST_AIRFIELD_BEARING_DEG = "aviation_emergency_nearest_airfield_bearing_deg"
    const val NEAREST_AIRFIELD_ELEVATION_FT = "aviation_emergency_nearest_airfield_elevation_ft"
    const val SENSOR_QUALITY = "aviation_emergency_sensor_quality"
    const val WARNING = "aviation_emergency_warning"
    const val AUDIT_JSON = "aviation_emergency_audit_json"
    const val ERROR = "aviation_emergency_error"

    val outputs = listOf(
        VALUE, STATUS, SNAPSHOT_TIME_ISO,
        DEVICE_ATTITUDE_VALID, DEVICE_PITCH_DEG, DEVICE_ROLL_DEG, DEVICE_HEADING_DEG,
        MOUNT_ORIENTATION, ATTITUDE_CALIBRATED,
        GPS_GROUND_SPEED_KT, GPS_TRACK_DEG, GPS_ALTITUDE_FT, GPS_VERTICAL_SPEED_FPM,
        GPS_ACCURACY_M, GPS_FIX_AGE_S,
        PRESSURE_HPA, PRESSURE_ALTITUDE_FT, ACCELERATION_MAGNITUDE_G,
        NEAREST_AIRFIELD_IDENT, NEAREST_AIRFIELD_NAME, NEAREST_AIRFIELD_DISTANCE_NM,
        NEAREST_AIRFIELD_BEARING_DEG, NEAREST_AIRFIELD_ELEVATION_FT,
        SENSOR_QUALITY, WARNING, AUDIT_JSON, ERROR
    )
}

data class AviationEmergencySample(
    val rawPitchDeg: Double? = null,
    val rawRollDeg: Double? = null,
    val deviceHeadingDeg: Double? = null,
    val pressureHpa: Double? = null,
    val gpsGroundSpeedKt: Double? = null,
    val gpsTrackDeg: Double? = null,
    val gpsAltitudeFt: Double? = null,
    val gpsVerticalSpeedFpm: Double? = null,
    val gpsAccuracyM: Double? = null,
    val gpsFixAgeSeconds: Double? = null,
    val magneticAccuracy: Int? = null,
    val accelerationMagnitudeG: Double? = null,
    val rotationVectorAvailable: Boolean = false
)

data class AviationEmergencyCalibration(
    val mountOrientation: String = "portrait_up",
    val pitchZeroDeg: Double = 0.0,
    val rollZeroDeg: Double = 0.0,
    val calibrated: Boolean = false
)

data class AviationEmergencyAttitude(
    val pitchDeg: Double?,
    val rollDeg: Double?,
    val valid: Boolean
)

object AviationEmergencyInstrumentEngine {
    private const val STANDARD_PRESSURE_HPA = 1013.25

    fun transformAttitude(
        rawPitchDeg: Double?,
        rawRollDeg: Double?,
        calibration: AviationEmergencyCalibration
    ): AviationEmergencyAttitude {
        if (rawPitchDeg == null || rawRollDeg == null) return AviationEmergencyAttitude(null, null, false)

        val transformed = when (calibration.mountOrientation) {
            "landscape_left" -> (-rawRollDeg) to rawPitchDeg
            "landscape_right" -> rawRollDeg to (-rawPitchDeg)
            "portrait_down" -> (-rawPitchDeg) to (-rawRollDeg)
            else -> rawPitchDeg to rawRollDeg
        }

        val pitch = normaliseSigned(transformed.first - calibration.pitchZeroDeg)
        val roll = normaliseSigned(transformed.second - calibration.rollZeroDeg)
        return AviationEmergencyAttitude(pitch, roll, calibration.calibrated)
    }

    fun calibrationOffsets(rawPitchDeg: Double?, rawRollDeg: Double?, mountOrientation: String): Pair<Double, Double>? {
        if (rawPitchDeg == null || rawRollDeg == null) return null
        return when (mountOrientation) {
            "landscape_left" -> (-rawRollDeg) to rawPitchDeg
            "landscape_right" -> rawRollDeg to (-rawPitchDeg)
            "portrait_down" -> (-rawPitchDeg) to (-rawRollDeg)
            else -> rawPitchDeg to rawRollDeg
        }
    }

    fun pressureAltitudeFt(pressureHpa: Double?): Double? {
        val pressure = pressureHpa?.takeIf { it in 100.0..1200.0 } ?: return null
        return 145_366.45 * (1.0 - (pressure / STANDARD_PRESSURE_HPA).pow(0.190284))
    }

    fun quality(sample: AviationEmergencySample, attitude: AviationEmergencyAttitude): String {
        val problems = mutableListOf<String>()
        if (sample.gpsAccuracyM == null) problems += "NO GPS"
        else if (sample.gpsAccuracyM > 100.0) problems += "GPS POOR"
        else if (sample.gpsAccuracyM > 30.0) problems += "GPS FAIR"
        if ((sample.gpsFixAgeSeconds ?: 0.0) > 5.0) problems += "GPS STALE"
        if (sample.rawPitchDeg == null || sample.rawRollDeg == null) problems += "NO ATT SENSOR"
        else if (!sample.rotationVectorAvailable) problems += "ATT FALLBACK"
        if (sample.rawPitchDeg != null && sample.rawRollDeg != null && !attitude.valid) problems += "ATT UNCAL"
        val g = sample.accelerationMagnitudeG
        if (g != null && kotlin.math.abs(g - 1.0) > 0.18) problems += "ATT DYNAMIC"
        if (sample.magneticAccuracy != null && sample.magneticAccuracy <= 1) problems += "MAG LOW"
        return if (problems.isEmpty()) "REFERENCE DATA AVAILABLE" else problems.joinToString(" · ")
    }

    fun values(
        sample: AviationEmergencySample,
        calibration: AviationEmergencyCalibration,
        nearestAirfield: AviationAirfieldRepository.Airfield? = null
    ): Map<String, String> {
        val attitude = transformAttitude(sample.rawPitchDeg, sample.rawRollDeg, calibration)
        val pressureAltitude = pressureAltitudeFt(sample.pressureHpa)
        val quality = quality(sample, attitude)
        val warnings = buildList {
            add("Supplementary phone reference only — not certified flight instruments.")
            if (nearestAirfield != null) add("Nearest-airfield data is positional reference only and does not establish runway suitability, availability or safety.")
            if (!calibration.calibrated) add("Device attitude is uncalibrated and must not be interpreted as aircraft attitude.")
            if (!sample.rotationVectorAvailable && sample.rawPitchDeg != null && sample.rawRollDeg != null) add("Rotation-vector sensor is unavailable; device attitude is using the accelerometer/magnetometer fallback and is more vulnerable to acceleration error.")
            sample.accelerationMagnitudeG?.let { g ->
                if (kotlin.math.abs(g - 1.0) > 0.18) add("Device acceleration is far from 1g; attitude reference is dynamically degraded.")
            }
            if ((sample.gpsFixAgeSeconds ?: 0.0) > 5.0) add("GNSS fix is stale.")
            if ((sample.gpsAccuracyM ?: 0.0) > 100.0) add("GNSS position accuracy is poor.")
            if (sample.pressureHpa != null) add("Phone pressure altitude reflects ambient device pressure and may be invalid in a pressurised cabin.")
        }.joinToString(" ")

        val timestamp = Instant.now().toString()
        val headline = buildString {
            append("GS ")
            append(sample.gpsGroundSpeedKt?.let { AviationCalculations.format(it, 0) } ?: "—")
            append(" kt · TRK ")
            append(sample.gpsTrackDeg?.let { AviationCalculations.format(it, 0) } ?: "—")
            append("° · GNSS ALT ")
            append(sample.gpsAltitudeFt?.let { AviationCalculations.format(it, 0) } ?: "—")
            append(" ft")
        }

        val audit = JSONObject().apply {
            put("snapshot_time_iso", timestamp)
            put("mount_orientation", calibration.mountOrientation)
            put("attitude_calibrated", calibration.calibrated)
            put("pitch_zero_deg", calibration.pitchZeroDeg)
            put("roll_zero_deg", calibration.rollZeroDeg)
            put("raw_pitch_deg", sample.rawPitchDeg ?: JSONObject.NULL)
            put("raw_roll_deg", sample.rawRollDeg ?: JSONObject.NULL)
            put("device_pitch_deg", attitude.pitchDeg ?: JSONObject.NULL)
            put("device_roll_deg", attitude.rollDeg ?: JSONObject.NULL)
            put("device_heading_deg", sample.deviceHeadingDeg ?: JSONObject.NULL)
            put("gps_ground_speed_kt", sample.gpsGroundSpeedKt ?: JSONObject.NULL)
            put("gps_track_deg", sample.gpsTrackDeg ?: JSONObject.NULL)
            put("gps_altitude_ft", sample.gpsAltitudeFt ?: JSONObject.NULL)
            put("gps_vertical_speed_fpm", sample.gpsVerticalSpeedFpm ?: JSONObject.NULL)
            put("gps_accuracy_m", sample.gpsAccuracyM ?: JSONObject.NULL)
            put("gps_fix_age_s", sample.gpsFixAgeSeconds ?: JSONObject.NULL)
            put("pressure_hpa", sample.pressureHpa ?: JSONObject.NULL)
            put("pressure_altitude_ft", pressureAltitude ?: JSONObject.NULL)
            put("acceleration_magnitude_g", sample.accelerationMagnitudeG ?: JSONObject.NULL)
            put("rotation_vector_available", sample.rotationVectorAvailable)
            put("magnetic_accuracy", sample.magneticAccuracy ?: JSONObject.NULL)
            put("sensor_quality", quality)
            put("nearest_airfield_ident", nearestAirfield?.ident ?: JSONObject.NULL)
            put("nearest_airfield_name", nearestAirfield?.name ?: JSONObject.NULL)
            put("nearest_airfield_distance_nm", nearestAirfield?.distanceNm ?: JSONObject.NULL)
            put("nearest_airfield_bearing_deg", nearestAirfield?.bearingDeg ?: JSONObject.NULL)
            put("warning", warnings)
        }

        return linkedMapOf(
            AviationEmergencyFields.VALUE to headline,
            AviationEmergencyFields.STATUS to "succeeded",
            AviationEmergencyFields.SNAPSHOT_TIME_ISO to timestamp,
            AviationEmergencyFields.DEVICE_ATTITUDE_VALID to attitude.valid.toString(),
            AviationEmergencyFields.DEVICE_PITCH_DEG to attitude.pitchDeg?.let { AviationCalculations.format(it, 1) }.orEmpty(),
            AviationEmergencyFields.DEVICE_ROLL_DEG to attitude.rollDeg?.let { AviationCalculations.format(it, 1) }.orEmpty(),
            AviationEmergencyFields.DEVICE_HEADING_DEG to sample.deviceHeadingDeg?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            AviationEmergencyFields.MOUNT_ORIENTATION to calibration.mountOrientation,
            AviationEmergencyFields.ATTITUDE_CALIBRATED to calibration.calibrated.toString(),
            AviationEmergencyFields.GPS_GROUND_SPEED_KT to sample.gpsGroundSpeedKt?.let { AviationCalculations.format(it, 1) }.orEmpty(),
            AviationEmergencyFields.GPS_TRACK_DEG to sample.gpsTrackDeg?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            AviationEmergencyFields.GPS_ALTITUDE_FT to sample.gpsAltitudeFt?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            AviationEmergencyFields.GPS_VERTICAL_SPEED_FPM to sample.gpsVerticalSpeedFpm?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            AviationEmergencyFields.GPS_ACCURACY_M to sample.gpsAccuracyM?.let { AviationCalculations.format(it, 1) }.orEmpty(),
            AviationEmergencyFields.GPS_FIX_AGE_S to sample.gpsFixAgeSeconds?.let { AviationCalculations.format(it, 1) }.orEmpty(),
            AviationEmergencyFields.PRESSURE_HPA to sample.pressureHpa?.let { AviationCalculations.format(it, 1) }.orEmpty(),
            AviationEmergencyFields.PRESSURE_ALTITUDE_FT to pressureAltitude?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            AviationEmergencyFields.ACCELERATION_MAGNITUDE_G to sample.accelerationMagnitudeG?.let { AviationCalculations.format(it, 2) }.orEmpty(),
            AviationEmergencyFields.NEAREST_AIRFIELD_IDENT to nearestAirfield?.ident.orEmpty(),
            AviationEmergencyFields.NEAREST_AIRFIELD_NAME to nearestAirfield?.name.orEmpty(),
            AviationEmergencyFields.NEAREST_AIRFIELD_DISTANCE_NM to nearestAirfield?.distanceNm?.let { AviationCalculations.format(it, 1) }.orEmpty(),
            AviationEmergencyFields.NEAREST_AIRFIELD_BEARING_DEG to nearestAirfield?.bearingDeg?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            AviationEmergencyFields.NEAREST_AIRFIELD_ELEVATION_FT to nearestAirfield?.elevationFt?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            AviationEmergencyFields.SENSOR_QUALITY to quality,
            AviationEmergencyFields.WARNING to warnings,
            AviationEmergencyFields.AUDIT_JSON to audit.toString(),
            AviationEmergencyFields.ERROR to ""
        )
    }

    private fun normaliseSigned(value: Double): Double = ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}

object As100AviationEmergencyInstrumentsMethod : As100Method {
    const val ID = "aviation.emergency.instruments"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Emergency aviation reference instruments")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "Emergency instrument panel",
        version = AVIATION_VERSION,
        description = "Create a supplementary phone-sensor aviation reference snapshot with explicitly labelled GNSS/device sources.",
        inputs = listOf(
            "mount_orientation", "pitch_zero_deg", "roll_zero_deg", "attitude_calibrated",
            "latitude", "longitude", "gps_ground_speed_kt", "gps_track_deg", "gps_altitude_ft",
            "gps_vertical_speed_fpm", "gps_accuracy_m", "gps_fix_age_s", "device_pitch_deg", "device_roll_deg",
            "device_heading_deg", "pressure_hpa", "airfield_radius_nm", "airfield_refresh_policy"
        ),
        outputs = AviationEmergencyFields.outputs,
        graphOutputs = listOf("aviation.emergency.instruments"),
        parameters = mapOf("category" to "Aviation", "status" to "Development")
    )

    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, failure("Emergency instrument execution requires the Android capability boundary for live phone sensors and GNSS."), InvocationContext.from(request.context))

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult =
        AviationExecutionSupport.complete(this, AVIATION_VERSION, ID, request, values, invocation, AviationEmergencyFields.STATUS, AviationEmergencyFields.ERROR)

    fun failure(error: String): Map<String, String> = linkedMapOf(
        AviationEmergencyFields.VALUE to "",
        AviationEmergencyFields.STATUS to "failed",
        AviationEmergencyFields.DEVICE_ATTITUDE_VALID to "false",
        AviationEmergencyFields.ATTITUDE_CALIBRATED to "false",
        AviationEmergencyFields.SENSOR_QUALITY to "UNAVAILABLE",
        AviationEmergencyFields.WARNING to "Supplementary phone reference only — not certified flight instruments.",
        AviationEmergencyFields.AUDIT_JSON to "{}",
        AviationEmergencyFields.ERROR to error
    )
}
