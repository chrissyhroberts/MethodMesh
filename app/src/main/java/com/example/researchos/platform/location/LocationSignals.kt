package com.example.researchos.platform.location

import android.location.Location
import com.example.researchos.core.researchos.DeviceService
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.SpatialContext
import com.example.researchos.core.researchos.TemporalContext

/**
 * AS1.00 Device Service for Android location.
 *
 * This service is source-only: it normalises Android Location readings into
 * transient location signals. It does not decide whether a participant has
 * arrived, calculate target navigation evidence, or create observations.
 */
object AndroidLocationDeviceService : DeviceService {
    override val serviceId: String = SERVICE_ID

    override fun describe(): Map<String, String> = mapOf(
        "service_id" to SERVICE_ID,
        "service_type" to "device.location",
        "platform" to "android",
        "signal_type" to SIGNAL_TYPE_LOCATION_FIX,
        "responsibility" to "Emit Android location-fix signals without interpreting them."
    )

    fun signalFromLocation(location: Location): Signal {
        val eventTime = if (location.time > 0L) location.time else System.currentTimeMillis()
        return Signal(
            signalType = SIGNAL_TYPE_LOCATION_FIX,
            sourceService = SERVICE_ID,
            payload = linkedMapOf(
                "latitude" to location.latitude.toString(),
                "longitude" to location.longitude.toString(),
                "accuracy_m" to if (location.hasAccuracy()) location.accuracy.toString() else "",
                "altitude_m" to if (location.hasAltitude()) location.altitude.toString() else "",
                "bearing_deg" to if (location.hasBearing()) location.bearing.toString() else "",
                "speed_mps" to if (location.hasSpeed()) location.speed.toString() else "",
                "provider" to (location.provider ?: "unknown")
            ),
            temporalContext = TemporalContext(
                eventTimeEpochMs = eventTime,
                systemTimeEpochMs = System.currentTimeMillis()
            ),
            spatialContext = SpatialContext(
                referenceSystem = "WGS84",
                location = linkedMapOf(
                    "latitude" to location.latitude.toString(),
                    "longitude" to location.longitude.toString(),
                    "accuracy_m" to if (location.hasAccuracy()) location.accuracy.toString() else ""
                )
            ),
            provenance = ProvenanceContext(
                provider = "android.location",
                methodId = SERVICE_ID
            )
        )
    }

    const val SERVICE_ID = "device.location.android"
    const val SIGNAL_TYPE_LOCATION_FIX = "location.fix"
}
