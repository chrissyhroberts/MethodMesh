package com.example.methodmesh.modules.webactions

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.onlinedata.ApiDefinition
import com.example.methodmesh.core.onlinedata.BundledApiDefinitions
import com.example.methodmesh.settings.SettingsState

/** Fixed, discoverable webactions entries for the bundled online data links. */
object WebApiMethods {
    val all: List<WebApiMethod> = listOf(
        WebApiMethod("web.api.openmeteo_current_weather", BundledApiDefinitions.openMeteoCurrentWeather),
        WebApiMethod("web.api.openmeteo_daily_forecast", BundledApiDefinitions.openMeteoDailyForecast),
        WebApiMethod("web.api.openmeteo_air_quality", BundledApiDefinitions.openMeteoAirQuality),
        WebApiMethod("web.api.gdacs_current_events", BundledApiDefinitions.gdacsAllEventsGeoJson),
        WebApiMethod("web.api.usgs_earthquakes_today", BundledApiDefinitions.usgsEarthquakesDay),
        WebApiMethod("web.api.worldbank_indicator", BundledApiDefinitions.worldBankIndicatorLatest),
        WebApiMethod("web.api.frankfurter_rates", BundledApiDefinitions.frankfurterLatestRates),
        WebApiMethod("web.api.gbif_country_occurrences", BundledApiDefinitions.gbifCountryOccurrences)
    )

    val byId: Map<String, WebApiMethod> = all.associateBy { it.id }
}

class WebApiMethod(
    override val id: String,
    val definition: ApiDefinition
) : As100Method {
    private val version = "0.1.0"

    override val ref = ArchitectureRef(ArchitectureId(id), "Method", definition.name)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.Calculation,
        name = definition.name,
        version = version,
        description = "Fetch the complete declared ${definition.name} data stream and return useful values plus full JSON sidecar.",
        inputs = definition.inputs.map { it.id },
        outputs = WebApiFields.outputs,
        graphOutputs = listOf("web.api"),
        parameters = mapOf("category" to "Web data", "status" to "Development", "api_definition_id" to definition.id)
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val settings = request.context + ("definition_id" to definition.id)
        return WebApiSupport.resultFor(
            request = request,
            values = WebApiSupport.run(definition, settings),
            invocation = InvocationContext.from(request.context),
            methodId = id,
            methodRef = ref,
            methodVersion = version,
            phenomenon = "web.api"
        )
    }
}
