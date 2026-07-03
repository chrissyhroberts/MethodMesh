package com.example.researchos.platform.sensors

data class SensorReading(
    val id: String,
    val label: String,
    val available: Boolean,
    val values: List<Float> = emptyList(),
    val unit: String? = null,
    val accuracy: Int? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
