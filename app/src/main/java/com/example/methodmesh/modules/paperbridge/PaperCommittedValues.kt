package com.example.methodmesh.modules.paperbridge

import com.example.methodmesh.core.methodmesh.ExecutionResult
import org.json.JSONObject

/** Projections of the original execution, never reconstruction inputs for a new execution. */
internal fun paperCommittedFields(result: ExecutionResult?): Map<String, String> =
    result?.observations?.singleOrNull()?.values.orEmpty()

/** The committed structured value object is authoritative, not the current editable template. */
internal fun paperCommittedDynamicFields(result: ExecutionResult?): Map<String, String> {
    val raw = paperCommittedFields(result)[PaperBridgeFields.VALUES_JSON] ?: return emptyMap()
    val obj = JSONObject(raw)
    return obj.keys().asSequence().associateWith { obj.getString(it) }
}
