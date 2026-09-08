package com.example.methodmesh.modules.soundgenerator

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object SoundGeneratorModule : MethodMeshModule {
    override val moduleId = "soundgenerator"
    override val displayName = "Sound generator"
    override val summary = "Generate local tones, noise and sweeps with explicit digital level, routing and playback provenance."

    override fun as100Methods() = listOf(As100SoundPlayMethod)

    override fun rilBindings() = listOf(
        RilBinding("play sound", As100SoundPlayMethod.ID, "Generate and play a configured sound stimulus"),
        RilBinding("generate tone", As100SoundPlayMethod.ID, "Generate and play a tone"),
        RilBinding("play tone", As100SoundPlayMethod.ID, "Generate and play a tone"),
        RilBinding("play noise", As100SoundPlayMethod.ID, "Generate and play a noise stimulus")
    )

    override fun capabilityScreens() = listOf(SoundGeneratorCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100SoundPlayMethod.ID to listOf(
            MethodSetting.ChoiceSetting("stimulus_type", "Sound type", defaultValue = "tone", choices = listOf("tone", "noise", "sweep")),
            MethodSetting.ChoiceSetting("waveform", "Waveform", defaultValue = "sine", choices = listOf("sine", "square", "triangle", "sawtooth")),
            MethodSetting.FloatSetting("frequency_hz", "Frequency (Hz)", defaultValue = 1000f, minimum = 1f, maximum = 23999f),
            MethodSetting.ChoiceSetting("noise_type", "Noise type", defaultValue = "white", choices = listOf("white", "pink", "brown")),
            MethodSetting.FloatSetting("sweep_start_hz", "Sweep start (Hz)", defaultValue = 250f, minimum = 1f, maximum = 23999f),
            MethodSetting.FloatSetting("sweep_end_hz", "Sweep end (Hz)", defaultValue = 8000f, minimum = 1f, maximum = 23999f),
            MethodSetting.ChoiceSetting("sweep_scale", "Sweep scale", defaultValue = "logarithmic", choices = listOf("logarithmic", "linear")),
            MethodSetting.FloatSetting("level_dbfs", "Digital peak level (dBFS)", defaultValue = -30f, minimum = -80f, maximum = 0f),
            MethodSetting.IntSetting("duration_ms", "Duration (ms)", defaultValue = 1000, minimum = 20, maximum = 60000),
            MethodSetting.ChoiceSetting("channel", "Channel", defaultValue = "both", choices = listOf("both", "left", "right")),
            MethodSetting.IntSetting("fade_ms", "Fade in/out (ms)", defaultValue = 10, minimum = 0, maximum = 5000),
            MethodSetting.ChoiceSetting("gate_mode", "Pattern", defaultValue = "steady", choices = listOf("steady", "pulsed")),
            MethodSetting.IntSetting("pulse_on_ms", "Pulse on (ms)", defaultValue = 500, minimum = 5, maximum = 60000),
            MethodSetting.IntSetting("pulse_off_ms", "Pulse off (ms)", defaultValue = 500, minimum = 0, maximum = 60000),
            MethodSetting.ChoiceSetting("sample_rate_hz", "Sample rate (Hz)", defaultValue = "48000", choices = listOf("48000", "44100")),
            MethodSetting.ChoiceSetting("noise_seed_mode", "Noise seed mode", defaultValue = "secure_random", choices = listOf("secure_random", "fixed_seed")),
            MethodSetting.TextSetting("noise_seed", "Fixed noise seed", defaultValue = ""),
            MethodSetting.TextSetting("output_device_id", "Output device ID", defaultValue = ""),
            MethodSetting.ChoiceSetting("system_volume_policy", "System volume policy", defaultValue = "preserve", choices = listOf("preserve", "require_percent", "temporary_set_percent")),
            MethodSetting.IntSetting("system_volume_percent", "System media volume (%)", defaultValue = 50, minimum = 0, maximum = 100)
        )
    )
}
