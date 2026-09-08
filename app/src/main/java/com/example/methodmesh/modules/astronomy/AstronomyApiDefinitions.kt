package com.example.methodmesh.modules.astronomy

import android.content.Context
import com.example.methodmesh.core.onlinedata.ApiAttribution
import com.example.methodmesh.core.onlinedata.ApiDefinition
import com.example.methodmesh.core.onlinedata.ApiDefinitionOrigin
import com.example.methodmesh.core.onlinedata.ApiDefinitionRepository
import com.example.methodmesh.core.onlinedata.ApiInputDefinition
import com.example.methodmesh.core.onlinedata.ApiInputType
import com.example.methodmesh.core.onlinedata.ApiPrivacy
import com.example.methodmesh.core.onlinedata.ApiResponseDefinition
import com.example.methodmesh.core.onlinedata.ApiResponseType
import com.example.methodmesh.core.onlinedata.CacheMode
import com.example.methodmesh.core.onlinedata.CachePolicy
import com.example.methodmesh.core.onlinedata.LocationDisclosureMode
import com.example.methodmesh.modules.apiget.ApiGetFields
import com.example.methodmesh.modules.apiget.As100ApiGetMethod

/** Astronomy-owned Open-Meteo declarations, executed through the shared api.get capability. */
object AstronomyApiDefinitions {
    private fun baseInputs() = listOf(
        ApiInputDefinition("latitude", "Latitude", type = ApiInputType.LATITUDE),
        ApiInputDefinition("longitude", "Longitude", type = ApiInputType.LONGITUDE)
    )
    private val privacy = ApiPrivacy(sendsLocation = true, locationMode = LocationDisclosureMode.ROUNDED, roundedLocationRadiusMeters = 5_000)
    private val weatherAttribution = ApiAttribution("Open-Meteo", "https://open-meteo.com/", requiredText = "Weather data by Open-Meteo")

    val current = ApiDefinition(
        id = "openmeteo.astronomy_current", name = "Open-Meteo astronomy current",
        description = "Current weather fields used by MethodMesh astronomy utilities.", origin = ApiDefinitionOrigin.USER,
        version = 1, editable = true, cloneable = true, urlTemplate = "https://api.open-meteo.com/v1/forecast",
        queryParameters = mapOf(
            "latitude" to "{latitude}", "longitude" to "{longitude}",
            "current" to "temperature_2m,relative_humidity_2m,dew_point_2m,cloud_cover,visibility,wind_speed_10m,wind_gusts_10m",
            "timezone" to "auto"
        ), inputs = baseInputs(),
        response = ApiResponseDefinition(ApiResponseType.JSON, expectedPaths = listOf("current.temperature_2m","current.relative_humidity_2m","current.cloud_cover")),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 900), privacy = privacy,
        attribution = weatherAttribution, documentationUrl = "https://open-meteo.com/en/docs"
    )

    val hourly = ApiDefinition(
        id = "openmeteo.astronomy_hourly", name = "Open-Meteo astronomy hourly forecast",
        description = "Hourly weather fields used for astronomy imaging-window planning.", origin = ApiDefinitionOrigin.USER,
        version = 1, editable = true, cloneable = true, urlTemplate = "https://api.open-meteo.com/v1/forecast",
        queryParameters = mapOf(
            "latitude" to "{latitude}", "longitude" to "{longitude}",
            "hourly" to "temperature_2m,relative_humidity_2m,dew_point_2m,cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,visibility,wind_speed_10m,wind_gusts_10m",
            "forecast_days" to "2", "timezone" to "auto"
        ), inputs = baseInputs(),
        response = ApiResponseDefinition(ApiResponseType.JSON, expectedPaths = listOf("hourly.time","hourly.cloud_cover","hourly.visibility","hourly.wind_gusts_10m")),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 1800), privacy = privacy,
        attribution = weatherAttribution, documentationUrl = "https://open-meteo.com/en/docs"
    )

    val jetStream = ApiDefinition(
        id = "openmeteo.astronomy_jetstream", name = "Open-Meteo GFS astronomy jet stream",
        description = "GFS upper-atmosphere wind at 300, 250 and 200 hPa for high-resolution astronomy planning.", origin = ApiDefinitionOrigin.USER,
        version = 1, editable = true, cloneable = true, urlTemplate = "https://api.open-meteo.com/v1/gfs",
        queryParameters = mapOf(
            "latitude" to "{latitude}", "longitude" to "{longitude}",
            "hourly" to "wind_speed_300hPa,wind_direction_300hPa,wind_speed_250hPa,wind_direction_250hPa,wind_speed_200hPa,wind_direction_200hPa",
            "forecast_hours" to "1", "wind_speed_unit" to "ms", "timezone" to "auto"
        ), inputs = baseInputs(),
        response = ApiResponseDefinition(ApiResponseType.JSON, expectedPaths = listOf("hourly.time[0]","hourly.wind_speed_300hPa[0]","hourly.wind_speed_250hPa[0]","hourly.wind_speed_200hPa[0]")),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 1800), privacy = privacy,
        attribution = weatherAttribution, documentationUrl = "https://open-meteo.com/en/docs/gfs-api"
    )

    val airQuality = ApiDefinition(
        id = "openmeteo.astronomy_air_quality", name = "Open-Meteo astronomy air quality",
        description = "Current particulate and aerosol haze indicators for astronomy.", origin = ApiDefinitionOrigin.USER,
        version = 1, editable = true, cloneable = true, urlTemplate = "https://air-quality-api.open-meteo.com/v1/air-quality",
        queryParameters = mapOf(
            "latitude" to "{latitude}", "longitude" to "{longitude}",
            "current" to "pm2_5,pm10,dust,aerosol_optical_depth", "timezone" to "auto"
        ), inputs = baseInputs(),
        response = ApiResponseDefinition(ApiResponseType.JSON, expectedPaths = listOf("current.pm2_5","current.aerosol_optical_depth")),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 1800), privacy = privacy,
        attribution = ApiAttribution("Open-Meteo", "https://open-meteo.com/", requiredText = "Air-quality data by Open-Meteo"),
        documentationUrl = "https://open-meteo.com/en/docs/air-quality-api"
    )

    fun ensureInstalled(context: Context) {
        listOf(current, hourly, airQuality, jetStream).forEach { definition ->
            if (ApiDefinitionRepository.find(definition.id) == null) ApiDefinitionRepository.saveUserDefinition(context, definition)
        }
    }
}

data class AstronomyLivePayload(val weatherJson: String, val airQualityJson: String = "")

data class AstronomyDashboardLivePayload(
    val weatherJson: String,
    val airQualityJson: String,
    val hourlyJson: String,
    val jetJson: String,
    val retrievedTimeIso: String,
    val fromCache: Boolean,
    val dataAgeHours: Double?
)

object AstronomyLiveData {
    fun current(context: Context, latitude: Double, longitude: Double): AstronomyLivePayload {
        AstronomyApiDefinitions.ensureInstalled(context)
        val common = mapOf("latitude" to latitude.toString(), "longitude" to longitude.toString())
        val weather = As100ApiGetMethod.runApi(common + ("definition_id" to AstronomyApiDefinitions.current.id))
        val aq = As100ApiGetMethod.runApi(common + ("definition_id" to AstronomyApiDefinitions.airQuality.id))
        check(weather[ApiGetFields.STATUS] == "succeeded") { weather[ApiGetFields.ERROR].orEmpty().ifBlank { "Weather API request failed." } }
        return AstronomyLivePayload(weather[ApiGetFields.RESPONSE_JSON].orEmpty(), if (aq[ApiGetFields.STATUS] == "succeeded") aq[ApiGetFields.RESPONSE_JSON].orEmpty() else "")
    }

    fun hourly(context: Context, latitude: Double, longitude: Double): String {
        AstronomyApiDefinitions.ensureInstalled(context)
        val values = As100ApiGetMethod.runApi(mapOf(
            "definition_id" to AstronomyApiDefinitions.hourly.id,
            "latitude" to latitude.toString(), "longitude" to longitude.toString()
        ))
        check(values[ApiGetFields.STATUS] == "succeeded") { values[ApiGetFields.ERROR].orEmpty().ifBlank { "Hourly weather API request failed." } }
        return values[ApiGetFields.RESPONSE_JSON].orEmpty()
    }

    fun dashboard(context: Context, latitude: Double, longitude: Double): AstronomyDashboardLivePayload {
        AstronomyApiDefinitions.ensureInstalled(context)
        val common = mapOf("latitude" to latitude.toString(), "longitude" to longitude.toString())
        fun call(definitionId: String): Map<String,String> = As100ApiGetMethod.runApi(common + ("definition_id" to definitionId))
        val weather = call(AstronomyApiDefinitions.current.id)
        val aq = call(AstronomyApiDefinitions.airQuality.id)
        val hourly = call(AstronomyApiDefinitions.hourly.id)
        val jet = call(AstronomyApiDefinitions.jetStream.id)
        check(weather[ApiGetFields.STATUS] == "succeeded") { weather[ApiGetFields.ERROR].orEmpty().ifBlank { "Weather API request failed." } }
        check(hourly[ApiGetFields.STATUS] == "succeeded") { hourly[ApiGetFields.ERROR].orEmpty().ifBlank { "Hourly weather API request failed." } }
        val successful = listOf(weather, aq, hourly, jet).filter { it[ApiGetFields.STATUS] == "succeeded" }
        val retrieved = successful.mapNotNull { it[ApiGetFields.RETRIEVED_TIME_ISO]?.takeIf(String::isNotBlank) }.minOrNull().orEmpty()
        val age = successful.mapNotNull { it[ApiGetFields.DATA_AGE_HOURS]?.toDoubleOrNull() }.maxOrNull()
        return AstronomyDashboardLivePayload(
            weatherJson = weather[ApiGetFields.RESPONSE_JSON].orEmpty(),
            airQualityJson = if (aq[ApiGetFields.STATUS] == "succeeded") aq[ApiGetFields.RESPONSE_JSON].orEmpty() else "",
            hourlyJson = hourly[ApiGetFields.RESPONSE_JSON].orEmpty(),
            jetJson = if (jet[ApiGetFields.STATUS] == "succeeded") jet[ApiGetFields.RESPONSE_JSON].orEmpty() else "",
            retrievedTimeIso = retrieved,
            fromCache = successful.any { it[ApiGetFields.FROM_CACHE] == "true" },
            dataAgeHours = age
        )
    }
}
