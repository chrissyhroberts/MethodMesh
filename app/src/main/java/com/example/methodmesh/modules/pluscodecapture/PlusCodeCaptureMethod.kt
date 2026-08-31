package com.example.methodmesh.modules.pluscodecapture

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONObject
import java.time.Instant

object PlusCodeCaptureFields {
    const val STATUS = "plus_code_status"
    const val PLUS_CODE = "plus_code"
    const val CODE_LENGTH = "plus_code_length"
    const val CENTROID_LATITUDE = "plus_code_centroid_latitude"
    const val CENTROID_LONGITUDE = "plus_code_centroid_longitude"
    const val GPS_LATITUDE = "plus_code_gps_latitude"
    const val GPS_LONGITUDE = "plus_code_gps_longitude"
    const val GPS_ACCURACY_M = "plus_code_gps_accuracy_m"
    const val GPS_FIX_COUNT = "plus_code_gps_fix_count"
    const val BASEMAP_MODE = "plus_code_basemap_mode"
    const val BASEMAP_ACTUAL_SOURCE = "plus_code_basemap_actual_source"
    const val SELECTED_TIME_ISO = "plus_code_selected_time_iso"
    const val AUDIT_JSON = "plus_code_audit_json"
    const val ERROR = "plus_code_error"

    val outputs = listOf(
        STATUS,
        PLUS_CODE,
        CODE_LENGTH,
        CENTROID_LATITUDE,
        CENTROID_LONGITUDE,
        GPS_LATITUDE,
        GPS_LONGITUDE,
        GPS_ACCURACY_M,
        GPS_FIX_COUNT,
        BASEMAP_MODE,
        BASEMAP_ACTUAL_SOURCE,
        SELECTED_TIME_ISO,
        AUDIT_JSON,
        ERROR
    )
}

object As100PlusCodeCaptureMethod : As100Method {
    const val ID = "plus_code.capture"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Plus Code capture")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Plus Code capture",
        version = VERSION,
        description = "Capture a full Open Location Code by combining GPS with a locally generated selectable grid.",
        outputs = PlusCodeCaptureFields.outputs,
        graphOutputs = listOf("location.plus_code.capture"),
        parameters = mapOf(
            "category" to "Mapping",
            "status" to "Production",
            "offline" to "true"
        )
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
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
        val values = if (settingsState != null) {
            captureValues(settingsState.asMap().mapValues { it.value.toString() })
        } else {
            captureValues(request.context)
        }
        return result(request, values, InvocationContext.from(request.context))
    }

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[PlusCodeCaptureFields.STATUS] == "succeeded"
        val code = values[PlusCodeCaptureFields.PLUS_CODE].orEmpty()
        val entity = Entity(
            id = ArchitectureId("plus-code:${code.ifBlank { System.currentTimeMillis().toString() }}"),
            entityType = "PlusCodeLocation",
            attributes = values
        )
        val provenance = ProvenanceContext("methodmesh.location.plus_code", ID, VERSION)
        val observation = Observation(
            phenomenon = "location.plus_code.capture",
            subject = ArchitectureRef(entity.id, entity.objectType, code),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(PlusCodeCaptureFields.ERROR to values[PlusCodeCaptureFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun captureValues(settings: Map<String, String>): Map<String, String> {
        val rawCode = settings.value("plus_code").orEmpty()
        val codeLength = settings.value("code_length")?.toIntOrNull()?.coerceIn(2, 10)?.let {
            if (it % 2 == 0) it else it - 1
        } ?: 10
        val selectedLatitude = settings.value("selected_centroid_latitude")?.toDoubleOrNull()
            ?: settings.value("selected_latitude")?.toDoubleOrNull()
        val selectedLongitude = settings.value("selected_centroid_longitude")?.toDoubleOrNull()
            ?: settings.value("selected_longitude")?.toDoubleOrNull()
        val gpsLatitude = settings.value("gps_latitude")?.toDoubleOrNull()
        val gpsLongitude = settings.value("gps_longitude")?.toDoubleOrNull()
        val gpsAccuracy = settings.value("gps_accuracy_m")?.toDoubleOrNull()
        val gpsFixCount = settings.value("gps_fix_count")?.toIntOrNull() ?: 0
        val basemapMode = settings.value("basemap_mode") ?: "auto"
        val basemapActualSource = settings.value("basemap_actual_source") ?: "blank_grid"
        val selectedTime = settings.value("selected_time_iso") ?: Instant.now().toString()

        val area = runCatching {
            when {
                rawCode.isNotBlank() -> OpenLocationCode.decode(rawCode)
                selectedLatitude != null && selectedLongitude != null -> OpenLocationCode.cellFor(selectedLatitude, selectedLongitude, codeLength)
                gpsLatitude != null && gpsLongitude != null -> OpenLocationCode.cellFor(gpsLatitude, gpsLongitude, codeLength)
                else -> error("No selected cell or GPS fix is available.")
            }
        }.getOrElse { error ->
            return failure(
                codeLength = codeLength,
                gpsLatitude = gpsLatitude,
                gpsLongitude = gpsLongitude,
                gpsAccuracy = gpsAccuracy,
                gpsFixCount = gpsFixCount,
                basemapMode = basemapMode,
                basemapActualSource = basemapActualSource,
                error = error.message ?: "Could not calculate Plus Code."
            )
        }

        val audit = auditJson(
            area = area,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude,
            gpsAccuracy = gpsAccuracy,
            gpsFixCount = gpsFixCount,
            basemapMode = basemapMode,
            basemapActualSource = basemapActualSource,
            selectedTime = selectedTime,
            error = ""
        )

        return linkedMapOf(
            PlusCodeCaptureFields.STATUS to "succeeded",
            PlusCodeCaptureFields.PLUS_CODE to area.code,
            PlusCodeCaptureFields.CODE_LENGTH to area.codeLength.toString(),
            PlusCodeCaptureFields.CENTROID_LATITUDE to area.centerLatitude.formatCoordinate(),
            PlusCodeCaptureFields.CENTROID_LONGITUDE to area.centerLongitude.formatCoordinate(),
            PlusCodeCaptureFields.GPS_LATITUDE to gpsLatitude?.formatCoordinate().orEmpty(),
            PlusCodeCaptureFields.GPS_LONGITUDE to gpsLongitude?.formatCoordinate().orEmpty(),
            PlusCodeCaptureFields.GPS_ACCURACY_M to gpsAccuracy?.formatNumber().orEmpty(),
            PlusCodeCaptureFields.GPS_FIX_COUNT to gpsFixCount.toString(),
            PlusCodeCaptureFields.BASEMAP_MODE to basemapMode,
            PlusCodeCaptureFields.BASEMAP_ACTUAL_SOURCE to basemapActualSource,
            PlusCodeCaptureFields.SELECTED_TIME_ISO to selectedTime,
            PlusCodeCaptureFields.AUDIT_JSON to audit,
            PlusCodeCaptureFields.ERROR to ""
        )
    }

    private fun failure(
        codeLength: Int,
        gpsLatitude: Double?,
        gpsLongitude: Double?,
        gpsAccuracy: Double?,
        gpsFixCount: Int,
        basemapMode: String,
        basemapActualSource: String,
        error: String
    ): Map<String, String> {
        val audit = JSONObject()
            .put("methodmesh_method_id", ID)
            .put("status", "failed")
            .put("error", error)
            .put("code_length", codeLength)
            .put("gps", JSONObject()
                .put("latitude", gpsLatitude)
                .put("longitude", gpsLongitude)
                .put("accuracy_m", gpsAccuracy)
                .put("fix_count", gpsFixCount)
            )
            .put("basemap", JSONObject()
                .put("mode", basemapMode)
                .put("actual_source", basemapActualSource)
            )
            .put("timestamp_iso", Instant.now().toString())
            .toString()
        return linkedMapOf(
            PlusCodeCaptureFields.STATUS to "failed",
            PlusCodeCaptureFields.PLUS_CODE to "",
            PlusCodeCaptureFields.CODE_LENGTH to codeLength.toString(),
            PlusCodeCaptureFields.CENTROID_LATITUDE to "",
            PlusCodeCaptureFields.CENTROID_LONGITUDE to "",
            PlusCodeCaptureFields.GPS_LATITUDE to gpsLatitude?.formatCoordinate().orEmpty(),
            PlusCodeCaptureFields.GPS_LONGITUDE to gpsLongitude?.formatCoordinate().orEmpty(),
            PlusCodeCaptureFields.GPS_ACCURACY_M to gpsAccuracy?.formatNumber().orEmpty(),
            PlusCodeCaptureFields.GPS_FIX_COUNT to gpsFixCount.toString(),
            PlusCodeCaptureFields.BASEMAP_MODE to basemapMode,
            PlusCodeCaptureFields.BASEMAP_ACTUAL_SOURCE to basemapActualSource,
            PlusCodeCaptureFields.SELECTED_TIME_ISO to Instant.now().toString(),
            PlusCodeCaptureFields.AUDIT_JSON to audit,
            PlusCodeCaptureFields.ERROR to error
        )
    }

    private fun auditJson(
        area: PlusCodeArea,
        gpsLatitude: Double?,
        gpsLongitude: Double?,
        gpsAccuracy: Double?,
        gpsFixCount: Int,
        basemapMode: String,
        basemapActualSource: String,
        selectedTime: String,
        error: String
    ): String = JSONObject()
        .put("methodmesh_method_id", ID)
        .put("status", if (error.isBlank()) "succeeded" else "failed")
        .put("plus_code", area.code)
        .put("code_length", area.codeLength)
        .put("selected_cell", JSONObject()
            .put("south", area.south)
            .put("west", area.west)
            .put("north", area.north)
            .put("east", area.east)
            .put("center_latitude", area.centerLatitude)
            .put("center_longitude", area.centerLongitude)
        )
        .put("gps", JSONObject()
            .put("latitude", gpsLatitude)
            .put("longitude", gpsLongitude)
            .put("accuracy_m", gpsAccuracy)
            .put("fix_count", gpsFixCount)
        )
        .put("basemap", JSONObject()
            .put("mode", basemapMode)
            .put("actual_source", basemapActualSource)
            .put("fallback_used", basemapMode == "auto" && basemapActualSource != "online")
        )
        .put("timestamp_iso", selectedTime)
        .toString()

    private fun Map<String, String>.value(key: String): String? =
        (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

    private fun Double.formatCoordinate(): String = "%.8f".format(this).trimEnd('0').trimEnd('.')
    private fun Double.formatNumber(): String = "%.3f".format(this).trimEnd('0').trimEnd('.')
}
