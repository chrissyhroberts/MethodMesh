package com.example.methodmesh.modules.astronomy

import com.example.methodmesh.platform.location.OpenLocationCode
import com.example.methodmesh.settings.MethodSetting

internal enum class AstronomyAutoKind(val method: AstronomyMethodBase, val title: String) {
    Conditions(As100ConditionsMethod, "Astro conditions"),
    Dew(As100DewRiskMethod, "Dew risk"),
    Window(As100ImagingWindowMethod, "Tonight's imaging window");

    val fields: List<MethodSetting> get() = AstronomyModule.capabilitySettings().getValue(method.id)
    val errorField: String get() = when (this) {
        Conditions -> ConditionsFields.ERROR
        Dew -> DewFields.ERROR
        Window -> ImagingWindowFields.ERROR
    }
}

internal fun MethodSetting.autoDefault(): String = when (this) {
    is MethodSetting.BooleanSetting -> defaultValue.toString()
    is MethodSetting.IntSetting -> defaultValue.toString()
    is MethodSetting.FloatSetting -> defaultValue.toString()
    is MethodSetting.TextSetting -> defaultValue
    is MethodSetting.ChoiceSetting -> defaultValue
    is MethodSetting.MultiChoiceSetting -> defaultValue
}

internal data class AstronomyAutoPlan(
    val inputs: Map<String, String>,
    val source: String,
    val location: AstroLocation? = null,
    val needsCurrent: Boolean = false,
    val needsHourly: Boolean = false
) {
    val needsDeviceLocation: Boolean get() = (needsCurrent || needsHourly) && location == null
}

private val weatherFields = listOf("weather_json", "air_quality_json", "cloud_cover_pct", "visibility_m",
    "wind_kmh", "wind_gust_kmh", "temperature_c", "relative_humidity_pct", "humidity_pct",
    "aerosol_optical_depth", "pm2_5", "moon_altitude_deg", "moon_illumination_pct",
    "dew_point_c", "use_supplied_dew_point", "sensor_profile")

/** Decide offline/supplied/live routing before permission UI. Never mutates caller settings. */
internal fun astronomyAutoPlan(kind: AstronomyAutoKind, raw: Map<String, String>): AstronomyAutoPlan {
    val input = raw - setOf("forecast_warning", "resolved_data_source_label", "resolved_location_source", "data_source")
    validateAutoNumbers(kind, input)
    if (kind != AstronomyAutoKind.Window) {
        val mode = input["source_mode"].orEmpty().ifBlank { "auto" }
        require(mode in listOf("auto", "manual", "upstream")) { "Choose a valid data source." }
        if (mode == "manual") {
            val values = kind.fields.associate { it.id to it.autoDefault() } + input
            val resolved = normalizeHumidity(values, input)
            validateAutoNumbers(kind, resolved)
            return AstronomyAutoPlan(resolved + ("data_source" to "manual"), "manual values")
        }
        val upstream = upstreamMap(input, weatherFields)
        val hasMeasurement = if (kind == AstronomyAutoKind.Conditions) upstream.isNotEmpty() else
            !upstream["weather_json"].isNullOrBlank() ||
                (!upstream["temperature_c"].isNullOrBlank() &&
                    (!upstream["relative_humidity_pct"].isNullOrBlank() || !upstream["humidity_pct"].isNullOrBlank()))
        if (mode == "upstream" || hasMeasurement) {
            // Supplied provider JSON must not be masked by scalar preset defaults.
            val base = if (!upstream["weather_json"].isNullOrBlank()) input - weatherFields.toSet() else input
            val merged = base + upstream
            val resolved = normalizeHumidity(merged, upstream.ifEmpty { input })
            validateAutoNumbers(kind, resolved)
            return AstronomyAutoPlan(resolved +
                ("data_source" to "upstream"), "supplied workflow data")
        }
        if (!input["weather_json"].isNullOrBlank() ||
            (kind == AstronomyAutoKind.Conditions && !input["air_quality_json"].isNullOrBlank())) {
            return AstronomyAutoPlan(input + ("data_source" to "api"), "supplied API result")
        }
        return AstronomyAutoPlan(input, "live weather", autoSite(input), needsCurrent = true)
    }
    val forecast = upstreamValue(input, "forecast_json") ?: input["forecast_json"].orEmpty()
    val location = autoSite(input)
    // A supplied forecast still needs a site, but never needs another provider request.
    return AstronomyAutoPlan(input + ("forecast_json" to forecast),
        if (forecast.isNotBlank()) "supplied forecast" else "hourly forecast", location, needsHourly = true)
}

private fun normalizeHumidity(values: Map<String, String>, supplied: Map<String, String>): Map<String, String> =
    if (supplied["relative_humidity_pct"].isNullOrBlank() && !supplied["humidity_pct"].isNullOrBlank())
        values + ("relative_humidity_pct" to supplied.getValue("humidity_pct")) else values

private fun validateAutoNumbers(kind: AstronomyAutoKind, input: Map<String, String>) {
    kind.fields.forEach { field ->
        val text = input[field.id]?.takeIf { it.isNotBlank() } ?: return@forEach
        when (field) {
            is MethodSetting.FloatSetting -> {
                val value = text.toDoubleOrNull()
                require(value != null && value.isFinite() &&
                    (field.minimum == null || value >= field.minimum.toDouble()) &&
                    (field.maximum == null || value <= field.maximum.toDouble())) {
                    "Enter a valid " + field.label.lowercase() + " within its allowed range."
                }
            }
            is MethodSetting.IntSetting -> {
                val value = text.toIntOrNull()
                require(value != null && (field.minimum == null || value >= field.minimum) &&
                    (field.maximum == null || value <= field.maximum)) {
                    "Enter a valid " + field.label.lowercase() + " within its allowed range."
                }
            }
            else -> Unit
        }
    }
}

private fun autoSite(input: Map<String, String>): AstroLocation? {
    val mode = input["location_source"].orEmpty().ifBlank { "auto" }
    require(mode in listOf("auto", "manual", "plus_code")) { "Choose a valid location source." }
    fun coordinates(lat: String?, lon: String?, source: String, required: Boolean): AstroLocation? {
        if (!required && lat.isNullOrBlank() && lon.isNullOrBlank()) return null
        val latitude = lat?.toDoubleOrNull(); val longitude = lon?.toDoubleOrNull()
        require(latitude != null && latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude != null && longitude.isFinite() && longitude in -180.0..180.0) {
            "Enter both valid latitude and longitude; the supplied site cannot be replaced with GPS."
        }
        return AstroLocation(latitude, longitude, source)
    }
    if (mode == "manual") return coordinates(input["latitude"], input["longitude"], "supplied coordinates", true)
    val plus = (upstreamValue(input, "plus_code") ?: input["plus_code"]).orEmpty().trim()
    if (plus.isNotBlank() || mode == "plus_code") {
        val area = if (plus.indexOf('+') == 8) runCatching { OpenLocationCode.decode(plus) }.getOrNull() else null
        require(area != null && area.centerLatitude in -90.0..90.0 && area.centerLongitude in -180.0..180.0) {
            "Enter a valid full Plus Code."
        }
        return AstroLocation(area.centerLatitude, area.centerLongitude, "supplied Plus Code")
    }
    val upstreamLat = upstreamValue(input, "latitude"); val upstreamLon = upstreamValue(input, "longitude")
    if (upstreamLat != null || upstreamLon != null)
        return coordinates(upstreamLat, upstreamLon, "coordinates from previous workflow step", true)
    val supplied = coordinates(input["latitude"], input["longitude"], "supplied coordinates", false)
    // Existing auto-mode presets use 0/0 as the unspecified-site sentinel.
    return supplied?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }
}
