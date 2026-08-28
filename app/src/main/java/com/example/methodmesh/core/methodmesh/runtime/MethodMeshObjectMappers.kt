package com.example.methodmesh.core.methodmesh.runtime

import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.KnowledgeObject
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation

fun Signal.asRef(label: String? = signalType): ArchitectureRef =
    ArchitectureRef(id = id, type = objectType, label = label)

fun KnowledgeObject.asRef(label: String? = null): ArchitectureRef =
    ArchitectureRef(id = id, type = objectType, label = label)

fun Transformation.asRef(label: String? = action): ArchitectureRef =
    ArchitectureRef(id = id, type = objectType, label = label)
