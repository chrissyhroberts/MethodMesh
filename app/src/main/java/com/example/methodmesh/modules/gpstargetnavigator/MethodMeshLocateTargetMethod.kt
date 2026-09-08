package com.example.methodmesh.modules.gpstargetnavigator

import android.location.Location
import com.example.methodmesh.core.ResearchRuntime
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.SpatialContext
import com.example.methodmesh.core.methodmesh.State
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.modules.pluscodecapture.OpenLocationCode
import com.example.methodmesh.platform.location.AndroidLocationDeviceService
import com.example.methodmesh.settings.SettingsState

/**
 * Canonical GPS target-navigation capability.
 *
 * All surfaces use the same method ID and output field contract. The native
 * screen supplies the long-running interaction and Commit boundary; presets,
 * protocols and ODK project the same settings/outputs.
 */
object As100LocateTargetMethod : As100Method {
    const val ID = "gps_target_navigator"
    const val VERSION = "0.4.1"

    private val OUTPUT_FIELDS = listOf(
        // Live/current fields.
        "target_name",
        "target_plus_code",
        "target_latitude",
        "target_longitude",
        "current_latitude",
        "current_longitude",
        "accuracy_m",
        "distance_m",
        "bearing_deg",
        "heading_deg",
        "relative_bearing_deg",
        "arrived",
        "timestamp_ms",
        "update_count",
        "status",
        // Committed navigation-outcome fields.
        "capability",
        "event_type",
        "navigation_completed",
        "arrival_radius_m",
        "arrival_latitude",
        "arrival_longitude",
        "arrival_accuracy_m",
        "final_distance_m",
        "started_at_ms",
        "ended_at_ms",
        "duration_seconds",
        "sample_count",
        "first_fix_latitude",
        "first_fix_longitude",
        "last_fix_latitude",
        "last_fix_longitude",
        "min_distance_m",
        "mean_accuracy_m",
        "max_accuracy_m"
    )

    override val id: String = ID

    override val ref: ArchitectureRef = ArchitectureRef(
        id = ArchitectureId(ID),
        type = "Method",
        label = "Locate Target"
    )

    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "GPS Target Navigator",
        version = VERSION,
        description = "Navigate to a WGS84 coordinate or full Plus Code and return live plus committed navigation evidence.",
        inputs = listOf(AndroidLocationDeviceService.SIGNAL_TYPE_LOCATION_FIX),
        outputs = OUTPUT_FIELDS,
        parameters = mapOf(
            "category" to "Mapping",
            "status" to "Production",
            "device_service" to AndroidLocationDeviceService.SERVICE_ID,
            "target" to "target_plus_code|target_latitude,target_longitude",
            "arrival" to "arrival_radius_m"
        )
    )

    override val contract: MethodContract = MethodContract(
        method = ref,
        acceptedSignals = listOf(AndroidLocationDeviceService.SIGNAL_TYPE_LOCATION_FIX),
        // Target selection is conditional: target_plus_code OR lat/lon. The
        // module validates/resolves that condition rather than pretending all
        // three are simultaneously required.
        requiredContext = listOf("arrival_radius_m"),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation, KnowledgeObjectType.State),
        producedFields = descriptor.outputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult {
        val signal = request.signals.firstOrNull()
        val output = if (settingsState != null) {
            resolveTargetIntoSettings(settingsState)
            outputValues(settingsState).mapValues { it.value?.toString().orEmpty() }
        } else {
            outputFromRequest(request, signal).mapValues { it.value?.toString().orEmpty() }
        }

        val targetId = output["target_name"].orEmpty().ifBlank {
            "${output["target_latitude"].orEmpty()},${output["target_longitude"].orEmpty()}"
        }
        val targetEntity = Entity(
            id = ArchitectureId("target:$targetId"),
            entityType = "SpatialTarget",
            attributes = mapOf(
                "target_name" to output["target_name"].orEmpty(),
                "target_plus_code" to output["target_plus_code"].orEmpty(),
                "target_latitude" to output["target_latitude"].orEmpty(),
                "target_longitude" to output["target_longitude"].orEmpty()
            ),
            spatialContext = SpatialContext(
                referenceSystem = "WGS84",
                location = mapOf(
                    "latitude" to output["target_latitude"].orEmpty(),
                    "longitude" to output["target_longitude"].orEmpty()
                )
            )
        )
        val observationSpatialContext = signal?.spatialContext ?: SpatialContext(
            referenceSystem = "WGS84",
            location = mapOf(
                "latitude" to output["current_latitude"].orEmpty(),
                "longitude" to output["current_longitude"].orEmpty(),
                "accuracy_m" to output["accuracy_m"].orEmpty()
            )
        )
        val observation = Observation(
            phenomenon = "location.target_navigation",
            subject = ArchitectureRef(targetEntity.id, targetEntity.objectType, output["target_name"]),
            values = output,
            sourceSignal = signal?.let { ArchitectureRef(it.id, it.objectType, it.signalType) },
            temporalContext = signal?.temporalContext ?: request.temporalContext,
            spatialContext = observationSpatialContext,
            provenance = ProvenanceContext(
                provider = signal?.provenance?.provider ?: "methodmesh.location",
                methodId = ID,
                methodVersion = VERSION
            )
        )

        val targetRef = ArchitectureRef(targetEntity.id, targetEntity.objectType, output["target_name"].orEmpty())
        val state = State(
            subject = targetRef,
            stateType = "navigation.arrival_state",
            values = mapOf(
                "arrived" to output["arrived"].orEmpty(),
                "distance_m" to output["distance_m"].orEmpty(),
                "arrival_radius_m" to (output["arrival_radius_m"] ?: request.context["arrival_radius_m"]).orEmpty()
            ),
            temporalContext = observation.temporalContext,
            spatialContext = observation.spatialContext
        )

        val transformation = Transformation(
            action = "interpret.location.target_navigation",
            method = ref,
            inputs = signal?.let { listOf(ArchitectureRef(it.id, it.objectType, it.signalType)) } ?: emptyList(),
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            resultingState = state,
            status = TransformationStatus.Succeeded,
            temporalContext = observation.temporalContext,
            provenance = ProvenanceContext(
                provider = "methodmesh.location",
                methodId = ID,
                methodVersion = VERSION
            )
        )

        return As100ExecutionEngine.complete(
            request = request,
            status = TransformationStatus.Succeeded,
            entities = listOf(targetEntity),
            observations = listOf(observation),
            states = listOf(state),
            transformations = listOf(transformation),
            diagnostics = mapOf("method" to ID)
        )
    }

    fun updateSettingsFromLocation(
        settingsState: SettingsState,
        currentLatitude: Double,
        currentLongitude: Double,
        accuracy: Float,
        targetLatitude: Double,
        targetLongitude: Double,
        arrivalRadius: Float
    ) {
        val result = distanceAndBearing(
            currentLatitude = currentLatitude,
            currentLongitude = currentLongitude,
            targetLatitude = targetLatitude,
            targetLongitude = targetLongitude
        )
        settingsState.setFloat("current_latitude", currentLatitude.toFloat())
        settingsState.setFloat("current_longitude", currentLongitude.toFloat())
        settingsState.setFloat("accuracy_m", accuracy)
        settingsState.setFloat("distance_m", result.distanceMeters)
        settingsState.setFloat("bearing_deg", result.initialBearingDegrees)
        settingsState.setBoolean("arrived", result.distanceMeters <= arrivalRadius)
        settingsState.setString("timestamp_ms", System.currentTimeMillis().toString())
        if (settingsState.getString("status").isBlank()) settingsState.setString("status", "updated")
    }

    fun outputValues(settingsState: SettingsState): Map<String, Any?> {
        resolveTargetIntoSettings(settingsState)
        val targetLatitude = settingsState.getFloat("target_latitude")
        val targetLongitude = settingsState.getFloat("target_longitude")
        val currentLatitude = settingsState.getFloat("current_latitude")
        val currentLongitude = settingsState.getFloat("current_longitude")
        val arrivalRadius = settingsState.getFloat("arrival_radius_m")

        val hasFix = settingsState.getString("timestamp_ms").isNotBlank() || currentLatitude != 0f || currentLongitude != 0f
        if (hasFix) {
            updateSettingsFromLocation(
                settingsState = settingsState,
                currentLatitude = currentLatitude.toDouble(),
                currentLongitude = currentLongitude.toDouble(),
                accuracy = settingsState.getFloat("accuracy_m"),
                targetLatitude = targetLatitude.toDouble(),
                targetLongitude = targetLongitude.toDouble(),
                arrivalRadius = arrivalRadius
            )
        }

        return calculateOutputFields(
            targetName = settingsState.getString("target_name"),
            targetPlusCode = settingsState.getString("target_plus_code"),
            targetLatitude = targetLatitude,
            targetLongitude = targetLongitude,
            currentLatitude = currentLatitude,
            currentLongitude = currentLongitude,
            accuracy = settingsState.getFloat("accuracy_m"),
            heading = settingsState.getFloat("heading_deg"),
            updateCount = settingsState.getFloat("update_count"),
            arrivalRadius = arrivalRadius,
            timestampMs = settingsState.getString("timestamp_ms"),
            status = settingsState.getString("status"),
            hasLocationFix = hasFix
        )
    }

    /** Build, but do not automatically persist, a committed navigation result. */
    fun navigationOutcomeResult(fields: Map<String, Any?>): ExecutionResult {
        val request = request(action = ID, context = emptyMap())
        val provenance = ProvenanceContext(
            provider = "methodmesh.presentation.gps_target_navigator",
            methodId = ID,
            methodVersion = VERSION
        )
        val observation = Observation(
            phenomenon = "location.navigation_outcome",
            values = fields.mapValues { it.value?.toString().orEmpty() },
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = "record.navigation.outcome",
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Succeeded,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = TransformationStatus.Succeeded,
            observations = listOf(observation),
            transformations = listOf(transformation)
        )
    }

    /** Backwards-compatible explicit session-recording helper. Native Commit no longer calls this automatically. */
    fun recordNavigationOutcome(fields: Map<String, Any?>): ExecutionResult =
        navigationOutcomeResult(fields).also { ResearchRuntime.session.record(it) }

    private fun outputFromRequest(request: ExecutionRequest, signal: Signal?): Map<String, Any?> {
        val currentLatitude = signal?.payload?.get("latitude")?.toDoubleOrNull()
        val currentLongitude = signal?.payload?.get("longitude")?.toDoubleOrNull()
        val target = resolveTarget(request.context)
        val arrivalRadius = request.context["arrival_radius_m"]?.toFloatOrNull()
            ?: request.context["arrival_radius"]?.toFloatOrNull()
            ?: 10f

        if (currentLatitude == null || currentLongitude == null || target == null) return emptyMap()
        return calculateOutputFields(
            targetName = request.context["target_name"].orEmpty(),
            targetPlusCode = target.plusCode,
            targetLatitude = target.latitude.toFloat(),
            targetLongitude = target.longitude.toFloat(),
            currentLatitude = currentLatitude.toFloat(),
            currentLongitude = currentLongitude.toFloat(),
            accuracy = signal.payload["accuracy_m"]?.toFloatOrNull() ?: 0f,
            heading = request.context["heading_deg"]?.toFloatOrNull() ?: 0f,
            updateCount = request.context["update_count"]?.toFloatOrNull() ?: 0f,
            arrivalRadius = arrivalRadius,
            hasLocationFix = true
        )
    }

    private fun resolveTargetIntoSettings(settingsState: SettingsState) {
        val plusCode = settingsState.getString("target_plus_code")
        if (plusCode.isBlank()) return
        runCatching { OpenLocationCode.decode(plusCode) }.onSuccess { area ->
            settingsState.setString("target_plus_code", plusCode.trim().uppercase())
            settingsState.setFloat("target_latitude", area.centerLatitude.toFloat())
            settingsState.setFloat("target_longitude", area.centerLongitude.toFloat())
        }
    }

    private fun resolveTarget(context: Map<String, String>): ResolvedTarget? {
        val plusCode = context["target_plus_code"]
            ?: context["input_target_plus_code"]
            ?: context["plus_code"]
            ?: context["input_plus_code"]
        if (!plusCode.isNullOrBlank()) {
            val area = runCatching { OpenLocationCode.decode(plusCode) }.getOrNull()
            if (area != null) {
                return ResolvedTarget(
                    latitude = area.centerLatitude,
                    longitude = area.centerLongitude,
                    plusCode = plusCode.trim().uppercase()
                )
            }
        }

        val latitude = (context["target_latitude"] ?: context["input_target_latitude"] ?: context["latitude"] ?: context["lat"])
            ?.toDoubleOrNull()
        val longitude = (context["target_longitude"] ?: context["input_target_longitude"] ?: context["longitude"] ?: context["lon"] ?: context["lng"])
            ?.toDoubleOrNull()
        return if (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            ResolvedTarget(latitude, longitude, "")
        } else null
    }

    private fun calculateOutputFields(
        targetName: String,
        targetPlusCode: String,
        targetLatitude: Float,
        targetLongitude: Float,
        currentLatitude: Float,
        currentLongitude: Float,
        accuracy: Float,
        heading: Float,
        updateCount: Float,
        arrivalRadius: Float,
        timestampMs: String = System.currentTimeMillis().toString(),
        status: String = "updated",
        hasLocationFix: Boolean = currentLatitude != 0f || currentLongitude != 0f
    ): Map<String, Any?> {
        val hasFix = hasLocationFix
        val result = if (hasFix) {
            distanceAndBearing(
                currentLatitude = currentLatitude.toDouble(),
                currentLongitude = currentLongitude.toDouble(),
                targetLatitude = targetLatitude.toDouble(),
                targetLongitude = targetLongitude.toDouble()
            )
        } else NavigationResult(0f, 0f)
        val relativeBearing = relativeBearingDegrees(result.initialBearingDegrees, heading)
        return mapOf(
            "target_name" to targetName,
            "target_plus_code" to targetPlusCode,
            "target_latitude" to targetLatitude,
            "target_longitude" to targetLongitude,
            "current_latitude" to currentLatitude,
            "current_longitude" to currentLongitude,
            "accuracy_m" to accuracy,
            "distance_m" to result.distanceMeters,
            "bearing_deg" to result.initialBearingDegrees,
            "heading_deg" to heading,
            "relative_bearing_deg" to relativeBearing,
            "arrived" to (hasFix && result.distanceMeters <= arrivalRadius),
            "timestamp_ms" to timestampMs,
            "update_count" to updateCount.toInt(),
            "status" to status,
            "arrival_radius_m" to arrivalRadius
        )
    }

    private fun distanceAndBearing(
        currentLatitude: Double,
        currentLongitude: Double,
        targetLatitude: Double,
        targetLongitude: Double
    ): NavigationResult {
        val result = FloatArray(3)
        Location.distanceBetween(currentLatitude, currentLongitude, targetLatitude, targetLongitude, result)
        val bearing = ((result[1] % 360f) + 360f) % 360f
        return NavigationResult(result[0], bearing)
    }

    private fun relativeBearingDegrees(targetBearing: Float, heading: Float): Float =
        ((targetBearing - heading + 540f) % 360f) - 180f

    private data class NavigationResult(val distanceMeters: Float, val initialBearingDegrees: Float)
    private data class ResolvedTarget(val latitude: Double, val longitude: Double, val plusCode: String)
}
