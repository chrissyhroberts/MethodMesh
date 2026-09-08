package com.example.methodmesh.core.onlinedata

object BundledApiDefinitions {
    val openMeteoCurrentWeather = ApiDefinition(
        id = "openmeteo.current_weather",
        name = "Open-Meteo current weather",
        description = "Current weather at a supplied latitude/longitude.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://api.open-meteo.com/v1/forecast",
        queryParameters = mapOf(
            "latitude" to "{latitude}",
            "longitude" to "{longitude}",
            "current" to "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
            "timezone" to "auto"
        ),
        inputs = listOf(
            ApiInputDefinition("latitude", "Latitude", type = ApiInputType.LATITUDE),
            ApiInputDefinition("longitude", "Longitude", type = ApiInputType.LONGITUDE)
        ),
        response = ApiResponseDefinition(
            type = ApiResponseType.JSON,
            expectedPaths = listOf(
                "current.temperature_2m",
                "current.relative_humidity_2m",
                "current.wind_speed_10m"
            )
        ),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 900),
        privacy = ApiPrivacy(
            sendsLocation = true,
            locationMode = LocationDisclosureMode.ROUNDED,
            roundedLocationRadiusMeters = 5_000
        ),
        attribution = ApiAttribution(
            providerName = "Open-Meteo",
            providerUrl = "https://open-meteo.com/",
            requiredText = "Weather data by Open-Meteo"
        ),
        documentationUrl = "https://open-meteo.com/en/docs"
    )

    val gdacsAllEventsGeoJson = ApiDefinition(
        id = "gdacs.all_events_geojson",
        name = "GDACS current events",
        description = "Compact GDACS current disaster event feed as GeoJSON.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://www.gdacs.org/contentdata/xml/gdacs_app_feed.json",
        response = ApiResponseDefinition(
            type = ApiResponseType.JSON,
            expectedPaths = listOf(
                "features",
                "features[0].properties"
            )
        ),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 360),
        attribution = ApiAttribution(
            providerName = "GDACS",
            providerUrl = "https://www.gdacs.org/",
            requiredText = "Disaster event data by GDACS"
        ),
        documentationUrl = "https://data.gdacs.org/feed_reference.aspx"
    )

    val openMeteoDailyForecast = ApiDefinition(
        id = "openmeteo.daily_forecast",
        name = "Open-Meteo daily forecast",
        description = "Daily forecast values at a supplied latitude/longitude.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://api.open-meteo.com/v1/forecast",
        queryParameters = mapOf(
            "latitude" to "{latitude}",
            "longitude" to "{longitude}",
            "daily" to "temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max",
            "forecast_days" to "1",
            "timezone" to "auto"
        ),
        inputs = listOf(
            ApiInputDefinition("latitude", "Latitude", type = ApiInputType.LATITUDE),
            ApiInputDefinition("longitude", "Longitude", type = ApiInputType.LONGITUDE)
        ),
        response = ApiResponseDefinition(
            type = ApiResponseType.JSON,
            expectedPaths = listOf(
                "daily.temperature_2m_max[0]",
                "daily.temperature_2m_min[0]",
                "daily.precipitation_sum[0]",
                "daily.precipitation_probability_max[0]"
            )
        ),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 3600),
        privacy = ApiPrivacy(
            sendsLocation = true,
            locationMode = LocationDisclosureMode.ROUNDED,
            roundedLocationRadiusMeters = 5_000
        ),
        attribution = ApiAttribution(
            providerName = "Open-Meteo",
            providerUrl = "https://open-meteo.com/",
            requiredText = "Weather data by Open-Meteo"
        ),
        documentationUrl = "https://open-meteo.com/en/docs"
    )

    val openMeteoAirQuality = ApiDefinition(
        id = "openmeteo.air_quality_current",
        name = "Open-Meteo air quality",
        description = "Current air-quality indicators at a supplied latitude/longitude.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://air-quality-api.open-meteo.com/v1/air-quality",
        queryParameters = mapOf(
            "latitude" to "{latitude}",
            "longitude" to "{longitude}",
            "current" to "european_aqi,us_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,ozone,sulphur_dioxide,dust,uv_index",
            "timezone" to "auto"
        ),
        inputs = listOf(
            ApiInputDefinition("latitude", "Latitude", type = ApiInputType.LATITUDE),
            ApiInputDefinition("longitude", "Longitude", type = ApiInputType.LONGITUDE)
        ),
        response = ApiResponseDefinition(
            type = ApiResponseType.JSON,
            expectedPaths = listOf(
                "current.european_aqi",
                "current.pm2_5",
                "current.pm10",
                "current.us_aqi"
            )
        ),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 1800),
        privacy = ApiPrivacy(
            sendsLocation = true,
            locationMode = LocationDisclosureMode.ROUNDED,
            roundedLocationRadiusMeters = 5_000
        ),
        attribution = ApiAttribution(
            providerName = "Open-Meteo",
            providerUrl = "https://open-meteo.com/",
            requiredText = "Air-quality data by Open-Meteo"
        ),
        documentationUrl = "https://open-meteo.com/en/docs/air-quality-api"
    )

    val usgsEarthquakesDay = ApiDefinition(
        id = "usgs.earthquakes_day",
        name = "USGS earthquakes today",
        description = "USGS all-earthquakes GeoJSON feed for the last day.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson",
        response = ApiResponseDefinition(
            type = ApiResponseType.JSON,
            expectedPaths = listOf(
                "features[0].properties.title",
                "features[0].properties.mag",
                "features[0].properties.place",
                "metadata.count"
            )
        ),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 900),
        attribution = ApiAttribution(
            providerName = "USGS",
            providerUrl = "https://earthquake.usgs.gov/",
            requiredText = "Earthquake data by USGS"
        ),
        documentationUrl = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/geojson.php"
    )

    val worldBankIndicatorLatest = ApiDefinition(
        id = "worldbank.indicator_latest",
        name = "World Bank indicator",
        description = "Latest available World Bank indicator value for a country.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://api.worldbank.org/v2/country/{country}/indicator/{indicator}",
        queryParameters = mapOf(
            "format" to "json",
            "per_page" to "10"
        ),
        inputs = listOf(
            ApiInputDefinition("country", "Country code", type = ApiInputType.STRING, defaultValue = "GB"),
            ApiInputDefinition("indicator", "Indicator code", type = ApiInputType.STRING, defaultValue = "SP.POP.TOTL")
        ),
        response = ApiResponseDefinition(
            type = ApiResponseType.JSON,
            expectedPaths = listOf(
                "first:[1][].value",
                "first:[1][].date",
                "[1][0].country.value",
                "[1][0].indicator.value"
            )
        ),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 86_400),
        attribution = ApiAttribution(
            providerName = "World Bank",
            providerUrl = "https://data.worldbank.org/",
            requiredText = "Indicator data by the World Bank"
        ),
        documentationUrl = "https://datahelpdesk.worldbank.org/knowledgebase/articles/889392-about-the-indicators-api-documentation"
    )

    val frankfurterLatestRates = ApiDefinition(
        id = "frankfurter.latest_rates",
        name = "Frankfurter exchange rates",
        description = "Latest reference exchange rates.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://api.frankfurter.dev/v1/latest",
        queryParameters = mapOf(
            "base" to "{base}",
            "symbols" to "{symbols}"
        ),
        inputs = listOf(
            ApiInputDefinition("base", "Base currency", type = ApiInputType.STRING, defaultValue = "GBP"),
            ApiInputDefinition("symbols", "Target currencies", type = ApiInputType.STRING, defaultValue = "USD,EUR")
        ),
        response = ApiResponseDefinition(
            type = ApiResponseType.JSON,
            expectedPaths = listOf(
                "date",
                "base",
                "rates.USD",
                "rates.EUR"
            )
        ),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 3600),
        attribution = ApiAttribution(
            providerName = "Frankfurter",
            providerUrl = "https://frankfurter.dev/",
            requiredText = "Exchange-rate data by Frankfurter"
        ),
        documentationUrl = "https://frankfurter.dev/"
    )

    val gbifCountryOccurrences = ApiDefinition(
        id = "gbif.country_occurrences",
        name = "GBIF country occurrences",
        description = "Recent GBIF occurrence records for a country.",
        origin = ApiDefinitionOrigin.BUNDLED,
        version = 1,
        editable = false,
        cloneable = true,
        urlTemplate = "https://api.gbif.org/v1/occurrence/search",
        queryParameters = mapOf(
            "country" to "{country}",
            "limit" to "5"
        ),
        inputs = listOf(
            ApiInputDefinition("country", "Country code", type = ApiInputType.STRING, defaultValue = "GB")
        ),
        response = ApiResponseDefinition(
            type = ApiResponseType.JSON,
            expectedPaths = listOf(
                "count",
                "results[0].species",
                "results[0].scientificName",
                "results[0].eventDate"
            )
        ),
        cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 3600),
        attribution = ApiAttribution(
            providerName = "GBIF",
            providerUrl = "https://www.gbif.org/",
            requiredText = "Biodiversity data by GBIF"
        ),
        documentationUrl = "https://techdocs.gbif.org/en/openapi/v1/occurrence"
    )

    val all: List<ApiDefinition> = listOf(
        openMeteoCurrentWeather,
        openMeteoDailyForecast,
        openMeteoAirQuality,
        gdacsAllEventsGeoJson,
        usgsEarthquakesDay,
        worldBankIndicatorLatest,
        frankfurterLatestRates,
        gbifCountryOccurrences
    )

    fun registry(): ApiDefinitionRegistry =
        InMemoryApiDefinitionRegistry(all)
}
