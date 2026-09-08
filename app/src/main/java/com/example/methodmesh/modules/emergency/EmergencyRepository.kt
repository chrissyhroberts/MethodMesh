package com.example.methodmesh.modules.emergency

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

object EmergencyRepository {
    private const val PREFS = "methodmesh_emergency_v2"
    private const val KEY_COVERAGE = "coverage_json"
    private const val KEY_ALERTS = "alerts_json"
    private const val KEY_PREPARED_REGIONS = "prepared_regions_json"
    private const val KEY_MONITORING_MODE = "monitoring_mode"
    private const val KEY_MONITORING_REGION = "monitoring_region"
    private const val KEY_MONITORING_STARTED = "monitoring_started_at"
    private const val STRATEGIC_POI_FILE = "emergency/strategic_exit_core.csv"

    fun currentStatus(context: Context, now: Instant = Instant.now()): EmergencyStatusSnapshot {
        val coverage = loadCoverage(context)
        val alerts = loadAlerts(context)
        return EmergencyStatusEngine.evaluate(
            coverage = coverage,
            alerts = alerts,
            preparedness = preparednessState(context),
            now = now
        )
    }

    fun saveSourceResult(context: Context, result: EmergencySourceResult) {
        val currentCoverage = loadCoverage(context).associateBy { it.domain }.toMutableMap()
        result.coverage.forEach { currentCoverage[it.domain] = it }
        saveCoverage(context, currentCoverage.values.toList())

        val currentAlerts = loadAlerts(context).associateBy { it.eventId }.toMutableMap()
        result.alerts.forEach { currentAlerts[it.eventId] = it }
        saveAlerts(context, currentAlerts.values.toList())
    }

    fun clearLiveCache(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_COVERAGE)
            .remove(KEY_ALERTS)
            .apply()
    }

    fun referenceItems(): List<EmergencyContentItem> = EmergencyCoreData.referenceItems

    fun emergencyNumbersForCountry(countryIso2: String): List<EmergencyCoreData.EmergencyNumber> =
        EmergencyCoreData.emergencyNumbers.filter { it.jurisdictionCode.equals(countryIso2, ignoreCase = true) }

    fun strategicPois(context: Context): List<EmergencyStrategicPoi> {
        val installed = File(context.filesDir, STRATEGIC_POI_FILE)
        if (!installed.exists()) return EmergencyCoreData.strategicPoiFixture
        return runCatching { parseStrategicPoiCsv(installed.readText()) }.getOrDefault(EmergencyCoreData.strategicPoiFixture)
    }

    fun installStrategicPoiPack(context: Context, csv: String) {
        val parsed = parseStrategicPoiCsv(csv)
        require(parsed.isNotEmpty()) { "POI pack is empty." }
        val target = File(context.filesDir, STRATEGIC_POI_FILE)
        target.parentFile?.mkdirs()
        target.writeText(csv)
    }

    fun markPreparedRegion(context: Context, region: String, ready: Boolean) {
        val current = preparedRegions(context).toMutableSet()
        if (ready) current += region else current -= region
        val array = JSONArray().apply { current.sorted().forEach(::put) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PREPARED_REGIONS, array.toString())
            .apply()
    }

    fun preparedRegions(context: Context): Set<String> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PREPARED_REGIONS, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildSet { repeat(array.length()) { add(array.getString(it)) } }
    }.getOrDefault(emptySet())


    fun setMonitoringSession(context: Context, mode: EmergencyMonitoringMode, region: String = "") {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MONITORING_MODE, mode.name)
            .putString(KEY_MONITORING_REGION, region)
        if (mode == EmergencyMonitoringMode.OFF) {
            editor.remove(KEY_MONITORING_STARTED)
        } else {
            editor.putString(KEY_MONITORING_STARTED, Instant.now().toString())
        }
        editor.apply()
    }

    fun monitoringSession(context: Context): EmergencyMonitoringSession {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = runCatching { EmergencyMonitoringMode.valueOf(prefs.getString(KEY_MONITORING_MODE, EmergencyMonitoringMode.OFF.name) ?: EmergencyMonitoringMode.OFF.name) }
            .getOrDefault(EmergencyMonitoringMode.OFF)
        return EmergencyMonitoringSession(
            mode = mode,
            startedAt = prefs.getString(KEY_MONITORING_STARTED, null)?.let { runCatching { Instant.parse(it) }.getOrNull() },
            region = prefs.getString(KEY_MONITORING_REGION, "").orEmpty()
        )
    }

    fun preparednessState(context: Context): EmergencyPreparednessState = when {
        preparedRegions(context).isNotEmpty() && strategicPois(context).isNotEmpty() -> EmergencyPreparednessState.READY
        preparedRegions(context).isNotEmpty() || strategicPois(context).isNotEmpty() -> EmergencyPreparednessState.PARTIAL
        else -> EmergencyPreparednessState.MINIMAL
    }

    private fun loadCoverage(context: Context): List<EmergencyDomainCoverage> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COVERAGE, "[]") ?: "[]"
        val array = JSONArray(raw)
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            EmergencyDomainCoverage(
                domain = EmergencyHazardDomain.valueOf(item.getString("domain")),
                state = EmergencyCoverageState.valueOf(item.getString("state")),
                critical = item.optBoolean("critical", true),
                sourceId = item.optString("source_id"),
                lastSuccessfulRefreshAt = item.optString("last_successful_refresh_at").takeIf(String::isNotBlank)?.let(Instant::parse),
                checkedAt = item.optString("checked_at").takeIf(String::isNotBlank)?.let(Instant::parse),
                detail = item.optString("detail")
            )
        }
    }.getOrDefault(emptyList())

    private fun saveCoverage(context: Context, items: List<EmergencyDomainCoverage>) {
        val array = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject()
                    .put("domain", item.domain.name)
                    .put("state", item.state.name)
                    .put("critical", item.critical)
                    .put("source_id", item.sourceId)
                    .put("last_successful_refresh_at", item.lastSuccessfulRefreshAt?.toString().orEmpty())
                    .put("checked_at", item.checkedAt?.toString().orEmpty())
                    .put("detail", item.detail))
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_COVERAGE, array.toString()).apply()
    }

    private fun loadAlerts(context: Context): List<EmergencyAlert> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ALERTS, "[]") ?: "[]"
        val array = JSONArray(raw)
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            EmergencyAlert(
                eventId = item.getString("event_id"),
                title = item.getString("title"),
                domain = EmergencyHazardDomain.valueOf(item.getString("domain")),
                sourceId = item.getString("source_id"),
                authority = EmergencySourceAuthority.valueOf(item.getString("authority")),
                candidateRisk = EmergencyRiskState.valueOf(item.getString("candidate_risk")),
                issuedAt = item.optString("issued_at").takeIf(String::isNotBlank)?.let(Instant::parse),
                retrievedAt = Instant.parse(item.getString("retrieved_at")),
                expiresAt = item.optString("expires_at").takeIf(String::isNotBlank)?.let(Instant::parse),
                locallyRelevant = item.optBoolean("locally_relevant"),
                relevanceReason = item.optString("relevance_reason").takeIf(String::isNotBlank)?.let(EmergencyRelevanceReason::valueOf),
                distanceM = item.takeIf { it.has("distance_m") && !it.isNull("distance_m") }?.optDouble("distance_m"),
                corroborated = item.optBoolean("corroborated"),
                adapterAllowsRed = item.optBoolean("adapter_allows_red"),
                detail = item.optString("detail")
            )
        }
    }.getOrDefault(emptyList())

    private fun saveAlerts(context: Context, items: List<EmergencyAlert>) {
        val array = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject()
                    .put("event_id", item.eventId)
                    .put("title", item.title)
                    .put("domain", item.domain.name)
                    .put("source_id", item.sourceId)
                    .put("authority", item.authority.name)
                    .put("candidate_risk", item.candidateRisk.name)
                    .put("issued_at", item.issuedAt?.toString().orEmpty())
                    .put("retrieved_at", item.retrievedAt.toString())
                    .put("expires_at", item.expiresAt?.toString().orEmpty())
                    .put("locally_relevant", item.locallyRelevant)
                    .put("relevance_reason", item.relevanceReason?.name.orEmpty())
                    .put("distance_m", item.distanceM)
                    .put("corroborated", item.corroborated)
                    .put("adapter_allows_red", item.adapterAllowsRed)
                    .put("detail", item.detail))
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ALERTS, array.toString()).apply()
    }

    internal fun parseStrategicPoiCsv(csv: String): List<EmergencyStrategicPoi> {
        val lines = csv.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        if (lines.isEmpty()) return emptyList()
        val header = splitCsvLine(lines.first())
        val index = header.withIndex().associate { it.value to it.index }
        fun field(row: List<String>, name: String): String = index[name]?.let(row::getOrNull).orEmpty()
        return lines.drop(1).mapNotNull { line ->
            val row = splitCsvLine(line)
            val lat = field(row, "latitude").toDoubleOrNull() ?: return@mapNotNull null
            val lon = field(row, "longitude").toDoubleOrNull() ?: return@mapNotNull null
            val category = runCatching { EmergencyPoiCategory.valueOf(field(row, "category")) }.getOrNull() ?: return@mapNotNull null
            EmergencyStrategicPoi(
                id = field(row, "id"),
                name = field(row, "name"),
                category = category,
                latitude = lat,
                longitude = lon,
                countryCode = field(row, "country_code"),
                representedCountryCodes = field(row, "represented_country_codes").split('|').filter(String::isNotBlank).map { it.uppercase() }.toSet(),
                code = field(row, "code"),
                sourceId = field(row, "source_id"),
                sourceVersion = field(row, "source_version"),
                operationalStatus = field(row, "operational_status").ifBlank { "unknown" }
            )
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        out += current.toString()
        return out
    }
}
