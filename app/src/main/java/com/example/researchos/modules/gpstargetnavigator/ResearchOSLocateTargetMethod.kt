package com.example.researchos.modules.gpstargetnavigator

import android.location.Location
import com.example.researchos.core.ResearchRuntime
import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.Entity
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.core.researchos.MethodObjectType
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.SpatialContext
import com.example.researchos.core.researchos.State
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.platform.location.AndroidLocationDeviceService
import com.example.researchos.settings.SettingsState

/**
 * Native AS1.00 method for locating/navigating relative to a configured target.
 *
 * The GPS interaction screen owns permission prompts and live Compose display.
 * This method owns the research operation: location signal + target context ->
 * navigation observation/state/transformation and transport output fields.
 */
object As100LocateTargetMethod : As100Method {
    const val ID = "gps_target_navigator"
    const val VERSION = "0.3.0"

    override val id: String = ID

    override val ref: ArchitectureRef = ArchitectureRef(
        id = ArchitectureId(ID),
        type = "Method",
        label = "Locate Target"
    )

    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Locate Target",
        version = VERSION,
        description = "Interpret an Android location-fix signal against a configured target coordinate and produce navigation evidence.",
        inputs = listOf(AndroidLocationDeviceService.SIGNAL_TYPE_LOCATION_FIX),
        outputs = listOf(
            "target_name",
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
            "status"
        ),

        parameters = mapOf(
            "category" to "Mapping",
            "status" to "Experimental",
            "device_service" to AndroidLocationDeviceService.SERVICE_ID
        )
    )

    override val contract: MethodContract = MethodContract(
        method = ref,
        acceptedSignals = listOf(AndroidLocationDeviceService.SIGNAL_TYPE_LOCATION_FIX),
        requiredContext = listOf("target_latitude", "target_longitude", "arrival_radius_m"),
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
            outputValues(settingsState).mapValues { it.value.toString() }
        } else {
            val currentLatitude = signal?.payload?.get("latitude")?.toDoubleOrNull()
            val currentLongitude = signal?.payload?.get("longitude")?.toDoubleOrNull()
            val targetLatitude = request.context["target_latitude"]?.toDoubleOrNull()
            val targetLongitude = request.context["target_longitude"]?.toDoubleOrNull()
            val arrivalRadius = request.context["arrival_radius_m"]?.toFloatOrNull() ?: 10f
            if (currentLatitude != null && currentLongitude != null && targetLatitude != null && targetLongitude != null) {
                calculateOutputFields(
                    targetName = request.context["target_name"].orEmpty(),
                    targetLatitude = targetLatitude.toFloat(),
                    targetLongitude = targetLongitude.toFloat(),
                    currentLatitude = currentLatitude.toFloat(),
                    currentLongitude = currentLongitude.toFloat(),
                    accuracy = signal.payload["accuracy_m"]?.toFloatOrNull() ?: 0f,
                    heading = request.context["heading_deg"]?.toFloatOrNull() ?: 0f,
                    updateCount = request.context["update_count"]?.toFloatOrNull() ?: 0f,
                    arrivalRadius = arrivalRadius
                ).mapValues { it.value.toString() }
            } else {
                emptyMap()
            }
        }

        val targetId = output["target_name"].orEmpty().ifBlank {
            "${output["target_latitude"].orEmpty()},${output["target_longitude"].orEmpty()}"
        }
        val targetEntity = Entity(
            id = ArchitectureId("target:$targetId"),
            entityType = "SpatialTarget",
            attributes = mapOf(
                "target_name" to output["target_name"].orEmpty(),
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
                provider = signal?.provenance?.provider ?: "researchos.location",
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
                "arrival_radius_m" to request.context["arrival_radius_m"].orEmpty()
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
                provider = "researchos.location",
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
        settingsState.setString("status", "updated")
    }

    fun outputValues(settingsState: SettingsState): Map<String, Any?> {
        val targetLatitude = settingsState.getFloat("target_latitude")
        val targetLongitude = settingsState.getFloat("target_longitude")
        val currentLatitude = settingsState.getFloat("current_latitude")
        val currentLongitude = settingsState.getFloat("current_longitude")
        val arrivalRadius = settingsState.getFloat("arrival_radius_m")

        if (currentLatitude != 0f || currentLongitude != 0f) {
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
                targetLatitude = targetLatitude,
                targetLongitude = targetLongitude,
                currentLatitude = currentLatitude,
                currentLongitude = currentLongitude,
                accuracy = settingsState.getFloat("accuracy_m"),
                heading = settingsState.getFloat("heading_deg"),
                updateCount = settingsState.getFloat("update_count"),
                arrivalRadius = arrivalRadius,
                timestampMs = settingsState.getString("timestamp_ms"),
                status = settingsState.getString("status")
            )
    }

    fun recordNavigationOutcome(fields: Map<String, Any?>): ExecutionResult {
        val request = request(action = ID, context = emptyMap())
        val provenance = ProvenanceContext(
            provider = "researchos.presentation.gps_target_navigator",
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
        ).also { ResearchRuntime.session.record(it) }
    }

    private fun calculateOutputFields(
        targetName: String,
        targetLatitude: Float,
        targetLongitude: Float,
        currentLatitude: Float,
        currentLongitude: Float,
        accuracy: Float,
        heading: Float,
        updateCount: Float,
        arrivalRadius: Float,
        timestampMs: String = System.currentTimeMillis().toString(),
        status: String = "updated"
    ): Map<String, Any?> {
        val result = if (currentLatitude != 0f || currentLongitude != 0f) {
            distanceAndBearing(
                currentLatitude = currentLatitude.toDouble(),
                currentLongitude = currentLongitude.toDouble(),
                targetLatitude = targetLatitude.toDouble(),
                targetLongitude = targetLongitude.toDouble()
            )
        } else {
            NavigationResult(0f, 0f)
        }
        val relativeBearing = relativeBearingDegrees(result.initialBearingDegrees, heading)
        return mapOf(
            "target_name" to targetName,
            "target_latitude" to targetLatitude,
            "target_longitude" to targetLongitude,
            "current_latitude" to currentLatitude,
            "current_longitude" to currentLongitude,
            "accuracy_m" to accuracy,
            "distance_m" to result.distanceMeters,
            "bearing_deg" to result.initialBearingDegrees,
            "heading_deg" to heading,
            "relative_bearing_deg" to relativeBearing,
            "arrived" to (result.distanceMeters <= arrivalRadius && (currentLatitude != 0f || currentLongitude != 0f)),
            "timestamp_ms" to timestampMs,
            "update_count" to updateCount.toInt(),
            "status" to status
        )
    }

    private fun distanceAndBearing(
        currentLatitude: Double,
        currentLongitude: Double,
        targetLatitude: Double,
        targetLongitude: Double
    ): NavigationResult {
        val result = FloatArray(3)
        Location.distanceBetween(
            currentLatitude,
            currentLongitude,
            targetLatitude,
            targetLongitude,
            result
        )
        val bearing = ((result[1] % 360f) + 360f) % 360f
        return NavigationResult(
            distanceMeters = result[0],
            initialBearingDegrees = bearing
        )
    }

    private fun relativeBearingDegrees(targetBearing: Float, heading: Float): Float =
        ((targetBearing - heading + 540f) % 360f) - 180f

    private data class NavigationResult(
        val distanceMeters: Float,
        val initialBearingDegrees: Float
    )
}
