package com.example.methodmesh.modules.star_spectrum

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
import com.example.methodmesh.settings.SettingsState

object StarSpectrumReferenceFields {
    const val STATUS = "spectrum_reference_status"
    const val ID = "spectrum_reference_id"
    const val SOURCE_IMAGE_URI = "spectrum_reference_source_image_uri"
    const val SOURCE_IMAGE_SHA256 = "spectrum_reference_source_image_sha256"
    const val ANNOTATED_IMAGE_URI = "spectrum_reference_annotated_image_uri"
    const val SPECTRUM_URI = "spectrum_reference_spectrum_uri"
    const val ANCHORS_URI = "spectrum_reference_anchors_uri"
    const val REFERENCE_JSON_URI = "spectrum_reference_json_uri"
    const val PROVENANCE_URI = "spectrum_reference_provenance_uri"
    const val MANIFEST_URI = "spectrum_reference_manifest_uri"
    const val BUNDLE_URI = "spectrum_reference_bundle_uri"
    const val ANNOTATED_IMAGE_SHA256 = "spectrum_reference_annotated_image_sha256"
    const val SPECTRUM_SHA256 = "spectrum_reference_spectrum_sha256"
    const val ANCHORS_SHA256 = "spectrum_reference_anchors_sha256"
    const val REFERENCE_JSON_SHA256 = "spectrum_reference_json_sha256"
    const val PROVENANCE_SHA256 = "spectrum_reference_provenance_sha256"
    const val MANIFEST_SHA256 = "spectrum_reference_manifest_sha256"
    const val BUNDLE_SHA256 = "spectrum_reference_bundle_sha256"
    const val NAME = "spectrum_reference_name"
    const val STAR_NAME = "spectrum_reference_star_name"
    const val SPECTRAL_TYPE = "spectrum_reference_spectral_type"
    const val ANCHOR_COUNT = "spectrum_reference_anchor_count"
    const val ANCHOR_SOURCE = "spectrum_reference_anchor_source"
    const val POLYNOMIAL_ORDER = "spectrum_reference_polynomial_order"
    const val COEFFICIENTS_JSON = "spectrum_reference_coefficients_json"
    const val RMS_NM = "spectrum_reference_rms_nm"
    const val DISPERSION_NM_PER_PX = "spectrum_reference_dispersion_nm_per_px"
    const val WAVELENGTH_MIN_NM = "spectrum_reference_wavelength_min_nm"
    const val WAVELENGTH_MAX_NM = "spectrum_reference_wavelength_max_nm"
    const val TRACE_QUALITY = "spectrum_reference_trace_quality"
    const val CREATED_TIME_ISO = "spectrum_reference_created_time_iso"
    const val REFERENCE_JSON = "spectrum_reference_json"
    const val ERROR = "spectrum_reference_error"

    val outputs = listOf(
        STATUS, ID, SOURCE_IMAGE_URI, SOURCE_IMAGE_SHA256,
        ANNOTATED_IMAGE_URI, SPECTRUM_URI, ANCHORS_URI, REFERENCE_JSON_URI, PROVENANCE_URI, MANIFEST_URI, BUNDLE_URI,
        ANNOTATED_IMAGE_SHA256, SPECTRUM_SHA256, ANCHORS_SHA256, REFERENCE_JSON_SHA256, PROVENANCE_SHA256, MANIFEST_SHA256, BUNDLE_SHA256,
        NAME, STAR_NAME, SPECTRAL_TYPE, ANCHOR_COUNT, ANCHOR_SOURCE, POLYNOMIAL_ORDER,
        COEFFICIENTS_JSON, RMS_NM, DISPERSION_NM_PER_PX, WAVELENGTH_MIN_NM,
        WAVELENGTH_MAX_NM, TRACE_QUALITY, CREATED_TIME_ISO, REFERENCE_JSON, ERROR
    )
}

object As100StarSpectrumReferenceMethod : As100Method {
    const val ID = "astronomy.spectrum.reference.create"
    const val VERSION = "0.2.25"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Create spectrum wavelength reference")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Create spectrum reference",
        version = VERSION,
        description = "Create and persist a reusable wavelength calibration from a Horne-optimally extracted A-type reference-star spectrum using automatic Balmer-pattern consensus with manual review/fallback.",
        outputs = StarSpectrumReferenceFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Astronomy",
            "status" to "Development",
            "maturity" to "DEVELOPMENT",
            "connectivity" to "OFFLINE",
            "interactive" to "true"
        )
    )

    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val invocation = InvocationContext.from(request.context)
        val values = if (request.context[StarSpectrumReferenceFields.STATUS] == "succeeded") {
            request.context.filterKeys { it in StarSpectrumReferenceFields.outputs }
        } else {
            failureValues("Reference creation needs a reference-star image, trace and wavelength anchors. Run the interactive capability surface.")
        }
        return result(request, values, invocation)
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val succeeded = values[StarSpectrumReferenceFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.star_spectrum", ID, VERSION)
        val observation = Observation(
            phenomenon = ID,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (succeeded) emptyMap() else mapOf(StarSpectrumReferenceFields.ERROR to values[StarSpectrumReferenceFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun failureValues(error: String): Map<String, String> = StarSpectrumReferenceFields.outputs.associateWith { "" }.toMutableMap().apply {
        this[StarSpectrumReferenceFields.STATUS] = "failed"
        this[StarSpectrumReferenceFields.ANCHOR_COUNT] = "0"
        this[StarSpectrumReferenceFields.ERROR] = error
    }
}
