package com.example.methodmesh.modules.astronomy

import com.example.methodmesh.core.methodmesh.MethodObjectType
import org.json.JSONObject
import java.util.Locale

object LightPollutionFields {
    const val STATUS = "astronomy_light_pollution_status"
    const val RESULT = "astronomy_light_pollution_result"
    const val RADIANCE = "astronomy_light_pollution_radiance_nw_cm2_sr"
    const val UNIT = "astronomy_light_pollution_unit"
    const val LABEL = "astronomy_light_pollution_label"
    const val DATASET = "astronomy_light_pollution_dataset_id"
    const val DATASET_DATE = "astronomy_light_pollution_dataset_date"
    const val SOURCE = "astronomy_light_pollution_source"
    const val REGION = "astronomy_light_pollution_region"
    const val RESOLUTION = "astronomy_light_pollution_resolution_m"
    const val DISTANCE = "astronomy_light_pollution_sample_distance_km"
    const val CACHE_KIND = "astronomy_light_pollution_cache_kind"
    const val AUDIT = "astronomy_light_pollution_audit_json"
    const val ERROR = "astronomy_light_pollution_error"

    // Retained for compatibility with v0.5 imported Falchi-style regional grids.
    const val LEGACY_VALUE = "astronomy_light_pollution_artificial_zenith_radiance_mcd_m2"
    const val LEGACY_RATIO = "astronomy_light_pollution_artificial_to_natural_ratio"

    val outputs = listOf(
        STATUS, RESULT, RADIANCE, UNIT, LABEL, DATASET, DATASET_DATE, SOURCE, REGION,
        RESOLUTION, DISTANCE, CACHE_KIND, LEGACY_VALUE, LEGACY_RATIO, AUDIT, ERROR
    )
}

object As100LightPollutionMethod : AstronomyMethodBase(
    "astronomy.light_pollution",
    "Light pollution",
    "Map and sample a cached or freshly downloaded nighttime-radiance region as an astronomy light-pollution proxy.",
    "astronomy.light_pollution",
    MethodObjectType.Calculation,
    LightPollutionFields.RESULT,
    LightPollutionFields.STATUS,
    LightPollutionFields.ERROR,
    LightPollutionFields.outputs,
    version = "0.6.0"
) {
    fun fromLookup(lookup: LightPollutionRepository.Lookup): Map<String, String> {
        val nasaRadiance = lookup.unit.contains("nW", ignoreCase = true)
        val label = if (nasaRadiance) nasaProxyLabel(lookup.value) else legacySkyLabel(lookup.value)
        val legacyRatio = if (!nasaRadiance) lookup.value / 0.174 else null
        val resultText = if (nasaRadiance) {
            "$label · ${fmt(lookup.value, 2)} ${lookup.unit} nighttime radiance"
        } else {
            "$label · ${fmt(lookup.value, 3)} ${lookup.unit} imported sky-brightness proxy"
        }
        return success(
            LightPollutionFields.RESULT to resultText,
            LightPollutionFields.RADIANCE to if (nasaRadiance) fmt(lookup.value, 4) else "",
            LightPollutionFields.UNIT to lookup.unit,
            LightPollutionFields.LABEL to label,
            LightPollutionFields.DATASET to lookup.datasetId,
            LightPollutionFields.DATASET_DATE to lookup.datasetDate,
            LightPollutionFields.SOURCE to lookup.source,
            LightPollutionFields.REGION to lookup.regionName,
            LightPollutionFields.RESOLUTION to fmt(lookup.resolutionM, 0),
            LightPollutionFields.DISTANCE to fmt(lookup.sampleDistanceKm, 3),
            LightPollutionFields.CACHE_KIND to lookup.cacheKind,
            LightPollutionFields.LEGACY_VALUE to if (!nasaRadiance) fmt(lookup.value, 6) else "",
            LightPollutionFields.LEGACY_RATIO to legacyRatio?.let { fmt(it, 3) }.orEmpty(),
            LightPollutionFields.AUDIT to JSONObject().apply {
                put("method_id", id)
                put("algorithm_version", "0.6.0")
                put("dataset_source", lookup.source)
                put("dataset_date", lookup.datasetDate)
                put("unit", lookup.unit)
                put("cache_kind", lookup.cacheKind)
                put("interpretation", if (nasaRadiance)
                    "Nighttime at-sensor radiance is used as an artificial-light / light-pollution proxy; it is not observed zenith sky brightness and is not a Bortle class."
                else
                    "Imported legacy regional sky-brightness proxy; not a Bortle class.")
                put("nasa_gibs_quantisation", if (nasaRadiance) "Radiance estimated from the official GIBS greyscale colormap used in the cached WMS image." else JSONObject.NULL)
            }.toString()
        )
    }

    fun noCache(): Map<String, String> = linkedMapOf(
        LightPollutionFields.STATUS to "failed",
        LightPollutionFields.RESULT to "NO LIGHT-POLLUTION DATA FOR THIS LOCATION",
        LightPollutionFields.ERROR to "No cached radiance region covers this location. Download a NASA region or import a legacy region."
    )

    fun locationFailure(message: String): Map<String, String> = linkedMapOf(
        LightPollutionFields.STATUS to "failed",
        LightPollutionFields.RESULT to "LIGHT-POLLUTION LOOKUP COULD NOT RESOLVE A LOCATION",
        LightPollutionFields.ERROR to message
    )

    private fun nasaProxyLabel(radiance: Double): String = when {
        radiance < 0.5 -> "VERY LOW NIGHT LIGHT"
        radiance < 1.0 -> "LOW NIGHT LIGHT"
        radiance < 2.0 -> "MODERATE NIGHT LIGHT"
        radiance < 5.0 -> "BRIGHT"
        radiance < 10.0 -> "VERY BRIGHT"
        else -> "INTENSE NIGHT LIGHT"
    }

    private fun legacySkyLabel(value: Double): String {
        val ratio = value / 0.174
        return when {
            ratio < 0.08 -> "VERY DARK"
            ratio < 0.33 -> "DARK"
            ratio < 1.0 -> "MODERATE"
            ratio < 4.0 -> "BRIGHT"
            else -> "VERY BRIGHT"
        }
    }

    private fun fmt(v: Double, d: Int) = String.format(Locale.US, "%.${d}f", v)
}
