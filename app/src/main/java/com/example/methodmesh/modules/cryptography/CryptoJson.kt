package com.example.methodmesh.modules.cryptography

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object CryptoJson {
    fun provenance(
        methodId: String,
        status: String,
        sourceSha256: String? = null,
        outputSha256: String? = null,
        format: String? = null,
        protection: String? = null,
        keyFingerprints: List<String> = emptyList(),
        extra: Map<String, Any?> = emptyMap()
    ): String {
        val obj = JSONObject()
        obj.put("schema", "methodmesh.crypto.provenance.v2")
        obj.put("method_id", methodId)
        obj.put("status", status)
        obj.put("created_time_iso", Instant.now().toString())
        sourceSha256?.let { obj.put("source_sha256", it) }
        outputSha256?.let { obj.put("output_sha256", it) }
        format?.let { obj.put("format", it) }
        protection?.let { obj.put("protection", it) }
        if (keyFingerprints.isNotEmpty()) obj.put("key_fingerprints", JSONArray(keyFingerprints))
        extra.forEach { (key, value) -> obj.put(key, value) }
        return obj.toString()
    }

    data class PublicIdentity(
        val displayName: String?,
        val jwk: String,
        val fingerprint: String,
        val fromIdentityToken: Boolean
    )

    /** Accept either a MethodMesh public identity token or a raw public JWK. */
    fun parsePublicIdentity(value: String): PublicIdentity {
        val trimmed = value.trim()
        val obj = JSONObject(trimmed)
        return if (obj.optString("schema") == "methodmesh.identity.jwk.v1" && obj.has("jwk")) {
            val jwk = obj.getJSONObject("jwk").toString()
            val fp = JwsIdentityEngine.thumbprint(jwk)
            val stated = obj.optString("jwk_thumbprint_sha256")
            if (stated.isNotBlank()) require(stated == fp) { "Identity token thumbprint does not match its public key." }
            PublicIdentity(obj.optString("display_name").ifBlank { null }, jwk, fp, true)
        } else {
            val fp = JwsIdentityEngine.thumbprint(trimmed)
            PublicIdentity(null, trimmed, fp, false)
        }
    }

    /** Public identity token for QR/NFC. It contains only display metadata + public JWK. */
    fun identityToken(displayName: String, jwk: String): String = JSONObject().apply {
        put("schema", "methodmesh.identity.jwk.v1")
        put("display_name", displayName)
        put("jwk", JSONObject(jwk))
        put("jwk_thumbprint_sha256", JwsIdentityEngine.thumbprint(jwk))
    }.toString()
}
