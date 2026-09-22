package com.example.methodmesh.modules.filelab

import org.json.JSONArray
import org.json.JSONObject

object FileLabJson {
    fun inspectionToJson(value: FileInspection): String = JSONObject().apply {
        put("display_name", value.displayName)
        put("format_id", value.formatId)
        put("format_name", value.formatName)
        put("mime_type", value.mimeType ?: "")
        put("confidence", value.confidence)
        put("size_bytes", value.sizeBytes)
        put("sha256", value.sha256)
        put("sampled_bytes", value.sampledBytes)
        put("inspection_depth", value.inspectionDepth.name)
        put("facts", JSONArray().apply { value.facts.forEach { fact -> put(JSONObject().put("key", fact.key).put("value", fact.value).put("copy_value", fact.copyValue)) } })
        put("warnings", JSONArray(value.warnings))
        put("actions", JSONArray(value.availableActions))
        put("evidence", JSONArray().apply { value.evidence.forEach { item -> put(JSONObject().put("source", item.source).put("detail", item.detail)) } })
        value.knowledge?.let { knowledge ->
            put("knowledge", JSONObject()
                .put("category", knowledge.category)
                .put("description", knowledge.description)
                .put("technical_identity", knowledge.technicalIdentity)
                .put("typical_uses", knowledge.typicalUses)
                .put("typical_software", JSONArray(knowledge.typicalSoftware))
                .put("common_producers", JSONArray(knowledge.commonProducers))
                .put("related_formats", JSONArray(knowledge.relatedFormats))
                .put("caution", knowledge.caution)
                .put("filelab_support", knowledge.fileLabSupport))
        }
    }.toString()

    fun inspectionFromJson(text: String): FileInspection? = runCatching {
        val json = JSONObject(text)
        val facts = json.optJSONArray("facts").toObjects().map { FileFact(it.optString("key"), it.optString("value"), it.optString("copy_value", it.optString("value"))) }
        val evidence = json.optJSONArray("evidence").toObjects().map { DetectionEvidence(it.optString("source"), it.optString("detail")) }
        FileInspection(
            displayName = json.optString("display_name"),
            formatId = json.optString("format_id"),
            formatName = json.optString("format_name"),
            mimeType = json.optString("mime_type").ifBlank { null },
            confidence = json.optInt("confidence"),
            sizeBytes = json.optLong("size_bytes"),
            sha256 = json.optString("sha256"),
            facts = facts,
            warnings = json.optJSONArray("warnings").toStrings(),
            availableActions = json.optJSONArray("actions").toStrings(),
            evidence = evidence,
            sampledBytes = json.optInt("sampled_bytes"),
            inspectionDepth = runCatching { InspectionDepth.valueOf(json.optString("inspection_depth")) }.getOrDefault(InspectionDepth.RECOGNISED),
            knowledge = json.optJSONObject("knowledge")?.let { k ->
                FileFormatKnowledge(
                    category = k.optString("category"),
                    description = k.optString("description"),
                    technicalIdentity = k.optString("technical_identity"),
                    typicalUses = k.optString("typical_uses"),
                    typicalSoftware = k.optJSONArray("typical_software").toStrings(),
                    commonProducers = k.optJSONArray("common_producers").toStrings(),
                    relatedFormats = k.optJSONArray("related_formats").toStrings(),
                    caution = k.optString("caution"),
                    fileLabSupport = k.optString("filelab_support")
                )
            }
        )
    }.getOrNull()

    fun factsJson(value: FileInspection): String = JSONArray().apply {
        value.facts.forEach { put(JSONObject().put("key", it.key).put("value", it.value)) }
    }.toString()

    fun evidenceJson(value: FileInspection): String = JSONArray().apply {
        value.evidence.forEach { put(JSONObject().put("source", it.source).put("detail", it.detail)) }
    }.toString()

    fun stringListJson(values: List<String>): String = JSONArray(values).toString()

    fun knowledgeJson(value: FileFormatKnowledge?): String = if (value == null) "{}" else JSONObject()
        .put("category", value.category)
        .put("description", value.description)
        .put("technical_identity", value.technicalIdentity)
        .put("typical_uses", value.typicalUses)
        .put("typical_software", JSONArray(value.typicalSoftware))
        .put("common_producers", JSONArray(value.commonProducers))
        .put("related_formats", JSONArray(value.relatedFormats))
        .put("caution", value.caution)
        .put("filelab_support", value.fileLabSupport)
        .toString()

    private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else buildList {
        for (i in 0 until length()) add(optString(i))
    }

    private fun JSONArray?.toObjects(): List<JSONObject> = if (this == null) emptyList() else buildList {
        for (i in 0 until length()) optJSONObject(i)?.let(::add)
    }
}
