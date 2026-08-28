package com.example.methodmesh.core.methodmesh

enum class MethodObjectType {
    Method,
    DeviceService,
    SignalInterpreter,
    Rule,
    Calculation,
    Workflow
}

interface MethodObject : ArchitectureObject {
    val methodType: MethodObjectType
    val version: String?
    val description: String?
    override val objectType: String
        get() = methodType.name
}

data class MethodDescriptor(
    override val id: ArchitectureId,
    override val methodType: MethodObjectType,
    val name: String,
    override val version: String? = null,
    override val description: String? = null,
    val inputs: List<String> = emptyList(),
    val outputs: List<String> = emptyList(),
    val graphOutputs: List<String> = emptyList(),
    val parameters: Map<String, String> = emptyMap()
) : MethodObject

data class MethodContract(
    val method: ArchitectureRef,
    val acceptedSignals: List<String> = emptyList(),
    val requiredContext: List<String> = emptyList(),
    val producedKnowledgeTypes: List<KnowledgeObjectType> = emptyList(),
    val producedFields: List<String> = emptyList(),
    val producedGraphOutputs: List<String> = emptyList()
)
