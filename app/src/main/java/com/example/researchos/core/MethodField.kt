package com.example.researchos.core

enum class MethodFieldType {
    Text,
    Integer,
    Float,
    Boolean,
    Json
}

data class MethodField(
    val id: String,
    val label: String,
    val type: MethodFieldType,
    val required: Boolean = true,
    val description: String? = null,
    val requiredWhen: RequiredWhen = if (required) RequiredWhen.Always else RequiredWhen.IfAvailable,
    val graphPath: String? = null
)
