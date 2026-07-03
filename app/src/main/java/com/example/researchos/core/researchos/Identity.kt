package com.example.researchos.core.researchos

import java.util.UUID

/** Stable identifier for any AS1.00 architectural object. */
data class ArchitectureId(
    val value: String = UUID.randomUUID().toString()
) {
    override fun toString(): String = value
}

/** Lightweight reference used to connect objects without embedding them. */
data class ArchitectureRef(
    val id: ArchitectureId,
    val type: String,
    val label: String? = null
)

/** Base marker for objects that may be referenced inside the AS1.00 graph. */
interface ArchitectureObject {
    val id: ArchitectureId
    val objectType: String
}

fun ArchitectureObject.ref(label: String? = null): ArchitectureRef =
    ArchitectureRef(id = id, type = objectType, label = label)
