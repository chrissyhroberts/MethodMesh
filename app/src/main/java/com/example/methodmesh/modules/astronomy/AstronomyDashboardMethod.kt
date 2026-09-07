package com.example.methodmesh.modules.astronomy

import com.example.methodmesh.core.methodmesh.MethodObjectType
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object DashboardFields {
    const val STATUS = "astronomy_dashboard_status"
    const val RESULT = "astronomy_dashboard_result"
    const val OVERALL_LABEL = "astronomy_dashboard_overall_label"
    const val OVERALL_SCORE = "astronomy_dashboard_overall_score"
    const val PLANETARY_LABEL = "astronomy_dashboard_planetary_label"
    const val PLANETARY_SCORE = "astronomy_dashboard_planetary_score"
    const val DEEP_SKY_LABEL = "astronomy_dashboard_deep_sky_label"
    const val LATITUDE = "astronomy_dashboard_latitude"
    const val LONGITUDE = "astronomy_dashboard_longitude"
    const val LOCATION_SOURCE = "astronomy_dashboard_location_source"
    const val RETRIEVED_TIME = "astronomy_dashboard_retrieved_time_iso"
    const val DATA_AGE_HOURS = "astronomy_dashboard_data_age_hours"
    const val FROM_CACHE = "astronomy_dashboard_from_cache"
    const val TEMPERATURE = "astronomy_dashboard_temperature_c"
    const val HUMIDITY = "astronomy_dashboard_relative_humidity_pct"
    const val DEW_POINT = "astronomy_dashboard_dew_point_c"
    const val DEW_MARGIN = "astronomy_dashboard_dew_margin_c"
    const val DEW_RISK = "astronomy_dashboard_dew_risk"
    const val CLOUD = "astronomy_dashboard_cloud_cover_pct"
    const val VISIBILITY = "astronomy_dashboard_visibility_m"
    const val WIND = "astronomy_dashboard_wind_ms"
    const val GUST = "astronomy_dashboard_gust_ms"
    const val PM25 = "astronomy_dashboard_pm2_5"
    const val AOD = "astronomy_dashboard_aerosol_optical_depth"
    const val LIGHT_RADIANCE = "astronomy_dashboard_light_pollution_radiance_nw_cm2_sr"
    const val LIGHT_UNIT = "astronomy_dashboard_light_pollution_unit"
    const val LIGHT_LABEL = "astronomy_dashboard_light_pollution_label"
    const val LIGHT_DATASET = "astronomy_dashboard_light_pollution_dataset"
    const val LIGHT_DATASET_DATE = "astronomy_dashboard_light_pollution_dataset_date"
    const val LIGHT_CACHE_KIND = "astronomy_dashboard_light_pollution_cache_kind"
    // v0.5 compatibility fields; normally blank for NASA GIBS raster data.
    const val LIGHT_VALUE = "astronomy_dashboard_light_pollution_mcd_m2"
    const val LIGHT_RATIO = "astronomy_dashboard_light_pollution_ratio"
    const val JET_300 = "astronomy_dashboard_jet_300hpa_ms"
    const val JET_250 = "astronomy_dashboard_jet_250hpa_ms"
    const val JET_200 = "astronomy_dashboard_jet_200hpa_ms"
    const val JET_LABEL = "astronomy_dashboard_upper_atmosphere_label"
    const val MOON_ALT = "astronomy_dashboard_moon_altitude_deg"
    const val MOON_ILL = "astronomy_dashboard_moon_illumination_pct"
    const val WINDOW_START = "astronomy_dashboard_best_window_start_iso"
    const val WINDOW_END = "astronomy_dashboard_best_window_end_iso"
    const val WINDOW_SCORE = "astronomy_dashboard_best_window_score"
    const val FORECAST_CLOUD = "astronomy_dashboard_cloud_forecast_6h_json"
    const val JSON = "astronomy_dashboard_json"
    const val AUDIT = "astronomy_dashboard_audit_json"
    const val ERROR = "astronomy_dashboard_error"
    val outputs = listOf(
        STATUS, RESULT, OVERALL_LABEL, OVERALL_SCORE, PLANETARY_LABEL, PLANETARY_SCORE, DEEP_SKY_LABEL,
        LATITUDE, LONGITUDE, LOCATION_SOURCE, RETRIEVED_TIME, DATA_AGE_HOURS, FROM_CACHE,
        TEMPERATURE, HUMIDITY, DEW_POINT, DEW_MARGIN, DEW_RISK, CLOUD, VISIBILITY, WIND, GUST, PM25, AOD,
        LIGHT_RADIANCE, LIGHT_UNIT, LIGHT_LABEL, LIGHT_DATASET, LIGHT_DATASET_DATE, LIGHT_CACHE_KIND, LIGHT_VALUE, LIGHT_RATIO, JET_300, JET_250, JET_200, JET_LABEL, MOON_ALT, MOON_ILL, WINDOW_START, WINDOW_END, WINDOW_SCORE,
        FORECAST_CLOUD, JSON, AUDIT, ERROR
    )
}

object As100DashboardMethod : AstronomyMethodBase(
    "astronomy.dashboard",
    "Astronomy dashboard",
    "Refreshable observing dashboard combining local weather, haze, dew, Moon geometry, upper-atmosphere wind and the next useful observing window.",
    "astronomy.dashboard",
    MethodObjectType.Calculation,
    DashboardFields.RESULT,
    DashboardFields.STATUS,
    DashboardFields.ERROR,
    DashboardFields.outputs,
    version = "0.6.0"
) {
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val latitude = settings.value("latitude")?.toDoubleOrNull() ?: return failure("Latitude is required.")
        val longitude = settings.value("longitude")?.toDoubleOrNull() ?: return failure("Longitude is required.")
        val weatherJson = settings.value("weather_json").orEmpty()
        if (weatherJson.isBlank()) return failure("Current weather JSON is required; the dashboard screen fetches this automatically.")

        return runCatching {
            val weatherRoot = JSONObject(weatherJson)
            val current = weatherRoot.optJSONObject("current") ?: JSONObject()
            val airRoot = settings.value("air_quality_json")?.takeIf { it.isNotBlank() }?.let(::JSONObject)
            val air = airRoot?.optJSONObject("current")
            val jetRoot = settings.value("jet_json")?.takeIf { it.isNotBlank() }?.let(::JSONObject)
            val jetHourly = jetRoot?.optJSONObject("hourly")
            val hourlyJson = settings.value("hourly_json") ?: settings.value("forecast_json").orEmpty()

            fun number(obj: JSONObject?, key: String): Double? = obj?.optDouble(key, Double.NaN)?.takeIf { it.isFinite() }
            fun first(obj: JSONObject?, key: String): Double? = obj?.optJSONArray(key)?.let { arr -> if (arr.length() > 0) arr.optDouble(0, Double.NaN).takeIf { it.isFinite() } else null }

            val temperature = number(current, "temperature_2m")
            val humidity = number(current, "relative_humidity_2m")
            val suppliedDew = number(current, "dew_point_2m")
            val dew = if (temperature != null && humidity != null) AstronomyMath.dewRisk(temperature, humidity, suppliedDew) else null
            val cloud = number(current, "cloud_cover")
            val visibility = number(current, "visibility")
            val windKmh = number(current, "wind_speed_10m")
            val gustKmh = number(current, "wind_gusts_10m")
            val pm25 = number(air, "pm2_5")
            val aod = number(air, "aerosol_optical_depth")
            val lightRadiance = settings.value("light_pollution_radiance_nw_cm2_sr")?.toDoubleOrNull()
            val lightUnit = settings.value("light_pollution_unit").orEmpty()
            val lightLabel = settings.value("light_pollution_label").orEmpty()
            val lightDataset = settings.value("light_pollution_dataset").orEmpty()
            val lightDatasetDate = settings.value("light_pollution_dataset_date").orEmpty()
            val lightCacheKind = settings.value("light_pollution_cache_kind").orEmpty()
            val lightValue = settings.value("light_pollution_value_mcd_m2")?.toDoubleOrNull()
            val lightRatio = settings.value("light_pollution_ratio")?.toDoubleOrNull()
            val moon = AstronomyMath.moonHorizontal(Instant.now(), latitude, longitude)

            val conditions = AstronomyMath.conditionsScore(
                cloudCoverPct = cloud,
                visibilityM = visibility,
                windKmh = windKmh,
                gustKmh = gustKmh,
                temperatureC = temperature,
                humidityPct = humidity,
                dewPointC = dew?.dewPointC,
                aerosolOpticalDepth = aod,
                pm25 = pm25,
                moonAltitudeDeg = moon.altitudeDeg,
                moonIlluminationPct = moon.illuminationPct
            )

            val jet300 = first(jetHourly, "wind_speed_300hPa")
            val jet250 = first(jetHourly, "wind_speed_250hPa")
            val jet200 = first(jetHourly, "wind_speed_200hPa")
            val jetPeak = listOfNotNull(jet300, jet250, jet200).maxOrNull()
            val jetScore = jetPeak?.let { ((55.0 - it) / 45.0 * 100.0).roundToInt().coerceIn(0, 100) } ?: 65
            val jetLabel = rating(jetScore)
            val planetaryScore = (conditions.overall * 0.55 + jetScore * 0.45).roundToInt().coerceIn(0, 100)
            val planetaryLabel = rating(planetaryScore)
            val overallScore = (conditions.overall * 0.80 + jetScore * 0.20).roundToInt().coerceIn(0, 100)
            val overallLabel = rating(overallScore)

            val window = if (hourlyJson.isNotBlank()) As100ImagingWindowMethod.calculate(
                mapOf(
                    "latitude" to latitude.toString(),
                    "longitude" to longitude.toString(),
                    "forecast_json" to hourlyJson,
                    "hours_ahead" to (settings.value("hours_ahead") ?: "14"),
                    "interval_minutes" to (settings.value("interval_minutes") ?: "30"),
                    "minimum_score" to (settings.value("minimum_score") ?: "55"),
                    "use_target" to "false"
                )
            ) else emptyMap()

            val cloud6h = cloudForecast(hourlyJson, 6)
            val resultText = buildString {
                append(overallLabel)
                append(" · ")
                append(overallScore)
                append("/100")
                append(" · planetary ")
                append(planetaryLabel)
                dew?.let { append(" · dew "); append(it.risk) }
                window[ImagingWindowFields.START]?.takeIf { it.isNotBlank() }?.let { append(" · best from "); append(it) }
            }

            val payload = JSONObject().apply {
                put("overall_label", overallLabel); put("overall_score", overallScore)
                put("planetary_label", planetaryLabel); put("planetary_score", planetaryScore)
                put("deep_sky_label", conditions.label); put("conditions_score", conditions.overall)
                put("latitude", latitude); put("longitude", longitude)
                put("temperature_c", temperature); put("relative_humidity_pct", humidity)
                put("dew_point_c", dew?.dewPointC); put("dew_margin_c", dew?.marginC); put("dew_risk", dew?.risk)
                put("cloud_cover_pct", cloud); put("visibility_m", visibility)
                put("wind_ms", windKmh?.div(3.6)); put("gust_ms", gustKmh?.div(3.6))
                put("pm2_5", pm25); put("aerosol_optical_depth", aod)
                put("light_pollution_radiance_nw_cm2_sr", lightRadiance); put("light_pollution_unit", lightUnit)
                put("light_pollution_label", lightLabel); put("light_pollution_dataset", lightDataset); put("light_pollution_dataset_date", lightDatasetDate); put("light_pollution_cache_kind", lightCacheKind)
                put("light_pollution_mcd_m2", lightValue); put("light_pollution_ratio", lightRatio)
                put("jet_300hpa_ms", jet300); put("jet_250hpa_ms", jet250); put("jet_200hpa_ms", jet200); put("upper_atmosphere_label", jetLabel)
                put("moon_altitude_deg", moon.altitudeDeg); put("moon_illumination_pct", moon.illuminationPct)
                put("best_window_start_iso", window[ImagingWindowFields.START].orEmpty()); put("best_window_end_iso", window[ImagingWindowFields.END].orEmpty())
                put("best_window_score", window[ImagingWindowFields.SCORE].orEmpty()); put("cloud_forecast_6h", JSONArray(cloud6h))
                put("retrieved_time_iso", settings.value("retrieved_time_iso").orEmpty())
                put("data_age_hours", settings.value("data_age_hours").orEmpty())
                put("from_cache", settings.value("from_cache").orEmpty())
            }

            success(
                DashboardFields.RESULT to resultText,
                DashboardFields.OVERALL_LABEL to overallLabel,
                DashboardFields.OVERALL_SCORE to overallScore.toString(),
                DashboardFields.PLANETARY_LABEL to planetaryLabel,
                DashboardFields.PLANETARY_SCORE to planetaryScore.toString(),
                DashboardFields.DEEP_SKY_LABEL to conditions.label,
                DashboardFields.LATITUDE to fmt(latitude, 6),
                DashboardFields.LONGITUDE to fmt(longitude, 6),
                DashboardFields.LOCATION_SOURCE to (settings.value("resolved_location_source") ?: settings.value("location_source")).orEmpty(),
                DashboardFields.RETRIEVED_TIME to settings.value("retrieved_time_iso").orEmpty(),
                DashboardFields.DATA_AGE_HOURS to settings.value("data_age_hours").orEmpty(),
                DashboardFields.FROM_CACHE to settings.value("from_cache").orEmpty(),
                DashboardFields.TEMPERATURE to temperature?.let { fmt(it, 1) }.orEmpty(),
                DashboardFields.HUMIDITY to humidity?.let { fmt(it, 0) }.orEmpty(),
                DashboardFields.DEW_POINT to dew?.dewPointC?.let { fmt(it, 1) }.orEmpty(),
                DashboardFields.DEW_MARGIN to dew?.marginC?.let { fmt(it, 1) }.orEmpty(),
                DashboardFields.DEW_RISK to dew?.risk.orEmpty(),
                DashboardFields.CLOUD to cloud?.let { fmt(it, 0) }.orEmpty(),
                DashboardFields.VISIBILITY to visibility?.let { fmt(it, 0) }.orEmpty(),
                DashboardFields.WIND to windKmh?.div(3.6)?.let { fmt(it, 1) }.orEmpty(),
                DashboardFields.GUST to gustKmh?.div(3.6)?.let { fmt(it, 1) }.orEmpty(),
                DashboardFields.PM25 to pm25?.let { fmt(it, 1) }.orEmpty(),
                DashboardFields.AOD to aod?.let { fmt(it, 2) }.orEmpty(),
                DashboardFields.LIGHT_RADIANCE to lightRadiance?.let { fmt(it, 3) }.orEmpty(),
                DashboardFields.LIGHT_UNIT to lightUnit,
                DashboardFields.LIGHT_LABEL to lightLabel,
                DashboardFields.LIGHT_DATASET to lightDataset,
                DashboardFields.LIGHT_DATASET_DATE to lightDatasetDate,
                DashboardFields.LIGHT_CACHE_KIND to lightCacheKind,
                DashboardFields.LIGHT_VALUE to lightValue?.let { fmt(it, 4) }.orEmpty(),
                DashboardFields.LIGHT_RATIO to lightRatio?.let { fmt(it, 2) }.orEmpty(),
                DashboardFields.JET_300 to jet300?.let { fmt(it, 1) }.orEmpty(),
                DashboardFields.JET_250 to jet250?.let { fmt(it, 1) }.orEmpty(),
                DashboardFields.JET_200 to jet200?.let { fmt(it, 1) }.orEmpty(),
                DashboardFields.JET_LABEL to jetLabel,
                DashboardFields.MOON_ALT to fmt(moon.altitudeDeg, 1),
                DashboardFields.MOON_ILL to fmt(moon.illuminationPct, 0),
                DashboardFields.WINDOW_START to window[ImagingWindowFields.START].orEmpty(),
                DashboardFields.WINDOW_END to window[ImagingWindowFields.END].orEmpty(),
                DashboardFields.WINDOW_SCORE to window[ImagingWindowFields.SCORE].orEmpty(),
                DashboardFields.FORECAST_CLOUD to JSONArray(cloud6h).toString(),
                DashboardFields.JSON to payload.toString(),
                DashboardFields.AUDIT to JSONObject().apply {
                    put("method_id", id)
                    put("algorithm_version", "0.6.0")
                    put("upper_atmosphere_interpretation", "Heuristic planning proxy from GFS wind at 300/250/200 hPa; not a direct seeing measurement.")
                    put("cloud_source", "Open-Meteo forecast; satellite nowcast not yet included")
                    put("light_pollution_interpretation", "NASA VIIRS nighttime at-sensor radiance may be supplied by the dashboard as a light-pollution proxy; not observed zenith sky brightness or Bortle class.")
                    put("refresh_semantics", "Refresh replaces preview; graph observation is recorded only on confirm/return.")
                }.toString()
            )
        }.getOrElse { failure(it.message ?: "Could not calculate astronomy dashboard.") }
    }

    private fun cloudForecast(json: String, count: Int): List<Double> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            val hourly = root.optJSONObject("hourly") ?: return@runCatching emptyList<Double>()
            val values = hourly.optJSONArray("cloud_cover") ?: return@runCatching emptyList<Double>()
            val times = hourly.optJSONArray("time")
            val offset = ZoneOffset.ofTotalSeconds(root.optInt("utc_offset_seconds", 0))
            val now = Instant.now().minusSeconds(1800)
            val start = if (times == null) 0 else (0 until times.length()).firstOrNull { i ->
                runCatching { LocalDateTime.parse(times.optString(i)).toInstant(offset) >= now }.getOrDefault(false)
            } ?: 0
            (start until minOf(start + count, values.length())).mapNotNull { i -> values.optDouble(i, Double.NaN).takeIf { it.isFinite() } }
        }.getOrDefault(emptyList())
    }

    private fun rating(score: Int): String = when {
        score >= 85 -> "EXCELLENT"
        score >= 70 -> "GOOD"
        score >= 55 -> "MARGINAL"
        score >= 35 -> "POOR"
        else -> "VERY POOR"
    }

    private fun fmt(value: Double, digits: Int): String = String.format(Locale.US, "%.${digits}f", value)
}
