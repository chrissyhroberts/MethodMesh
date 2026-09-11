package com.example.methodmesh.modules.weather

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.core.onlinedata.roundLocationForDisclosure
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import kotlin.math.max

internal const val WEATHER_MODULE_VERSION = "0.2.6"

internal object WeatherFields {
    val dashboard = listOf(
        "weather_dashboard_status", "weather_dashboard_result",
        "weather_dashboard_requested_latitude", "weather_dashboard_requested_longitude",
        "weather_dashboard_query_latitude", "weather_dashboard_query_longitude",
        "weather_dashboard_temperature_c", "weather_dashboard_condition", "weather_dashboard_data_class",
        "weather_dashboard_precipitation_mm", "weather_dashboard_next_rain_time_iso", "weather_dashboard_next_rain_mm",
        "weather_dashboard_wind_speed_ms", "weather_dashboard_wind_direction_deg", "weather_dashboard_pressure_msl_hpa",
        "weather_dashboard_valid_time_iso", "weather_dashboard_provider", "weather_dashboard_radar_frame_count",
        "weather_dashboard_retrieved_time_iso", "weather_dashboard_from_cache",
        "weather_dashboard_data_age_hours", "weather_dashboard_audit_json", "weather_dashboard_error"
    )
    val conditions = listOf(
        "weather_conditions_status", "weather_conditions_result", "weather_conditions_requested_latitude", "weather_conditions_requested_longitude",
        "weather_conditions_query_latitude", "weather_conditions_query_longitude", "weather_conditions_grid_latitude", "weather_conditions_grid_longitude",
        "weather_conditions_valid_time_iso", "weather_conditions_retrieved_time_iso", "weather_conditions_data_class", "weather_conditions_provider",
        "weather_conditions_temperature_c", "weather_conditions_apparent_temperature_c", "weather_conditions_relative_humidity_pct",
        "weather_conditions_dew_point_c", "weather_conditions_precipitation_mm", "weather_conditions_rain_mm",
        "weather_conditions_pressure_msl_hpa", "weather_conditions_surface_pressure_hpa", "weather_conditions_cloud_cover_pct",
        "weather_conditions_wind_speed_ms", "weather_conditions_wind_direction_deg", "weather_conditions_wind_direction_compass",
        "weather_conditions_wind_gust_ms", "weather_conditions_weather_code", "weather_conditions_condition_label",
        "weather_conditions_from_cache", "weather_conditions_data_age_hours", "weather_conditions_audit_json", "weather_conditions_error"
    )
    val forecast = listOf(
        "weather_forecast_status", "weather_forecast_result", "weather_forecast_start_time_iso", "weather_forecast_end_time_iso",
        "weather_forecast_selected_time_iso", "weather_forecast_selected_temperature_c", "weather_forecast_selected_precipitation_mm",
        "weather_forecast_selected_precipitation_probability_pct", "weather_forecast_selected_wind_speed_ms",
        "weather_forecast_daily_high_c", "weather_forecast_daily_low_c", "weather_forecast_daily_precipitation_mm",
        "weather_forecast_series_json", "weather_forecast_daily_series_json", "weather_forecast_provider", "weather_forecast_from_cache", "weather_forecast_data_age_hours",
        "weather_forecast_audit_json", "weather_forecast_error"
    )
    val precipitation = listOf(
        "weather_precipitation_status", "weather_precipitation_result", "weather_precipitation_valid_from_iso", "weather_precipitation_valid_to_iso",
        "weather_precipitation_current_mm", "weather_precipitation_total_mm", "weather_precipitation_next_time_iso",
        "weather_precipitation_threshold_mm_per_hour", "weather_precipitation_next_threshold_time_iso",
        "weather_precipitation_max_hourly_mm", "weather_precipitation_series_json", "weather_precipitation_provider",
        "weather_precipitation_from_cache", "weather_precipitation_data_age_hours", "weather_precipitation_audit_json", "weather_precipitation_error"
    )
    val radar = listOf(
        "weather_radar_status", "weather_radar_result", "weather_radar_requested_latitude", "weather_radar_requested_longitude",
        "weather_radar_frame_time_iso", "weather_radar_frame_class", "weather_radar_provider", "weather_radar_coverage_state",
        "weather_radar_host", "weather_radar_frame_path", "weather_radar_tile_template", "weather_radar_zoom",
        "weather_radar_observed_frame_count", "weather_radar_nowcast_frame_count", "weather_radar_timeline_json",
        "weather_radar_retrieved_time_iso", "weather_radar_from_cache", "weather_radar_data_age_hours",
        "weather_radar_audit_json", "weather_radar_error"
    )
    val meteogram = listOf(
        "weather_meteogram_status", "weather_meteogram_result", "weather_meteogram_selected_time_iso", "weather_meteogram_temperature_c",
        "weather_meteogram_dew_point_c", "weather_meteogram_precipitation_mm", "weather_meteogram_pressure_msl_hpa",
        "weather_meteogram_wind_speed_ms", "weather_meteogram_wind_gust_ms", "weather_meteogram_cloud_cover_pct",
        "weather_meteogram_values_json", "weather_meteogram_series_json", "weather_meteogram_provider",
        "weather_meteogram_from_cache", "weather_meteogram_data_age_hours", "weather_meteogram_audit_json", "weather_meteogram_error"
    )
    val wind = listOf(
        "weather_wind_status", "weather_wind_result", "weather_wind_valid_time_iso", "weather_wind_speed_ms",
        "weather_wind_direction_deg", "weather_wind_direction_compass", "weather_wind_gust_ms", "weather_wind_data_class",
        "weather_wind_provider", "weather_wind_from_cache", "weather_wind_data_age_hours", "weather_wind_audit_json", "weather_wind_error"
    )
    val atmosphere = listOf(
        "weather_atmosphere_status", "weather_atmosphere_result", "weather_atmosphere_valid_time_iso", "weather_atmosphere_cape_jkg",
        "weather_atmosphere_temperature_850hpa_c", "weather_atmosphere_relative_humidity_850hpa_pct",
        "weather_atmosphere_wind_speed_850hpa_ms", "weather_atmosphere_geopotential_height_850hpa_m",
        "weather_atmosphere_temperature_500hpa_c", "weather_atmosphere_geopotential_height_500hpa_m",
        "weather_atmosphere_wind_speed_300hpa_ms", "weather_atmosphere_wind_direction_300hpa_deg",
        "weather_atmosphere_values_json", "weather_atmosphere_provider", "weather_atmosphere_from_cache",
        "weather_atmosphere_data_age_hours", "weather_atmosphere_audit_json", "weather_atmosphere_error"
    )
    val sun = listOf(
        "weather_sun_status", "weather_sun_result", "weather_sun_date", "weather_sun_sunrise_iso", "weather_sun_sunset_iso",
        "weather_sun_daylight_seconds", "weather_sun_sunshine_seconds", "weather_sun_uv_index_max", "weather_sun_provider",
        "weather_sun_from_cache", "weather_sun_data_age_hours", "weather_sun_audit_json", "weather_sun_error"
    )
    val snapshot = listOf(
        "weather_snapshot_status", "weather_snapshot_result", "weather_snapshot_requested_time_iso", "weather_snapshot_valid_time_iso",
        "weather_snapshot_requested_latitude", "weather_snapshot_requested_longitude", "weather_snapshot_query_latitude",
        "weather_snapshot_query_longitude", "weather_snapshot_grid_latitude", "weather_snapshot_grid_longitude",
        "weather_snapshot_data_class", "weather_snapshot_provider", "weather_snapshot_temperature_c",
        "weather_snapshot_apparent_temperature_c", "weather_snapshot_relative_humidity_pct", "weather_snapshot_dew_point_c",
        "weather_snapshot_precipitation_mm", "weather_snapshot_pressure_msl_hpa", "weather_snapshot_wind_speed_ms",
        "weather_snapshot_wind_direction_deg", "weather_snapshot_wind_gust_ms", "weather_snapshot_cloud_cover_pct",
        "weather_snapshot_visibility_m", "weather_snapshot_weather_code", "weather_snapshot_retrieved_time_iso",
        "weather_snapshot_audit_json", "weather_snapshot_error"
    )
    val modelCompare = listOf(
        "weather_model_compare_status", "weather_model_compare_result", "weather_model_compare_valid_time_iso",
        "weather_model_compare_temperature_mean_c", "weather_model_compare_temperature_min_c", "weather_model_compare_temperature_max_c",
        "weather_model_compare_temperature_spread_c", "weather_model_compare_precipitation_mean_mm", "weather_model_compare_wind_mean_ms",
        "weather_model_compare_member_count", "weather_model_compare_series_json", "weather_model_compare_provider",
        "weather_model_compare_from_cache", "weather_model_compare_data_age_hours", "weather_model_compare_audit_json", "weather_model_compare_error"
    )
}

internal abstract class WeatherMethodBase(
    final override val id: String,
    name: String,
    description: String,
    inputs: List<String>,
    outputs: List<String>,
    maturity: String = "DEVELOPMENT",
    connectivity: String = "ONLINE_OFFLINE",
    methodType: MethodObjectType = MethodObjectType.Calculation
) : As100Method {
    final override val ref = ArchitectureRef(ArchitectureId(id), "Method", name)
    final override val descriptor = MethodDescriptor(
        id = ArchitectureId(id), methodType = methodType, name = name, version = WEATHER_MODULE_VERSION,
        description = description, inputs = inputs, outputs = outputs, graphOutputs = listOf(id),
        parameters = mapOf(
            "category" to "Weather", "status" to if (maturity == "EXPERIMENTAL") "Experimental" else "Development",
            "maturity" to maturity, "connectivity" to connectivity,
            "interaction_lifecycle" to "live_working_result_commit", "icon_key" to "tool"
        )
    )
    final override val contract = MethodContract(
        method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs
    )

    final override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    final override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val capture = WeatherRuntime.capture(this, request.context)
        return result(request, capture.values, InvocationContext.from(request.context))
    }

    fun calculate(settings: Map<String, String>): Map<String, String> = WeatherCalculations.calculate(id, descriptor.outputs, settings)

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val statusKey = descriptor.outputs.firstOrNull { it.endsWith("_status") }.orEmpty()
        val errorKey = descriptor.outputs.firstOrNull { it.endsWith("_error") }.orEmpty()
        val ok = values[statusKey] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.weather", id, WEATHER_MODULE_VERSION)
        val observation = Observation(phenomenon = id, values = values, temporalContext = request.temporalContext, provenance = provenance)
        val transformation = Transformation(
            action = id, method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext, provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request, status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            observations = listOf(observation), transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(errorKey to values[errorKey].orEmpty())
        ).withInvocationContext(invocation)
    }
}

internal object As100WeatherDashboardMethod : WeatherMethodBase(
    "weather.dashboard", "Weather dashboard", "Integrated live weather instrument dashboard.",
    listOf("latitude", "longitude", "threshold_mm_per_hour", "offline_only"), WeatherFields.dashboard
)
internal object As100WeatherConditionsMethod : WeatherMethodBase(
    "weather.conditions", "Weather conditions", "Current or selected-time meteorological conditions.",
    listOf("latitude", "longitude", "target_time_iso", "offline_only"), WeatherFields.conditions
)
internal object As100WeatherForecastMethod : WeatherMethodBase(
    "weather.forecast", "Weather forecast", "Hourly and daily forecast with structured series.",
    listOf("latitude", "longitude", "target_time_iso", "horizon_hours", "offline_only"), WeatherFields.forecast
)
internal object As100WeatherPrecipitationMethod : WeatherMethodBase(
    "weather.precipitation", "Precipitation", "Quantitative precipitation timing, threshold and accumulation.",
    listOf("latitude", "longitude", "target_time_iso", "horizon_hours", "threshold_mm_per_hour", "offline_only"), WeatherFields.precipitation
)
internal object As100WeatherRadarMethod : WeatherMethodBase(
    "weather.radar", "Weather radar", "Observed radar history and genuine provider nowcast frames.",
    listOf("latitude", "longitude", "frame_time_iso", "zoom", "offline_only"), WeatherFields.radar, methodType = MethodObjectType.DeviceService
)
internal object As100WeatherMeteogramMethod : WeatherMethodBase(
    "weather.meteogram", "Weather meteogram", "Detailed meteorological time-series inspection.",
    listOf("latitude", "longitude", "target_time_iso", "horizon_hours", "offline_only"), WeatherFields.meteogram
)
internal object As100WeatherWindMethod : WeatherMethodBase(
    "weather.wind", "Wind", "Wind direction, sustained speed and gust instrument.",
    listOf("latitude", "longitude", "target_time_iso", "offline_only"), WeatherFields.wind
)
internal object As100WeatherAtmosphereMethod : WeatherMethodBase(
    "weather.atmosphere", "Detailed atmosphere", "Experimental GFS pressure-level and convective meteorology.",
    listOf("latitude", "longitude", "target_time_iso", "offline_only"), WeatherFields.atmosphere, maturity = "EXPERIMENTAL"
)
internal object As100WeatherSunMethod : WeatherMethodBase(
    "weather.sun", "Sun and daylight", "Sunrise, sunset, daylight, sunshine and UV.",
    listOf("latitude", "longitude", "target_time_iso", "offline_only"), WeatherFields.sun
)
internal object As100WeatherSnapshotMethod : WeatherMethodBase(
    "weather.snapshot", "Weather snapshot", "Research-friendly weather snapshot for a location and timestamp.",
    listOf("latitude", "longitude", "target_time_iso", "source_policy"), WeatherFields.snapshot, connectivity = "ONLINE_ONLY"
)
internal object As100WeatherModelCompareMethod : WeatherMethodBase(
    "weather.model_compare", "Model comparison", "Experimental ensemble spread comparison; spread is not a calibrated confidence interval.",
    listOf("latitude", "longitude", "target_time_iso"), WeatherFields.modelCompare, maturity = "EXPERIMENTAL", connectivity = "ONLINE_ONLY"
)

internal object WeatherCalculations {
    fun calculate(methodId: String, outputs: List<String>, settings: Map<String, String>): Map<String, String> = runCatching {
        when (methodId) {
            As100WeatherDashboardMethod.id -> dashboard(outputs, settings)
            As100WeatherConditionsMethod.id -> conditions(outputs, settings)
            As100WeatherForecastMethod.id -> forecast(outputs, settings)
            As100WeatherPrecipitationMethod.id -> precipitation(outputs, settings)
            As100WeatherRadarMethod.id -> radar(outputs, settings)
            As100WeatherMeteogramMethod.id -> meteogram(outputs, settings)
            As100WeatherWindMethod.id -> wind(outputs, settings)
            As100WeatherAtmosphereMethod.id -> atmosphere(outputs, settings)
            As100WeatherSunMethod.id -> sun(outputs, settings)
            As100WeatherSnapshotMethod.id -> snapshot(outputs, settings)
            As100WeatherModelCompareMethod.id -> modelCompare(outputs, settings)
            else -> failure(outputs, "Unknown Weather method $methodId")
        }
    }.getOrElse { failure(outputs, it.message ?: "Weather calculation failed.") }

    private fun dashboard(outputs: List<String>, s: Map<String, String>): Map<String, String> {
        val w = forecastBundle(s) ?: return failure(outputs, apiError(s))
        val c = w.current ?: return failure(outputs, "Current weather is unavailable.")
        val nextRain = futureHours(w).firstOrNull { (it.precipitationMm ?: 0.0) >= threshold(s) }
        val result = buildList {
            c.temperatureC?.let { add("${f(it)} °C") }
            add(WeatherJson.weatherLabel(c.weatherCode))
            nextRain?.let { add("rain ${hourLabel(it.timeIso)}") }
        }.joinToString(" · ")
        val query = disclosed(s)
        val radarCount = WeatherJson.parseRadar(input(s, "radar_json")).all.size
        return success(outputs, s, mapOf(
            "weather_dashboard_result" to result,
            "weather_dashboard_requested_latitude" to input(s, "latitude"),
            "weather_dashboard_requested_longitude" to input(s, "longitude"),
            "weather_dashboard_query_latitude" to query.first,
            "weather_dashboard_query_longitude" to query.second,
            "weather_dashboard_temperature_c" to fs(c.temperatureC),
            "weather_dashboard_condition" to WeatherJson.weatherLabel(c.weatherCode),
            "weather_dashboard_data_class" to WeatherSourceClass.MODELLED_CURRENT.name,
            "weather_dashboard_precipitation_mm" to fs(c.precipitationMm),
            "weather_dashboard_next_rain_time_iso" to nextRain?.timeIso.orEmpty(),
            "weather_dashboard_next_rain_mm" to fs(nextRain?.precipitationMm),
            "weather_dashboard_wind_speed_ms" to fs(c.windSpeedMs),
            "weather_dashboard_wind_direction_deg" to fs(c.windDirectionDeg),
            "weather_dashboard_pressure_msl_hpa" to fs(c.pressureMslHpa),
            "weather_dashboard_valid_time_iso" to c.validTimeIso,
            "weather_dashboard_provider" to provider(s),
            "weather_dashboard_radar_frame_count" to radarCount.toString(),
            "weather_dashboard_retrieved_time_iso" to retrieved(s),
            "weather_dashboard_from_cache" to fromCache(s),
            "weather_dashboard_data_age_hours" to age(s)
        ))
    }

    private fun conditions(outputs: List<String>, s: Map<String, String>): Map<String, String> {
        val w = forecastBundle(s) ?: return failure(outputs, apiError(s))
        val selected = WeatherJson.nearestHour(w.hourly, input(s, "target_time_iso"))
        val requested = input(s, "target_time_iso")
        if (requested.isNotBlank() && selected != null && tooFar(requested, selected.timeIso)) {
            return failure(outputs, "No forecast value is available within 90 minutes of the selected time. Use weather.snapshot for historical reconstruction.")
        }
        val c = (if (requested.isBlank()) w.current else selected?.toCurrent())
            ?: w.current ?: return failure(outputs, "Weather conditions are unavailable.")
        val query = disclosed(s)
        val result = listOfNotNull(c.temperatureC?.let { "${f(it)} °C" }, WeatherJson.weatherLabel(c.weatherCode), c.windSpeedMs?.let { "wind ${f(it)} m/s" }).joinToString(" · ")
        return success(outputs, s, mapOf(
            "weather_conditions_result" to result,
            "weather_conditions_requested_latitude" to input(s, "latitude"),
            "weather_conditions_requested_longitude" to input(s, "longitude"),
            "weather_conditions_query_latitude" to query.first,
            "weather_conditions_query_longitude" to query.second,
            "weather_conditions_grid_latitude" to fs(w.latitude, 6),
            "weather_conditions_grid_longitude" to fs(w.longitude, 6),
            "weather_conditions_valid_time_iso" to c.validTimeIso,
            "weather_conditions_retrieved_time_iso" to retrieved(s),
            "weather_conditions_data_class" to if (selected != null && requested.isNotBlank()) WeatherSourceClass.FORECAST.name else WeatherSourceClass.MODELLED_CURRENT.name,
            "weather_conditions_provider" to provider(s),
            "weather_conditions_temperature_c" to fs(c.temperatureC),
            "weather_conditions_apparent_temperature_c" to fs(c.apparentTemperatureC),
            "weather_conditions_relative_humidity_pct" to fs(c.relativeHumidityPct),
            "weather_conditions_dew_point_c" to fs(c.dewPointC),
            "weather_conditions_precipitation_mm" to fs(c.precipitationMm),
            "weather_conditions_rain_mm" to fs(c.rainMm),
            "weather_conditions_pressure_msl_hpa" to fs(c.pressureMslHpa),
            "weather_conditions_surface_pressure_hpa" to fs(c.surfacePressureHpa),
            "weather_conditions_cloud_cover_pct" to fs(c.cloudCoverPct),
            "weather_conditions_wind_speed_ms" to fs(c.windSpeedMs),
            "weather_conditions_wind_direction_deg" to fs(c.windDirectionDeg),
            "weather_conditions_wind_direction_compass" to WeatherJson.compass(c.windDirectionDeg),
            "weather_conditions_wind_gust_ms" to fs(c.windGustMs),
            "weather_conditions_weather_code" to c.weatherCode?.toString().orEmpty(),
            "weather_conditions_condition_label" to WeatherJson.weatherLabel(c.weatherCode),
            "weather_conditions_from_cache" to fromCache(s),
            "weather_conditions_data_age_hours" to age(s)
        ))
    }

    private fun forecast(outputs: List<String>, s: Map<String, String>): Map<String, String> {
        val w = forecastBundle(s) ?: return failure(outputs, apiError(s))
        val hours = futureHours(w).take(horizon(s))
        val requested = input(s, "target_time_iso")
        val selected = WeatherJson.nearestHour(hours, requested) ?: hours.firstOrNull()
        if (requested.isNotBlank() && selected != null && tooFar(requested, selected.timeIso)) {
            return failure(outputs, "Selected time is outside the available forecast horizon.")
        }
        val today = w.daily.firstOrNull()
        val series = JSONArray().apply { hours.forEach { h -> put(hourJson(h)) } }.toString()
        val dailySeries = JSONArray().apply { w.daily.take(10).forEach { day -> put(dayJson(day)) } }.toString()
        return success(outputs, s, mapOf(
            "weather_forecast_result" to "${today?.temperatureMaxC?.let(::f) ?: "—"}/${today?.temperatureMinC?.let(::f) ?: "—"} °C · rain ${today?.precipitationSumMm?.let(::f) ?: "—"} mm",
            "weather_forecast_start_time_iso" to hours.firstOrNull()?.timeIso.orEmpty(),
            "weather_forecast_end_time_iso" to hours.lastOrNull()?.timeIso.orEmpty(),
            "weather_forecast_selected_time_iso" to selected?.timeIso.orEmpty(),
            "weather_forecast_selected_temperature_c" to fs(selected?.temperatureC),
            "weather_forecast_selected_precipitation_mm" to fs(selected?.precipitationMm),
            "weather_forecast_selected_precipitation_probability_pct" to fs(selected?.precipitationProbabilityPct),
            "weather_forecast_selected_wind_speed_ms" to fs(selected?.windSpeedMs),
            "weather_forecast_daily_high_c" to fs(today?.temperatureMaxC),
            "weather_forecast_daily_low_c" to fs(today?.temperatureMinC),
            "weather_forecast_daily_precipitation_mm" to fs(today?.precipitationSumMm),
            "weather_forecast_series_json" to series,
            "weather_forecast_daily_series_json" to dailySeries,
            "weather_forecast_provider" to provider(s),
            "weather_forecast_from_cache" to fromCache(s),
            "weather_forecast_data_age_hours" to age(s)
        ))
    }

    private fun precipitation(outputs: List<String>, s: Map<String, String>): Map<String, String> {
        val w = forecastBundle(s) ?: return failure(outputs, apiError(s))
        val hours = precipitationWindow(w, s).take(horizon(s)); val t = threshold(s)
        val requested = input(s, "target_time_iso")
        if (requested.isNotBlank() && hours.firstOrNull()?.let { tooFar(requested, it.timeIso) } == true) {
            return failure(outputs, "Window start is outside the available forecast horizon.")
        }
        val next = hours.firstOrNull { (it.precipitationMm ?: 0.0) > 0.0 }
        val nextThreshold = hours.firstOrNull { (it.precipitationMm ?: 0.0) >= t }
        val total = hours.sumOf { it.precipitationMm ?: 0.0 }
        val maximum = hours.maxOfOrNull { it.precipitationMm ?: 0.0 } ?: 0.0
        val series = JSONArray().apply { hours.forEach { h -> put(JSONObject().put("time", h.timeIso).put("precipitation_mm", h.precipitationMm ?: JSONObject.NULL).put("probability_pct", h.precipitationProbabilityPct ?: JSONObject.NULL)) } }.toString()
        return success(outputs, s, mapOf(
            "weather_precipitation_result" to if (nextThreshold == null) "No ≥${f(t)} mm hourly precipitation in selected horizon" else "Next ≥${f(t)} mm: ${hourLabel(nextThreshold.timeIso)} · total ${f(total)} mm",
            "weather_precipitation_valid_from_iso" to hours.firstOrNull()?.timeIso.orEmpty(),
            "weather_precipitation_valid_to_iso" to hours.lastOrNull()?.timeIso.orEmpty(),
            "weather_precipitation_current_mm" to fs(w.current?.precipitationMm),
            "weather_precipitation_total_mm" to f(total),
            "weather_precipitation_next_time_iso" to next?.timeIso.orEmpty(),
            "weather_precipitation_threshold_mm_per_hour" to f(t),
            "weather_precipitation_next_threshold_time_iso" to nextThreshold?.timeIso.orEmpty(),
            "weather_precipitation_max_hourly_mm" to f(maximum),
            "weather_precipitation_series_json" to series,
            "weather_precipitation_provider" to provider(s),
            "weather_precipitation_from_cache" to fromCache(s),
            "weather_precipitation_data_age_hours" to age(s)
        ))
    }

    private fun radar(outputs: List<String>, s: Map<String, String>): Map<String, String> {
        val timeline = WeatherJson.parseRadar(input(s, "radar_json"))
        if (timeline.all.isEmpty()) return failure(outputs, apiError(s).ifBlank { "No radar frames are available." })
        val requested = WeatherJson.parseInstant(input(s, "frame_time_iso"))
        val selected = (if (requested == null) timeline.newest else timeline.all.minByOrNull { kotlin.math.abs(it.timeEpochSeconds - requested.epochSecond) })
            ?: return failure(outputs, "No radar frame selected.")
        if (requested != null && kotlin.math.abs(selected.timeEpochSeconds - requested.epochSecond) > 900) {
            return failure(outputs, "No radar frame is available within 15 minutes of the requested frame time.")
        }
        val tileTemplate = if (timeline.host.isBlank()) "" else "${timeline.host}${selected.path}/256/{z}/{x}/{y}/2/1_1.png"
        val zoom = input(s, "zoom").toDoubleOrNull()?.coerceIn(1.0, 7.0) ?: 5.0
        val timelineJson = JSONObject().apply {
            put("observed", JSONArray().apply {
                timeline.observed.forEach { frame -> put(JSONObject().put("time_iso", frame.timeIso).put("path", frame.path).put("class", frame.sourceClass.name)) }
            })
            put("nowcast", JSONArray().apply {
                timeline.nowcast.forEach { frame -> put(JSONObject().put("time_iso", frame.timeIso).put("path", frame.path).put("class", frame.sourceClass.name)) }
            })
        }.toString()
        return success(outputs, s, mapOf(
            "weather_radar_result" to "${if (selected.sourceClass == WeatherSourceClass.NOWCAST) "Provider nowcast" else "Observed radar"} · ${selected.timeIso}",
            "weather_radar_requested_latitude" to input(s, "latitude"),
            "weather_radar_requested_longitude" to input(s, "longitude"),
            "weather_radar_frame_time_iso" to selected.timeIso,
            "weather_radar_frame_class" to selected.sourceClass.name,
            "weather_radar_provider" to provider(s).ifBlank { "RainViewer" },
            "weather_radar_coverage_state" to "not_evaluated",
            "weather_radar_host" to timeline.host,
            "weather_radar_frame_path" to selected.path,
            "weather_radar_tile_template" to tileTemplate,
            "weather_radar_zoom" to fs(zoom),
            "weather_radar_observed_frame_count" to timeline.observed.size.toString(),
            "weather_radar_nowcast_frame_count" to timeline.nowcast.size.toString(),
            "weather_radar_timeline_json" to timelineJson,
            "weather_radar_retrieved_time_iso" to retrieved(s),
            "weather_radar_from_cache" to fromCache(s),
            "weather_radar_data_age_hours" to age(s)
        ))
    }

    private fun meteogram(outputs: List<String>, s: Map<String, String>): Map<String, String> {
        val w = forecastBundle(s) ?: return failure(outputs, apiError(s))
        val hours = futureHours(w).take(horizon(s)); val requested = input(s, "target_time_iso")
        val selected = WeatherJson.nearestHour(hours, requested) ?: hours.firstOrNull()
            ?: return failure(outputs, "No hourly meteorology available.")
        if (requested.isNotBlank() && tooFar(requested, selected.timeIso)) return failure(outputs, "Selected time is outside the meteogram horizon.")
        val values = hourJson(selected).toString()
        val series = JSONArray().apply { hours.forEach { put(hourJson(it)) } }.toString()
        return success(outputs, s, mapOf(
            "weather_meteogram_result" to "${hourLabel(selected.timeIso)} · ${selected.temperatureC?.let(::f) ?: "—"} °C · ${selected.precipitationMm?.let(::f) ?: "—"} mm",
            "weather_meteogram_selected_time_iso" to selected.timeIso,
            "weather_meteogram_temperature_c" to fs(selected.temperatureC),
            "weather_meteogram_dew_point_c" to fs(selected.dewPointC),
            "weather_meteogram_precipitation_mm" to fs(selected.precipitationMm),
            "weather_meteogram_pressure_msl_hpa" to fs(selected.pressureMslHpa),
            "weather_meteogram_wind_speed_ms" to fs(selected.windSpeedMs),
            "weather_meteogram_wind_gust_ms" to fs(selected.windGustMs),
            "weather_meteogram_cloud_cover_pct" to fs(selected.cloudCoverPct),
            "weather_meteogram_values_json" to values,
            "weather_meteogram_series_json" to series,
            "weather_meteogram_provider" to provider(s),
            "weather_meteogram_from_cache" to fromCache(s),
            "weather_meteogram_data_age_hours" to age(s)
        ))
    }

    private fun wind(outputs: List<String>, s: Map<String, String>): Map<String, String> {
        val w = forecastBundle(s) ?: return failure(outputs, apiError(s))
        val requested = input(s, "target_time_iso")
        val h = if (requested.isBlank()) null else WeatherJson.nearestHour(w.hourly, requested)
        if (requested.isNotBlank() && h != null && tooFar(requested, h.timeIso)) return failure(outputs, "Selected time is outside the available forecast horizon.")
        val speed = h?.windSpeedMs ?: w.current?.windSpeedMs
        val direction = h?.windDirectionDeg ?: w.current?.windDirectionDeg
        val gust = h?.windGustMs ?: w.current?.windGustMs
        val valid = h?.timeIso ?: w.current?.validTimeIso.orEmpty()
        return success(outputs, s, mapOf(
            "weather_wind_result" to "${speed?.let(::f) ?: "—"} m/s ${WeatherJson.compass(direction)} · gust ${gust?.let(::f) ?: "—"} m/s",
            "weather_wind_valid_time_iso" to valid,
            "weather_wind_speed_ms" to fs(speed),
            "weather_wind_direction_deg" to fs(direction),
            "weather_wind_direction_compass" to WeatherJson.compass(direction),
            "weather_wind_gust_ms" to fs(gust),
            "weather_wind_data_class" to if (h == null) WeatherSourceClass.MODELLED_CURRENT.name else WeatherSourceClass.FORECAST.name,
            "weather_wind_provider" to provider(s),
            "weather_wind_from_cache" to fromCache(s),
            "weather_wind_data_age_hours" to age(s)
        ))
    }

    private fun atmosphere(outputs: List<String>, s: Map<String, String>): Map<String, String> {
        val raw = input(s, "atmosphere_json")
        if (raw.isBlank()) return failure(outputs, apiError(s).ifBlank { "Atmospheric model data are unavailable." })
        val requested = input(s, "target_time_iso")
        val (time, values) = WeatherJson.hourlyValuesAt(raw, requested)
        if (requested.isNotBlank() && time.isNotBlank() && tooFar(requested, time)) return failure(outputs, "Selected time is outside the available pressure-level forecast horizon.")
        return success(outputs, s, mapOf(
            "weather_atmosphere_result" to "${hourLabel(time)} · CAPE ${values["cape"] ?: "—"} J/kg · 850 hPa ${values["temperature_850hPa"] ?: "—"} °C",
            "weather_atmosphere_valid_time_iso" to time,
            "weather_atmosphere_cape_jkg" to values["cape"].orEmpty(),
            "weather_atmosphere_temperature_850hpa_c" to values["temperature_850hPa"].orEmpty(),
            "weather_atmosphere_relative_humidity_850hpa_pct" to values["relative_humidity_850hPa"].orEmpty(),
            "weather_atmosphere_wind_speed_850hpa_ms" to values["wind_speed_850hPa"].orEmpty(),
            "weather_atmosphere_geopotential_height_850hpa_m" to values["geopotential_height_850hPa"].orEmpty(),
            "weather_atmosphere_temperature_500hpa_c" to values["temperature_500hPa"].orEmpty(),
            "weather_atmosphere_geopotential_height_500hpa_m" to values["geopotential_height_500hPa"].orEmpty(),
            "weather_atmosphere_wind_speed_300hpa_ms" to values["wind_speed_300hPa"].orEmpty(),
            "weather_atmosphere_wind_direction_300hpa_deg" to values["wind_direction_300hPa"].orEmpty(),
            "weather_atmosphere_values_json" to JSONObject(values).toString(),
            "weather_atmosphere_provider" to provider(s),
            "weather_atmosphere_from_cache" to fromCache(s),
            "weather_atmosphere_data_age_hours" to age(s)
        ))
    }

    private fun sun(outputs: List<String>, s: Map<String, String>): Map<String, String> {
        val w = forecastBundle(s) ?: return failure(outputs, apiError(s))
        val requestedDate = input(s, "target_time_iso").take(10).takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        val day = if (requestedDate != null) {
            w.daily.firstOrNull { it.dateIso == requestedDate }
                ?: return failure(outputs, "Selected date is outside the available solar forecast horizon.")
        } else {
            w.daily.firstOrNull() ?: return failure(outputs, "Daily solar data are unavailable.")
        }
        return success(outputs, s, mapOf(
            "weather_sun_result" to "Sunrise ${hourLabel(day.sunriseIso)} · sunset ${hourLabel(day.sunsetIso)} · UV ${day.uvIndexMax?.let(::f) ?: "—"}",
            "weather_sun_date" to day.dateIso, "weather_sun_sunrise_iso" to day.sunriseIso, "weather_sun_sunset_iso" to day.sunsetIso,
            "weather_sun_daylight_seconds" to fs(day.daylightSeconds), "weather_sun_sunshine_seconds" to fs(day.sunshineSeconds),
            "weather_sun_uv_index_max" to fs(day.uvIndexMax), "weather_sun_provider" to provider(s),
            "weather_sun_from_cache" to fromCache(s), "weather_sun_data_age_hours" to age(s)
        ))
    }

    private fun snapshot(outputs: List<String>, s: Map<String, String>): Map<String, String> {
        val raw = input(s, "archive_json").ifBlank { input(s, "historical_forecast_json").ifBlank { input(s, "weather_json") } }
        if (raw.isBlank()) return failure(outputs, apiError(s).ifBlank { "No weather data available for snapshot." })
        val w = WeatherJson.parseForecast(raw)
        val requested = input(s, "target_time_iso").ifBlank { Instant.now().toString() }
        val h = WeatherJson.nearestHour(w.hourly, requested)
        val requestedInstant = WeatherJson.parseInstant(requested)
        val selectedInstant = h?.let { WeatherJson.parseInstant(it.timeIso) }
        if (requestedInstant != null && selectedInstant != null &&
            kotlin.math.abs(java.time.Duration.between(requestedInstant, selectedInstant).seconds) > 5_400
        ) {
            return failure(outputs, "No weather value is available within 90 minutes of the requested timestamp for the selected source policy.")
        }
        val c = h?.toCurrent() ?: w.current ?: return failure(outputs, "No weather values available at the requested time.")
        val query = disclosed(s)
        val isArchive = input(s, "archive_json").isNotBlank()
        val isHistoricalForecast = input(s, "historical_forecast_json").isNotBlank()
        return success(outputs, s, mapOf(
            "weather_snapshot_result" to "${c.temperatureC?.let(::f) ?: "—"} °C · ${WeatherJson.weatherLabel(c.weatherCode)} · ${c.precipitationMm?.let(::f) ?: "—"} mm",
            "weather_snapshot_requested_time_iso" to requested, "weather_snapshot_valid_time_iso" to c.validTimeIso,
            "weather_snapshot_requested_latitude" to input(s, "latitude"), "weather_snapshot_requested_longitude" to input(s, "longitude"),
            "weather_snapshot_query_latitude" to query.first, "weather_snapshot_query_longitude" to query.second,
            "weather_snapshot_grid_latitude" to fs(w.latitude, 6), "weather_snapshot_grid_longitude" to fs(w.longitude, 6),
            "weather_snapshot_data_class" to when {
                isArchive -> WeatherSourceClass.REANALYSIS.name
                isHistoricalForecast -> WeatherSourceClass.HISTORICAL_FORECAST.name
                else -> WeatherSourceClass.FORECAST.name
            },
            "weather_snapshot_provider" to provider(s), "weather_snapshot_temperature_c" to fs(c.temperatureC),
            "weather_snapshot_apparent_temperature_c" to fs(c.apparentTemperatureC), "weather_snapshot_relative_humidity_pct" to fs(c.relativeHumidityPct),
            "weather_snapshot_dew_point_c" to fs(c.dewPointC), "weather_snapshot_precipitation_mm" to fs(c.precipitationMm),
            "weather_snapshot_pressure_msl_hpa" to fs(c.pressureMslHpa), "weather_snapshot_wind_speed_ms" to fs(c.windSpeedMs),
            "weather_snapshot_wind_direction_deg" to fs(c.windDirectionDeg), "weather_snapshot_wind_gust_ms" to fs(c.windGustMs),
            "weather_snapshot_cloud_cover_pct" to fs(c.cloudCoverPct), "weather_snapshot_visibility_m" to fs(c.visibilityM),
            "weather_snapshot_weather_code" to c.weatherCode?.toString().orEmpty(), "weather_snapshot_retrieved_time_iso" to retrieved(s)
        ))
    }

    private fun modelCompare(outputs: List<String>, s: Map<String, String>): Map<String, String> {
        val raw = input(s, "ensemble_json")
        if (raw.isBlank()) return failure(outputs, apiError(s).ifBlank { "No ensemble data available." })
        val summary = WeatherJson.ensembleSummary(raw, input(s, "target_time_iso"))
        if (summary.temperature.count == 0) return failure(outputs, "No ensemble temperature members were present.")
        val spread = if (summary.temperature.minimum != null && summary.temperature.maximum != null) summary.temperature.maximum - summary.temperature.minimum else null
        val time = summary.validTimeIso
        val requested = input(s, "target_time_iso")
        if (requested.isNotBlank() && time.isNotBlank() && tooFar(requested, time)) return failure(outputs, "Selected time is outside the available ensemble forecast horizon.")
        return success(outputs, s, mapOf(
            "weather_model_compare_result" to "Temperature ${summary.temperature.minimum?.let(::f) ?: "—"}–${summary.temperature.maximum?.let(::f) ?: "—"} °C · ${summary.temperature.count} members",
            "weather_model_compare_valid_time_iso" to time,
            "weather_model_compare_temperature_mean_c" to fs(summary.temperature.mean),
            "weather_model_compare_temperature_min_c" to fs(summary.temperature.minimum),
            "weather_model_compare_temperature_max_c" to fs(summary.temperature.maximum),
            "weather_model_compare_temperature_spread_c" to fs(spread),
            "weather_model_compare_precipitation_mean_mm" to fs(summary.precipitation.mean),
            "weather_model_compare_wind_mean_ms" to fs(summary.wind.mean),
            "weather_model_compare_member_count" to summary.temperature.count.toString(),
            "weather_model_compare_series_json" to summary.seriesJson,
            "weather_model_compare_provider" to provider(s),
            "weather_model_compare_from_cache" to fromCache(s), "weather_model_compare_data_age_hours" to age(s)
        ))
    }

    private fun forecastBundle(s: Map<String, String>): WeatherBundle? = input(s, "weather_json").takeIf(String::isNotBlank)?.let(WeatherJson::parseForecast)
    private fun futureHours(w: WeatherBundle): List<WeatherHour> {
        val current = w.current?.validTimeIso.orEmpty()
        if (current.isBlank()) return w.hourly
        val i = w.hourly.indexOfFirst { it.timeIso >= current }
        return if (i >= 0) w.hourly.drop(i) else w.hourly
    }
    private fun hourJson(h: WeatherHour) = JSONObject().apply {
        put("time", h.timeIso); putOpt("temperature_c", h.temperatureC); putOpt("apparent_temperature_c", h.apparentTemperatureC)
        putOpt("dew_point_c", h.dewPointC); putOpt("relative_humidity_pct", h.relativeHumidityPct); putOpt("precipitation_mm", h.precipitationMm)
        putOpt("precipitation_probability_pct", h.precipitationProbabilityPct); putOpt("pressure_msl_hpa", h.pressureMslHpa)
        putOpt("cloud_cover_pct", h.cloudCoverPct); putOpt("visibility_m", h.visibilityM); putOpt("wind_speed_ms", h.windSpeedMs)
        putOpt("wind_direction_deg", h.windDirectionDeg); putOpt("wind_gust_ms", h.windGustMs); putOpt("weather_code", h.weatherCode)
    }
    private fun dayJson(day: WeatherDay) = JSONObject().apply {
        put("date", day.dateIso); putOpt("weather_code", day.weatherCode); putOpt("temperature_max_c", day.temperatureMaxC)
        putOpt("temperature_min_c", day.temperatureMinC); putOpt("precipitation_sum_mm", day.precipitationSumMm)
        putOpt("precipitation_probability_max_pct", day.precipitationProbabilityMaxPct); putOpt("wind_speed_max_ms", day.windSpeedMaxMs)
        putOpt("wind_gust_max_ms", day.windGustMaxMs); putOpt("uv_index_max", day.uvIndexMax)
        put("sunrise", day.sunriseIso); put("sunset", day.sunsetIso)
    }

    private fun precipitationWindow(w: WeatherBundle, s: Map<String, String>): List<WeatherHour> {
        val requested = input(s, "target_time_iso")
        if (requested.isBlank()) return futureHours(w)
        val selected = WeatherJson.nearestHour(w.hourly, requested) ?: return futureHours(w)
        val index = w.hourly.indexOfFirst { it.timeIso == selected.timeIso }
        return if (index >= 0) w.hourly.drop(index) else futureHours(w)
    }

    private fun WeatherHour.toCurrent() = WeatherCurrent(
        validTimeIso = timeIso, temperatureC = temperatureC, apparentTemperatureC = apparentTemperatureC, relativeHumidityPct = relativeHumidityPct,
        dewPointC = dewPointC, precipitationMm = precipitationMm, rainMm = rainMm, showersMm = showersMm, snowfallCm = snowfallCm,
        pressureMslHpa = pressureMslHpa, surfacePressureHpa = surfacePressureHpa, cloudCoverPct = cloudCoverPct, visibilityM = visibilityM,
        windSpeedMs = windSpeedMs, windDirectionDeg = windDirectionDeg, windGustMs = windGustMs, weatherCode = weatherCode
    )

    private fun success(outputs: List<String>, s: Map<String, String>, values: Map<String, String>): Map<String, String> {
        val out = linkedMapOf<String, String>(); outputs.forEach { out[it] = "" }; out.putAll(values)
        outputs.firstOrNull { it.endsWith("_status") }?.let { out[it] = "succeeded" }
        outputs.firstOrNull { it.endsWith("_error") }?.let { out[it] = "" }
        outputs.firstOrNull { it.endsWith("_audit_json") }?.let { key ->
            out[key] = JSONObject().apply {
                put("methodmesh_weather_version", WEATHER_MODULE_VERSION); put("provider", provider(s)); put("retrieved_time_iso", retrieved(s))
                put("from_cache", fromCache(s)); put("data_age_hours", age(s)); put("requested_latitude", input(s, "latitude")); put("requested_longitude", input(s, "longitude"))
                val q = disclosed(s); put("query_latitude", q.first); put("query_longitude", q.second); put("location_disclosure", "rounded ~5 km by declared provider policy")
            }.toString()
        }
        return out
    }
    private fun failure(outputs: List<String>, message: String): Map<String, String> {
        val out = linkedMapOf<String, String>(); outputs.forEach { out[it] = "" }
        outputs.firstOrNull { it.endsWith("_status") }?.let { out[it] = "failed" }
        outputs.firstOrNull { it.endsWith("_error") }?.let { out[it] = message }
        outputs.firstOrNull { it.endsWith("_audit_json") }?.let { out[it] = JSONObject().put("methodmesh_weather_version", WEATHER_MODULE_VERSION).put("error", message).toString() }
        return out
    }

    private fun input(s: Map<String, String>, key: String): String = (s[key] ?: s["input_$key"] ?: s["previous_$key"]).orEmpty().trim()
    private fun provider(s: Map<String, String>) = input(s, "provider").ifBlank { "Open-Meteo" }
    private fun retrieved(s: Map<String, String>) = input(s, "retrieved_time_iso").ifBlank { Instant.now().toString() }
    private fun fromCache(s: Map<String, String>) = input(s, "from_cache").ifBlank { "false" }
    private fun age(s: Map<String, String>) = input(s, "data_age_hours")
    private fun apiError(s: Map<String, String>) = input(s, "api_error").ifBlank { "Weather data are unavailable." }
    private fun threshold(s: Map<String, String>) =
        input(s, "threshold_mm_per_hour").ifBlank { input(s, "rain_threshold_mm_per_hour") }
            .toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.2
    private fun horizon(s: Map<String, String>) =
        input(s, "horizon_hours").ifBlank { input(s, "lookahead_hours") }
            .toIntOrNull()?.coerceIn(1, 384)
            ?: input(s, "forecast_days").toIntOrNull()?.coerceIn(1, 16)?.times(24)
            ?: 24
    private fun tooFar(requestedIso: String, actualIso: String, toleranceSeconds: Long = 5_400): Boolean {
        val requested = WeatherJson.parseInstant(requestedIso) ?: return false
        val actual = WeatherJson.parseInstant(actualIso) ?: return false
        return kotlin.math.abs(java.time.Duration.between(requested, actual).seconds) > toleranceSeconds
    }

    private fun disclosed(s: Map<String, String>): Pair<String, String> {
        val lat = input(s, "latitude").toDoubleOrNull() ?: return "" to ""
        val lon = input(s, "longitude").toDoubleOrNull() ?: return "" to ""
        val r = roundLocationForDisclosure(lat, lon, 5_000)
        return r.latitudeString to r.longitudeString
    }
    private fun fs(v: Double?, digits: Int = 1) = if (v == null || !v.isFinite()) "" else String.format(Locale.US, "%.${digits}f", v)
    private fun f(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun hourLabel(iso: String) = if (iso.length >= 16) iso.substring(11, 16) else iso
}
