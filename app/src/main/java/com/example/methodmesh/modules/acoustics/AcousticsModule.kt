package com.example.methodmesh.modules.acoustics

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object AcousticsModule : MethodMeshModule {
    override val moduleId = "acoustics"
    override val displayName = "Acoustics"
    override val summary = "Analyse tones, tune instruments, estimate sound level, and compare measured tones with target frequencies."

    override fun as100Methods() = listOf(
        As100AcousticAnalyseMethod,
        As100AcousticTunerMethod,
        As100AcousticLevelMethod,
        As100AcousticCompareMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("analyse acoustic signal", As100AcousticAnalyseMethod.ID, "Measure pitch, waveform, spectrum and amplitude"),
        RilBinding("analyse tone", As100AcousticAnalyseMethod.ID, "Measure an incoming tone"),
        RilBinding("tune instrument", As100AcousticTunerMethod.ID, "Tune a chromatic or preset string instrument"),
        RilBinding("measure sound level", As100AcousticLevelMethod.ID, "Measure relative or calibrated acoustic level"),
        RilBinding("compare tone", As100AcousticCompareMethod.ID, "Compare a measured tone with a target frequency and tolerance")
    )

    override fun capabilityScreens() = listOf(
        AcousticAnalyseCapabilityScreen,
        AcousticTunerCapabilityScreen,
        AcousticLevelCapabilityScreen,
        AcousticCompareCapabilityScreen
    )

    override fun capabilitySettings() = mapOf(
        As100AcousticAnalyseMethod.ID to listOf(
            MethodSetting.FloatSetting("capture_seconds", "Capture duration", "Duration used for automatic/ODK measurements.", defaultValue = 2.0f, minimum = 0.5f, maximum = 30f, step = 0.5f, unit = "s", decimals = 1),
            MethodSetting.ChoiceSetting("sample_rate_hz", "Sample rate", "Audio sampling rate.", defaultValue = "48000", choices = listOf("44100", "48000")),
            MethodSetting.FloatSetting("min_frequency_hz", "Minimum pitch frequency", defaultValue = 40f, minimum = 20f, maximum = 1000f, step = 1f, unit = "Hz", decimals = 0),
            MethodSetting.FloatSetting("max_frequency_hz", "Maximum pitch frequency", defaultValue = 5000f, minimum = 100f, maximum = 10000f, step = 10f, unit = "Hz", decimals = 0),
            MethodSetting.FloatSetting("reference_a4_hz", "Reference A4", defaultValue = 440f, minimum = 400f, maximum = 480f, step = 0.1f, unit = "Hz", decimals = 1),
            MethodSetting.ChoiceSetting("speed_of_sound_mode", "Wavelength basis", "Wavelength is derived, not directly measured.", defaultValue = "temperature", choices = listOf("temperature", "fixed_speed")),
            MethodSetting.FloatSetting("temperature_c", "Air temperature", "Used to estimate speed of sound when wavelength basis is temperature.", defaultValue = 20f, minimum = -30f, maximum = 60f, step = 0.5f, unit = "°C", decimals = 1),
            MethodSetting.FloatSetting("speed_of_sound_mps", "Speed of sound", "Used only when wavelength basis is fixed_speed.", defaultValue = 343f, minimum = 250f, maximum = 450f, step = 0.1f, unit = "m/s", decimals = 1),
            MethodSetting.IntSetting("minimum_stable_ms", "Minimum stable duration", defaultValue = 500, minimum = 100, maximum = 5000, step = 100, unit = "ms"),
            MethodSetting.FloatSetting("maximum_sd_cents", "Maximum pitch variation", defaultValue = 5f, minimum = 0.5f, maximum = 50f, step = 0.5f, unit = "cents SD", decimals = 1),
            MethodSetting.FloatSetting("minimum_pitch_confidence", "Minimum pitch confidence", defaultValue = 0.60f, minimum = 0.1f, maximum = 1f, step = 0.05f, decimals = 2)
        ),
        As100AcousticTunerMethod.ID to listOf(
            MethodSetting.ChoiceSetting("instrument", "Instrument", defaultValue = "chromatic", choices = listOf("chromatic", "guitar", "guitar_drop_d", "ukulele_high_g", "ukulele_low_g", "violin", "viola", "cello", "bass", "mandolin")),
            MethodSetting.IntSetting("string_index", "String", "0 chooses the nearest target automatically; 1..6 selects a string where available.", defaultValue = 0, minimum = 0, maximum = 6, step = 1),
            MethodSetting.FloatSetting("reference_a4_hz", "Reference A4", defaultValue = 440f, minimum = 400f, maximum = 480f, step = 0.1f, unit = "Hz", decimals = 1),
            MethodSetting.FloatSetting("green_zone_cents", "In-tune zone", defaultValue = 5f, minimum = 1f, maximum = 25f, step = 0.5f, unit = "cents", decimals = 1),
            MethodSetting.FloatSetting("capture_seconds", "Capture duration", defaultValue = 2.0f, minimum = 0.5f, maximum = 30f, step = 0.5f, unit = "s", decimals = 1),
            MethodSetting.IntSetting("minimum_stable_ms", "Minimum stable duration", defaultValue = 500, minimum = 100, maximum = 5000, step = 100, unit = "ms"),
            MethodSetting.FloatSetting("maximum_sd_cents", "Maximum pitch variation", defaultValue = 5f, minimum = 0.5f, maximum = 50f, step = 0.5f, unit = "cents SD", decimals = 1),
            MethodSetting.FloatSetting("minimum_pitch_confidence", "Minimum pitch confidence", defaultValue = 0.60f, minimum = 0.1f, maximum = 1f, step = 0.05f, decimals = 2),
            MethodSetting.ChoiceSetting("sample_rate_hz", "Sample rate", defaultValue = "48000", choices = listOf("44100", "48000"))
        ),
        As100AcousticLevelMethod.ID to listOf(
            MethodSetting.FloatSetting("capture_seconds", "Capture duration", defaultValue = 3.0f, minimum = 0.5f, maximum = 60f, step = 0.5f, unit = "s", decimals = 1),
            MethodSetting.ChoiceSetting("sample_rate_hz", "Sample rate", defaultValue = "48000", choices = listOf("44100", "48000")),
            MethodSetting.ChoiceSetting("calibration_mode", "Level mode", "Uncalibrated returns dBFS. Calibrated applies a supplied offset to estimate dB SPL.", defaultValue = "uncalibrated", choices = listOf("uncalibrated", "calibrated")),
            MethodSetting.FloatSetting("calibration_offset_db", "Calibration offset", "dB SPL = dBFS + offset. Generate this in the native calibration helper or supply a validated value.", defaultValue = 0f, minimum = -50f, maximum = 200f, step = 0.1f, unit = "dB", decimals = 1),
            MethodSetting.FloatSetting("calibration_reference_db_spl", "Calibration reference", "Reference meter level used when deriving the offset.", defaultValue = 94f, minimum = 20f, maximum = 140f, step = 0.1f, unit = "dB SPL", decimals = 1),
            MethodSetting.TextSetting("calibration_note", "Calibration note", "Optional reference meter/profile identifier.", defaultValue = "")
        ),
        As100AcousticCompareMethod.ID to listOf(
            MethodSetting.FloatSetting("target_hz", "Target frequency", defaultValue = 1000f, minimum = 1f, maximum = 20000f, step = 0.1f, unit = "Hz", decimals = 2),
            MethodSetting.ChoiceSetting("tolerance_mode", "Tolerance unit", defaultValue = "hz", choices = listOf("hz", "percent", "cents")),
            MethodSetting.FloatSetting("tolerance_value", "Tolerance", defaultValue = 5f, minimum = 0f, maximum = 1000f, step = 0.1f, decimals = 2),
            MethodSetting.FloatSetting("capture_seconds", "Capture duration", defaultValue = 2.0f, minimum = 0.5f, maximum = 30f, step = 0.5f, unit = "s", decimals = 1),
            MethodSetting.IntSetting("minimum_stable_ms", "Minimum stable duration", defaultValue = 500, minimum = 100, maximum = 5000, step = 100, unit = "ms"),
            MethodSetting.FloatSetting("maximum_sd_cents", "Maximum pitch variation", defaultValue = 3f, minimum = 0.2f, maximum = 50f, step = 0.1f, unit = "cents SD", decimals = 1),
            MethodSetting.FloatSetting("minimum_pitch_confidence", "Minimum pitch confidence", defaultValue = 0.65f, minimum = 0.1f, maximum = 1f, step = 0.05f, decimals = 2),
            MethodSetting.ChoiceSetting("sample_rate_hz", "Sample rate", defaultValue = "48000", choices = listOf("44100", "48000")),
            MethodSetting.FloatSetting("min_frequency_hz", "Minimum pitch frequency", defaultValue = 40f, minimum = 20f, maximum = 1000f, step = 1f, unit = "Hz", decimals = 0),
            MethodSetting.FloatSetting("max_frequency_hz", "Maximum pitch frequency", defaultValue = 5000f, minimum = 100f, maximum = 10000f, step = 10f, unit = "Hz", decimals = 0)
        )
    )
}
