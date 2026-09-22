package com.example.methodmesh.core.methodmesh

/**
 * Immutable software identity captured when an execution completes.
 *
 * This is intentionally stored on ExecutionResult rather than looked up while
 * exporting a sidecar: later app/module upgrades must never rewrite which
 * implementation produced historical evidence.
 */
data class ExecutionModuleIdentity(
    val id: String,
    val name: String,
    val version: String,
    val maturity: String
)

data class ExecutionCapabilityIdentity(
    val id: String,
    val name: String,
    val version: String,
    val maturity: String,
    val module: ExecutionModuleIdentity
)

/**
 * Core-facing registry populated by the module extension layer at application
 * startup. Core execution code can freeze software identity without importing
 * module implementation packages.
 */
object ExecutionSoftwareMetadataRegistry {
    @Volatile private var capabilities: Map<String, ExecutionCapabilityIdentity> = emptyMap()

    @Synchronized
    fun install(values: Collection<ExecutionCapabilityIdentity>) {
        require(values.all { identity ->
            identity.id.isNotBlank() &&
                identity.version.isNotBlank() &&
                identity.maturity.isNotBlank() &&
                identity.module.id.isNotBlank() &&
                identity.module.version.isNotBlank() &&
                identity.module.maturity.isNotBlank()
        }) { "Execution software metadata must be complete." }
        require(values.map { it.id }.distinct().size == values.size) {
            "Execution software metadata capability IDs must be unique."
        }
        capabilities = values.associateBy { it.id }
    }

    fun find(capabilityId: String): ExecutionCapabilityIdentity? = capabilities[capabilityId]

    fun snapshot(capabilityIds: Iterable<String>): List<ExecutionCapabilityIdentity> =
        capabilityIds
            .filter(String::isNotBlank)
            .distinct()
            .mapNotNull(::find)
}
