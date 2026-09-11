package com.example.methodmesh.modules.weather

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.OffsetDateTime
import kotlin.math.abs

internal enum class WeatherSourceClass {
    OBSERVATION,
    RADAR_OBSERVATION,
    ANALYSIS,
    REANALYSIS,
    MODELLED_CURRENT,
    NOWCAST,
    FORECAST,
    ENSEMBLE_FORECAST,
    HISTORICAL_FORECAST,
    DERIVED
}

internal data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val source: String = "supplied coordinates"
)

internal data class WeatherCurrent(
    val validTimeIso: String = "",
    val temperatureC: Double? = null,
    val apparentTemperatureC: Double? = null,
    val relativeHumidityPct: Double? = null,
    val dewPointC: Double? = null,
    val precipitationMm: Double? = null,
    val rainMm: Double? = null,
    val showersMm: Double? = null,
    val snowfallCm: Double? = null,
    val pressureMslHpa: Double? = null,
    val surfacePressureHpa: Double? = null,
    val cloudCoverPct: Double? = null,
    val visibilityM: Double? = null,
    val windSpeedMs: Double? = null,
    val windDirectionDeg: Double? = null,
    val windGustMs: Double? = null,
    val weatherCode: Int? = null
)

internal data class WeatherHour(
    val timeIso: String,
    val temperatureC: Double? = null,
    val apparentTemperatureC: Double? = null,
    val relativeHumidityPct: Double? = null,
    val dewPointC: Double? = null,
    val precipitationMm: Double? = null,
    val rainMm: Double? = null,
    val showersMm: Double? = null,
    val snowfallCm: Double? = null,
    val precipitationProbabilityPct: Double? = null,
    val pressureMslHpa: Double? = null,
    val surfacePressureHpa: Double? = null,
    val cloudCoverPct: Double? = null,
    val cloudCoverLowPct: Double? = null,
    val cloudCoverMidPct: Double? = null,
    val cloudCoverHighPct: Double? = null,
    val visibilityM: Double? = null,
    val windSpeedMs: Double? = null,
    val windDirectionDeg: Double? = null,
    val windGustMs: Double? = null,
    val weatherCode: Int? = null,
    val uvIndex: Double? = null,
    val shortwaveRadiationWm2: Double? = null
)

internal data class WeatherDay(
    val dateIso: String,
    val weatherCode: Int? = null,
    val temperatureMaxC: Double? = null,
    val temperatureMinC: Double? = null,
    val sunriseIso: String = "",
    val sunsetIso: String = "",
    val daylightSeconds: Double? = null,
    val sunshineSeconds: Double? = null,
    val precipitationSumMm: Double? = null,
    val rainSumMm: Double? = null,
    val snowfallSumCm: Double? = null,
    val precipitationProbabilityMaxPct: Double? = null,
    val windSpeedMaxMs: Double? = null,
    val windGustMaxMs: Double? = null,
    val uvIndexMax: Double? = null
)

internal data class WeatherBundle(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val elevationM: Double? = null,
    val timezone: String = "",
    val current: WeatherCurrent? = null,
    val hourly: List<WeatherHour> = emptyList(),
    val daily: List<WeatherDay> = emptyList()
)

internal data class RadarFrame(
    val timeEpochSeconds: Long,
    val path: String,
    val sourceClass: WeatherSourceClass
) {
    val timeIso: String get() = Instant.ofEpochSecond(timeEpochSeconds).toString()
}

internal data class RadarTimeline(
    val host: String = "",
    val observed: List<RadarFrame> = emptyList(),
    val nowcast: List<RadarFrame> = emptyList()
) {
    val all: List<RadarFrame> get() = observed + nowcast
    val newest: RadarFrame? get() = all.maxByOrNull { it.timeEpochSeconds }
}

internal data class WeatherOnlinePayload(
    val definitionId: String,
    val provider: String,
    val json: String,
    val retrievedTimeIso: String,
    val fromCache: Boolean,
    val stale: Boolean,
    val dataAgeHours: Double?,
    val sourceUrlRedacted: String,
    val error: String = ""
) {
    val succeeded: Boolean get() = error.isBlank() && json.isNotBlank()
}

internal data class EnsembleStats(
    val count: Int = 0,
    val mean: Double? = null,
    val minimum: Double? = null,
    val maximum: Double? = null
)

internal data class EnsembleSummary(
    val validTimeIso: String = "",
    val temperature: EnsembleStats = EnsembleStats(),
    val precipitation: EnsembleStats = EnsembleStats(),
    val wind: EnsembleStats = EnsembleStats(),
    val seriesJson: String = ""
)

internal object WeatherJson {
    fun parseForecast(raw: String): WeatherBundle {
        if (raw.isBlank()) return WeatherBundle()
        val root = JSONObject(raw)
        val hourly = root.optJSONObject("hourly")
        val daily = root.optJSONObject("daily")
        val current = root.optJSONObject("current")
        return WeatherBundle(
            latitude = root.doubleOrNull("latitude"),
            longitude = root.doubleOrNull("longitude"),
            elevationM = root.doubleOrNull("elevation"),
            timezone = root.optString("timezone", ""),
            current = current?.let(::parseCurrent),
            hourly = hourly?.let(::parseHourly).orEmpty(),
            daily = daily?.let(::parseDaily).orEmpty()
        )
    }

    fun parseRadar(raw: String): RadarTimeline {
        if (raw.isBlank()) return RadarTimeline()
        val root = JSONObject(raw)
        val radar = root.optJSONObject("radar") ?: return RadarTimeline(host = root.optString("host", ""))
        return RadarTimeline(
            host = root.optString("host", ""),
            observed = parseFrames(radar.optJSONArray("past"), WeatherSourceClass.RADAR_OBSERVATION),
            nowcast = parseFrames(radar.optJSONArray("nowcast"), WeatherSourceClass.NOWCAST)
        )
    }

    fun hourlyValuesAt(raw: String, requestedIso: String? = null): Pair<String, Map<String, String>> {
        if (raw.isBlank()) return "" to emptyMap()
        val root = JSONObject(raw)
        val hourly = root.optJSONObject("hourly") ?: return "" to emptyMap()
        val times = hourly.optJSONArray("time") ?: return "" to emptyMap()
        val index = nearestTimeIndex(times, requestedIso)
        if (index < 0) return "" to emptyMap()
        val time = times.optString(index, "")
        val values = buildMap {
            val keys = hourly.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "time") continue
                val array = hourly.optJSONArray(key) ?: continue
                if (index >= array.length() || array.isNull(index)) continue
                put(key, array.opt(index)?.toString().orEmpty())
            }
        }
        return time to values
    }

    fun firstHourlyValues(raw: String): Map<String, String> = hourlyValuesAt(raw, null).second

    fun ensembleSummary(raw: String, requestedIso: String? = null): EnsembleSummary {
        if (raw.isBlank()) return EnsembleSummary()
        val root = JSONObject(raw)
        val hourly = root.optJSONObject("hourly") ?: return EnsembleSummary()
        val times = hourly.optJSONArray("time") ?: return EnsembleSummary()
        val index = nearestTimeIndex(times, requestedIso)
        if (index < 0) return EnsembleSummary()
        val temp = mutableListOf<Double>()
        val precip = mutableListOf<Double>()
        val wind = mutableListOf<Double>()
        val keys = hourly.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val a = hourly.optJSONArray(key) ?: continue
            if (index >= a.length() || a.isNull(index)) continue
            val value = a.optDouble(index, Double.NaN)
            if (!value.isFinite()) continue
            when {
                key == "temperature_2m" || key.startsWith("temperature_2m_member") -> temp += value
                key == "precipitation" || key.startsWith("precipitation_member") -> precip += value
                key == "wind_speed_10m" || key.startsWith("wind_speed_10m_member") -> wind += value
            }
        }
        return EnsembleSummary(
            validTimeIso = times.optString(index, ""),
            temperature = stats(temp), precipitation = stats(precip), wind = stats(wind), seriesJson = hourly.toString()
        )
    }

    fun nearestHour(hours: List<WeatherHour>, requestedIso: String?): WeatherHour? {
        if (hours.isEmpty()) return null
        val target = parseInstant(requestedIso) ?: Instant.now()
        return hours.minByOrNull { hour ->
            val t = parseInstant(hour.timeIso) ?: target
            abs(Duration.between(target, t).seconds)
        }
    }

    fun weatherLabel(code: Int?): String = when (code) {
        null -> "Weather unavailable"
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather code $code"
    }

    fun compass(degrees: Double?): String {
        val d = degrees ?: return ""
        val points = listOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW")
        return points[((d % 360 + 360) % 360 / 22.5 + 0.5).toInt() % 16]
    }

    fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw) }.getOrElse {
            runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrElse {
                runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }.getOrNull()
            }
        }
    }

    private fun parseCurrent(o: JSONObject) = WeatherCurrent(
        validTimeIso = o.optString("time", ""),
        temperatureC = o.doubleOrNull("temperature_2m"),
        apparentTemperatureC = o.doubleOrNull("apparent_temperature"),
        relativeHumidityPct = o.doubleOrNull("relative_humidity_2m"),
        dewPointC = o.doubleOrNull("dew_point_2m"),
        precipitationMm = o.doubleOrNull("precipitation"),
        rainMm = o.doubleOrNull("rain"),
        showersMm = o.doubleOrNull("showers"),
        snowfallCm = o.doubleOrNull("snowfall"),
        pressureMslHpa = o.doubleOrNull("pressure_msl"),
        surfacePressureHpa = o.doubleOrNull("surface_pressure"),
        cloudCoverPct = o.doubleOrNull("cloud_cover"),
        visibilityM = o.doubleOrNull("visibility"),
        windSpeedMs = o.doubleOrNull("wind_speed_10m"),
        windDirectionDeg = o.doubleOrNull("wind_direction_10m"),
        windGustMs = o.doubleOrNull("wind_gusts_10m"),
        weatherCode = o.intOrNull("weather_code")
    )

    private fun parseHourly(o: JSONObject): List<WeatherHour> {
        val times = o.optJSONArray("time") ?: return emptyList()
        fun d(name: String, index: Int): Double? = o.optJSONArray(name)?.doubleAt(index)
        fun i(name: String, index: Int): Int? = o.optJSONArray(name)?.intAt(index)
        return (0 until times.length()).mapNotNull { index ->
            val time = times.optString(index, "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WeatherHour(
                timeIso = time,
                temperatureC = d("temperature_2m", index),
                apparentTemperatureC = d("apparent_temperature", index),
                relativeHumidityPct = d("relative_humidity_2m", index),
                dewPointC = d("dew_point_2m", index),
                precipitationMm = d("precipitation", index),
                rainMm = d("rain", index),
                showersMm = d("showers", index),
                snowfallCm = d("snowfall", index),
                precipitationProbabilityPct = d("precipitation_probability", index),
                pressureMslHpa = d("pressure_msl", index),
                surfacePressureHpa = d("surface_pressure", index),
                cloudCoverPct = d("cloud_cover", index),
                cloudCoverLowPct = d("cloud_cover_low", index),
                cloudCoverMidPct = d("cloud_cover_mid", index),
                cloudCoverHighPct = d("cloud_cover_high", index),
                visibilityM = d("visibility", index),
                windSpeedMs = d("wind_speed_10m", index),
                windDirectionDeg = d("wind_direction_10m", index),
                windGustMs = d("wind_gusts_10m", index),
                weatherCode = i("weather_code", index),
                uvIndex = d("uv_index", index),
                shortwaveRadiationWm2 = d("shortwave_radiation", index)
            )
        }
    }

    private fun parseDaily(o: JSONObject): List<WeatherDay> {
        val dates = o.optJSONArray("time") ?: return emptyList()
        fun d(name: String, index: Int): Double? = o.optJSONArray(name)?.doubleAt(index)
        fun i(name: String, index: Int): Int? = o.optJSONArray(name)?.intAt(index)
        fun s(name: String, index: Int): String = o.optJSONArray(name)?.optString(index, "").orEmpty()
        return (0 until dates.length()).mapNotNull { index ->
            val date = dates.optString(index, "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WeatherDay(
                dateIso = date,
                weatherCode = i("weather_code", index),
                temperatureMaxC = d("temperature_2m_max", index),
                temperatureMinC = d("temperature_2m_min", index),
                sunriseIso = s("sunrise", index),
                sunsetIso = s("sunset", index),
                daylightSeconds = d("daylight_duration", index),
                sunshineSeconds = d("sunshine_duration", index),
                precipitationSumMm = d("precipitation_sum", index),
                rainSumMm = d("rain_sum", index),
                snowfallSumCm = d("snowfall_sum", index),
                precipitationProbabilityMaxPct = d("precipitation_probability_max", index),
                windSpeedMaxMs = d("wind_speed_10m_max", index),
                windGustMaxMs = d("wind_gusts_10m_max", index),
                uvIndexMax = d("uv_index_max", index)
            )
        }
    }

    private fun parseFrames(a: JSONArray?, sourceClass: WeatherSourceClass): List<RadarFrame> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { index ->
            val o = a.optJSONObject(index) ?: return@mapNotNull null
            val time = o.optLong("time", 0L)
            val path = o.optString("path", "")
            if (time <= 0 || path.isBlank()) null else RadarFrame(time, path, sourceClass)
        }
    }

    private fun nearestTimeIndex(times: JSONArray, requestedIso: String?): Int {
        if (times.length() == 0) return -1
        val target = parseInstant(requestedIso) ?: Instant.now()
        var bestIndex = 0
        var bestDistance = Long.MAX_VALUE
        for (index in 0 until times.length()) {
            val instant = parseInstant(times.optString(index, "")) ?: continue
            val distance = kotlin.math.abs(Duration.between(target, instant).seconds)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun stats(values: List<Double>): EnsembleStats {
        if (values.isEmpty()) return EnsembleStats()
        return EnsembleStats(values.size, values.average(), values.minOrNull(), values.maxOrNull())
    }
}

private fun JSONObject.doubleOrNull(name: String): Double? {
    if (!has(name) || isNull(name)) return null
    return optDouble(name, Double.NaN).takeIf { it.isFinite() }
}

private fun JSONObject.intOrNull(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return optInt(name)
}

private fun JSONArray.doubleAt(index: Int): Double? {
    if (index !in 0 until length() || isNull(index)) return null
    return optDouble(index, Double.NaN).takeIf { it.isFinite() }
}

private fun JSONArray.intAt(index: Int): Int? {
    if (index !in 0 until length() || isNull(index)) return null
    return optInt(index)
}

internal fun Double.fmt(decimals: Int = 1): String =
    String.format(java.util.Locale.US, "%.${decimals}f", this)
