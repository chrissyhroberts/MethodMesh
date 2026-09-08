package com.example.methodmesh.modules.hamradio

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object HamRadioModule : MethodMeshModule {
    override val moduleId = "hamradio"
    override val displayName = "Amateur radio"
    override val summary = "A live operating dashboard plus space weather, PSK activity, HF band guidance, Maidenhead/path and practical RF calculators."

    override fun as100Methods() = listOf(
        As100HamDashboardMethod,
        As100HamSpaceWeatherMethod,
        As100HamPskReporterMethod,
        As100HamBandAdviceMethod,
        As100HamMaidenheadMethod,
        As100HamPathMethod,
        As100HamAntennaMethod,
        As100HamSwrMethod,
        As100HamLinkMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("show ham radio dashboard", As100HamDashboardMethod.ID, "Refresh QTH, current HF band guidance, space weather and optional PSK activity"),
        RilBinding("read space weather", As100HamSpaceWeatherMethod.ID, "Read current radio-relevant NOAA space weather"),
        RilBinding("read psk reporter", As100HamPskReporterMethod.ID, "Read recent observed PSK Reporter reception activity"),
        RilBinding("recommend ham band", As100HamBandAdviceMethod.ID, "Rank HF amateur bands for current conditions"),
        RilBinding("convert maidenhead", As100HamMaidenheadMethod.ID, "Encode or decode a Maidenhead locator"),
        RilBinding("calculate radio path", As100HamPathMethod.ID, "Calculate distance and bearings between two Maidenhead locators"),
        RilBinding("calculate antenna length", As100HamAntennaMethod.ID, "Calculate wavelength-derived antenna starting dimensions"),
        RilBinding("calculate swr", As100HamSwrMethod.ID, "Calculate SWR from forward and reflected power"),
        RilBinding("calculate radio link", As100HamLinkMethod.ID, "Calculate FSPL, horizon and Fresnel-zone values")
    )

    override fun capabilityScreens() = listOf(
        HamRadioDashboardCapabilityScreen,
        HamSpaceWeatherCapabilityScreen,
        HamPskReporterCapabilityScreen,
        HamBandAdviceCapabilityScreen,
        HamMaidenheadCapabilityScreen,
        HamPathCapabilityScreen,
        HamAntennaCapabilityScreen,
        HamSwrCapabilityScreen,
        HamLinkCapabilityScreen
    )

    override fun capabilitySettings() = mapOf(
        As100HamDashboardMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "location_source", label = "QTH source",
                description = "Auto uses live GPS. A supplied Maidenhead locator or explicit coordinates override GPS.",
                defaultValue = "auto", choices = listOf("auto", "locator", "manual")
            ),
            MethodSetting.TextSetting(id = "qth_locator", label = "Maidenhead locator (optional)", defaultValue = ""),
            MethodSetting.FloatSetting(id = "latitude", label = "Latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, decimals = 6),
            MethodSetting.FloatSetting(id = "longitude", label = "Longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, decimals = 6),
            MethodSetting.FloatSetting(id = "target_distance_km", label = "Operating target distance", defaultValue = 2500f, minimum = 0f, unit = "km", decimals = 0),
            MethodSetting.ChoiceSetting(id = "mode", label = "Operating mode", defaultValue = "mixed", choices = listOf("mixed", "ssb", "cw", "ft8")),
            MethodSetting.TextSetting(id = "callsign", label = "My callsign (optional PSK Reporter)", defaultValue = ""),
            MethodSetting.ChoiceSetting(id = "psk_direction", label = "PSK Reporter direction", defaultValue = "sent", choices = listOf("sent", "received", "either")),
            MethodSetting.IntSetting(id = "psk_lookback_minutes", label = "PSK Reporter lookback", defaultValue = 30, minimum = 5, maximum = 1440, unit = "min")
        ),
        As100HamSpaceWeatherMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "refresh_mode", label = "Refresh mode", defaultValue = "cache_preferred",
                choices = listOf("cache_preferred", "fresh_required")
            )
        ),
        As100HamPskReporterMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "subject_type", label = "Subject type", defaultValue = "callsign",
                choices = listOf("callsign", "grid")
            ),
            MethodSetting.TextSetting(id = "subject", label = "Callsign or grid", defaultValue = ""),
            MethodSetting.ChoiceSetting(
                id = "direction", label = "Direction", defaultValue = "sent",
                choices = listOf("sent", "received", "either")
            ),
            MethodSetting.IntSetting(id = "lookback_minutes", label = "Look back", defaultValue = 30, minimum = 5, maximum = 1440, unit = "min"),
            MethodSetting.TextSetting(id = "mode", label = "Mode filter (blank = any)", defaultValue = ""),
            MethodSetting.FloatSetting(id = "min_frequency_mhz", label = "Minimum frequency (optional)", defaultValue = 0f, minimum = 0f, unit = "MHz", decimals = 3),
            MethodSetting.FloatSetting(id = "max_frequency_mhz", label = "Maximum frequency (optional)", defaultValue = 0f, minimum = 0f, unit = "MHz", decimals = 3),
            MethodSetting.IntSetting(id = "record_limit", label = "Maximum reports", defaultValue = 100, minimum = 1, maximum = 100),
            MethodSetting.ChoiceSetting(id = "include_no_locator", label = "Include reports without locator", defaultValue = "no", choices = listOf("no", "yes"))
        ),
        As100HamBandAdviceMethod.ID to listOf(
            MethodSetting.ChoiceSetting(id = "location_mode", label = "QTH input", defaultValue = "locator", choices = listOf("locator", "coordinates")),
            MethodSetting.TextSetting(id = "origin_locator", label = "Origin Maidenhead locator", defaultValue = ""),
            MethodSetting.FloatSetting(id = "latitude", label = "Origin latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, decimals = 5),
            MethodSetting.FloatSetting(id = "longitude", label = "Origin longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, decimals = 5),
            MethodSetting.TextSetting(id = "target_locator", label = "Target Maidenhead locator", defaultValue = ""),
            MethodSetting.FloatSetting(id = "target_distance_km", label = "Target distance", defaultValue = 2000f, minimum = 0f, unit = "km", decimals = 0),
            MethodSetting.ChoiceSetting(id = "conditions_source", label = "Conditions", defaultValue = "live_noaa", choices = listOf("live_noaa", "manual")),
            MethodSetting.FloatSetting(id = "kp", label = "Manual Kp", defaultValue = 2f, minimum = 0f, maximum = 9f, decimals = 1),
            MethodSetting.FloatSetting(id = "f107", label = "Manual F10.7", defaultValue = 100f, minimum = 50f, maximum = 400f, decimals = 0),
            MethodSetting.IntSetting(id = "r_scale", label = "Manual NOAA R scale", defaultValue = 0, minimum = 0, maximum = 5),
            MethodSetting.ChoiceSetting(id = "mode", label = "Operating mode", defaultValue = "mixed", choices = listOf("mixed", "ssb", "cw", "ft8", "ft4", "digital")),
            MethodSetting.TextSetting(id = "when_iso", label = "UTC ISO time (blank = now)", defaultValue = "")
        ),
        As100HamMaidenheadMethod.ID to listOf(
            MethodSetting.ChoiceSetting(id = "operation", label = "Operation", defaultValue = "encode", choices = listOf("encode", "decode")),
            MethodSetting.FloatSetting(id = "latitude", label = "Latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, decimals = 6),
            MethodSetting.FloatSetting(id = "longitude", label = "Longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, decimals = 6),
            MethodSetting.ChoiceSetting(id = "precision", label = "Locator characters", defaultValue = "6", choices = listOf("2", "4", "6", "8")),
            MethodSetting.TextSetting(id = "locator", label = "Locator", defaultValue = "")
        ),
        As100HamPathMethod.ID to listOf(
            MethodSetting.TextSetting(id = "origin_locator", label = "Origin locator", defaultValue = ""),
            MethodSetting.TextSetting(id = "destination_locator", label = "Destination locator", defaultValue = "")
        ),
        As100HamAntennaMethod.ID to listOf(
            MethodSetting.FloatSetting(id = "frequency_mhz", label = "Frequency", defaultValue = 14.2f, minimum = 0.001f, unit = "MHz", decimals = 3),
            MethodSetting.ChoiceSetting(id = "design", label = "Design", defaultValue = "dipole", choices = listOf("quarter_wave", "half_wave", "dipole", "five_eighths", "full_wave")),
            MethodSetting.FloatSetting(id = "velocity_factor", label = "Velocity/end-effect factor", defaultValue = 0.95f, minimum = 0.01f, maximum = 1.5f, decimals = 3)
        ),
        As100HamSwrMethod.ID to listOf(
            MethodSetting.FloatSetting(id = "forward_power_w", label = "Forward power", defaultValue = 100f, minimum = 0.001f, unit = "W", decimals = 2),
            MethodSetting.FloatSetting(id = "reflected_power_w", label = "Reflected power", defaultValue = 4f, minimum = 0f, unit = "W", decimals = 2)
        ),
        As100HamLinkMethod.ID to listOf(
            MethodSetting.FloatSetting(id = "frequency_mhz", label = "Frequency", defaultValue = 145f, minimum = 0.001f, unit = "MHz", decimals = 3),
            MethodSetting.FloatSetting(id = "distance_km", label = "Distance", defaultValue = 25f, minimum = 0.001f, unit = "km", decimals = 2),
            MethodSetting.FloatSetting(id = "tx_height_m", label = "TX antenna height", defaultValue = 10f, minimum = 0f, unit = "m", decimals = 1),
            MethodSetting.FloatSetting(id = "rx_height_m", label = "RX antenna height", defaultValue = 10f, minimum = 0f, unit = "m", decimals = 1),
            MethodSetting.FloatSetting(id = "tx_power_w", label = "TX power (0 = omit link budget)", defaultValue = 0f, minimum = 0f, unit = "W", decimals = 2),
            MethodSetting.FloatSetting(id = "antenna_gain_dbi", label = "TX antenna gain", defaultValue = 0f, unit = "dBi", decimals = 2),
            MethodSetting.FloatSetting(id = "feedline_loss_db", label = "TX feedline loss", defaultValue = 0f, minimum = 0f, unit = "dB", decimals = 2)
        )
    )
}
