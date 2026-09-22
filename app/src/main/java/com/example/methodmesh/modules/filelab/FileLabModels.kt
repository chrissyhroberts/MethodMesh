package com.example.methodmesh.modules.filelab

data class FileFact(val key: String, val value: String, val copyValue: String = value)
data class DetectionEvidence(val source: String, val detail: String)
enum class InspectionDepth { RECOGNISED, BASIC, STRUCTURED, DEEP }

data class FileInspection(
    val displayName: String,
    val formatId: String,
    val formatName: String,
    val mimeType: String?,
    val confidence: Int,
    val sizeBytes: Long,
    val sha256: String,
    val facts: List<FileFact> = emptyList(),
    val warnings: List<String> = emptyList(),
    val availableActions: List<String> = emptyList(),
    val evidence: List<DetectionEvidence> = emptyList(),
    val sampledBytes: Int = 0,
    val inspectionDepth: InspectionDepth = InspectionDepth.RECOGNISED,
    val knowledge: FileFormatKnowledge? = null
)

data class ConversionRoute(
    val sourceFormat: String,
    val targetFormat: String,
    val lossless: Boolean,
    val localOnly: Boolean = true,
    val notes: String? = null
)

data class ConversionArtifact(
    val sourceFormat: String,
    val targetFormat: String,
    val outputName: String,
    val outputUri: String,
    val outputMimeType: String,
    val outputSizeBytes: Long,
    val outputSha256: String,
    val warnings: List<String> = emptyList()
)
