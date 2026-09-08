package com.example.methodmesh.modules.apiget

import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.onlinedata.ApiExecutionResult
import com.example.methodmesh.core.onlinedata.ApiResultMeta
import com.example.methodmesh.core.onlinedata.BundledApiDefinitions
import com.example.methodmesh.core.onlinedata.OnlineExecutionStatus
import com.example.methodmesh.core.onlinedata.ResultTree
import com.example.methodmesh.transport.OutputFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ApiGetMethodTest {
    @Test
    fun selectedResultPathBecomesCoreApiValue() {
        val definition = BundledApiDefinitions.openMeteoCurrentWeather
        val data = ResultTree.objectNode(
            "current" to ResultTree.objectNode(
                "temperature_2m" to ResultTree.number(18.5),
                "relative_humidity_2m" to ResultTree.number(63)
            )
        )
        val apiResult = ApiExecutionResult(
            definitionId = definition.id,
            definitionVersion = definition.version,
            status = OnlineExecutionStatus.SUCCESS,
            meta = ApiResultMeta(
                requestedAt = Instant.parse("2026-09-02T12:00:00Z"),
                retrievedAt = Instant.parse("2026-09-02T12:00:01Z"),
                statusCode = 200,
                sourceUrlRedacted = "https://api.open-meteo.com/v1/forecast?latitude=52&longitude=-0.1"
            ),
            data = data
        )

        val values = As100ApiGetMethod.valuesFrom(definition, apiResult, mapOf("result_path" to "current.temperature_2m"))

        assertEquals("succeeded", values[ApiGetFields.STATUS])
        assertEquals("18.5", values[ApiGetFields.VALUE])
        assertEquals("Temperature", values[ApiGetFields.LABEL])
        assertTrue(values.getValue(ApiGetFields.RESPONSE_JSON).contains("relative_humidity_2m"))
    }

    @Test
    fun formatterKeepsCoreReturnSmallAndAuditReturnComplete() {
        val request = As100ApiGetMethod.request(context = emptyMap())
        val result = As100ApiGetMethod.result(
            request,
            linkedMapOf(
                ApiGetFields.STATUS to "succeeded",
                ApiGetFields.VALUE to "18.5",
                ApiGetFields.VALUES_JSON to "{\"current.temperature_2m\":{\"label\":\"Temperature\",\"value\":\"18.5\"}}",
                ApiGetFields.LABEL to "Temperature",
                ApiGetFields.DEFINITION_ID to "openmeteo.current_weather",
                ApiGetFields.DEFINITION_NAME to "Open-Meteo current weather",
                ApiGetFields.RESULT_PATH to "current.temperature_2m",
                ApiGetFields.PROVIDER to "Open-Meteo",
                ApiGetFields.HTTP_STATUS to "200",
                ApiGetFields.FROM_CACHE to "false",
                ApiGetFields.STALE to "false",
                ApiGetFields.SOURCE_URL to "https://example.invalid",
                ApiGetFields.RESPONSE_JSON to "{\"current\":{\"temperature_2m\":18.5}}",
                ApiGetFields.ERROR to "",
                ApiGetFields.RETRIEVED_TIME_ISO to "2026-09-02T12:00:01Z"
            ),
            invocation = null
        )

        assertEquals(TransformationStatus.Succeeded, result.status)
        val core = OutputFormatter.fields(result, includeProvenance = false, payloadMode = OutputFormatter.PayloadMode.CORE)
        val audit = OutputFormatter.fields(result, includeProvenance = false, payloadMode = OutputFormatter.PayloadMode.AUDIT)

        assertEquals("18.5", core[ApiGetFields.VALUE])
        assertTrue(core.containsKey(ApiGetFields.VALUES_JSON))
        assertFalse(core.containsKey(ApiGetFields.RESPONSE_JSON))
        assertTrue(audit.containsKey(ApiGetFields.RESPONSE_JSON))
    }

    @Test
    fun bundledApiPathOptionsCoverCuratedPack() {
        BundledApiDefinitions.all.forEach { definition ->
            assertTrue("No path options for ${definition.id}", As100ApiGetMethod.resultPathOptions(definition).isNotEmpty())
            assertTrue("No default paths for ${definition.id}", As100ApiGetMethod.defaultResultPaths(definition).isNotEmpty())
        }
    }

    @Test
    fun worldBankIndicatorUsesLatestAvailableNonBlankValue() {
        val definition = BundledApiDefinitions.worldBankIndicatorLatest
        val data = ResultTree.arrayNode(
            ResultTree.objectNode("lastupdated" to ResultTree.string("2026-07-13")),
            ResultTree.arrayNode(
                ResultTree.objectNode(
                    "date" to ResultTree.string("2025"),
                    "value" to ResultTree.NullNode,
                    "country" to ResultTree.objectNode("value" to ResultTree.string("United Kingdom")),
                    "indicator" to ResultTree.objectNode("value" to ResultTree.string("Example indicator"))
                ),
                ResultTree.objectNode(
                    "date" to ResultTree.string("2024"),
                    "value" to ResultTree.number(12.5),
                    "country" to ResultTree.objectNode("value" to ResultTree.string("United Kingdom")),
                    "indicator" to ResultTree.objectNode("value" to ResultTree.string("Example indicator"))
                )
            )
        )
        val apiResult = ApiExecutionResult(
            definitionId = definition.id,
            definitionVersion = definition.version,
            status = OnlineExecutionStatus.SUCCESS,
            meta = ApiResultMeta(requestedAt = Instant.parse("2026-09-02T12:00:00Z")),
            data = data
        )

        val values = As100ApiGetMethod.valuesFrom(
            definition,
            apiResult,
            mapOf("result_paths" to As100ApiGetMethod.defaultResultPaths(definition).joinToString("|"))
        )

        assertEquals("12.5", values[ApiGetFields.VALUE])
        assertTrue(values.getValue(ApiGetFields.VALUES_JSON).contains("2024"))
    }

    @Test
    fun frankfurterExchangeRateReturnsCoreRateAndConvertedAmount() {
        val definition = BundledApiDefinitions.frankfurterLatestRates
        val data = ResultTree.objectNode(
            "base" to ResultTree.string("GBP"),
            "date" to ResultTree.string("2026-09-02"),
            "rates" to ResultTree.objectNode(
                "USD" to ResultTree.number(1.35)
            )
        )
        val apiResult = ApiExecutionResult(
            definitionId = definition.id,
            definitionVersion = definition.version,
            status = OnlineExecutionStatus.SUCCESS,
            meta = ApiResultMeta(requestedAt = Instant.parse("2026-09-02T12:00:00Z")),
            data = data
        )

        val values = As100ApiGetMethod.valuesFrom(
            definition,
            apiResult,
            mapOf(
                "base" to "GBP",
                "target_currency" to "USD",
                "amount" to "100"
            )
        )

        assertEquals("succeeded", values[ApiGetFields.STATUS])
        assertEquals("1.35", values[ApiGetFields.VALUE])
        assertEquals("1.35", values[ApiGetFields.EXCHANGE_RATE])
        assertEquals("100", values[ApiGetFields.EXCHANGE_AMOUNT])
        assertEquals("135", values[ApiGetFields.EXCHANGE_CONVERTED])
        assertTrue(values.getValue(ApiGetFields.VALUES_JSON).contains("rates.USD"))
    }
}
