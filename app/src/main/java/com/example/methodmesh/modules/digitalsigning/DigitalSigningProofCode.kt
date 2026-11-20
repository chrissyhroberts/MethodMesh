package com.example.methodmesh.modules.digitalsigning

/** Human-friendly projection of the committed PDF hash. The full hash remains authoritative. */
object DigitalSigningProofCode {
    fun fromSha256(sha256: String): String {
        val hex = sha256.trim().lowercase()
        require(hex.matches(Regex("[0-9a-f]{64}"))) { "Expected a SHA-256 hex digest." }
        return "${hex.take(4)}-${hex.substring(4, 8)}".uppercase()
    }
}
