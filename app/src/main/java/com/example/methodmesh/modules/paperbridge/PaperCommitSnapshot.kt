package com.example.methodmesh.modules.paperbridge

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

/** Serialize review/audit/registration synchronously, before hashing can suspend. */
internal class PaperCommitSnapshot private constructor(
    private val stable: Map<String, String>,
    val dynamicValues: Map<String, String>,
    private val attachmentUris: List<String>
) {
    val fieldCount: Int get() = stable.getValue(PaperBridgeFields.FIELD_COUNT).toInt()
    val autoAcceptedCount: Int get() = stable.getValue(PaperBridgeFields.AUTO_ACCEPTED_COUNT).toInt()
    val reviewedCount: Int get() = stable.getValue(PaperBridgeFields.REVIEWED_COUNT).toInt()

    suspend fun withHashes(hash: suspend (String) -> String): Map<String, String> {
        suspend fun checkedHash(uri: String): String {
            currentCoroutineContext().ensureActive()
            return hash(uri).also { currentCoroutineContext().ensureActive() }
        }
        val sourceHash = checkedHash(stable.getValue(PaperBridgeFields.SOURCE_IMAGE))
        val rectifiedHash = checkedHash(stable.getValue(PaperBridgeFields.RECTIFIED_IMAGE))
        val metadata = JSONObject(stable.getValue(PaperBridgeFields.ATTACHMENT_METADATA_JSON))
        metadata.getJSONObject("source").put("sha256", sourceHash)
        metadata.getJSONObject("rectified").put("sha256", rectifiedHash)
        val attachments = metadata.getJSONArray("field_attachments")
        attachmentUris.forEachIndexed { index, uri ->
            attachments.getJSONObject(index).put("sha256", checkedHash(uri))
        }
        return stable + mapOf(
            PaperBridgeFields.SOURCE_SHA256 to sourceHash,
            PaperBridgeFields.RECTIFIED_SHA256 to rectifiedHash,
            PaperBridgeFields.ATTACHMENT_METADATA_JSON to metadata.toString()
        )
    }

    companion object {
        fun capture(
            extracted: PaperExtractionSession,
            results: List<PaperFieldResult>,
            manualAuditJson: String,
            automaticReturn: Boolean
        ): PaperCommitSnapshot {
            require(results.isNotEmpty() && results.all { it.resolved }) { "Resolve every review item before Commit." }
            val frozenResults = results.toList()
            val frozenManualAudit = manualAuditJson
            val manualEditCount = runCatching { JSONArray(frozenManualAudit).length() }.getOrDefault(0)
            val logicChecksJson = PaperXlsLogic.checksJson(frozenResults)
            val logicViolationCount = frozenResults.count { it.logicViolation }
            val logicUnknownCount = frozenResults.count { it.logicUnknown }
            val naFieldsJson = JSONArray(frozenResults.filter { it.naOverride }.map { it.spec.name }).toString()
            val stable = linkedMapOf(
                PaperBridgeFields.STATUS to "succeeded",
                PaperBridgeFields.TEMPLATE_ID to extracted.template.templateId,
                PaperBridgeFields.TEMPLATE_VERSION to extracted.template.version,
                PaperBridgeFields.VALUES_JSON to PaperExtractionEngine.valuesJson(frozenResults),
                PaperBridgeFields.DYNAMIC_FIELDS_JSON to PaperExtractionEngine.dynamicFieldsJson(frozenResults),
                PaperBridgeFields.FIELD_COUNT to frozenResults.size.toString(),
                PaperBridgeFields.AUTO_ACCEPTED_COUNT to frozenResults.count { it.autoAccepted }.toString(),
                PaperBridgeFields.REVIEWED_COUNT to frozenResults.count { it.reviewed }.toString(),
                PaperBridgeFields.UNRESOLVED_COUNT to "0",
                // The caller needs the complete scan bundle: original
                // acquisition plus registered analysis page. A caller-
                // supplied source is still part of that bundle.
                PaperBridgeFields.SOURCE_IMAGE to extracted.sourceUri.toString(),
                PaperBridgeFields.RECTIFIED_IMAGE to extracted.rectifiedUri.toString(),
                PaperBridgeFields.TEMPLATE_SHA256 to PaperBridgeFiles.sha256Text(extracted.template.rawJson),
                PaperBridgeFields.SOURCE_SHA256 to "",
                PaperBridgeFields.RECTIFIED_SHA256 to "",
                PaperBridgeFields.EXTRACTION_AUDIT_JSON to PaperExtractionEngine.auditJson(extracted, frozenResults, frozenManualAudit),
                PaperBridgeFields.MANUAL_EDITS_JSON to frozenManualAudit,
                PaperBridgeFields.MANUAL_EDIT_COUNT to manualEditCount.toString(),
                PaperBridgeFields.LOGIC_CHECKS_JSON to logicChecksJson,
                PaperBridgeFields.LOGIC_VIOLATION_COUNT to logicViolationCount.toString(),
                PaperBridgeFields.LOGIC_UNKNOWN_COUNT to logicUnknownCount.toString(),
                PaperBridgeFields.NA_FIELDS_JSON to naFieldsJson,
                PaperBridgeFields.ATTACHMENT_METADATA_JSON to JSONObject().apply {
                    put("schema", "methodmesh.paper.attachments.v1")
                    put("source", JSONObject().apply {
                        put("sha256", "")
                        put("role", "source_scan")
                        put("returned_to_caller", !(automaticReturn && extracted.acquisitionMode == "caller_supplied"))
                    })
                    put("rectified", JSONObject().apply {
                        put("sha256", "")
                        put("role", "registered_analysis_page")
                        put("width_px", extracted.rectifiedBitmap.width)
                        put("height_px", extracted.rectifiedBitmap.height)
                        put("coordinate_frame", if (extracted.template.registration.type == PaperRegistrationType.APRILTAG8) "canonical_page" else "anchor_centres")
                        put("registration_type", extracted.template.registration.type.name.lowercase())
                        if (extracted.template.registration.type != PaperRegistrationType.BULLSEYE4 && extracted.template.registration.schemaKey.isNotBlank()) put("registration_schema_key", extracted.template.registration.schemaKey)
                    })
                    put("field_attachments", org.json.JSONArray().apply {
                        frozenResults.filter { it.spec.type == PaperFieldType.IMAGE && it.finalValue.startsWith("content://") }.forEach { imageResult ->
                            put(JSONObject().apply {
                                put("field", imageResult.spec.name)
                                put("label", imageResult.spec.label)
                                put("sha256", "")
                                put("role", "paper_roi_image")
                            })
                        }
                    })
                    put("acquisition_mode", extracted.acquisitionMode)
                    put("registration_mode", extracted.rectification.registrationMode)
                    put("scan_time_iso", extracted.scanTimeIso)
                }.toString(),
                PaperBridgeFields.SCAN_TIME_ISO to extracted.scanTimeIso,
                PaperBridgeFields.ERROR to ""
            )
            return PaperCommitSnapshot(stable, frozenResults.associate { it.spec.name to it.finalValue },
                frozenResults.filter { it.spec.type == PaperFieldType.IMAGE && it.finalValue.startsWith("content://") }.map { it.finalValue })
        }
    }
}
