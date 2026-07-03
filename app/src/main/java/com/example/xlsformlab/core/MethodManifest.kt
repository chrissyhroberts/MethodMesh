package com.example.xlsformlab.core

enum class MethodCategory {
    Measurement,
    Observation,
    Imaging,
    Mapping,
    Protocol,
    Workflow,
    Randomisation,
    Attestation,
    NFC,
    Utilities
}

enum class MethodStatus {
    Experimental,
    Beta,
    Stable,
    Deprecated
}

/**
 * Describes a Method independently of its implementation.
 *
 * A MethodManifest is the discovery and registration record used by the
 * ResearchOS runtime. It declares what a Method is capable of doing,
 * the inputs it requires and the platform capabilities needed to execute it.
 */
data class MethodManifest(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val category: MethodCategory,
    val status: MethodStatus = MethodStatus.Experimental,

    /**
     * Research capabilities provided by this Method.
     * These indicate the kinds of research activities or Intents the
     * Method is capable of fulfilling.
     */
    val capabilities: List<ResearchActivity> = emptyList(),

    /**
     * Named inputs expected before execution.
     * Examples include participant, visit, household, specimen or location.
     */
    val requiredInputs: List<String> = emptyList(),

    /**
     * Platform capabilities required by the Method.
     * Examples include camera, NFC, Bluetooth, GPS or microphone.
     */
    val requiredDeviceFeatures: List<String> = emptyList(),

    /**
     * Short human-readable summary of the Method contract.
     */
    val contractSummary: String? = null
) {
    fun primaryCapabilityKind(): ResearchActivityKind? =
        capabilities.firstOrNull()?.kind
}
