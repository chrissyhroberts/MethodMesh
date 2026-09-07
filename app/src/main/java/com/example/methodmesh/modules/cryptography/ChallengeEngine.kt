package com.example.methodmesh.modules.cryptography

import org.json.JSONObject
import java.security.SecureRandom
import java.time.Instant

object ChallengeEngine {
    private val rng = SecureRandom()

    fun create(audience: String = "", ttlSeconds: Long = 300, nowEpochSeconds: Long = Instant.now().epochSecond): String {
        require(ttlSeconds in 30..3600)
        val nonce = ByteArray(32).also(rng::nextBytes)
        return JSONObject(
            linkedMapOf(
                "schema" to "methodmesh.challenge.v1",
                "nonce_b64url" to with(CryptoEncoding) { nonce.base64Url() },
                "audience" to audience,
                "issued_at" to Instant.ofEpochSecond(nowEpochSeconds).toString(),
                "expires_at" to Instant.ofEpochSecond(nowEpochSeconds + ttlSeconds).toString()
            )
        ).toString()
    }

    fun validate(challengeJson: String, expectedAudience: String? = null, nowEpochSeconds: Long = Instant.now().epochSecond) {
        val obj = JSONObject(challengeJson)
        require(obj.optString("schema") == "methodmesh.challenge.v1") { "Unsupported challenge schema." }
        CryptoEncoding.base64UrlDecode(obj.getString("nonce_b64url")).also {
            require(it.size >= 16) { "Challenge nonce is too short." }
        }
        val expires = Instant.parse(obj.getString("expires_at")).epochSecond
        require(nowEpochSeconds <= expires) { "Challenge has expired." }
        if (expectedAudience != null) {
            require(obj.optString("audience") == expectedAudience) { "Challenge audience does not match." }
        }
    }
}
