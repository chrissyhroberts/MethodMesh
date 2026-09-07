package com.example.methodmesh.modules.clinicalinstruments

data class ClinicalInstrumentDefinition(
    val schema: String,
    val id: String,
    val name: String,
    val version: String,
    val status: InstrumentStatus,
    val type: String,
    val category: String,
    val tags: List<String>,
    val summary: String,
    val sourceUrl: String,
    val citation: String,
    val rightsStatus: String,
    val rightsNote: String,
    val questions: List<ClinicalQuestion>,
    val derived: List<ClinicalExpression>,
    val scores: List<ClinicalExpression>,
    val classifications: List<ClinicalClassification>,
    val tests: List<ClinicalDefinitionTest>,
    val rawYaml: String,
    val definitionSha256: String
)

enum class InstrumentStatus { CORE, LOCAL }

data class ClinicalQuestion(
    val id: String,
    val label: String,
    val hint: String = "",
    val type: QuestionType,
    val unit: String = "",
    val required: Boolean = true,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val choices: List<ClinicalChoice> = emptyList(),
    /** Optional explicit value meaning this component could not be tested. */
    val notTestableValue: String? = null
)

enum class QuestionType { INTEGER, DECIMAL, BOOLEAN, SELECT_ONE, TEXT }

data class ClinicalChoice(val value: String, val label: String)

data class ClinicalExpression(
    val id: String,
    val expression: String,
    val label: String = "",
    /** If any listed question carries its declared not-testable value, return Null instead of calculating. */
    val requiresTestable: List<String> = emptyList()
)

data class ClinicalClassification(
    val whenExpression: String,
    val value: String,
    val label: String
)

data class ClinicalDefinitionTest(
    val name: String,
    val inputJson: String,
    val expectJson: String
)

data class ClinicalRunResult(
    val instrument: ClinicalInstrumentDefinition,
    val responses: Map<String, String>,
    val derived: Map<String, ClinicalValue>,
    val scores: Map<String, ClinicalValue>,
    val classification: ClinicalClassification?,
    val completed: Boolean,
    val error: String = ""
)

sealed interface ClinicalValue {
    data class Number(val value: Double) : ClinicalValue
    data class Bool(val value: Boolean) : ClinicalValue
    data class Text(val value: String) : ClinicalValue
    data object Null : ClinicalValue
}

data class ClinicalInstrumentSession(
    val runId: String,
    val instrumentId: String,
    val instrumentName: String,
    val instrumentVersion: String,
    val definitionSha256: String,
    val definitionYaml: String,
    val subjectId: String,
    val sessionLabel: String,
    val responses: Map<String, String>,
    val currentQuestionId: String,
    val startedAt: String,
    val updatedAt: String,
    val callerMethodId: String = "",
    val invocationContext: Map<String, String> = emptyMap()
)

data class DefinitionValidation(
    val valid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)
