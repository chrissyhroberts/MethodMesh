package com.example.methodmesh.modules.apiget

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
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
import com.example.methodmesh.core.onlinedata.ApiDefinition
import com.example.methodmesh.core.onlinedata.ApiDefinitionRepository
import com.example.methodmesh.core.onlinedata.ApiExecutionResult
import com.example.methodmesh.core.onlinedata.ApiGetExecutor
import com.example.methodmesh.core.onlinedata.ApiGetRequest
import com.example.methodmesh.core.onlinedata.HttpUrlConnectionOnlineHttpClient
import com.example.methodmesh.core.onlinedata.ResultLookup
import com.example.methodmesh.core.onlinedata.ResultPath
import com.example.methodmesh.core.onlinedata.ResultTree
import com.example.methodmesh.core.onlinedata.SharedApiResultCache
import com.example.methodmesh.core.onlinedata.toJsonString
import com.example.methodmesh.settings.SettingsState
import java.time.Instant

object ApiGetFields {
    const val STATUS = "api_status"
    const val VALUE = "api_value"
    const val VALUES_JSON = "api_values_json"
    const val LABEL = "api_label"
    const val DEFINITION_ID = "api_definition_id"
    const val DEFINITION_NAME = "api_definition_name"
    const val RESULT_PATH = "api_result_path"
    const val RESULT_PATHS = "api_result_paths"
    const val PROVIDER = "api_provider"
    const val HTTP_STATUS = "api_http_status"
    const val FROM_CACHE = "api_from_cache"
    const val STALE = "api_stale"
    const val SOURCE_URL = "api_source_url"
    const val RESPONSE_JSON = "api_response_json"
    const val ERROR = "api_error"
    const val RETRIEVED_TIME_ISO = "api_retrieved_time_iso"
    const val DATA_AGE_HOURS = "api_data_age_hours"
    const val EXCHANGE_RATE = "api_exchange_rate"
    const val EXCHANGE_AMOUNT = "api_exchange_amount"
    const val EXCHANGE_CONVERTED = "api_exchange_converted"

    val outputs = listOf(
        STATUS,
        VALUE,
        VALUES_JSON,
        LABEL,
        DEFINITION_ID,
        DEFINITION_NAME,
        RESULT_PATH,
        RESULT_PATHS,
        PROVIDER,
        HTTP_STATUS,
        FROM_CACHE,
        STALE,
        SOURCE_URL,
        RESPONSE_JSON,
        ERROR,
        RETRIEVED_TIME_ISO,
        DATA_AGE_HOURS,
        EXCHANGE_RATE,
        EXCHANGE_AMOUNT,
        EXCHANGE_CONVERTED
    )
}

data class ApiResultPathOption(
    val path: String,
    val label: String
)

object As100ApiGetMethod : As100Method {
    const val ID = "api.get"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Online API GET")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Online API GET",
        version = VERSION,
        description = "Run a declared online API definition and return a compact selected value plus audit JSON.",
        inputs = listOf("definition_id", "result_path"),
        outputs = ApiGetFields.outputs,
        graphOutputs = listOf("api.get"),
        parameters = mapOf("category" to "Online data", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        requiredContext = emptyList(),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, runApi(request.context), InvocationContext.from(request.context))

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[ApiGetFields.STATUS] == "succeeded"
        val entity = Entity(ArchitectureId("api-get:${System.currentTimeMillis()}"), "OnlineApiResult", temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("methodmesh.online_data", ID, VERSION)
        val observation = Observation(
            phenomenon = "api.get",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values + (ApiGetFields.RETRIEVED_TIME_ISO to values[ApiGetFields.RETRIEVED_TIME_ISO].orEmpty().ifBlank { Instant.now().toString() }),
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(ApiGetFields.ERROR to values[ApiGetFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun runApi(settings: Map<String, String>): Map<String, String> {
        val definitionId = settings.value("definition_id").ifBlank { "openmeteo.current_weather" }
        val definition = ApiDefinitionRepository.find(definitionId)
            ?: return failure(definitionId, "", "", "API definition was not found.")
        val inputs = apiInputs(definition, settings)
        val apiResult = ApiGetExecutor(
            registry = ApiDefinitionRepository,
            httpClient = HttpUrlConnectionOnlineHttpClient(),
            cache = SharedApiResultCache
        ).execute(ApiGetRequest(definitionId = definition.id, inputs = inputs))
        return valuesFrom(definition, apiResult, settings)
    }

    fun valuesFrom(definition: ApiDefinition, result: ApiExecutionResult, settings: Map<String, String>): Map<String, String> {
        val resultPath = if (definition.id == "frankfurter.latest_rates") {
            "rates.${settings.value("target_currency").ifBlank { settings.value("currency_to") }.ifBlank { settings.value("symbols").split(',', '|', ';').firstOrNull()?.trim().orEmpty() }.ifBlank { "USD" }.uppercase()}"
        } else {
            settings.value("result_path").ifBlank { defaultResultPath(definition) }
        }
        val selectedPaths = if (definition.id == "frankfurter.latest_rates") {
            listOf(resultPath, "date", "base")
        } else {
            selectedPaths(definition, settings, resultPath)
        }
        val fallback = settings.value("fallback_value")
        val selectedValues = selectedPaths.associateWith { path -> extractValue(definition, result.data, path) }
        val selectedValue = selectedValues[resultPath].orEmpty()
            .ifBlank { selectedValues.values.firstOrNull { it.isNotBlank() }.orEmpty() }
            .ifBlank { fallback }
        val ok = result.status.name == "SUCCESS" || result.status.name == "STALE_CACHE"
        val exchange = if (definition.id == "frankfurter.latest_rates") exchangeValues(result.data, settings, selectedValue) else emptyMap()
        return linkedMapOf(
            ApiGetFields.STATUS to if (ok) "succeeded" else "failed",
            ApiGetFields.VALUE to selectedValue,
            ApiGetFields.VALUES_JSON to selectedValuesJson(definition, selectedValues),
            ApiGetFields.LABEL to defaultLabel(definition, resultPath),
            ApiGetFields.DEFINITION_ID to definition.id,
            ApiGetFields.DEFINITION_NAME to definition.name,
            ApiGetFields.RESULT_PATH to resultPath,
            ApiGetFields.RESULT_PATHS to selectedPaths.joinToString("|"),
            ApiGetFields.PROVIDER to definition.attribution.providerName,
            ApiGetFields.HTTP_STATUS to result.meta.statusCode?.toString().orEmpty(),
            ApiGetFields.FROM_CACHE to result.meta.fromCache.toString(),
            ApiGetFields.STALE to result.meta.isStale.toString(),
            ApiGetFields.SOURCE_URL to result.meta.sourceUrlRedacted,
            ApiGetFields.RESPONSE_JSON to result.data.toJsonString(),
            ApiGetFields.ERROR to result.error?.message.orEmpty(),
            ApiGetFields.RETRIEVED_TIME_ISO to (result.meta.retrievedAt ?: result.meta.requestedAt).toString(),
            ApiGetFields.DATA_AGE_HOURS to dataAgeHours(result)
        ) + exchange
    }

    fun resultPathOptions(definition: ApiDefinition): List<ApiResultPathOption> =
        when (definition.id) {
            "openmeteo.current_weather" -> listOf(
                ApiResultPathOption("current.temperature_2m", "Temperature"),
                ApiResultPathOption("current.relative_humidity_2m", "Humidity"),
                ApiResultPathOption("current.apparent_temperature", "Feels like"),
                ApiResultPathOption("current.precipitation", "Precipitation"),
                ApiResultPathOption("current.weather_code", "Weather code"),
                ApiResultPathOption("current.cloud_cover", "Cloud cover"),
                ApiResultPathOption("current.wind_speed_10m", "Wind speed"),
                ApiResultPathOption("current.wind_direction_10m", "Wind direction"),
                ApiResultPathOption("current.wind_gusts_10m", "Wind gusts")
            )
            "openmeteo.daily_forecast" -> listOf(
                ApiResultPathOption("daily.temperature_2m_max[0]", "Max temperature"),
                ApiResultPathOption("daily.temperature_2m_min[0]", "Min temperature"),
                ApiResultPathOption("daily.precipitation_sum[0]", "Rainfall"),
                ApiResultPathOption("daily.precipitation_probability_max[0]", "Rain probability"),
                ApiResultPathOption("daily.wind_speed_10m_max[0]", "Max wind speed"),
                ApiResultPathOption("daily.wind_gusts_10m_max[0]", "Max wind gusts")
            )
            "openmeteo.air_quality_current" -> listOf(
                ApiResultPathOption("current.european_aqi", "European AQI"),
                ApiResultPathOption("current.us_aqi", "US AQI"),
                ApiResultPathOption("current.pm2_5", "PM2.5"),
                ApiResultPathOption("current.pm10", "PM10"),
                ApiResultPathOption("current.carbon_monoxide", "Carbon monoxide"),
                ApiResultPathOption("current.nitrogen_dioxide", "Nitrogen dioxide"),
                ApiResultPathOption("current.ozone", "Ozone"),
                ApiResultPathOption("current.sulphur_dioxide", "Sulphur dioxide"),
                ApiResultPathOption("current.dust", "Dust"),
                ApiResultPathOption("current.uv_index", "UV index")
            )
            "gdacs.all_events_geojson" -> listOf(
                ApiResultPathOption("features[].properties.name", "Event list"),
                ApiResultPathOption("features[].properties.description", "Event descriptions"),
                ApiResultPathOption("features[].properties.eventtype", "Event types"),
                ApiResultPathOption("features[].properties.alertlevel", "Alert levels"),
                ApiResultPathOption("features[0].properties.name", "Latest event"),
                ApiResultPathOption("features[0].properties.description", "Latest event description")
            )
            "usgs.earthquakes_day" -> listOf(
                ApiResultPathOption("features[].properties.title", "Earthquake list"),
                ApiResultPathOption("features[].properties.mag", "Magnitudes"),
                ApiResultPathOption("features[].properties.place", "Places"),
                ApiResultPathOption("features[0].properties.title", "Latest earthquake"),
                ApiResultPathOption("features[0].properties.mag", "Latest magnitude"),
                ApiResultPathOption("features[0].properties.place", "Latest place"),
                ApiResultPathOption("features[0].properties.type", "Event type"),
                ApiResultPathOption("features[0].properties.url", "USGS event link"),
                ApiResultPathOption("metadata.count", "Event count")
            )
            "worldbank.indicator_latest" -> listOf(
                ApiResultPathOption("first:[1][].value", "Latest available value"),
                ApiResultPathOption("first:[1][].date", "Latest available year"),
                ApiResultPathOption("[1][0].country.value", "Country"),
                ApiResultPathOption("[1][0].indicator.value", "Indicator")
            )
            "frankfurter.latest_rates" -> CurrencyChoices.all.map { choice ->
                ApiResultPathOption("rates.${choice.value}", "${choice.value} rate")
            } + listOf(
                ApiResultPathOption("date", "Rate date"),
                ApiResultPathOption("base", "Base currency")
            )
            "gbif.country_occurrences" -> listOf(
                ApiResultPathOption("count", "Occurrence count"),
                ApiResultPathOption("results[].species", "Species list"),
                ApiResultPathOption("results[].scientificName", "Scientific name list"),
                ApiResultPathOption("results[].eventDate", "Event date list"),
                ApiResultPathOption("results[0].species", "First species"),
                ApiResultPathOption("results[0].country", "First country"),
                ApiResultPathOption("results[0].decimalLatitude", "First latitude"),
                ApiResultPathOption("results[0].decimalLongitude", "First longitude")
            )
            else -> definition.response.expectedPaths.map { path ->
                ApiResultPathOption(path, defaultLabel(definition, path))
            }
        }

    fun defaultResultPath(definition: ApiDefinition): String =
        when (definition.id) {
            "openmeteo.current_weather" -> "current.temperature_2m"
            "openmeteo.daily_forecast" -> "daily.temperature_2m_max[0]"
            "openmeteo.air_quality_current" -> "current.european_aqi"
            "gdacs.all_events_geojson" -> "features[].properties.name"
            "usgs.earthquakes_day" -> "features[].properties.title"
            "worldbank.indicator_latest" -> "first:[1][].value"
            "frankfurter.latest_rates" -> "rates.USD"
            "gbif.country_occurrences" -> "results[].species"
            else -> definition.response.expectedPaths.firstOrNull().orEmpty()
        }

    fun inputChoices(definition: ApiDefinition, inputId: String): List<ApiInputChoice> =
        when {
            definition.id == "frankfurter.latest_rates" && inputId == "base" -> CountryCurrencyChoices.all
            definition.id == "frankfurter.latest_rates" && inputId == "symbols" -> CountryCurrencyChoices.all
            definition.id == "worldbank.indicator_latest" && inputId == "country" -> CountryChoices.common
            definition.id == "worldbank.indicator_latest" && inputId == "indicator" -> WorldBankIndicatorChoices.common
            definition.id == "gbif.country_occurrences" && inputId == "country" -> CountryChoices.common
            else -> emptyList()
        }

    fun inputIsMultiChoice(definition: ApiDefinition, inputId: String): Boolean =
        false

    fun defaultResultPaths(definition: ApiDefinition): List<String> =
        when (definition.id) {
            "openmeteo.current_weather" -> listOf("current.temperature_2m", "current.relative_humidity_2m")
            "openmeteo.daily_forecast" -> listOf(
                "daily.temperature_2m_max[0]",
                "daily.temperature_2m_min[0]",
                "daily.precipitation_sum[0]",
                "daily.precipitation_probability_max[0]"
            )
            "openmeteo.air_quality_current" -> listOf(
                "current.european_aqi",
                "current.pm2_5",
                "current.pm10"
            )
            "gdacs.all_events_geojson" -> listOf(
                "features[].properties.name",
                "features[].properties.alertlevel",
                "features[].properties.description"
            )
            "usgs.earthquakes_day" -> listOf(
                "features[].properties.title",
                "features[].properties.mag",
                "features[].properties.place",
                "metadata.count"
            )
            "worldbank.indicator_latest" -> listOf(
                "first:[1][].value",
                "first:[1][].date",
                "[1][0].country.value",
                "[1][0].indicator.value"
            )
            "frankfurter.latest_rates" -> listOf(
                "rates.USD",
                "rates.EUR",
                "date",
                "base"
            )
            "gbif.country_occurrences" -> listOf(
                "count",
                "results[].species",
                "results[].scientificName",
                "results[].eventDate"
            )
            else -> definition.response.expectedPaths.take(3).ifEmpty { listOf(defaultResultPath(definition)) }.filter { it.isNotBlank() }
        }

    fun defaultLabel(definition: ApiDefinition, resultPath: String): String =
        when {
            definition.id == "openmeteo.current_weather" && resultPath == "current.temperature_2m" -> "Temperature"
            definition.id == "openmeteo.current_weather" && resultPath == "current.relative_humidity_2m" -> "Humidity"
            definition.id == "openmeteo.current_weather" && resultPath == "current.apparent_temperature" -> "Feels like"
            definition.id == "openmeteo.current_weather" && resultPath == "current.precipitation" -> "Precipitation"
            definition.id == "openmeteo.current_weather" && resultPath == "current.weather_code" -> "Weather code"
            definition.id == "openmeteo.current_weather" && resultPath == "current.cloud_cover" -> "Cloud cover"
            definition.id == "openmeteo.current_weather" && resultPath == "current.wind_speed_10m" -> "Wind speed"
            definition.id == "openmeteo.current_weather" && resultPath == "current.wind_direction_10m" -> "Wind direction"
            definition.id == "openmeteo.current_weather" && resultPath == "current.wind_gusts_10m" -> "Wind gusts"
            definition.id == "openmeteo.daily_forecast" && resultPath == "daily.temperature_2m_max[0]" -> "Max temperature"
            definition.id == "openmeteo.daily_forecast" && resultPath == "daily.temperature_2m_min[0]" -> "Min temperature"
            definition.id == "openmeteo.daily_forecast" && resultPath == "daily.precipitation_sum[0]" -> "Rainfall"
            definition.id == "openmeteo.daily_forecast" && resultPath == "daily.precipitation_probability_max[0]" -> "Rain probability"
            definition.id == "openmeteo.daily_forecast" && resultPath == "daily.wind_speed_10m_max[0]" -> "Max wind speed"
            definition.id == "openmeteo.daily_forecast" && resultPath == "daily.wind_gusts_10m_max[0]" -> "Max wind gusts"
            definition.id == "openmeteo.air_quality_current" && resultPath == "current.european_aqi" -> "European AQI"
            definition.id == "openmeteo.air_quality_current" && resultPath == "current.us_aqi" -> "US AQI"
            definition.id == "openmeteo.air_quality_current" && resultPath == "current.pm2_5" -> "PM2.5"
            definition.id == "openmeteo.air_quality_current" && resultPath == "current.pm10" -> "PM10"
            definition.id == "openmeteo.air_quality_current" && resultPath == "current.carbon_monoxide" -> "Carbon monoxide"
            definition.id == "openmeteo.air_quality_current" && resultPath == "current.nitrogen_dioxide" -> "Nitrogen dioxide"
            definition.id == "openmeteo.air_quality_current" && resultPath == "current.ozone" -> "Ozone"
            definition.id == "openmeteo.air_quality_current" && resultPath == "current.sulphur_dioxide" -> "Sulphur dioxide"
            definition.id == "openmeteo.air_quality_current" && resultPath == "current.dust" -> "Dust"
            definition.id == "openmeteo.air_quality_current" && resultPath == "current.uv_index" -> "UV index"
            definition.id == "gdacs.all_events_geojson" && resultPath == "features[0].properties.description" -> "Latest event description"
            definition.id == "gdacs.all_events_geojson" && resultPath == "features[0].properties.eventtype" -> "Latest event type"
            definition.id == "gdacs.all_events_geojson" && resultPath == "features[0].properties.alertlevel" -> "Latest alert level"
            definition.id == "gdacs.all_events_geojson" && resultPath == "features[0].properties.htmldescription" -> "Latest event HTML description"
            definition.id == "gdacs.all_events_geojson" && resultPath == "features[0].geometry.coordinates[0]" -> "Latest longitude"
            definition.id == "gdacs.all_events_geojson" && resultPath == "features[0].geometry.coordinates[1]" -> "Latest latitude"
            definition.id == "gdacs.all_events_geojson" && resultPath == "features[].properties.name" -> "Event list"
            definition.id == "gdacs.all_events_geojson" && resultPath == "features[].properties.description" -> "Event descriptions"
            definition.id == "gdacs.all_events_geojson" && resultPath == "features[].properties.eventtype" -> "Event types"
            definition.id == "gdacs.all_events_geojson" && resultPath == "features[].properties.alertlevel" -> "Alert levels"
            definition.id == "gdacs.all_events_geojson" -> "Latest GDACS event"
            definition.id == "usgs.earthquakes_day" && resultPath == "features[0].properties.title" -> "Latest earthquake"
            definition.id == "usgs.earthquakes_day" && resultPath == "features[0].properties.mag" -> "Latest magnitude"
            definition.id == "usgs.earthquakes_day" && resultPath == "features[0].properties.place" -> "Latest place"
            definition.id == "usgs.earthquakes_day" && resultPath == "features[0].properties.type" -> "Event type"
            definition.id == "usgs.earthquakes_day" && resultPath == "features[0].properties.url" -> "USGS event link"
            definition.id == "usgs.earthquakes_day" && resultPath == "features[].properties.title" -> "Earthquake list"
            definition.id == "usgs.earthquakes_day" && resultPath == "features[].properties.mag" -> "Magnitudes"
            definition.id == "usgs.earthquakes_day" && resultPath == "features[].properties.place" -> "Places"
            definition.id == "usgs.earthquakes_day" && resultPath == "metadata.count" -> "Event count"
            definition.id == "worldbank.indicator_latest" && resultPath == "first:[1][].value" -> "Latest available value"
            definition.id == "worldbank.indicator_latest" && resultPath == "first:[1][].date" -> "Latest available year"
            definition.id == "worldbank.indicator_latest" && resultPath == "[1][0].country.value" -> "Country"
            definition.id == "worldbank.indicator_latest" && resultPath == "[1][0].indicator.value" -> "Indicator"
            definition.id == "frankfurter.latest_rates" && resultPath == "rates.USD" -> "USD rate"
            definition.id == "frankfurter.latest_rates" && resultPath == "rates.EUR" -> "EUR rate"
            definition.id == "frankfurter.latest_rates" && resultPath == "rates.GBP" -> "GBP rate"
            definition.id == "frankfurter.latest_rates" && resultPath == "date" -> "Rate date"
            definition.id == "frankfurter.latest_rates" && resultPath == "base" -> "Base currency"
            definition.id == "gbif.country_occurrences" && resultPath == "count" -> "Occurrence count"
            definition.id == "gbif.country_occurrences" && resultPath == "results[].species" -> "Species list"
            definition.id == "gbif.country_occurrences" && resultPath == "results[].scientificName" -> "Scientific name list"
            definition.id == "gbif.country_occurrences" && resultPath == "results[].eventDate" -> "Event date list"
            definition.id == "gbif.country_occurrences" && resultPath == "results[0].species" -> "First species"
            definition.id == "gbif.country_occurrences" && resultPath == "results[0].scientificName" -> "First scientific name"
            definition.id == "gbif.country_occurrences" && resultPath == "results[0].eventDate" -> "First event date"
            definition.id == "gbif.country_occurrences" && resultPath == "results[0].country" -> "First country"
            definition.id == "gbif.country_occurrences" && resultPath == "results[0].decimalLatitude" -> "First latitude"
            definition.id == "gbif.country_occurrences" && resultPath == "results[0].decimalLongitude" -> "First longitude"
            else -> resultPath.substringAfterLast('.').ifBlank { "API value" }
        }

    private fun selectedPaths(definition: ApiDefinition, settings: Map<String, String>, fallbackPath: String): List<String> {
        val raw = settings.value("result_paths")
            .ifBlank { settings.value("api_result_paths") }
            .ifBlank { settings.value("selected_result_paths") }
        val parsed = raw.split('|', ',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return parsed.ifEmpty { defaultResultPaths(definition).ifEmpty { listOf(fallbackPath) } }
            .distinct()
    }

    private fun selectedValuesJson(definition: ApiDefinition, values: Map<String, String>): String {
        val tree = ResultTree.ObjectNode(values.mapValues { (path, value) ->
            ResultTree.objectNode(
                "label" to ResultTree.string(defaultLabel(definition, path)),
                "value" to ResultTree.string(value)
            )
        })
        return tree.toJsonString()
    }

    private fun extractValue(definition: ApiDefinition, data: ResultTree, resultPath: String): String {
        if (definition.id == "worldbank.indicator_latest") {
            worldBankLatestValue(data, resultPath)?.let { return it }
        }
        return extractValue(data, resultPath)
    }

    private fun extractValue(data: ResultTree, resultPath: String): String {
        if (resultPath.isBlank()) return ""
        if (resultPath.startsWith("first:")) {
            return extractListValue(data, resultPath.removePrefix("first:"))
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }
        if ("[]" in resultPath) return extractListValue(data, resultPath)
        return when (val lookup = runCatching { ResultPath.parse(resultPath).resolve(data) }.getOrElse { ResultLookup.Missing(resultPath) }) {
            is ResultLookup.Value -> scalarString(lookup.value)
            ResultLookup.NullValue -> ""
            is ResultLookup.Missing -> ""
            is ResultLookup.TypeError -> ""
        }
    }

    private fun extractListValue(data: ResultTree, resultPath: String): String {
        val arrayPath = resultPath.substringBefore("[]")
        val childPath = resultPath.substringAfter("[]").removePrefix(".")
        val array = when (val lookup = runCatching { ResultPath.parse(arrayPath).resolve(data) }.getOrElse { ResultLookup.Missing(arrayPath) }) {
            is ResultLookup.Value -> lookup.value as? ResultTree.ArrayNode
            else -> null
        } ?: return ""
        return array.values.mapNotNull { item ->
            val value = if (childPath.isBlank()) {
                item
            } else {
                when (val lookup = runCatching { ResultPath.parse(childPath).resolve(item) }.getOrElse { ResultLookup.Missing(childPath) }) {
                    is ResultLookup.Value -> lookup.value
                    else -> null
                }
            }
            value?.let(::scalarString)?.takeIf { it.isNotBlank() }
        }.distinct().take(20).joinToString("\n")
    }

    private fun worldBankLatestValue(data: ResultTree, resultPath: String): String? {
        val row = latestWorldBankRow(data) ?: return null
        val childPath = when (resultPath) {
            "first:[1][].value" -> "value"
            "first:[1][].date" -> "date"
            "[1][0].country.value" -> "country.value"
            "[1][0].indicator.value" -> "indicator.value"
            else -> return null
        }
        val value = when (val lookup = runCatching { ResultPath.parse(childPath).resolve(row) }.getOrElse { ResultLookup.Missing(childPath) }) {
            is ResultLookup.Value -> scalarString(lookup.value)
            else -> ""
        }
        return value
    }

    private fun latestWorldBankRow(data: ResultTree): ResultTree? {
        val rows = when (val lookup = runCatching { ResultPath.parse("[1]").resolve(data) }.getOrElse { ResultLookup.Missing("[1]") }) {
            is ResultLookup.Value -> lookup.value as? ResultTree.ArrayNode
            else -> null
        } ?: return null
        return rows.values.firstOrNull { row ->
            when (val lookup = runCatching { ResultPath.parse("value").resolve(row) }.getOrElse { ResultLookup.Missing("value") }) {
                is ResultLookup.Value -> scalarString(lookup.value).isNotBlank()
                else -> false
            }
        } ?: rows.values.firstOrNull()
    }

    private fun scalarString(tree: ResultTree): String =
        when (tree) {
            is ResultTree.StringNode -> tree.value
            is ResultTree.NumberNode -> tree.value.toString().trimEnd('0').trimEnd('.')
            is ResultTree.BooleanNode -> tree.value.toString()
            ResultTree.NullNode -> ""
            is ResultTree.ObjectNode -> tree.toJsonString()
            is ResultTree.ArrayNode -> tree.toJsonString()
        }

    private fun failure(definitionId: String, definitionName: String, provider: String, error: String): Map<String, String> =
        linkedMapOf(
            ApiGetFields.STATUS to "failed",
            ApiGetFields.VALUE to "",
            ApiGetFields.VALUES_JSON to "{}",
            ApiGetFields.LABEL to "",
            ApiGetFields.DEFINITION_ID to definitionId,
            ApiGetFields.DEFINITION_NAME to definitionName,
            ApiGetFields.RESULT_PATH to "",
            ApiGetFields.RESULT_PATHS to "",
            ApiGetFields.PROVIDER to provider,
            ApiGetFields.HTTP_STATUS to "",
            ApiGetFields.FROM_CACHE to "false",
            ApiGetFields.STALE to "false",
            ApiGetFields.SOURCE_URL to "",
            ApiGetFields.RESPONSE_JSON to "{}",
            ApiGetFields.ERROR to error,
            ApiGetFields.RETRIEVED_TIME_ISO to Instant.now().toString(),
            ApiGetFields.DATA_AGE_HOURS to "0"
        )

    private fun Map<String, String>.value(key: String): String =
        (this[key] ?: this["input_$key"]).orEmpty()

    private fun apiInputs(definition: ApiDefinition, settings: Map<String, String>): Map<String, String> {
        val normalized = settings.mapKeys { (key, _) -> key.removePrefix("input_") }.toMutableMap()
        fun copyIfBlank(target: String, vararg aliases: String) {
            if (!definition.inputs.any { it.id == target }) return
            if (!normalized[target].isNullOrBlank()) return
            aliases.firstNotNullOfOrNull { alias -> normalized[alias]?.takeIf { it.isNotBlank() } }
                ?.let { normalized[target] = it }
        }
        copyIfBlank("latitude", "gps_latitude", "current_latitude", "location_latitude", "plus_code_gps_latitude")
        copyIfBlank("longitude", "gps_longitude", "current_longitude", "location_longitude", "plus_code_gps_longitude")
        if (definition.id == "frankfurter.latest_rates") {
            val targetCurrency = normalized["target_currency"].orEmpty().ifBlank {
                normalized["currency_to"].orEmpty().ifBlank { "USD" }
            }.uppercase()
            normalized["symbols"] = targetCurrency
            normalized["result_path"] = "rates.$targetCurrency"
            normalized["result_paths"] = "rates.$targetCurrency|date|base"
        }
        return normalized.filterKeys { key -> definition.inputs.any { it.id == key } }
    }

    private fun dataAgeHours(result: ApiExecutionResult): String {
        val retrieved = result.meta.retrievedAt ?: result.meta.requestedAt
        val ageSeconds = java.time.Duration.between(retrieved, Instant.now()).seconds.coerceAtLeast(0)
        return "%.2f".format(java.util.Locale.US, ageSeconds / 3600.0)
    }

    private fun exchangeValues(data: ResultTree, settings: Map<String, String>, selectedValue: String): Map<String, String> {
        val amount = settings.value("amount")
            .ifBlank { settings.value("exchange_amount") }
            .toDoubleOrNull()
        val targetCurrency = settings.value("target_currency")
            .ifBlank { settings.value("currency_to") }
            .ifBlank {
                settings.value("symbols").split(',', '|', ';').firstOrNull()?.trim().orEmpty()
            }
            .ifBlank { selectedRateCurrency(settings.value("result_path")) }
            .ifBlank { "USD" }
            .uppercase()
        val rate = extractValue(data, "rates.$targetCurrency").toDoubleOrNull()
            ?: selectedValue.toDoubleOrNull()
        val converted = if (amount != null && rate != null) amount * rate else null
        return linkedMapOf(
            ApiGetFields.EXCHANGE_RATE to rate?.formatCompact().orEmpty(),
            ApiGetFields.EXCHANGE_AMOUNT to amount?.formatCompact().orEmpty(),
            ApiGetFields.EXCHANGE_CONVERTED to converted?.formatCompact().orEmpty()
        )
    }

    private fun selectedRateCurrency(path: String): String =
        path.takeIf { it.startsWith("rates.") }?.removePrefix("rates.").orEmpty()

    private fun Double.formatCompact(): String =
        if (this % 1.0 == 0.0) toLong().toString() else "%.6f".format(java.util.Locale.US, this).trimEnd('0').trimEnd('.')
}

data class ApiInputChoice(
    val value: String,
    val label: String
)

private object CurrencyChoices {
    val all = java.util.Currency.getAvailableCurrencies()
        .map { currency -> currency.currencyCode to currency.getDisplayName(java.util.Locale.ENGLISH) }
        .distinctBy { it.first }
        .sortedWith(compareBy<Pair<String, String>> { it.second }.thenBy { it.first })
        .map { (code, name) -> ApiInputChoice(code, "$name ($code)") }
}

private object CountryCurrencyChoices {
    private val countryCurrencyChoices = java.util.Locale.getISOCountries().mapNotNull { countryCode ->
        runCatching {
            val locale = java.util.Locale("", countryCode)
            val currency = java.util.Currency.getInstance(locale)
            val countryName = locale.getDisplayCountry(java.util.Locale.ENGLISH)
            val currencyName = currency.getDisplayName(java.util.Locale.ENGLISH)
            ApiInputChoice(currency.currencyCode, "$countryName — $currencyName (${currency.currencyCode})")
        }.getOrNull()
    }

    val all = (countryCurrencyChoices + CurrencyChoices.all)
        .distinctBy { it.label }
        .sortedBy { it.label }
}

private object CountryChoices {
    val common = java.util.Locale.getISOCountries()
        .map { code -> code to java.util.Locale("", code).getDisplayCountry(java.util.Locale.ENGLISH) }
        .filter { (_, name) -> name.isNotBlank() }
        .sortedWith(compareBy<Pair<String, String>> { it.second }.thenBy { it.first })
        .map { (code, name) -> ApiInputChoice(code, "$name ($code)") }
}

private object WorldBankIndicatorChoices {
    val common = listOf(
        "SP.POP.TOTL" to "Population, total",
        "SP.POP.GROW" to "Population growth",
        "NY.GDP.MKTP.CD" to "GDP, current US$",
        "NY.GDP.PCAP.CD" to "GDP per capita, current US$",
        "SI.POV.NAHC" to "Poverty headcount ratio at national poverty lines",
        "SI.POV.DDAY" to "Extreme poverty headcount ratio",
        "SH.STA.BRTC.ZS" to "Births attended by skilled health staff",
        "SH.DYN.MORT" to "Under-5 mortality rate",
        "SP.DYN.LE00.IN" to "Life expectancy at birth",
        "SE.ADT.LITR.ZS" to "Adult literacy rate",
        "SE.PRM.ENRR" to "Primary school enrolment",
        "EG.ELC.ACCS.ZS" to "Access to electricity",
        "SH.H2O.BASW.ZS" to "People using basic drinking water services",
        "SH.STA.BASS.ZS" to "People using basic sanitation services",
        "EN.POP.DNST" to "Population density",
        "AG.LND.FRST.ZS" to "Forest area",
        "EN.ATM.CO2E.PC" to "CO₂ emissions per capita"
    ).map { (code, name) -> ApiInputChoice(code, "$name ($code)") }
}
