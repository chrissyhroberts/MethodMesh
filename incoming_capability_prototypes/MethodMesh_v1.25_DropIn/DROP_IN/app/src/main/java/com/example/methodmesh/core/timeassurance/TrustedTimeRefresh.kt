package com.example.methodmesh.core.timeassurance

/**
 * Optional pluggable provider for acquiring a new externally trusted time
 * anchor. Core owns the contract; a module such as Trusted Timestamp owns the
 * network/protocol implementation.
 */
interface TrustedTimeRefreshProvider {
    val providerId: String
    val displayName: String
    suspend fun refresh(): TrustedTimeRefreshResult
}

object TrustedTimeRefreshRegistry {
    @Volatile private var provider: TrustedTimeRefreshProvider? = null

    @Synchronized
    fun register(value: TrustedTimeRefreshProvider) {
        val existing = provider
        require(existing == null || existing.providerId == value.providerId) {
            "A different trusted-time refresh provider is already registered: ${existing?.providerId}"
        }
        provider = value
    }

    fun currentProvider(): TrustedTimeRefreshProvider? = provider

    suspend fun refresh(): TrustedTimeRefreshResult {
        val active = provider ?: return TrustedTimeRefreshResult(
            success = false,
            message = "No trusted-time refresh provider is installed."
        )
        return active.refresh()
    }
}
