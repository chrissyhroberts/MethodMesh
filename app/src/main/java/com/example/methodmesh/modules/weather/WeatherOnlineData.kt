package com.example.methodmesh.modules.weather

import com.example.methodmesh.core.onlinedata.ApiAttribution
import com.example.methodmesh.core.onlinedata.ApiDefinition
import com.example.methodmesh.core.onlinedata.ApiDefinitionOrigin
import com.example.methodmesh.core.onlinedata.ApiGetExecutor
import com.example.methodmesh.core.onlinedata.ApiGetRequest
import com.example.methodmesh.core.onlinedata.ApiInputDefinition
import com.example.methodmesh.core.onlinedata.ApiInputType
import com.example.methodmesh.core.onlinedata.ApiPrivacy
import com.example.methodmesh.core.onlinedata.ApiResponseDefinition
import com.example.methodmesh.core.onlinedata.ApiResponseType
import com.example.methodmesh.core.onlinedata.CacheMode
import com.example.methodmesh.core.onlinedata.CachePolicy
import com.example.methodmesh.core.onlinedata.HttpUrlConnectionOnlineHttpClient
import com.example.methodmesh.core.onlinedata.InMemoryApiDefinitionRegistry
import com.example.methodmesh.core.onlinedata.LocationDisclosureMode
import com.example.methodmesh.core.onlinedata.OnlineExecutionStatus
import com.example.methodmesh.core.onlinedata.SharedApiResultCache
import com.example.methodmesh.core.onlinedata.roundLocationForDisclosure
import com.example.methodmesh.core.onlinedata.toJsonString
import java.time.Duration
import java.time.Instant

internal object WeatherApiDefinitions {
    private val locationInputs = listOf(
        ApiInputDefinition("latitude", "Latitude", type = ApiInputType.LATITUDE),
        ApiInputDefinition("longitude", "Longitude", type = ApiInputType.LONGITUDE)
    )
    private val roundedLocation = ApiPrivacy(
        sendsLocation = true,
        locationMode = LocationDisclosureMode.ROUNDED,
        roundedLocationRadiusMeters = 5_000
    )
    private val openMeteo = ApiAttribution(
        providerName = "Open-Meteo",
        providerUrl = "https://open-meteo.com/",
        license = "CC BY 4.0",
        requiredText = "Weather data by Open-Meteo"
    )

    val forecast = ApiDefinition(
        id = "openmeteo.weather_forecast",
        name = "Open-Meteo MethodMesh weather forecast",
        description = "Current, hourly and daily meteorology for the Weather module.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://api.open-meteo.com/v1/forecast",
        queryParameters = mapOf(
            "latitude" to "{latitude}",
            "longitude" to "{longitude}",
            "current" to "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,rain,showers,snowfall,weather_code,cloud_cover,surface_pressure,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
            "hourly" to "temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,precipitation,rain,showers,snowfall,precipitation_probability,weather_code,pressure_msl,surface_pressure,cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,shortwave_radiation",
            "daily" to "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,daylight_duration,sunshine_duration,precipitation_sum,rain_sum,snowfall_sum,precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,uv_index_max",
            "forecast_days" to "{forecast_days}",
            "wind_speed_unit" to "ms",
            "timezone" to "UTC"
        ),
        inputs = locationInputs + ApiInputDefinition(
            "forecast_days", "Forecast days", type = ApiInputType.NUMBER, defaultValue = "10"
        ),
        response = ApiResponseDefinition(
            ApiResponseType.JSON,
            expectedPaths = listOf("current.temperature_2m", "hourly.time", "daily.time")
        ),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 900),
        privacy = roundedLocation,
        attribution = openMeteo,
        documentationUrl = "https://open-meteo.com/en/docs"
    )

    val archive = ApiDefinition(
        id = "openmeteo.weather_archive",
        name = "Open-Meteo MethodMesh historical weather",
        description = "Historical/reanalysis meteorology for timestamped Weather snapshots.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://archive-api.open-meteo.com/v1/archive",
        queryParameters = mapOf(
            "latitude" to "{latitude}",
            "longitude" to "{longitude}",
            "start_date" to "{start_date}",
            "end_date" to "{end_date}",
            "hourly" to "temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,precipitation,rain,snowfall,pressure_msl,surface_pressure,cloud_cover,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
            "wind_speed_unit" to "ms",
            "timezone" to "UTC"
        ),
        inputs = locationInputs + listOf(
            ApiInputDefinition("start_date", "Start date"),
            ApiInputDefinition("end_date", "End date")
        ),
        response = ApiResponseDefinition(ApiResponseType.JSON, expectedPaths = listOf("hourly.time")),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 86_400),
        privacy = roundedLocation,
        attribution = openMeteo,
        documentationUrl = "https://open-meteo.com/en/docs/historical-weather-api"
    )

    val historicalForecast = ApiDefinition(
        id = "openmeteo.weather_historical_forecast",
        name = "Open-Meteo MethodMesh historical forecast",
        description = "High-resolution archived forecast-analysis time series for recent/past Weather snapshots.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://historical-forecast-api.open-meteo.com/v1/forecast",
        queryParameters = mapOf(
            "latitude" to "{latitude}",
            "longitude" to "{longitude}",
            "start_date" to "{start_date}",
            "end_date" to "{end_date}",
            "hourly" to "temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,precipitation,rain,showers,snowfall,weather_code,pressure_msl,surface_pressure,cloud_cover,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
            "wind_speed_unit" to "ms",
            "timezone" to "UTC"
        ),
        inputs = locationInputs + listOf(
            ApiInputDefinition("start_date", "Start date"),
            ApiInputDefinition("end_date", "End date")
        ),
        response = ApiResponseDefinition(ApiResponseType.JSON, expectedPaths = listOf("hourly.time")),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 86_400),
        privacy = roundedLocation,
        attribution = openMeteo,
        documentationUrl = "https://open-meteo.com/en/docs/historical-forecast-api"
    )

    val atmosphere = ApiDefinition(
        id = "openmeteo.weather_atmosphere",
        name = "Open-Meteo MethodMesh atmosphere",
        description = "Selected GFS pressure-level and convective variables.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://api.open-meteo.com/v1/gfs",
        queryParameters = mapOf(
            "latitude" to "{latitude}",
            "longitude" to "{longitude}",
            "hourly" to "cape,temperature_850hPa,relative_humidity_850hPa,wind_speed_850hPa,wind_direction_850hPa,geopotential_height_850hPa,temperature_500hPa,wind_speed_500hPa,geopotential_height_500hPa,wind_speed_300hPa,wind_direction_300hPa",
            "forecast_hours" to "{forecast_hours}",
            "wind_speed_unit" to "ms",
            "timezone" to "UTC"
        ),
        inputs = locationInputs + ApiInputDefinition("forecast_hours", "Forecast hours", type = ApiInputType.NUMBER, defaultValue = "24"),
        response = ApiResponseDefinition(ApiResponseType.JSON, expectedPaths = listOf("hourly.time")),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 1800),
        privacy = roundedLocation,
        attribution = openMeteo,
        documentationUrl = "https://open-meteo.com/en/docs/gfs-api"
    )

    val ensemble = ApiDefinition(
        id = "openmeteo.weather_ensemble",
        name = "Open-Meteo MethodMesh ensemble",
        description = "Ensemble forecast data for model spread inspection.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://ensemble-api.open-meteo.com/v1/ensemble",
        queryParameters = mapOf(
            "latitude" to "{latitude}",
            "longitude" to "{longitude}",
            "hourly" to "temperature_2m,precipitation,wind_speed_10m",
            "forecast_hours" to "{forecast_hours}",
            "wind_speed_unit" to "ms",
            "timezone" to "UTC"
        ),
        inputs = locationInputs + ApiInputDefinition("forecast_hours", "Forecast hours", type = ApiInputType.NUMBER, defaultValue = "48"),
        response = ApiResponseDefinition(ApiResponseType.JSON, expectedPaths = listOf("hourly.time")),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 1800),
        privacy = roundedLocation,
        attribution = openMeteo,
        documentationUrl = "https://open-meteo.com/en/docs/ensemble-api"
    )

    val radar = ApiDefinition(
        id = "rainviewer.weather_radar_timeline",
        name = "RainViewer radar timeline",
        description = "Observed radar timeline metadata and provider-supplied nowcast frames.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://api.rainviewer.com/public/weather-maps.json",
        response = ApiResponseDefinition(ApiResponseType.JSON, expectedPaths = listOf("host", "radar.past")),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 600),
        attribution = ApiAttribution(
            providerName = "RainViewer",
            providerUrl = "https://www.rainviewer.com/",
            requiredText = "Radar data by RainViewer"
        ),
        documentationUrl = "https://www.rainviewer.com/api.html"
    )

    val all = listOf(forecast, archive, historicalForecast, atmosphere, ensemble, radar)
    val registry = InMemoryApiDefinitionRegistry(all)
}

internal object WeatherOnlineData {
    fun execute(
        definition: ApiDefinition,
        inputs: Map<String, String> = emptyMap(),
        offlineOnly: Boolean = false,
        researchLinked: Boolean = false
    ): WeatherOnlinePayload {
        val result = ApiGetExecutor(
            registry = WeatherApiDefinitions.registry,
            httpClient = HttpUrlConnectionOnlineHttpClient(),
            cache = SharedApiResultCache
        ).execute(
            ApiGetRequest(
                definitionId = definition.id,
                inputs = inputs,
                networkPolicy = if (offlineOnly) CacheMode.OFFLINE_ONLY else null,
                researchLinked = researchLinked
            )
        )
        val ok = result.status == OnlineExecutionStatus.SUCCESS || result.status == OnlineExecutionStatus.STALE_CACHE
        val retrieved = result.meta.retrievedAt
        val age = retrieved?.let { Duration.between(it, Instant.now()).seconds.coerceAtLeast(0) / 3600.0 }
        return WeatherOnlinePayload(
            definitionId = definition.id,
            provider = definition.attribution.providerName,
            json = if (ok) result.data.toJsonString() else "",
            retrievedTimeIso = (retrieved ?: result.meta.requestedAt).toString(),
            fromCache = result.meta.fromCache,
            stale = result.meta.isStale,
            dataAgeHours = age,
            sourceUrlRedacted = result.meta.sourceUrlRedacted,
            error = if (ok) "" else result.error?.message.orEmpty().ifBlank { result.status.name }
        )
    }

    fun queryLocation(location: WeatherLocation): WeatherLocation {
        val rounded = roundLocationForDisclosure(location.latitude, location.longitude, 5_000)
        return WeatherLocation(rounded.latitude, rounded.longitude, "rounded API disclosure (~5 km)")
    }
}
