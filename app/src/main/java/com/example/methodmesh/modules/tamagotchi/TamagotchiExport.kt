package com.example.methodmesh.modules.tamagotchi

import android.content.Context
import androidx.core.content.FileProvider
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.crypto.Digests
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class TamagotchiExportResult(
    val uri: String,
    val sha256: String,
    val artifactRef: String,
    val file: File
)

object TamagotchiExporter {
    fun export(context: Context, sessionId: String, includeAnalysisRequested: Boolean): TamagotchiExportResult {
        val store = TamagotchiStore(context)
        val session = store.loadAndAdvance(sessionId) ?: error("Tamagotchi session not found: $sessionId")
        val scenario = TamagotchiCatalog.scenario(session.scenarioId)
        val includeLatent = includeAnalysisRequested && session.ended
        val history = store.historyLines(session)

        val out = File(context.cacheDir, "tamagotchi-${session.id.take(8)}-${System.currentTimeMillis()}.zip")
        ZipOutputStream(FileOutputStream(out)).use { zip ->
            fun text(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            text("manifest.json", JSONObject()
                .put("format", "methodmesh.tamagotchi.session")
                .put("format_version", "1.0")
                .put("session_id", session.id)
                .put("creature_id", session.creatureId)
                .put("creature_name", session.creatureName)
                .put("scenario_id", session.scenarioId)
                .put("seed", session.seed)
                .put("started_at", session.startedAtIso)
                .put("ended_at", session.endedAtIso ?: JSONObject.NULL)
                .put("analysis_included", includeLatent)
                .put("history_authoritative", "history.jsonl")
                .toString(2))

            text("scenario.json", ScenarioJson.encode(scenario, includeLatent).toString(2))
            val snapshot = if (includeLatent) {
                session.toJson()
            } else {
                JSONObject()
                    .put("id", session.id)
                    .put("creature_id", session.creatureId)
                    .put("creature_name", session.creatureName)
                    .put("scenario_id", session.scenarioId)
                    .put("seed", session.seed)
                    .put("simulation_mode", session.simulationMode.wire)
                    .put("off_session_mode", session.offSessionMode.wire)
                    .put("started_at", session.startedAtIso)
                    .put("simulation_minute", session.simulationMinute)
                    .put("visible_state", TamagotchiEngine.visibleState(session, scenario).toJsonObject())
                    .put("ended_at", session.endedAtIso ?: JSONObject.NULL)
                    .put("history_artifact_ref", session.historyArtifactRef)
                    .put("generated_options", org.json.JSONArray().also { a ->
                        session.generatedOptions.forEach { option -> a.put(JSONObject().put("id", option.id).put("label", option.label).put("generator_id", option.generatorId)) }
                    })
            }
            text("session_snapshot.json", snapshot.toString(2))

            val selectedHistory = history.filter { event ->
                includeLatent || event.optString("visibility") != "latent"
            }
            text("history.jsonl", selectedHistory.joinToString("\n") { it.toString() } + if (selectedHistory.isNotEmpty()) "\n" else "")

            text("actions.csv", csv(
                header = listOf("recorded_at", "simulation_minute", "action_id", "label", "generated"),
                rows = selectedHistory.filter { it.optString("type") == "action" }.map { e ->
                    val p = e.optJSONObject("payload") ?: JSONObject()
                    listOf(e.optString("recorded_at"), e.optDouble("simulation_minute"), p.optString("action_id"), p.optString("label"), p.optBoolean("generated"))
                }
            ))
            text("observations.csv", csv(
                header = listOf("recorded_at", "simulation_minute", "type", "payload_json"),
                rows = selectedHistory.filter { it.optString("type") in setOf("observation", "measurement") }.map { e ->
                    listOf(e.optString("recorded_at"), e.optDouble("simulation_minute"), e.optString("type"), e.optJSONObject("payload")?.toString().orEmpty())
                }
            ))
            text("states.csv", stateCsv(selectedHistory, includeLatent))
            text("generated_options.csv", csv(
                header = listOf("recorded_at", "option_id", "label", "generator_id", "parameters_json"),
                rows = selectedHistory.filter { it.optString("type") == "generated_option" }.map { e ->
                    val p = e.optJSONObject("payload") ?: JSONObject()
                    listOf(e.optString("recorded_at"), p.optString("id"), p.optString("label"), p.optString("generator_id"), p.toString())
                }
            ))
            text("notifications.csv", csv(
                header = listOf("recorded_at", "simulation_minute", "type", "reason"),
                rows = selectedHistory.filter { it.optString("type").startsWith("notification_") }.map { e ->
                    listOf(e.optString("recorded_at"), e.optDouble("simulation_minute"), e.optString("type"), e.optJSONObject("payload")?.optString("reason").orEmpty())
                }
            ))
            if (includeLatent) {
                text("analysis/latent_final_state.json", session.state.toJsonObject().toString(2))
                text("analysis/README.txt", "Analysis is included because the care period has ended. Latent state and generated-option parameters may now be inspected.\n")
            } else {
                text("analysis/README.txt", "Latent analysis was not included. Analysis is available only after the care period ends.\n")
            }
        }
        val bytes = out.readBytes()
        val sha = Digests.sha256Hex(bytes)
        val ref = AndroidArtifacts.service(context).createPersistent(
            name = "Tamagotchi ${session.creatureName} ${session.id.take(8)} dataset.zip",
            mime = "application/zip",
            input = ByteArrayInputStream(bytes),
            collectionId = session.historyArtifactRef
        )
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out).toString()
        return TamagotchiExportResult(uri, sha, ref.id, out)
    }

    private fun stateCsv(events: List<JSONObject>, includeLatent: Boolean): String {
        val rows = mutableListOf<List<Any?>>()
        events.forEach { e ->
            val type = e.optString("type")
            val p = e.optJSONObject("payload") ?: return@forEach
            val state = when {
                includeLatent && type == "state_transition" -> p.optJSONObject("after")
                type == "care_state_transition" -> p.optJSONObject("visible_state")
                else -> null
            } ?: return@forEach
            state.keys().forEach { variable ->
                rows += listOf(e.optString("recorded_at"), e.optDouble("simulation_minute"), variable, state.optDouble(variable), if (includeLatent && type == "state_transition") "latent" else "care")
            }
        }
        return csv(listOf("recorded_at", "simulation_minute", "variable", "value", "visibility"), rows)
    }

    private fun csv(header: List<String>, rows: List<List<Any?>>): String = buildString {
        append(header.joinToString(",") { escape(it) }).append('\n')
        rows.forEach { row -> append(row.joinToString(",") { escape(it?.toString().orEmpty()) }).append('\n') }
    }

    private fun escape(raw: String): String = if (raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${raw.replace("\"", "\"\"")}\""
    } else raw
}

object ScenarioJson {
    fun encode(s: ScenarioDefinition, revealLatent: Boolean = false) = JSONObject()
        .put("id", s.id)
        .put("title", s.title)
        .put("subtitle", s.subtitle)
        .put("teaching_note", s.teachingNote)
        .put("variables", org.json.JSONArray().also { a ->
            s.variables.filter { revealLatent || it.careVisible }.forEach { v -> a.put(JSONObject()
                .put("id", v.id).put("label", v.label).put("minimum", v.minimum).put("maximum", v.maximum)
                .put("initial", v.initial).put("drift_per_simulation_hour", v.driftPerSimulationHour)
                .put("care_visible", v.careVisible).put("measurable", v.measurable).put("unit", v.unit)) }
        })
        .put("actions", org.json.JSONArray().also { a ->
            s.actions.forEach { action -> a.put(JSONObject()
                .put("id", action.id).put("label", action.label).put("description", action.description)
                .put("visible_effects", action.immediateEffects.filterKeys { id -> revealLatent || s.variable(id)?.careVisible != false }.toJsonObject())
                .apply { if (revealLatent) put("hidden_effects", action.hiddenEffects.toJsonObject()) }
                .put("advances_minutes", action.advancesMinutes)) }
        })
        .put("procedural_generators", org.json.JSONArray().also { a ->
            s.generators.forEach { g ->
                a.put(JSONObject().put("id", g.id).put("count", g.count).put("labels", org.json.JSONArray().also { labels -> g.labels.forEach(labels::put) })
                    .apply {
                        if (revealLatent) {
                            put("effect_ranges", JSONObject().also { o -> g.effectRanges.forEach { (k,r) -> o.put(k, org.json.JSONArray().put(r.start).put(r.endInclusive)) } })
                            put("hidden_effect_ranges", JSONObject().also { o -> g.hiddenEffectRanges.forEach { (k,r) -> o.put(k, org.json.JSONArray().put(r.start).put(r.endInclusive)) } })
                        }
                    })
            }
        })
}
