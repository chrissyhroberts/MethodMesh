package com.example.researchos.platform.nfc

import android.nfc.Tag
import com.example.researchos.core.researchos.DeviceService
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.TemporalContext

/**
 * Platform NFC event captured by the Android NFC device service.
 *
 * The AS1.00 Signal is the architectural object consumed by the runtime. The
 * Android Tag remains available inside this platform wrapper so the NFC method
 * can perform NDEF I/O without leaking platform handles into transport code.
 */
data class NfcTagSignal(
    val signal: Signal,
    val androidTag: Tag
)

/**
 * AS1.00 Device Service for Android NFC.
 *
 * This service is deliberately source-only: it normalises Android Tag discovery
 * into a transient Signal. It does not decide what the tag means and does not
 * create observations, evidence, artifacts, or transformations.
 */
object AndroidNfcDeviceService : DeviceService {
    override val serviceId: String = SERVICE_ID

    override fun describe(): Map<String, String> = mapOf(
        "service_id" to SERVICE_ID,
        "service_type" to "device.nfc",
        "platform" to "android",
        "signal_type" to SIGNAL_TYPE_TAG_DISCOVERED,
        "responsibility" to "Emit NFC tag-discovery signals without interpreting them."
    )

    fun signalFromTag(tag: Tag): Signal {
        val emittedAt = System.currentTimeMillis()
        return Signal(
            signalType = SIGNAL_TYPE_TAG_DISCOVERED,
            sourceService = SERVICE_ID,
            payload = linkedMapOf(
                "tag_uid_hex" to tag.id.toHexString(),
                "tag_uid_dec" to tag.id.toUnsignedLongString(),
                "tech_list" to tag.techList.joinToString(",") { it.substringAfterLast('.') }
            ),
            temporalContext = TemporalContext(
                eventTimeEpochMs = emittedAt,
                systemTimeEpochMs = emittedAt
            ),
            provenance = ProvenanceContext(
                provider = "android.nfc",
                methodId = SERVICE_ID
            )
        )
    }

    fun tagSignalFromTag(tag: Tag): NfcTagSignal = NfcTagSignal(
        signal = signalFromTag(tag),
        androidTag = tag
    )

    const val SERVICE_ID = "device.nfc.android"
    const val SIGNAL_TYPE_TAG_DISCOVERED = "nfc.tag.discovered"
}

private fun ByteArray?.toHexString(): String =
    this?.joinToString(separator = "") { byte -> "%02X".format(byte) }.orEmpty()

private fun ByteArray?.toUnsignedLongString(): String {
    val bytes = this ?: return ""
    var value = 0L
    bytes.forEach { value = (value shl 8) + (it.toInt() and 0xff) }
    return value.toString()
}
