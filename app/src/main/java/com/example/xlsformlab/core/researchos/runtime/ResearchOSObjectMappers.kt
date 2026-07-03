package com.example.xlsformlab.core.researchos.runtime

import com.example.xlsformlab.core.researchos.ArchitectureRef
import com.example.xlsformlab.core.researchos.KnowledgeObject
import com.example.xlsformlab.core.researchos.Signal
import com.example.xlsformlab.core.researchos.Transformation

fun Signal.asRef(label: String? = signalType): ArchitectureRef =
    ArchitectureRef(id = id, type = objectType, label = label)

fun KnowledgeObject.asRef(label: String? = null): ArchitectureRef =
    ArchitectureRef(id = id, type = objectType, label = label)

fun Transformation.asRef(label: String? = action): ArchitectureRef =
    ArchitectureRef(id = id, type = objectType, label = label)
