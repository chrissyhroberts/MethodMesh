package com.example.methodmesh.modules.emergency

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
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
import com.example.methodmesh.modules.pluscodecapture.OpenLocationCode
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object EmergencyStatusFields {
    const val STATUS = "emergency_status"
    const val PREPAREDNESS = "emergency_preparedness"
    const val PRIMARY_ALERT = "emergency_primary_alert"
    const val PRIMARY_DOMAIN = "emergency_primary_domain"
    const val SOURCE_ID = "emergency_source_id"
    const val INCOMPLETE_COVERAGE = "emergency_incomplete_critical_coverage"
    const val CHECKED_AT = "emergency_checked_at_iso"
    const val EXPLANATION = "emergency_status_explanation"
    const val AUDIT_JSON = "emergency_status_audit_json"
    const val ERROR = "emergency_status_error"
    val outputs = listOf(STATUS, PREPAREDNESS, PRIMARY_ALERT, PRIMARY_DOMAIN, SOURCE_ID, INCOMPLETE_COVERAGE, CHECKED_AT, EXPLANATION, AUDIT_JSON, ERROR)
}

object EmergencyLocationFields {
    const val STATUS = "emergency_location_status"
    const val LATITUDE = "emergency_latitude"
    const val LONGITUDE = "emergency_longitude"
    const val ACCURACY_M = "emergency_accuracy_m"
    const val PLUS_CODE = "emergency_plus_code"
    const val CAPTURED_AT = "emergency_location_captured_at_iso"
    const val SHARE_TEXT = "emergency_location_share_text"
    const val AUDIT_JSON = "emergency_location_audit_json"
    const val ERROR = "emergency_location_error"
    val outputs = listOf(STATUS, LATITUDE, LONGITUDE, ACCURACY_M, PLUS_CODE, CAPTURED_AT, SHARE_TEXT, AUDIT_JSON, ERROR)
}

object EmergencyExitFields {
    const val STATUS = "emergency_exit_status"
    const val SUMMARY = "emergency_exit_summary"
    const val OPTION_COUNT = "emergency_exit_option_count"
    const val OPTIONS_JSON = "emergency_exit_options_json"
    const val SOURCE_NOTE = "emergency_exit_source_note"
    const val ERROR = "emergency_exit_error"
    val outputs = listOf(STATUS, SUMMARY, OPTION_COUNT, OPTIONS_JSON, SOURCE_NOTE, ERROR)
}

object EmergencyReferenceFields {
    const val STATUS = "emergency_reference_status"
    const val GUIDE_ID = "emergency_guide_id"
    const val TITLE = "emergency_guide_title"
    const val NOW_TEXT = "emergency_guide_now"
    const val THIRTY_SECOND_TEXT = "emergency_guide_30_sec"
    const val SOURCE = "emergency_guide_source"
    const val SOURCE_VERSION = "emergency_guide_source_version"
    const val REVIEW_DUE = "emergency_guide_review_due"
    const val ERROR = "emergency_reference_error"
    val outputs = listOf(STATUS, GUIDE_ID, TITLE, NOW_TEXT, THIRTY_SECOND_TEXT, SOURCE, SOURCE_VERSION, REVIEW_DUE, ERROR)
}

object EmergencyPackFields {
    const val STATUS = "emergency_pack_status"
    const val REGION = "emergency_pack_region"
    const val COMPONENTS = "emergency_pack_components"
    const val PREPAREDNESS = "emergency_pack_preparedness"
    const val MESSAGE = "emergency_pack_message"
    const val ERROR = "emergency_pack_error"
    val outputs = listOf(STATUS, REGION, COMPONENTS, PREPAREDNESS, MESSAGE, ERROR)
}

private const val EMERGENCY_VERSION = "0.2.0"

object As100EmergencyStatusMethod : As100Method {
    const val ID = "emergency.status"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Emergency local status")
    override val descriptor = emergencyDescriptor(
        id = ID,
        name = "Emergency local status",
        description = "Return the explainable local emergency risk and preparedness state without treating missing data as green.",
        outputs = EmergencyStatusFields.outputs,
        phenomenon = "emergency.local.status"
    )
    override val contract = emergencyContract(ref, descriptor)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        unsupported(request, "Emergency status requires the Android cache/source boundary.")

    fun values(snapshot: EmergencyStatusSnapshot): Map<String, String> {
        val alert = snapshot.primaryAlert
        val coverageJson = JSONArray().apply {
            snapshot.coverage.forEach { item ->
                put(JSONObject()
                    .put("domain", item.domain.name)
                    .put("state", item.state.name)
                    .put("critical", item.critical)
                    .put("source_id", item.sourceId)
                    .put("checked_at", item.checkedAt?.toString().orEmpty())
                    .put("detail", item.detail))
            }
        }
        val audit = JSONObject()
            .put("method_id", ID)
            .put("risk", snapshot.localRisk.name)
            .put("preparedness", snapshot.preparedness.name)
            .put("checked_at", snapshot.checkedAt.toString())
            .put("incomplete_critical_coverage", snapshot.incompleteCriticalCoverage)
            .put("primary_alert_id", alert?.eventId.orEmpty())
            .put("coverage", coverageJson)
            .put("explanation", JSONArray(snapshot.explanation))
            .toString()
        return linkedMapOf(
            EmergencyStatusFields.STATUS to snapshot.localRisk.name,
            EmergencyStatusFields.PREPAREDNESS to snapshot.preparedness.name,
            EmergencyStatusFields.PRIMARY_ALERT to alert?.title.orEmpty(),
            EmergencyStatusFields.PRIMARY_DOMAIN to alert?.domain?.name.orEmpty(),
            EmergencyStatusFields.SOURCE_ID to alert?.sourceId.orEmpty(),
            EmergencyStatusFields.INCOMPLETE_COVERAGE to snapshot.incompleteCriticalCoverage.toString(),
            EmergencyStatusFields.CHECKED_AT to snapshot.checkedAt.toString(),
            EmergencyStatusFields.EXPLANATION to snapshot.explanation.joinToString(" | "),
            EmergencyStatusFields.AUDIT_JSON to audit,
            EmergencyStatusFields.ERROR to ""
        )
    }

    fun result(request: ExecutionRequest, snapshot: EmergencyStatusSnapshot, invocation: InvocationContext?): ExecutionResult =
        emergencyResult(request, ref, ID, "emergency.local.status", values(snapshot), invocation)
}

object As100EmergencyLocationMethod : As100Method {
    const val ID = "emergency.location"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Emergency location")
    override val descriptor = emergencyDescriptor(
        id = ID,
        name = "Emergency location",
        description = "Capture GPS coordinates and a locally calculated Plus Code for copying or sharing.",
        outputs = EmergencyLocationFields.outputs,
        phenomenon = "emergency.location.capture"
    )
    override val contract = emergencyContract(ref, descriptor)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val settings = settingsState?.asMap()?.mapValues { it.value.toString() } ?: request.context
        val lat = settings.value("latitude")?.toDoubleOrNull()
        val lon = settings.value("longitude")?.toDoubleOrNull()
        val codeLength = settings.value("plus_code_length")?.toIntOrNull()?.coerceIn(8, 10)?.let { if (it % 2 == 0) it else it - 1 } ?: 10
        if (lat == null || lon == null) return unsupported(request, "GPS capture requires the Android location boundary.")
        return result(request, locationValues(lat, lon, settings.value("accuracy_m")?.toDoubleOrNull(), codeLength = codeLength), InvocationContext.from(request.context))
    }

    fun locationValues(latitude: Double, longitude: Double, accuracyM: Double? = null, capturedAt: Instant = Instant.now(), codeLength: Int = 10): Map<String, String> {
        val plusCode = OpenLocationCode.encode(latitude, longitude, codeLength.coerceIn(8, 10).let { if (it % 2 == 0) it else it - 1 })
        val coords = "${latitude.coord()}, ${longitude.coord()}"
        val share = "$plusCode\n$coords"
        val audit = JSONObject()
            .put("method_id", ID)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("accuracy_m", accuracyM)
            .put("plus_code", plusCode)
            .put("captured_at", capturedAt.toString())
            .toString()
        return linkedMapOf(
            EmergencyLocationFields.STATUS to "succeeded",
            EmergencyLocationFields.LATITUDE to latitude.coord(),
            EmergencyLocationFields.LONGITUDE to longitude.coord(),
            EmergencyLocationFields.ACCURACY_M to accuracyM?.number().orEmpty(),
            EmergencyLocationFields.PLUS_CODE to plusCode,
            EmergencyLocationFields.CAPTURED_AT to capturedAt.toString(),
            EmergencyLocationFields.SHARE_TEXT to share,
            EmergencyLocationFields.AUDIT_JSON to audit,
            EmergencyLocationFields.ERROR to ""
        )
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult =
        emergencyResult(request, ref, ID, "emergency.location.capture", values, invocation)
}

object As100EmergencyExitFindMethod : As100Method {
    const val ID = "emergency.exit.find"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Find emergency exit options")
    override val descriptor = emergencyDescriptor(
        id = ID,
        name = "Find emergency exit options",
        description = "Rank sparse offline strategic exit POIs near a location without claiming that facilities are operational.",
        outputs = EmergencyExitFields.outputs,
        phenomenon = "emergency.exit.options"
    )
    override val contract = emergencyContract(ref, descriptor)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        unsupported(request, "Exit ranking requires installed Android emergency POI assets and a location fix.")

    fun values(options: List<EmergencyRankedPoi>): Map<String, String> {
        val json = JSONArray().apply {
            options.forEach { item ->
                put(JSONObject()
                    .put("id", item.poi.id)
                    .put("name", item.poi.name)
                    .put("category", item.poi.category.name)
                    .put("latitude", item.poi.latitude)
                    .put("longitude", item.poi.longitude)
                    .put("distance_m", item.distanceM)
                    .put("bearing_deg", item.bearingDeg)
                    .put("code", item.poi.code)
                    .put("source_id", item.poi.sourceId)
                    .put("source_version", item.poi.sourceVersion)
                    .put("operational_status", item.poi.operationalStatus)
                    .put("plus_code", OpenLocationCode.encode(item.poi.latitude, item.poi.longitude, 10)))
            }
        }
        val summary = options.take(4).joinToString("; ") { item ->
            "${item.poi.name} ${"%.1f".format(item.distanceM / 1000.0)} km"
        }
        return linkedMapOf(
            EmergencyExitFields.STATUS to if (options.isEmpty()) "no_offline_options" else "succeeded",
            EmergencyExitFields.SUMMARY to summary,
            EmergencyExitFields.OPTION_COUNT to options.size.toString(),
            EmergencyExitFields.OPTIONS_JSON to json.toString(),
            EmergencyExitFields.SOURCE_NOTE to "Offline POIs indicate known locations only. Operational status must be verified when possible.",
            EmergencyExitFields.ERROR to if (options.isEmpty()) "No strategic exit POI pack is installed for ranking." else ""
        )
    }

    fun result(request: ExecutionRequest, options: List<EmergencyRankedPoi>, invocation: InvocationContext?): ExecutionResult =
        emergencyResult(request, ref, ID, "emergency.exit.options", values(options), invocation, succeeded = options.isNotEmpty())
}

object As100EmergencyReferenceOpenMethod : As100Method {
    const val ID = "emergency.reference.open"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Open emergency reference")
    override val descriptor = emergencyDescriptor(
        id = ID,
        name = "Open emergency reference",
        description = "Open a versioned offline emergency reference card with source and review metadata.",
        outputs = EmergencyReferenceFields.outputs,
        phenomenon = "emergency.reference.open"
    )
    override val contract = emergencyContract(ref, descriptor)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val settings = settingsState?.asMap()?.mapValues { it.value.toString() } ?: request.context
        val guideId = settings.value("guide_id") ?: "adult_cpr_aed"
        val item = EmergencyCoreData.referenceItems.firstOrNull { it.id == guideId }
            ?: return emergencyResult(request, ref, ID, "emergency.reference.open", failureReference(guideId), InvocationContext.from(request.context), false)
        return result(request, item, InvocationContext.from(request.context))
    }

    fun values(item: EmergencyContentItem): Map<String, String> = linkedMapOf(
        EmergencyReferenceFields.STATUS to if (item.validatedAt == null) "development_unvalidated" else "succeeded",
        EmergencyReferenceFields.GUIDE_ID to item.id,
        EmergencyReferenceFields.TITLE to item.title,
        EmergencyReferenceFields.NOW_TEXT to item.nowText,
        EmergencyReferenceFields.THIRTY_SECOND_TEXT to item.thirtySecondText,
        EmergencyReferenceFields.SOURCE to listOf(item.sourceOrganisation, item.sourceTitle).filter(String::isNotBlank).joinToString(" - "),
        EmergencyReferenceFields.SOURCE_VERSION to item.sourcePublicationVersion,
        EmergencyReferenceFields.REVIEW_DUE to item.reviewDueAt?.toString().orEmpty(),
        EmergencyReferenceFields.ERROR to if (item.sourcePublicationVersion == "unvalidated") "This guide is an intentionally unpopulated Development slot." else ""
    )

    fun result(request: ExecutionRequest, item: EmergencyContentItem, invocation: InvocationContext?): ExecutionResult =
        emergencyResult(request, ref, ID, "emergency.reference.open", values(item), invocation, succeeded = item.sourcePublicationVersion != "unvalidated")

    private fun failureReference(id: String) = linkedMapOf(
        EmergencyReferenceFields.STATUS to "failed",
        EmergencyReferenceFields.GUIDE_ID to id,
        EmergencyReferenceFields.TITLE to "",
        EmergencyReferenceFields.NOW_TEXT to "",
        EmergencyReferenceFields.THIRTY_SECOND_TEXT to "",
        EmergencyReferenceFields.SOURCE to "",
        EmergencyReferenceFields.SOURCE_VERSION to "",
        EmergencyReferenceFields.REVIEW_DUE to "",
        EmergencyReferenceFields.ERROR to "Unknown emergency guide: $id"
    )
}

object As100EmergencyPackPrepareMethod : As100Method {
    const val ID = "emergency.pack.prepare"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Prepare emergency location pack")
    override val descriptor = emergencyDescriptor(
        id = ID,
        name = "Prepare emergency location pack",
        description = "Prepare or inspect a regional offline emergency pack. Download integration remains Development.",
        outputs = EmergencyPackFields.outputs,
        phenomenon = "emergency.pack.prepare"
    )
    override val contract = emergencyContract(ref, descriptor)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        unsupported(request, "Regional emergency pack download requires the Android storage/network boundary.")

    fun values(region: String, preparedness: EmergencyPreparednessState): Map<String, String> = linkedMapOf(
        EmergencyPackFields.STATUS to "development",
        EmergencyPackFields.REGION to region,
        EmergencyPackFields.COMPONENTS to "strategic POIs | offline map/routing hook | dense local POIs | authoritative local guidance",
        EmergencyPackFields.PREPAREDNESS to preparedness.name,
        EmergencyPackFields.MESSAGE to "Pack manifests and local install state are supported; network downloader/provider adapters are not yet configured.",
        EmergencyPackFields.ERROR to ""
    )

    fun result(request: ExecutionRequest, region: String, preparedness: EmergencyPreparednessState, invocation: InvocationContext?): ExecutionResult =
        emergencyResult(request, ref, ID, "emergency.pack.prepare", values(region, preparedness), invocation)
}

private fun emergencyDescriptor(
    id: String,
    name: String,
    description: String,
    outputs: List<String>,
    phenomenon: String
) = MethodDescriptor(
    id = ArchitectureId(id),
    methodType = MethodObjectType.Workflow,
    name = name,
    version = EMERGENCY_VERSION,
    description = description,
    outputs = outputs,
    graphOutputs = listOf(phenomenon),
    parameters = mapOf(
        "category" to "Emergency",
        "status" to "Development",
        "offline_first" to "true",
        "persistent_shade" to "deferred"
    )
)

private fun emergencyContract(ref: ArchitectureRef, descriptor: MethodDescriptor) = MethodContract(
    method = ref,
    producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
    producedFields = descriptor.outputs,
    producedGraphOutputs = descriptor.graphOutputs
)

private fun emergencyResult(
    request: ExecutionRequest,
    methodRef: ArchitectureRef,
    methodId: String,
    phenomenon: String,
    values: Map<String, String>,
    invocation: InvocationContext?,
    succeeded: Boolean = true
): ExecutionResult {
    val provenance = ProvenanceContext("methodmesh.emergency", methodId, EMERGENCY_VERSION)
    val observation = Observation(
        phenomenon = phenomenon,
        subject = null,
        values = values,
        temporalContext = request.temporalContext,
        provenance = provenance
    )
    val status = if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed
    val transformation = Transformation(
        action = methodId,
        method = methodRef,
        outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
        status = status,
        temporalContext = request.temporalContext,
        provenance = provenance
    )
    return As100ExecutionEngine.complete(
        request = request,
        status = status,
        observations = listOf(observation),
        transformations = listOf(transformation),
        diagnostics = if (succeeded) emptyMap() else mapOf("emergency_error" to values.entries.firstOrNull { it.key.endsWith("_error") }?.value.orEmpty())
    ).withInvocationContext(invocation)
}

private fun unsupported(request: ExecutionRequest, reason: String): ExecutionResult =
    As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to reason))

private fun Map<String, String>.value(key: String): String? =
    (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

private fun Double.coord(): String = "%.8f".format(this).trimEnd('0').trimEnd('.')
private fun Double.number(): String = "%.2f".format(this).trimEnd('0').trimEnd('.')
