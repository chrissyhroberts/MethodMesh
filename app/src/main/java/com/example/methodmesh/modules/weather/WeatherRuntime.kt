package com.example.methodmesh.modules.weather

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal data class WeatherRuntimeCapture(
    val settings: Map<String, String>,
    val values: Map<String, String>,
    val forecastPayload: WeatherOnlinePayload? = null,
    val radarPayload: WeatherOnlinePayload? = null,
    val atmospherePayload: WeatherOnlinePayload? = null,
    val archivePayload: WeatherOnlinePayload? = null,
    val historicalForecastPayload: WeatherOnlinePayload? = null,
    val ensemblePayload: WeatherOnlinePayload? = null
)

internal object WeatherRuntime {
    fun capture(method: WeatherMethodBase, sourceSettings: Map<String, String>): WeatherRuntimeCapture {
        val latitude = value(sourceSettings, "latitude").toDoubleOrNull()
        val longitude = value(sourceSettings, "longitude").toDoubleOrNull()
        if (latitude == null || latitude !in -90.0..90.0 || longitude == null || longitude !in -180.0..180.0) {
            val settings = sourceSettings + ("api_error" to "Valid latitude and longitude are required.")
            return WeatherRuntimeCapture(settings, method.calculate(settings))
        }

        val locationInputs = mapOf("latitude" to latitude.toString(), "longitude" to longitude.toString())
        val offlineOnly = value(sourceSettings, "offline_only").equals("true", ignoreCase = true)
        var forecast: WeatherOnlinePayload? = null
        var radar: WeatherOnlinePayload? = null
        var atmosphere: WeatherOnlinePayload? = null
        var archive: WeatherOnlinePayload? = null
        var historicalForecast: WeatherOnlinePayload? = null
        var ensemble: WeatherOnlinePayload? = null

        when (method.id) {
            As100WeatherDashboardMethod.id -> {
                forecast = WeatherOnlineData.execute(
                    WeatherApiDefinitions.forecast,
                    locationInputs + ("forecast_days" to forecastDays(sourceSettings)),
                    offlineOnly = offlineOnly
                )
                // Radar failure must not make the whole dashboard fail.
                radar = WeatherOnlineData.execute(WeatherApiDefinitions.radar, offlineOnly = offlineOnly)
            }
            As100WeatherRadarMethod.id -> radar = WeatherOnlineData.execute(WeatherApiDefinitions.radar, offlineOnly = offlineOnly)
            As100WeatherAtmosphereMethod.id -> atmosphere = WeatherOnlineData.execute(
                WeatherApiDefinitions.atmosphere,
                locationInputs + ("forecast_hours" to targetForecastHours(sourceSettings, 24)),
                offlineOnly = offlineOnly
            )
            As100WeatherModelCompareMethod.id -> ensemble = WeatherOnlineData.execute(
                WeatherApiDefinitions.ensemble,
                locationInputs + ("forecast_hours" to targetForecastHours(sourceSettings, 48)),
                offlineOnly = offlineOnly
            )
            As100WeatherSnapshotMethod.id -> {
                val target = value(sourceSettings, "target_time_iso")
                val date = target.take(10).takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                val policy = value(sourceSettings, "source_policy").ifBlank { "best_available" }
                val today = LocalDate.now(ZoneOffset.UTC)
                val targetDate = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

                when {
                    targetDate == null -> Unit
                    targetDate.isBefore(today) && policy == "forecast" -> Unit
                    !targetDate.isBefore(today) && policy in setOf("reanalysis", "historical_forecast") -> Unit
                    targetDate.isBefore(today) && policy == "historical_forecast" -> {
                        historicalForecast = WeatherOnlineData.execute(
                            WeatherApiDefinitions.historicalForecast,
                            locationInputs + mapOf("start_date" to date, "end_date" to date),
                            offlineOnly = offlineOnly,
                            researchLinked = true
                        )
                    }
                    targetDate.isBefore(today) -> {
                        // best_available and explicit reanalysis both prefer a reconstruction of experienced conditions.
                        archive = WeatherOnlineData.execute(
                            WeatherApiDefinitions.archive,
                            locationInputs + mapOf("start_date" to date, "end_date" to date),
                            offlineOnly = offlineOnly,
                            researchLinked = true
                        )
                    }
                    else -> {
                        forecast = WeatherOnlineData.execute(
                            WeatherApiDefinitions.forecast,
                            locationInputs + ("forecast_days" to forecastDays(sourceSettings)),
                            offlineOnly = offlineOnly,
                            researchLinked = true
                        )
                    }
                }
            }
            else -> forecast = WeatherOnlineData.execute(
                WeatherApiDefinitions.forecast,
                locationInputs + ("forecast_days" to forecastDays(sourceSettings)),
                offlineOnly = offlineOnly
            )
        }

        val primary = when (method.id) {
            As100WeatherRadarMethod.id -> radar
            As100WeatherAtmosphereMethod.id -> atmosphere
            As100WeatherModelCompareMethod.id -> ensemble
            As100WeatherSnapshotMethod.id -> archive ?: historicalForecast ?: forecast
            else -> forecast
        }

        val settings = sourceSettings.toMutableMap().apply {
            put("latitude", latitude.toString()); put("longitude", longitude.toString())
            forecast?.let { put("weather_json", it.json) }
            radar?.let { put("radar_json", it.json) }
            atmosphere?.let { put("atmosphere_json", it.json) }
            archive?.let { put("archive_json", it.json) }
            historicalForecast?.let { put("historical_forecast_json", it.json) }
            ensemble?.let { put("ensemble_json", it.json) }
            primary?.let {
                put("provider", it.provider)
                put("retrieved_time_iso", it.retrievedTimeIso)
                put("from_cache", it.fromCache.toString())
                put("data_age_hours", it.dataAgeHours?.toString().orEmpty())
                put("source_url_redacted", it.sourceUrlRedacted)
                put("api_error", it.error)
            }
            if (method.id == As100WeatherSnapshotMethod.id && primary == null) {
                val targetDate = value(sourceSettings, "target_time_iso").take(10).let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val policy = value(sourceSettings, "source_policy").ifBlank { "best_available" }
                put("api_error", when {
                    targetDate == null -> "Weather snapshot needs a valid ISO-8601 target time."
                    targetDate.isBefore(LocalDate.now(ZoneOffset.UTC)) && policy == "forecast" -> "A current forecast cannot reconstruct a past forecast. Choose best_available, reanalysis or historical_forecast."
                    !targetDate.isBefore(LocalDate.now(ZoneOffset.UTC)) && policy == "reanalysis" -> "Reanalysis is a past-weather product. Choose best_available or forecast for current/future time."
                    !targetDate.isBefore(LocalDate.now(ZoneOffset.UTC)) && policy == "historical_forecast" -> "Historical forecast is a past-forecast product. Choose best_available or forecast for current/future time."
                    else -> "Weather provider did not return a result."
                })
            } else if (primary == null) put("api_error", "Weather provider did not return a result.")
        }
        return WeatherRuntimeCapture(settings, method.calculate(settings), forecast, radar, atmosphere, archive, historicalForecast, ensemble)
    }

    private fun targetForecastHours(settings: Map<String, String>, minimum: Int): String {
        val target = WeatherJson.parseInstant(value(settings, "target_time_iso")) ?: return minimum.toString()
        val hours = if (target.isAfter(Instant.now())) Duration.between(Instant.now(), target).toHours() + 3 else minimum.toLong()
        return maxOf(minimum.toLong(), hours).coerceIn(1, 384).toString()
    }

    private fun forecastDays(settings: Map<String, String>): String {
        val explicit = value(settings, "forecast_days").toIntOrNull()?.coerceIn(1, 16)
        val horizonHours = value(settings, "horizon_hours").toIntOrNull()?.coerceIn(1, 384) ?: 48
        val horizonDays = ((horizonHours + 23) / 24).coerceIn(1, 16)
        val target = WeatherJson.parseInstant(value(settings, "target_time_iso"))
        val targetDays = target?.takeIf { it.isAfter(Instant.now()) }?.let {
            ((Duration.between(Instant.now(), it).toHours().coerceAtLeast(0) + 23) / 24 + 1).toInt().coerceIn(1, 16)
        } ?: 1
        return maxOf(explicit ?: 1, horizonDays, targetDays).coerceIn(1, 16).toString()
    }

    private fun value(settings: Map<String, String>, key: String): String =
        (settings[key] ?: settings["input_$key"] ?: settings["previous_$key"]).orEmpty().trim()
}
