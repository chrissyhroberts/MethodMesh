package com.example.methodmesh.core.scheduling

import com.example.methodmesh.core.methodmesh.TransformationStatus
import org.json.JSONObject

/** Android result delivery is not proof that the capability execution succeeded. */
internal object CapabilityReturnCompletion {
    fun succeeded(resultOk: Boolean, fields: Map<String, String?>): Boolean {
        if (!resultOk) return false
        val canonicalKeys = fields.keys.filter { it == STATUS || it == FULL_JSON }
        // A supported return namespace prefixes both metadata keys. Prefer the
        // canonical envelope when present, never a prior step's prefixed data.
        val metadataKeys = canonicalKeys.ifEmpty {
            fields.keys.filter { it.endsWith("_$STATUS") || it.endsWith("_$FULL_JSON") }
        }
        // Preserve legacy result-code-only callers. Current MethodMesh returns
        // always include the FULL envelope, including CORE payload projection.
        if (metadataKeys.isEmpty()) return true
        return metadataKeys.all { key ->
            val status = if (key == FULL_JSON || key.endsWith("_$FULL_JSON")) {
                runCatching { JSONObject(fields[key].orEmpty()).getString(STATUS) }.getOrNull()
            } else fields[key]
            status == TransformationStatus.Succeeded.name
        }
    }

    private const val STATUS = "methodmesh_status"
    private const val FULL_JSON = "methodmesh_full_json"
}
