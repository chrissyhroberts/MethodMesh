package com.example.researchos.core.researchos.runtime

import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.KnowledgeObject
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.Transformation

fun Signal.asRef(label: String? = signalType): ArchitectureRef =
    ArchitectureRef(id = id, type = objectType, label = label)

fun KnowledgeObject.asRef(label: String? = null): ArchitectureRef =
    ArchitectureRef(id = id, type = objectType, label = label)

fun Transformation.asRef(label: String? = action): ArchitectureRef =
    ArchitectureRef(id = id, type = objectType, label = label)
