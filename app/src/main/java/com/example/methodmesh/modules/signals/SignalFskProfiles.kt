package com.example.methodmesh.modules.signals

internal data class SignalFskProfile(
    val id: String,
    val label: String,
    val markHz: Double,
    val spaceHz: Double,
    val bitMs: Int
)

internal object SignalFskProfiles {
    val audible: List<SignalFskProfile> = listOf(
        SignalFskProfile("A", "A · proven · 1.2/2.2 kHz · 60 ms", 1200.0, 2200.0, 60),
        SignalFskProfile("B", "B · fast on proven carriers · 1.2/2.2 kHz · 40 ms", 1200.0, 2200.0, 40),
        SignalFskProfile("C", "C · lower-band alternative · 0.9/1.6 kHz · 60 ms", 900.0, 1600.0, 60),
        SignalFskProfile("D", "D · lower-band fast · 0.9/1.6 kHz · 40 ms", 900.0, 1600.0, 40)
    )

    // Many phone speakers/microphones roll off sharply above ~15-16 kHz. These
    // profiles deliberately step upward from a device-friendly high-audio pair
    // instead of assuming 18-19 kHz is usable.
    val highBand: List<SignalFskProfile> = listOf(
        SignalFskProfile("A", "A · faster low high-band · 12/13 kHz · 80 ms", 12000.0, 13000.0, 80),
        SignalFskProfile("B", "B · 13/14 kHz · 100 ms", 13000.0, 14000.0, 100),
        SignalFskProfile("C", "C · 14/15 kHz · 120 ms", 14000.0, 15000.0, 120),
        SignalFskProfile("D", "D · music-resistant test profile · 15/16 kHz · 160 ms", 15000.0, 16000.0, 160)
    )

    fun list(ultrasonic: Boolean): List<SignalFskProfile> = if (ultrasonic) highBand else audible

    fun byId(ultrasonic: Boolean, id: String): SignalFskProfile? =
        list(ultrasonic).firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun matching(ultrasonic: Boolean, markHz: Double, spaceHz: Double, bitMs: Int): SignalFskProfile? =
        list(ultrasonic).firstOrNull {
            kotlin.math.abs(it.markHz - markHz) < 1.0 &&
                kotlin.math.abs(it.spaceHz - spaceHz) < 1.0 &&
                it.bitMs == bitMs
        }

    fun nearest(ultrasonic: Boolean, markHz: Double, spaceHz: Double, bitMs: Int): SignalFskProfile =
        list(ultrasonic).minByOrNull {
            kotlin.math.abs(it.markHz - markHz) / 100.0 +
                kotlin.math.abs(it.spaceHz - spaceHz) / 100.0 +
                kotlin.math.abs(it.bitMs - bitMs).toDouble()
        } ?: list(ultrasonic).first()
}
