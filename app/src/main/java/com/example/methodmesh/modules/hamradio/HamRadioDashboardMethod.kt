package com.example.methodmesh.modules.hamradio

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object HamDashboardFields {
    const val STATUS = "ham_dashboard_status"
    const val RESULT = "ham_dashboard_result"
    const val PRIMARY_BAND = "ham_dashboard_primary_band"
    const val PRIMARY_MHZ = "ham_dashboard_primary_mhz"
    const val PRIMARY_SCORE = "ham_dashboard_primary_score"
    const val SECONDARY_BANDS = "ham_dashboard_secondary_bands"
    const val RANKED_BANDS_JSON = "ham_dashboard_ranked_bands_json"
    const val QTH_LOCATOR = "ham_dashboard_qth_locator"
    const val LATITUDE = "ham_dashboard_latitude"
    const val LONGITUDE = "ham_dashboard_longitude"
    const val LOCATION_SOURCE = "ham_dashboard_location_source"
    const val TARGET_DISTANCE_KM = "ham_dashboard_target_distance_km"
    const val MODE = "ham_dashboard_mode"
    const val SOLAR_ELEVATION = "ham_dashboard_solar_elevation_deg"
    const val SOLAR_HOUR = "ham_dashboard_local_solar_hour"
    const val KP = "ham_dashboard_kp"
    const val F107 = "ham_dashboard_f107"
    const val WIND = "ham_dashboard_solar_wind_kms"
    const val BZ = "ham_dashboard_bz_nt"
    const val G_SCALE = "ham_dashboard_g_scale"
    const val R_SCALE = "ham_dashboard_r_scale"
    const val S_SCALE = "ham_dashboard_s_scale"
    const val SPACE_WEATHER_RESULT = "ham_dashboard_space_weather_result"
    const val SPACE_WEATHER_FROM_CACHE = "ham_dashboard_space_weather_from_cache"
    const val SPACE_WEATHER_STALE = "ham_dashboard_space_weather_stale"
    const val ALERT = "ham_dashboard_alert"
    const val PSK_ENABLED = "ham_dashboard_psk_enabled"
    const val PSK_STATUS = "ham_dashboard_psk_status"
    const val PSK_RESULT = "ham_dashboard_psk_result"
    const val PSK_REPORT_COUNT = "ham_dashboard_psk_report_count"
    const val PSK_UNIQUE_CALLSIGNS = "ham_dashboard_psk_unique_callsigns"
    const val PSK_UNIQUE_GRIDS = "ham_dashboard_psk_unique_grids"
    const val PSK_BAND_COUNTS_JSON = "ham_dashboard_psk_band_counts_json"
    const val PSK_MODE_COUNTS_JSON = "ham_dashboard_psk_mode_counts_json"
    const val PSK_FROM_CACHE = "ham_dashboard_psk_from_cache"
    const val PSK_RETRY_AFTER_SECONDS = "ham_dashboard_psk_retry_after_seconds"
    const val RETRIEVED_TIME = "ham_dashboard_retrieved_time_iso"
    const val JSON = "ham_dashboard_json"
    const val AUDIT = "ham_dashboard_audit_json"
    const val ERROR = "ham_dashboard_error"

    val outputs = listOf(
        STATUS, RESULT, PRIMARY_BAND, PRIMARY_MHZ, PRIMARY_SCORE, SECONDARY_BANDS, RANKED_BANDS_JSON,
        QTH_LOCATOR, LATITUDE, LONGITUDE, LOCATION_SOURCE, TARGET_DISTANCE_KM, MODE, SOLAR_ELEVATION,
        SOLAR_HOUR, KP, F107, WIND, BZ, G_SCALE, R_SCALE, S_SCALE, SPACE_WEATHER_RESULT,
        SPACE_WEATHER_FROM_CACHE, SPACE_WEATHER_STALE, ALERT, PSK_ENABLED, PSK_STATUS, PSK_RESULT,
        PSK_REPORT_COUNT, PSK_UNIQUE_CALLSIGNS, PSK_UNIQUE_GRIDS, PSK_BAND_COUNTS_JSON,
        PSK_MODE_COUNTS_JSON, PSK_FROM_CACHE, PSK_RETRY_AFTER_SECONDS, RETRIEVED_TIME, JSON, AUDIT, ERROR
    )
}

object As100HamDashboardMethod : As100Method {
    const val ID = "ham.dashboard"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Amateur-radio operating dashboard")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Ham radio dashboard",
        version = HAM_VERSION,
        description = "Combine current space weather, localised HF band guidance and optional PSK Reporter activity into a refreshable operator dashboard.",
        outputs = HamDashboardFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Amateur radio",
            "status" to "Development",
            "dashboard" to "true",
            "psk_minimum_request_interval_seconds" to HamRadioRepository.PSK_REPORTER_MIN_REQUEST_INTERVAL_SECONDS.toString()
        )
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        HamMethodSupport.request(ref, action, context, signals, inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, calculate(request.context), InvocationContext.from(request.context))

    fun calculate(settings: Map<String, String>): Map<String, String> = try {
        val space = jsonMap(settings.hamValue("space_weather_values_json").orEmpty())
        val band = jsonMap(settings.hamValue("band_advice_values_json").orEmpty())
        val psk = jsonMap(settings.hamValue("psk_values_json").orEmpty())

        require(space.isNotEmpty()) { "Space-weather component snapshot is required; the dashboard screen fetches this automatically." }
        require(band.isNotEmpty()) { "Band-advice component snapshot is required; the dashboard screen calculates this automatically." }
        require(space[HamSpaceWeatherFields.STATUS] == "succeeded") {
            space[HamSpaceWeatherFields.ERROR].orEmpty().ifBlank { "Space-weather component failed." }
        }
        require(band[HamBandFields.STATUS] == "succeeded") {
            band[HamBandFields.ERROR].orEmpty().ifBlank { "Band-advice component failed." }
        }

        val primary = band[HamBandFields.PRIMARY].orEmpty()
        val primaryMhz = band[HamBandFields.PRIMARY_MHZ].orEmpty()
        val primaryScore = band[HamBandFields.PRIMARY_SCORE].orEmpty()
        val ranked = band[HamBandFields.RANKED_JSON].orEmpty().ifBlank { "[]" }
        val secondary = runCatching {
            val arr = JSONArray(ranked)
            (1 until minOf(arr.length(), 4))
                .mapNotNull { arr.optJSONObject(it)?.optString("band")?.takeIf(String::isNotBlank) }
                .joinToString(" / ")
        }.getOrDefault("")

        val alert = radioAlert(
            kp = space[HamSpaceWeatherFields.KP]?.toDoubleOrNull(),
            g = space[HamSpaceWeatherFields.G]?.toIntOrNull(),
            r = space[HamSpaceWeatherFields.R]?.toIntOrNull(),
            s = space[HamSpaceWeatherFields.S]?.toIntOrNull(),
            stale = space[HamSpaceWeatherFields.STALE] == "true"
        )
        val pskEnabled = settings.hamValue("callsign").orEmpty().isNotBlank()
        val pskStatus = if (pskEnabled) psk[HamPskReporterFields.STATUS].orEmpty().ifBlank { "not_loaded" } else "disabled"
        val retrieved = settings.hamValue("retrieved_time_iso") ?: Instant.now().toString()

        val resultText = buildString {
            append("Try ").append(primary)
            if (primaryMhz.isNotBlank()) append(" · ").append(primaryMhz).append(" MHz")
            if (primaryScore.isNotBlank()) append(" · score ").append(primaryScore).append("/100")
            if (secondary.isNotBlank()) append(" · then ").append(secondary)
        }

        val payload = JSONObject().apply {
            put("primary_band", primary)
            put("primary_mhz", primaryMhz.toDoubleOrNull() ?: JSONObject.NULL)
            put("primary_score", primaryScore.toIntOrNull() ?: JSONObject.NULL)
            put("secondary_bands", secondary)
            put("ranked_bands", runCatching { JSONArray(ranked) }.getOrDefault(JSONArray()))
            put("qth_locator", settings.hamValue("qth_locator") ?: band[HamBandFields.ORIGIN].orEmpty())
            put("latitude", settings.hamValue("latitude")?.toDoubleOrNull() ?: JSONObject.NULL)
            put("longitude", settings.hamValue("longitude")?.toDoubleOrNull() ?: JSONObject.NULL)
            put("location_source", settings.hamValue("location_source").orEmpty())
            put("target_distance_km", band[HamBandFields.DISTANCE]?.toDoubleOrNull() ?: JSONObject.NULL)
            put("mode", band[HamBandFields.MODE].orEmpty())
            put("space_weather", JSONObject(space))
            put("psk_reporter", if (psk.isEmpty()) JSONObject() else JSONObject(psk))
            put("retrieved_time_iso", retrieved)
        }
        val audit = JSONObject().apply {
            put("dashboard_version", HAM_VERSION)
            put("band_heuristic_version", band[HamBandFields.HEURISTIC_VERSION].orEmpty())
            put("space_weather_provider", space[HamSpaceWeatherFields.PROVIDER].orEmpty())
            put("space_weather_from_cache", space[HamSpaceWeatherFields.FROM_CACHE] == "true")
            put("space_weather_stale", space[HamSpaceWeatherFields.STALE] == "true")
            put("psk_enabled", pskEnabled)
            put("psk_status", pskStatus)
            put("psk_from_cache", psk[HamPskReporterFields.FROM_CACHE] == "true")
            put("psk_retry_after_seconds", psk[HamPskReporterFields.RETRY_AFTER_SECONDS]?.toLongOrNull() ?: 0)
            put("psk_minimum_request_interval_seconds", HamRadioRepository.PSK_REPORTER_MIN_REQUEST_INTERVAL_SECONDS)
        }

        linkedMapOf(
            HamDashboardFields.STATUS to "succeeded",
            HamDashboardFields.RESULT to resultText,
            HamDashboardFields.PRIMARY_BAND to primary,
            HamDashboardFields.PRIMARY_MHZ to primaryMhz,
            HamDashboardFields.PRIMARY_SCORE to primaryScore,
            HamDashboardFields.SECONDARY_BANDS to secondary,
            HamDashboardFields.RANKED_BANDS_JSON to ranked,
            HamDashboardFields.QTH_LOCATOR to (settings.hamValue("qth_locator") ?: band[HamBandFields.ORIGIN].orEmpty()),
            HamDashboardFields.LATITUDE to settings.hamValue("latitude").orEmpty(),
            HamDashboardFields.LONGITUDE to settings.hamValue("longitude").orEmpty(),
            HamDashboardFields.LOCATION_SOURCE to settings.hamValue("location_source").orEmpty(),
            HamDashboardFields.TARGET_DISTANCE_KM to band[HamBandFields.DISTANCE].orEmpty(),
            HamDashboardFields.MODE to band[HamBandFields.MODE].orEmpty(),
            HamDashboardFields.SOLAR_ELEVATION to band[HamBandFields.SOLAR_ELEVATION].orEmpty(),
            HamDashboardFields.SOLAR_HOUR to band[HamBandFields.SOLAR_HOUR].orEmpty(),
            HamDashboardFields.KP to space[HamSpaceWeatherFields.KP].orEmpty(),
            HamDashboardFields.F107 to space[HamSpaceWeatherFields.F107].orEmpty(),
            HamDashboardFields.WIND to space[HamSpaceWeatherFields.WIND].orEmpty(),
            HamDashboardFields.BZ to space[HamSpaceWeatherFields.BZ].orEmpty(),
            HamDashboardFields.G_SCALE to space[HamSpaceWeatherFields.G].orEmpty(),
            HamDashboardFields.R_SCALE to space[HamSpaceWeatherFields.R].orEmpty(),
            HamDashboardFields.S_SCALE to space[HamSpaceWeatherFields.S].orEmpty(),
            HamDashboardFields.SPACE_WEATHER_RESULT to space[HamSpaceWeatherFields.RESULT].orEmpty(),
            HamDashboardFields.SPACE_WEATHER_FROM_CACHE to space[HamSpaceWeatherFields.FROM_CACHE].orEmpty(),
            HamDashboardFields.SPACE_WEATHER_STALE to space[HamSpaceWeatherFields.STALE].orEmpty(),
            HamDashboardFields.ALERT to alert,
            HamDashboardFields.PSK_ENABLED to pskEnabled.toString(),
            HamDashboardFields.PSK_STATUS to pskStatus,
            HamDashboardFields.PSK_RESULT to psk[HamPskReporterFields.RESULT].orEmpty(),
            HamDashboardFields.PSK_REPORT_COUNT to psk[HamPskReporterFields.REPORT_COUNT].orEmpty(),
            HamDashboardFields.PSK_UNIQUE_CALLSIGNS to psk[HamPskReporterFields.UNIQUE_CALLSIGNS].orEmpty(),
            HamDashboardFields.PSK_UNIQUE_GRIDS to psk[HamPskReporterFields.UNIQUE_GRIDS].orEmpty(),
            HamDashboardFields.PSK_BAND_COUNTS_JSON to psk[HamPskReporterFields.BAND_COUNTS_JSON].orEmpty().ifBlank { "{}" },
            HamDashboardFields.PSK_MODE_COUNTS_JSON to psk[HamPskReporterFields.MODE_COUNTS_JSON].orEmpty().ifBlank { "{}" },
            HamDashboardFields.PSK_FROM_CACHE to psk[HamPskReporterFields.FROM_CACHE].orEmpty(),
            HamDashboardFields.PSK_RETRY_AFTER_SECONDS to psk[HamPskReporterFields.RETRY_AFTER_SECONDS].orEmpty(),
            HamDashboardFields.RETRIEVED_TIME to retrieved,
            HamDashboardFields.JSON to payload.toString(),
            HamDashboardFields.AUDIT to audit.toString(),
            HamDashboardFields.ERROR to ""
        )
    } catch (e: Exception) {
        HamDashboardFields.outputs.associateWith { "" }.toMutableMap().apply {
            this[HamDashboardFields.STATUS] = "failed"
            this[HamDashboardFields.RESULT] = "Ham radio dashboard unavailable"
            this[HamDashboardFields.RETRIEVED_TIME] = Instant.now().toString()
            this[HamDashboardFields.ERROR] = e.message ?: "Dashboard calculation failed."
        }
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult =
        HamMethodSupport.complete(request, ID, ref, ID, "HamRadioDashboard", HamDashboardFields.STATUS, HamDashboardFields.ERROR, values, invocation)

    private fun jsonMap(json: String): Map<String, String> = runCatching {
        if (json.isBlank()) return emptyMap()
        val obj = JSONObject(json)
        buildMap { obj.keys().forEach { key -> put(key, obj.optString(key, "")) } }
    }.getOrDefault(emptyMap())

    private fun radioAlert(kp: Double?, g: Int?, r: Int?, s: Int?, stale: Boolean): String {
        val alerts = buildList {
            if (stale) add("Space-weather data stale")
            if ((r ?: 0) > 0) add("Radio blackout R$r")
            if ((g ?: 0) > 0) add("Geomagnetic storm G$g")
            if ((s ?: 0) > 0) add("Solar radiation storm S$s")
            if ((kp ?: 0.0) >= 5.0 && (g ?: 0) == 0) add("Elevated Kp ${fmt(kp, 1)}")
        }
        return alerts.joinToString(" · ").ifBlank { "No NOAA G/R/S alert above zero" }
    }
}
