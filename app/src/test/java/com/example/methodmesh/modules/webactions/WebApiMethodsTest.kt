package com.example.methodmesh.modules.webactions

import com.example.methodmesh.transport.OutputFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebApiMethodsTest {
    @Test
    fun exposesOneNamedCapabilityForEveryBundledApiLink() {
        assertEquals(8, WebApiMethods.all.size)
        assertEquals(WebApiMethods.all.size, WebApiMethods.all.map { it.id }.toSet().size)
        assertTrue(WebApiMethods.all.all { it.id.startsWith("web.api.") })
        assertEquals(
            setOf(
                "openmeteo.current_weather",
                "openmeteo.daily_forecast",
                "openmeteo.air_quality_current",
                "gdacs.all_events_geojson",
                "usgs.earthquakes_day",
                "worldbank.indicator_latest",
                "frankfurter.latest_rates",
                "gbif.country_occurrences"
            ),
            WebApiMethods.all.map { it.definition.id }.toSet()
        )
    }

    @Test
    fun fixedCapabilityResultKeepsCompleteResponseInFullSidecar() {
        val method = WebApiMethods.byId.getValue("web.api.usgs_earthquakes_today")
        val request = method.request(context = mapOf("input_result_paths" to "metadata.count"))
        val result = WebApiSupport.resultFor(
            request = request,
            values = mapOf(
                WebApiFields.STATUS to "succeeded",
                WebApiFields.DEFINITION_ID to method.definition.id,
                WebApiFields.RESPONSE_JSON to "{\"type\":\"FeatureCollection\",\"features\":[]}",
                WebApiFields.VALUE to "{\"type\":\"FeatureCollection\",\"features\":[]}",
                WebApiFields.RESULT_PATH to "metadata.count",
                WebApiFields.RETRIEVED_TIME_ISO to "2026-09-27T12:00:00Z"
            ),
            invocation = null,
            methodId = method.id, methodRef = method.ref,
            methodVersion = method.descriptor.version.orEmpty(), phenomenon = "web.api"
        )

        val full = OutputFormatter.fields(result, includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL)
        assertTrue(full.getValue("methodmesh_full_json").toString().contains("FeatureCollection"))
        assertEquals(method.id, full["methodmesh_method_id"])
    }
}
