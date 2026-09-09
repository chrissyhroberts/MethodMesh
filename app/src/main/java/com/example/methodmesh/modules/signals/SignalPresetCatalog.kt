package com.example.methodmesh.modules.signals

/**
 * Shared discrete tuning catalog for Signals physical channels.
 *
 * Native TX and RX screens deliberately draw from the same values so a user can
 * select the same named setting on both devices without translating sliders.
 * Legacy numeric settings are still read by the screens for backwards compatibility.
 */
internal object SignalPresetCatalog {
    data class TonePreset(val id: String, val label: String, val hz: Double, val toleranceHz: Double)
    data class LevelPreset(val id: String, val label: String, val dbfs: Double)
    data class SurfacePreset(val id: String, val label: String, val carrierHz: Double, val bitMs: Int)

    val morseOpticalWpm = listOf(5, 8, 10)
    val morseAudioWpm = listOf(5, 10, 20, 30)

    val morseTones = listOf(
        TonePreset("A", "A · 500 Hz", 500.0, 100.0),
        TonePreset("B", "B · 700 Hz", 700.0, 120.0),
        TonePreset("C", "C · 1.2 kHz", 1200.0, 160.0),
        TonePreset("D", "D · 2.2 kHz", 2200.0, 220.0)
    )

    val toneTolerances = listOf(
        "Tight" to 80.0,
        "Normal" to 120.0,
        "Wide" to 220.0,
        "Very wide" to 400.0
    )

    val levelThresholds = listOf(
        LevelPreset("Very quiet", "Very quiet · −78 dBFS", -78.0),
        LevelPreset("Quiet", "Quiet · −66 dBFS", -66.0),
        LevelPreset("Normal", "Normal · −54 dBFS", -54.0),
        LevelPreset("Loud", "Loud · −42 dBFS", -42.0)
    )

    // Orthogonal Morse spacing axes. A sender and receiver may choose the same
    // independent values rather than relying on one overloaded speed slider.
    val elementGapUnits = listOf(1.0, 1.25, 1.5)
    val letterGapUnits = listOf(3.0, 4.0, 5.0)
    val wordGapUnits = listOf(7.0, 9.0, 12.0)
    val cycleGapUnits = listOf(10, 15, 24)
    val repeatCounts = listOf(2, 3, 5, 10)

    val pttLeadMs = listOf(0, 120, 300, 600)

    val qrShardBytes = listOf(64, 96, 160, 256)
    val qrDwellMs = listOf(250, 400, 650, 1000)

    val surfaceProfiles = listOf(
        SurfacePreset("A", "A · 120 Hz · 80 ms", 120.0, 80),
        SurfacePreset("B", "B · 180 Hz · 60 ms", 180.0, 60),
        SurfacePreset("C", "C · 240 Hz · 60 ms", 240.0, 60),
        SurfacePreset("D", "D · 180 Hz · 100 ms", 180.0, 100)
    )
    val surfaceCalibrationMs = listOf(1000, 1500, 2500)
    val surfaceSensitivity = listOf(2.0, 2.5, 3.5, 5.0)

    fun closestMorseTone(hz: Double): TonePreset =
        morseTones.minByOrNull { kotlin.math.abs(it.hz - hz) } ?: morseTones[1]

    fun closestLevel(dbfs: Double): LevelPreset =
        levelThresholds.minByOrNull { kotlin.math.abs(it.dbfs - dbfs) } ?: levelThresholds[2]

    fun surfaceById(id: String): SurfacePreset? =
        surfaceProfiles.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun closestSurface(carrierHz: Double, bitMs: Int): SurfacePreset =
        surfaceProfiles.minByOrNull { kotlin.math.abs(it.carrierHz - carrierHz) + kotlin.math.abs(it.bitMs - bitMs) * 2.0 }
            ?: surfaceProfiles[1]
}
