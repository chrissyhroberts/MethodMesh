package com.example.methodmesh.modules

import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.runtime.As100Method

enum class MaturityStatus(val wireValue: String, val label: String) {
    Production("production", "Production"),
    Development("development", "Development"),
    Experimental("experimental", "Experimental");

    companion object {
        fun parse(value: String?): MaturityStatus? = when (value?.trim()?.lowercase()) {
            "production", "prod" -> Production
            "development", "dev" -> Development
            "experimental", "experiment" -> Experimental
            else -> null
        }
    }
}

data class ModuleRuntimeMetadata(
    val id: String,
    val name: String,
    val version: String,
    val maturity: MaturityStatus
)

data class CapabilityRuntimeMetadata(
    val id: String,
    val name: String,
    val version: String,
    val maturity: MaturityStatus,
    val module: ModuleRuntimeMetadata
)

/**
 * One canonical resolver for module/capability identity shown in UI and written
 * into FULL execution envelopes. Existing descriptor status strings are read as
 * a migration compatibility source, but only the canonical [MaturityStatus]
 * values leave this boundary.
 */
object MethodMeshMetadataResolver {
    fun moduleVersion(module: MethodMeshModule): String =
        module.as100Methods()
            .mapNotNull { it.descriptor.version?.trim()?.takeIf(String::isNotBlank) }
            .maxWithOrNull(Comparator(::compareVersions))
            ?: MODULE_VERSION_BASELINE

    fun moduleMaturity(module: MethodMeshModule): MaturityStatus {
        val capabilityStates = module.as100Methods().map { method ->
            explicitCapabilityMaturity(method.descriptor) ?: MaturityStatus.Development
        }
        if (capabilityStates.isEmpty()) return MaturityStatus.Development
        return when {
            capabilityStates.all { it == MaturityStatus.Production } -> MaturityStatus.Production
            capabilityStates.all { it == MaturityStatus.Experimental } -> MaturityStatus.Experimental
            else -> MaturityStatus.Development
        }
    }

    fun capabilityMaturity(method: As100Method, module: MethodMeshModule?): MaturityStatus =
        explicitCapabilityMaturity(method.descriptor)
            ?: module?.maturity
            ?: MaturityStatus.Development

    fun moduleMetadata(module: MethodMeshModule): ModuleRuntimeMetadata = ModuleRuntimeMetadata(
        id = module.moduleId,
        name = module.displayName,
        version = module.version,
        maturity = module.maturity
    )

    fun capabilityMetadata(method: As100Method, module: MethodMeshModule?): CapabilityRuntimeMetadata {
        val moduleMetadata = module?.let(::moduleMetadata) ?: ModuleRuntimeMetadata(
            id = "core",
            name = "MethodMesh core",
            version = method.descriptor.version?.takeIf(String::isNotBlank) ?: MODULE_VERSION_BASELINE,
            maturity = explicitCapabilityMaturity(method.descriptor) ?: MaturityStatus.Development
        )
        return CapabilityRuntimeMetadata(
            id = method.id,
            name = method.descriptor.name,
            version = requireNotNull(method.descriptor.version?.trim()?.takeIf(String::isNotBlank)) {
                "Capability ${method.id} must declare a nonblank version."
            },
            maturity = capabilityMaturity(method, module),
            module = moduleMetadata
        )
    }

    private fun explicitCapabilityMaturity(descriptor: MethodDescriptor): MaturityStatus? =
        MaturityStatus.parse(descriptor.parameters["maturity"])
            ?: MaturityStatus.parse(descriptor.parameters["status"])

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = semanticParts(left)
        val rightParts = semanticParts(right)
        val length = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until length) {
            val l = leftParts.getOrElse(index) { 0 }
            val r = rightParts.getOrElse(index) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return left.compareTo(right)
    }

    private fun semanticParts(value: String): List<Int> =
        value.substringBefore('-')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }

    private const val MODULE_VERSION_BASELINE = "1.0.0"
}
