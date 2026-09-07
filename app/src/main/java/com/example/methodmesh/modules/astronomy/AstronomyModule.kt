package com.example.methodmesh.modules.astronomy

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleDependency
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object AstronomyModule : MethodMeshModule {
    override val moduleId = "astronomy"
    override val displayName = "Astronomy"
    override val summary = "Plan, assess and document astronomical observing and imaging without duplicating a planetarium."

    override fun as100Methods() = listOf(
        As100DashboardMethod,
        As100LightPollutionMethod,
        As100PolarAlignMethod,
        As100ConditionsMethod,
        As100SkyTestMethod,
        As100ImagingWindowMethod,
        As100DewRiskMethod,
        As100ImageScaleMethod,
        As100ExposureLimitMethod,
        As100MountStabilityMethod,
        As100FocusMethod,
        As100SessionMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("show astronomy dashboard", As100DashboardMethod.id, "Refresh current observing, dew, upper-atmosphere and tonight conditions"),
        RilBinding("check light pollution", As100LightPollutionMethod.id, "Download, map and sample NASA VIIRS nighttime radiance around a location"),
        RilBinding("polar align", As100PolarAlignMethod.id, "Find true/celestial north or south and Polaris clock position"),
        RilBinding("assess astronomy conditions", As100ConditionsMethod.id, "Score imaging conditions"),
        RilBinding("test sky quality", As100SkyTestMethod.id, "Measure the sky against a good-night calibration"),
        RilBinding("plan imaging window", As100ImagingWindowMethod.id, "Find the best imaging period tonight"),
        RilBinding("check dew risk", As100DewRiskMethod.id, "Calculate condensation risk"),
        RilBinding("calculate image scale", As100ImageScaleMethod.id, "Calculate field of view and sampling"),
        RilBinding("calculate exposure limit", As100ExposureLimitMethod.id, "Estimate untracked exposure limit"),
        RilBinding("test mount stability", As100MountStabilityMethod.id, "Measure telescope or tripod vibration"),
        RilBinding("assist focus", As100FocusMethod.id, "Minimise point-source FWHM"),
        RilBinding("record astronomy session", As100SessionMethod.id, "Run a resumable observing session and create a shareable record")
    )


    override fun dependencies() = listOf(
        ModuleDependency("apiget", "Use the shared declarative API client for Open-Meteo weather and air quality."),
        ModuleDependency("pluscodecapture", "Accept Plus Codes as a location source without duplicating Open Location Code logic.")
    )

    override fun capabilityScreens() = listOf(
        AstronomyDashboardCapabilityScreen,
        LightPollutionCapabilityScreen,
        PolarAlignCapabilityScreen,
        AstronomyConditionsCapabilityScreen,
        SkyTestCapabilityScreen,
        ImagingWindowCapabilityScreen,
        DewRiskCapabilityScreen,
        ImageScaleCapabilityScreen,
        ExposureLimitCapabilityScreen,
        MountStabilityCapabilityScreen,
        FocusCapabilityScreen,
        SessionCapabilityScreen
    )

    override fun capabilitySettings() = mapOf(
        As100DashboardMethod.id to listOf(
            MethodSetting.ChoiceSetting("location_source", "Location source", description = "Choose GPS, Plus Code, or explicit coordinates. The selected source remains active until changed; the dashboard GPS button deliberately recentres.", defaultValue = "auto", choices = listOf("auto", "plus_code", "manual")),
            MethodSetting.TextSetting("plus_code", "Plus Code (optional)", defaultValue = ""),
            MethodSetting.FloatSetting("latitude", "Latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, unit = "°", decimals = 6),
            MethodSetting.FloatSetting("longitude", "Longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, unit = "°", decimals = 6),
            MethodSetting.IntSetting("hours_ahead", "Plan ahead", defaultValue = 14, minimum = 2, maximum = 24, unit = "h"),
            MethodSetting.IntSetting("minimum_score", "Usable window threshold", defaultValue = 55, minimum = 0, maximum = 100)
        ),
        As100LightPollutionMethod.id to listOf(
            MethodSetting.ChoiceSetting("location_source", "Location source", defaultValue = "auto", choices = listOf("auto", "plus_code", "manual")),
            MethodSetting.TextSetting("plus_code", "Plus Code", defaultValue = ""),
            MethodSetting.FloatSetting("latitude", "Latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, unit = "°", decimals = 6),
            MethodSetting.FloatSetting("longitude", "Longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, unit = "°", decimals = 6),
            MethodSetting.FloatSetting("radius_km", "Heatmap radius", description = "Regional NASA radiance cache centred on the selected site.", defaultValue = 50f, minimum = 5f, maximum = 200f, unit = "km", decimals = 0),
            MethodSetting.ChoiceSetting("basemap_mode", "Basemap", defaultValue = "auto", choices = listOf("auto", "satellite", "blank")),
            MethodSetting.FloatSetting("heatmap_opacity", "Heatmap opacity", defaultValue = 0.58f, minimum = 0.15f, maximum = 0.9f, step = 0.05f, decimals = 2),
            MethodSetting.BooleanSetting("fetch_if_missing", "Fetch NASA data if cache is missing", defaultValue = true)
        ),
        As100PolarAlignMethod.id to listOf(
            MethodSetting.FloatSetting("alignment_tolerance_deg", "Alignment tolerance", defaultValue = 2f, minimum = 0.5f, maximum = 15f, step = 0.5f, unit = "°", decimals = 1)
        ),
        As100ConditionsMethod.id to listOf(
            MethodSetting.ChoiceSetting("source_mode", "Data source", description = "Auto uses GPS + Open-Meteo. Upstream means values supplied by the same multi-step external workflow. Manual is an offline fallback.", defaultValue = "auto", choices = listOf("auto", "upstream", "manual")),
            MethodSetting.TextSetting("plus_code", "Plus Code (optional)", defaultValue = ""),
            MethodSetting.FloatSetting("latitude", "Latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, unit = "°", decimals = 6),
            MethodSetting.FloatSetting("longitude", "Longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, unit = "°", decimals = 6),
            MethodSetting.TextSetting("weather_json", "Weather API JSON (optional upstream input)", defaultValue = ""),
            MethodSetting.TextSetting("air_quality_json", "Air-quality API JSON (optional upstream input)", defaultValue = ""),
            MethodSetting.FloatSetting("cloud_cover_pct", "Cloud cover", defaultValue = 0f, minimum = 0f, maximum = 100f, unit = "%", decimals = 0),
            MethodSetting.FloatSetting("visibility_m", "Visibility", defaultValue = 30000f, minimum = 0f, maximum = 100000f, unit = "m", decimals = 0),
            MethodSetting.FloatSetting("wind_kmh", "Wind", defaultValue = 5f, minimum = 0f, maximum = 200f, unit = "km/h", decimals = 1),
            MethodSetting.FloatSetting("wind_gust_kmh", "Wind gust", defaultValue = 10f, minimum = 0f, maximum = 250f, unit = "km/h", decimals = 1),
            MethodSetting.FloatSetting("temperature_c", "Temperature", defaultValue = 10f, minimum = -60f, maximum = 60f, unit = "°C", decimals = 1),
            MethodSetting.FloatSetting("relative_humidity_pct", "Relative humidity", defaultValue = 70f, minimum = 1f, maximum = 100f, unit = "%", decimals = 0),
            MethodSetting.BooleanSetting("use_supplied_dew_point", "Use supplied dew point", defaultValue = false),
            MethodSetting.FloatSetting("dew_point_c", "Dew point", defaultValue = 0f, minimum = -80f, maximum = 60f, unit = "°C", decimals = 1),
            MethodSetting.FloatSetting("aerosol_optical_depth", "Aerosol optical depth", defaultValue = 0.1f, minimum = 0f, maximum = 5f, step = 0.01f, decimals = 2),
            MethodSetting.FloatSetting("pm2_5", "PM2.5", defaultValue = 5f, minimum = 0f, maximum = 1000f, unit = "µg/m³", decimals = 1),
            MethodSetting.FloatSetting("moon_altitude_deg", "Moon altitude", defaultValue = -10f, minimum = -90f, maximum = 90f, unit = "°", decimals = 1),
            MethodSetting.FloatSetting("moon_illumination_pct", "Moon illumination", defaultValue = 0f, minimum = 0f, maximum = 100f, unit = "%", decimals = 0)
        ),
        As100SkyTestMethod.id to listOf(
            MethodSetting.IntSetting("duration_seconds", "Test duration", defaultValue = 20, minimum = 3, maximum = 60, unit = "s"),
            MethodSetting.IntSetting("minimum_frames", "Minimum usable frames", defaultValue = 30, minimum = 10, maximum = 300),
            MethodSetting.TextSetting("calibration_note", "Good-night calibration note", defaultValue = "")
        ),
        As100ImagingWindowMethod.id to listOf(
            MethodSetting.ChoiceSetting("location_source", "Location source", defaultValue = "auto", choices = listOf("auto", "plus_code", "manual")),
            MethodSetting.TextSetting("plus_code", "Plus Code (optional supplied location)", defaultValue = ""),
            MethodSetting.FloatSetting("latitude", "Latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, unit = "°", decimals = 6),
            MethodSetting.FloatSetting("longitude", "Longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, unit = "°", decimals = 6),
            MethodSetting.TextSetting("target_name", "Target name", defaultValue = ""),
            MethodSetting.BooleanSetting("use_target", "Use target coordinates", defaultValue = false),
            MethodSetting.FloatSetting("target_ra_hours", "Target RA", description = "Decimal hours; leave target blank in native use for general sky planning.", defaultValue = 0f, minimum = 0f, maximum = 24f, unit = "h", decimals = 4),
            MethodSetting.FloatSetting("target_dec_deg", "Target declination", defaultValue = 0f, minimum = -90f, maximum = 90f, unit = "°", decimals = 4),
            MethodSetting.FloatSetting("minimum_altitude_deg", "Minimum target altitude", defaultValue = 25f, minimum = 0f, maximum = 80f, unit = "°", decimals = 0),
            MethodSetting.IntSetting("hours_ahead", "Plan ahead", defaultValue = 14, minimum = 2, maximum = 24, unit = "h"),
            MethodSetting.IntSetting("interval_minutes", "Planning interval", defaultValue = 30, minimum = 10, maximum = 60, unit = "min"),
            MethodSetting.IntSetting("minimum_score", "Usable score threshold", defaultValue = 55, minimum = 0, maximum = 100),
            MethodSetting.TextSetting("forecast_json", "Hourly forecast JSON (optional upstream input)", defaultValue = "")
        ),
        As100DewRiskMethod.id to listOf(
            MethodSetting.ChoiceSetting("source_mode", "Data source", description = "Auto uses GPS + Open-Meteo. Upstream means values supplied by the same multi-step external workflow. Manual is an offline fallback.", defaultValue = "auto", choices = listOf("auto", "upstream", "manual")),
            MethodSetting.TextSetting("plus_code", "Plus Code (optional)", defaultValue = ""),
            MethodSetting.FloatSetting("latitude", "Latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, unit = "°", decimals = 6),
            MethodSetting.FloatSetting("longitude", "Longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, unit = "°", decimals = 6),
            MethodSetting.TextSetting("weather_json", "Weather API JSON (optional upstream input)", defaultValue = ""),
            MethodSetting.FloatSetting("temperature_c", "Temperature", defaultValue = 10f, minimum = -60f, maximum = 60f, unit = "°C", decimals = 1),
            MethodSetting.FloatSetting("relative_humidity_pct", "Relative humidity", defaultValue = 70f, minimum = 1f, maximum = 100f, unit = "%", decimals = 0),
            MethodSetting.BooleanSetting("use_supplied_dew_point", "Use supplied dew point", defaultValue = false),
            MethodSetting.FloatSetting("dew_point_c", "Dew point", defaultValue = 0f, minimum = -80f, maximum = 60f, unit = "°C", decimals = 1)
        ),
        As100ImageScaleMethod.id to listOf(
            MethodSetting.FloatSetting("focal_length_mm", "Focal length", defaultValue = 400f, minimum = 1f, maximum = 20000f, unit = "mm", decimals = 1),
            MethodSetting.FloatSetting("aperture_mm", "Aperture", defaultValue = 80f, minimum = 1f, maximum = 2000f, unit = "mm", decimals = 1),
            MethodSetting.FloatSetting("pixel_size_micron", "Pixel size", defaultValue = 3.76f, minimum = 0.1f, maximum = 30f, unit = "µm", decimals = 2),
            MethodSetting.FloatSetting("sensor_width_mm", "Sensor width", defaultValue = 17.7f, minimum = 0.1f, maximum = 100f, unit = "mm", decimals = 2),
            MethodSetting.FloatSetting("sensor_height_mm", "Sensor height", defaultValue = 13.4f, minimum = 0.1f, maximum = 100f, unit = "mm", decimals = 2),
            MethodSetting.FloatSetting("multiplier", "Barlow/reducer multiplier", defaultValue = 1f, minimum = 0.1f, maximum = 10f, step = 0.01f, decimals = 2)
        ),
        As100ExposureLimitMethod.id to listOf(
            MethodSetting.FloatSetting("focal_length_mm", "Focal length", defaultValue = 50f, minimum = 1f, maximum = 2000f, unit = "mm", decimals = 1),
            MethodSetting.FloatSetting("pixel_size_micron", "Pixel size", defaultValue = 4f, minimum = 0.1f, maximum = 30f, unit = "µm", decimals = 2),
            MethodSetting.FloatSetting("declination_deg", "Target declination", defaultValue = 0f, minimum = -90f, maximum = 90f, unit = "°", decimals = 1),
            MethodSetting.FloatSetting("max_trail_pixels", "Maximum trail", defaultValue = 1f, minimum = 0.1f, maximum = 10f, unit = "px", decimals = 1),
            MethodSetting.FloatSetting("crop_factor", "Crop factor", defaultValue = 1f, minimum = 0.1f, maximum = 10f, decimals = 2)
        ),
        As100MountStabilityMethod.id to listOf(
            MethodSetting.IntSetting("duration_seconds", "Measurement duration", defaultValue = 10, minimum = 3, maximum = 60, unit = "s")
        ),
        As100FocusMethod.id to listOf(
            MethodSetting.ChoiceSetting("camera_use", "Camera use", description = "Focus mode expects a phone camera to view a bright star through an eyepiece/phone adapter or optical train.", defaultValue = "optical_adapter", choices = listOf("optical_adapter")),
            MethodSetting.ChoiceSetting("camera_facing", "Camera", defaultValue = "rear", choices = listOf("rear", "front")),
            MethodSetting.IntSetting("sample_frames", "Rolling-median frames", defaultValue = 30, minimum = 5, maximum = 200)
        ),
        As100SessionMethod.id to listOf(
            MethodSetting.TextSetting("target", "Target", defaultValue = ""),
            MethodSetting.TextSetting("start_iso", "Start time (ISO)", defaultValue = ""),
            MethodSetting.TextSetting("end_iso", "End time (ISO)", defaultValue = ""),
            MethodSetting.TextSetting("equipment", "Equipment", defaultValue = ""),
            MethodSetting.TextSetting("conditions_summary", "Conditions summary", defaultValue = ""),
            MethodSetting.TextSetting("conditions_score", "Conditions score", defaultValue = ""),
            MethodSetting.TextSetting("sky_test_summary", "Sky-test summary", defaultValue = ""),
            MethodSetting.TextSetting("notes", "Notes", defaultValue = ""),
            MethodSetting.BooleanSetting("save_session", "Save to local session history", defaultValue = false)
        )
    )
}
