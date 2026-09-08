package com.example.methodmesh.modules.clinicalinstruments

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

class ClinicalInstrumentRepository(context: Context) {
    private val root = File(context.filesDir, "clinical_instruments").apply { mkdirs() }
    private val localDir = File(root, "local").apply { mkdirs() }
    private val sessionDir = File(root, "sessions").apply { mkdirs() }

    fun coreDefinitions(): List<ClinicalInstrumentDefinition> = ClinicalInstrumentCoreDefinitions.definitions

    fun localDefinitions(): List<ClinicalInstrumentDefinition> = localDir.listFiles()
        .orEmpty()
        .filter { it.extension == "yaml" }
        .mapNotNull { file -> runCatching { ClinicalInstrumentYaml.parse(file.readText(), InstrumentStatus.LOCAL) }.getOrNull() }
        .sortedWith(compareBy<ClinicalInstrumentDefinition> { it.name.lowercase() }.thenByDescending { it.version })

    fun catalogue(): List<ClinicalInstrumentDefinition> = (coreDefinitions() + localDefinitions())
        .sortedWith(compareBy<ClinicalInstrumentDefinition> { it.category }.thenBy { it.name.lowercase() })

    fun findDefinition(id: String, version: String? = null): ClinicalInstrumentDefinition? = catalogue()
        .filter { it.id == id && (version == null || it.version == version) }
        .maxByOrNull { semverKey(it.version) }

    fun saveLocalDefinition(yaml: String): ClinicalInstrumentDefinition {
        val validation = ClinicalInstrumentYaml.validate(yaml, InstrumentStatus.LOCAL)
        require(validation.valid) { validation.errors.joinToString("; ") }
        val definition = ClinicalInstrumentYaml.parse(yaml, InstrumentStatus.LOCAL)
        require(definition.status == InstrumentStatus.LOCAL)
        require(coreDefinitions().none { it.id == definition.id && it.version == definition.version }) {
            "A core instrument already owns ${definition.id} ${definition.version}. Duplicate it with a new local id/version."
        }
        val file = localFile(definition.id, definition.version)
        if (file.exists()) {
            val existingHash = ClinicalInstrumentYaml.sha256(file.readText().trim() + "\n")
            require(existingHash == definition.definitionSha256) {
                "Local ${definition.id} ${definition.version} already exists with different content. Increase the version before saving changed logic."
            }
        } else {
            file.writeText(definition.rawYaml)
        }
        return definition
    }

    fun duplicateYaml(definition: ClinicalInstrumentDefinition): String {
        val newId = "local.${definition.id}.${System.currentTimeMillis()}"
        return definition.rawYaml
            .replace(Regex("(?m)^id: .+$"), "id: $newId")
            .replace(Regex("(?m)^name: .+$"), "name: ${definition.name} - local copy")
            .replace(Regex("(?m)^version: .+$"), "version: 0.1.0")
            .replace(Regex("(?m)^status: .+$"), "status: local")
            .replace(Regex("(?m)^rights_note: .+$"), "rights_note: Forked locally from ${definition.id} ${definition.version}; review source rights before redistribution.")
    }

    fun duplicateToLocal(definition: ClinicalInstrumentDefinition): ClinicalInstrumentDefinition = saveLocalDefinition(duplicateYaml(definition))

    fun deleteLocal(definition: ClinicalInstrumentDefinition): Boolean {
        require(definition.status == InstrumentStatus.LOCAL) { "Core instruments cannot be deleted." }
        return localFile(definition.id, definition.version).delete()
    }

    fun newLocalTemplate(): String = """
schema: methodmesh.clinical-instrument.v1
id: local.my_instrument
name: My local instrument
version: 0.1.0
status: local
type: screening
category: local
tags: [local]
summary: Describe the purpose and intended population.
source_url: ""
citation: ""
rights_status: local_author_supplied
rights_note: Confirm that you have the right to use and redistribute the item wording.
questions:
  - id: q1
    label: First question
    type: boolean
    required: true
scores:
  - id: score
    label: Score
    expression: q1
classifications:
  - when: score == 1
    value: criterion_present
    label: Criterion present
  - when: score == 0
    value: criterion_absent
    label: Criterion absent
tests:
  - name: yes
    input_json: '{"q1":true}'
    expect_json: '{"score":1,"classification":"criterion_present"}'
""".trimIndent() + "\n"

    fun newSession(
        definition: ClinicalInstrumentDefinition,
        subjectId: String,
        sessionLabel: String,
        callerMethodId: String = "",
        invocationContext: Map<String, String> = emptyMap()
    ): ClinicalInstrumentSession {
        val now = Instant.now().toString()
        return ClinicalInstrumentSession(
            runId = UUID.randomUUID().toString(),
            instrumentId = definition.id,
            instrumentName = definition.name,
            instrumentVersion = definition.version,
            definitionSha256 = definition.definitionSha256,
            definitionYaml = definition.rawYaml,
            subjectId = subjectId,
            sessionLabel = sessionLabel,
            responses = emptyMap(),
            currentQuestionId = definition.questions.firstOrNull()?.id.orEmpty(),
            startedAt = now,
            updatedAt = now,
            callerMethodId = callerMethodId,
            invocationContext = invocationContext
        ).also(::saveSession)
    }

    fun saveSession(session: ClinicalInstrumentSession) {
        val json = JSONObject()
            .put("run_id", session.runId)
            .put("instrument_id", session.instrumentId)
            .put("instrument_name", session.instrumentName)
            .put("instrument_version", session.instrumentVersion)
            .put("definition_sha256", session.definitionSha256)
            .put("definition_yaml", session.definitionYaml)
            .put("subject_id", session.subjectId)
            .put("session_label", session.sessionLabel)
            .put("current_question_id", session.currentQuestionId)
            .put("started_at", session.startedAt)
            .put("updated_at", session.updatedAt)
            .put("caller_method_id", session.callerMethodId)
            .put("invocation_context", JSONObject(session.invocationContext))
            .put("responses", JSONObject(session.responses))
        sessionFile(session.runId).writeText(json.toString())
    }

    fun listSessions(): List<ClinicalInstrumentSession> = sessionDir.listFiles()
        .orEmpty()
        .filter { it.extension == "json" }
        .mapNotNull { runCatching { sessionFromJson(JSONObject(it.readText())) }.getOrNull() }
        .sortedByDescending { it.updatedAt }

    fun loadSession(runId: String): ClinicalInstrumentSession? = sessionFile(runId)
        .takeIf { it.exists() }
        ?.let { runCatching { sessionFromJson(JSONObject(it.readText())) }.getOrNull() }

    fun deleteSession(runId: String): Boolean = sessionFile(runId).delete()

    fun updateSession(
        session: ClinicalInstrumentSession,
        responses: Map<String, String> = session.responses,
        currentQuestionId: String = session.currentQuestionId
    ): ClinicalInstrumentSession = session.copy(
        responses = responses,
        currentQuestionId = currentQuestionId,
        updatedAt = Instant.now().toString()
    ).also(::saveSession)

    private fun sessionFromJson(json: JSONObject): ClinicalInstrumentSession {
        val definitionYaml = json.getString("definition_yaml")
        val storedHash = json.getString("definition_sha256")
        require(ClinicalInstrumentYaml.sha256(definitionYaml.trim() + "\n") == storedHash) {
            "Session definition hash does not match its stored YAML snapshot."
        }
        val responsesJson = json.optJSONObject("responses") ?: JSONObject()
        val responses = responsesJson.keys().asSequence().associateWith { responsesJson.optString(it) }
        val invocationJson = json.optJSONObject("invocation_context") ?: JSONObject()
        val invocation = invocationJson.keys().asSequence().associateWith { invocationJson.optString(it) }
        return ClinicalInstrumentSession(
            runId = json.getString("run_id"),
            instrumentId = json.getString("instrument_id"),
            instrumentName = json.getString("instrument_name"),
            instrumentVersion = json.getString("instrument_version"),
            definitionSha256 = storedHash,
            definitionYaml = definitionYaml,
            subjectId = json.optString("subject_id"),
            sessionLabel = json.optString("session_label"),
            responses = responses,
            currentQuestionId = json.optString("current_question_id"),
            startedAt = json.getString("started_at"),
            updatedAt = json.getString("updated_at"),
            callerMethodId = json.optString("caller_method_id"),
            invocationContext = invocation
        )
    }

    private fun localFile(id: String, version: String) = File(localDir, "${safe(id)}__${safe(version)}.yaml")
    private fun sessionFile(runId: String) = File(sessionDir, "${safe(runId)}.json")
    private fun safe(value: String) = value.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    private fun semverKey(version: String): Long {
        val parts = version.substringBefore('-').split('.').map { it.toLongOrNull() ?: 0L }
        return (parts.getOrElse(0) { 0L } * 1_000_000_000L) + (parts.getOrElse(1) { 0L } * 1_000_000L) + parts.getOrElse(2) { 0L }
    }
}
