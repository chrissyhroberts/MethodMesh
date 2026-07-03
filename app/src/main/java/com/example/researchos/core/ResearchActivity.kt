package com.example.researchos.core

/**
 * ResearchOS-level activity families. These are deliberately independent of Android UI classes,
 * ODK appearance strings, sensors, or transport formats.
 */
enum class ResearchActivityKind {
    Observe,
    Measure,
    Classify,
    Choose,
    Annotate,
    Localise,
    Attest,
    Randomise,
    Link,
    VerifyCompleteness,
    NavigateWorkflow,
    Transfer
}

data class ResearchActivity(
    val id: String,
    val kind: ResearchActivityKind,
    val label: String,
    val description: String? = null,
    val consumesContext: List<String> = emptyList(),
    val producesEvidence: List<String> = emptyList()
)
