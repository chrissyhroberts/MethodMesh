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

object StarSpectrumAnalyseFields {
    const val STATUS = "spectrum_status"
    const val SOURCE_IMAGE_URI = "spectrum_source_image_uri"
    const val SOURCE_IMAGE_SHA256 = "spectrum_source_image_sha256"
    const val ANNOTATED_IMAGE_URI = "spectrum_annotated_image_uri"
    const val DATA_URI = "spectrum_data_uri"
    const val FEATURES_URI = "spectrum_features_uri"
    const val METADATA_URI = "spectrum_metadata_uri"
    const val PROVENANCE_URI = "spectrum_provenance_uri"
    const val MANIFEST_URI = "spectrum_manifest_uri"
    const val BUNDLE_URI = "spectrum_bundle_uri"
    const val ANNOTATED_IMAGE_SHA256 = "spectrum_annotated_image_sha256"
    const val DATA_SHA256 = "spectrum_data_sha256"
    const val FEATURES_SHA256 = "spectrum_features_sha256"
    const val METADATA_SHA256 = "spectrum_metadata_sha256"
    const val PROVENANCE_SHA256 = "spectrum_provenance_sha256"
    const val MANIFEST_SHA256 = "spectrum_manifest_sha256"
    const val BUNDLE_SHA256 = "spectrum_bundle_sha256"
    const val FEATURES_JSON = "spectrum_features_json"
    const val FEATURES_TEXT = "spectrum_features_text"
    const val FEATURE_COUNT = "spectrum_feature_count"
    const val REFERENCE_ID = "spectrum_reference_id"
    const val REFERENCE_NAME = "spectrum_reference_name"
    const val CALIBRATION_RMS_NM = "spectrum_calibration_rms_nm"
    const val CALIBRATION_CONFIDENCE = "spectrum_calibration_confidence"
    const val CALIBRATION_WARNING = "spectrum_calibration_warning"
    const val WAVELENGTH_MIN_NM = "spectrum_wavelength_min_nm"
    const val WAVELENGTH_MAX_NM = "spectrum_wavelength_max_nm"
    const val TRACE_QUALITY = "spectrum_trace_quality"
    const val TRACE_MEAN_CORRECTION_PX = "spectrum_trace_mean_correction_px"
    const val MEAN_SNR = "spectrum_mean_snr"
    const val CONTAMINATED_FRACTION = "spectrum_contaminated_fraction"
    const val SOURCE_WIDTH_PX = "spectrum_source_width_px"
    const val SOURCE_HEIGHT_PX = "spectrum_source_height_px"
    const val INTENSITY_SEMANTICS = "spectrum_intensity_semantics"
    const val INSTRUMENT_RESPONSE_CORRECTED = "spectrum_instrument_response_corrected"
    const val CONTINUUM_SEMANTICS = "spectrum_continuum_semantics"
    const val METADATA_JSON = "spectrum_metadata_json"
    const val ERROR = "spectrum_error"

    val outputs = listOf(
        STATUS,
        SOURCE_IMAGE_URI, SOURCE_IMAGE_SHA256,
        ANNOTATED_IMAGE_URI,
        DATA_URI,
        FEATURES_URI,
        METADATA_URI,
        PROVENANCE_URI,
        MANIFEST_URI,
        BUNDLE_URI,
        ANNOTATED_IMAGE_SHA256,
        DATA_SHA256,
        FEATURES_SHA256,
        METADATA_SHA256,
        PROVENANCE_SHA256,
        MANIFEST_SHA256,
        BUNDLE_SHA256,
        FEATURES_JSON,
        FEATURES_TEXT,
        FEATURE_COUNT,
        REFERENCE_ID,
        REFERENCE_NAME,
        CALIBRATION_RMS_NM,
        CALIBRATION_CONFIDENCE,
        CALIBRATION_WARNING,
        WAVELENGTH_MIN_NM,
        WAVELENGTH_MAX_NM,
        TRACE_QUALITY,
        TRACE_MEAN_CORRECTION_PX,
        MEAN_SNR,
        CONTAMINATED_FRACTION,
        SOURCE_WIDTH_PX,
        SOURCE_HEIGHT_PX,
        INTENSITY_SEMANTICS,
        INSTRUMENT_RESPONSE_CORRECTED,
        CONTINUUM_SEMANTICS,
        METADATA_JSON,
        ERROR
    )
}

object As100StarSpectrumAnalyseMethod : As100Method {
    const val ID = "astronomy.spectrum.analyse"
    const val VERSION = "0.2.25"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Star spectrum analysis")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Star spectrum analyser",
        version = VERSION,
        description = "Interactively trace a slitless stellar spectrum, perform robust two-sided background subtraction and Horne-style optimal extraction, inspect intermediate QA products, apply an optional saved wavelength reference, detect spectral features, and export analysis artefacts.",
        outputs = StarSpectrumAnalyseFields.outputs,
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
        val values = if (request.context[StarSpectrumAnalyseFields.STATUS] == "succeeded") {
            request.context.filterKeys { it in StarSpectrumAnalyseFields.outputs }
        } else {
            failureValues("Star spectrum analysis needs an image and an operator-defined trace. Run the interactive capability surface.")
        }
        return result(request, values, invocation)
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val succeeded = values[StarSpectrumAnalyseFields.STATUS] == "succeeded"
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
            diagnostics = if (succeeded) emptyMap() else mapOf(StarSpectrumAnalyseFields.ERROR to values[StarSpectrumAnalyseFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun failureValues(error: String): Map<String, String> = StarSpectrumAnalyseFields.outputs.associateWith { "" }.toMutableMap().apply {
        this[StarSpectrumAnalyseFields.STATUS] = "failed"
        this[StarSpectrumAnalyseFields.FEATURE_COUNT] = "0"
        this[StarSpectrumAnalyseFields.ERROR] = error
    }
}
