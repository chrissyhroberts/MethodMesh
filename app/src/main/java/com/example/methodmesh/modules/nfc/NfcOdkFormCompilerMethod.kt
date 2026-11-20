package com.example.methodmesh.modules.nfc

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

object NfcOdkFormCompilerFields {
    const val INPUT_NAME = "input_form_name"
    const val INPUT_URI = "input_form_uri"
    const val INPUT_MODE = "input_nfc_mode"
    const val INPUT_CONTRACT = "input_nfc_form_contract_version"
    const val MAPPED_FORM_URI = "mapped_form_uri"
    const val MAPPED_FORM_FILENAME = "mapped_form_filename"
    const val DELIVERY_ZIP_URI = "delivery_zip_uri"
    const val DELIVERY_ZIP_FILENAME = "delivery_zip_filename"
    const val MAPPED_FORM_VERSION = "mapped_form_version"
    const val COMPATIBILITY_STATEMENT = "compatibility_statement"
    const val NFC_METHOD_ID = "nfc_method_id"
    const val NFC_METHOD_VERSION = "nfc_method_version"
    const val CREDENTIAL_FORMAT_VERSION = "credential_format_version"
    const val NFC_CONTRACT_VERSION = "nfc_form_contract_version"
    const val CHANGED_FIELDS = "changed_fields"
    const val FORM_SHA256 = "mapped_form_sha256"
    const val TSA_PROOF_URI = "tsa_proof_uri"
    const val TSA_TIME_ISO = "tsa_time_iso"
    const val TSA_AUTHORITY = "tsa_authority"
    const val TSA_TRUST_STATUS = "tsa_trust_status"
    const val TSA_ERROR = "tsa_error"
    const val STATUS = "compiler_status"
    const val ERROR = "compiler_error"

    val outputs = listOf(DELIVERY_ZIP_URI, DELIVERY_ZIP_FILENAME, MAPPED_FORM_URI, MAPPED_FORM_FILENAME, MAPPED_FORM_VERSION, COMPATIBILITY_STATEMENT, NFC_METHOD_ID, NFC_METHOD_VERSION, CREDENTIAL_FORMAT_VERSION, NFC_CONTRACT_VERSION, CHANGED_FIELDS, FORM_SHA256, TSA_PROOF_URI, TSA_TIME_ISO, TSA_AUTHORITY, TSA_TRUST_STATUS, TSA_ERROR, STATUS, ERROR)
}

object As100NfcOdkFormCompilerMethod : As100Method {
    const val ID = "nfc_odk_form_compiler"
    const val VERSION = "1.0.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "NFC ODK form compiler")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "NFC ODK form compiler", version = VERSION,
        description = "Map an ordinary ODK XLSX form to a versioned MethodMesh NFC form and attach RFC 3161 timestamp evidence.",
        outputs = NfcOdkFormCompilerFields.outputs,
        parameters = mapOf("category" to "NFC", "status" to "Experimental", "interactive" to "true", "contract_versions" to "v1")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = result(request, mapOf(NfcOdkFormCompilerFields.STATUS to "failed", NfcOdkFormCompilerFields.ERROR to "The NFC form compiler requires an XLSX file and the capability screen."), InvocationContext.from(request.context), false)

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?, success: Boolean): ExecutionResult {
        val provenance = ProvenanceContext("methodmesh.nfc", ID, VERSION)
        val observation = Observation(
            phenomenon = ID,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val entity = Entity(
            id = ArchitectureId("nfc-form-compiler:${System.currentTimeMillis()}"),
            entityType = "NfcMappedOdkForm",
            temporalContext = request.temporalContext
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (success) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(request, if (success) TransformationStatus.Succeeded else TransformationStatus.Failed, entities = listOf(entity), observations = listOf(observation), transformations = listOf(transformation)).withInvocationContext(invocation)
    }
}
