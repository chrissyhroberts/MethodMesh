package com.example.methodmesh.modules.espmesh

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Compact phone-to-phone E2E live-voice packet carried opaquely by BLE/ESP-NOW.
 *
 * Radio TTL is deliberately NOT part of this packet because ESP relays must be
 * able to decrement it without possessing the phone E2E key. Everything below
 * is immutable E2E-authenticated metadata plus ciphertext.
 */
data class EspMeshLiveVoicePacket(
    val flags: Int,
    val channelTag: Int,
    val sourceTag: Int,
    val keyTag: Int,
    val sessionId: Long,
    val sequence: Int,
    val ciphertext: ByteArray
) {
    init {
        require(flags and FLAG_MASK == flags) { "Invalid voice flags" }
        require(ciphertext.size in GCM_TAG_BYTES..MAX_CIPHERTEXT_BYTES) { "Invalid voice ciphertext size" }
    }

    fun headerBytes(): ByteArray = ByteBuffer.allocate(HEADER_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .put(MAGIC)
        .put(VERSION.toByte())
        .put(flags.toByte())
        .putInt(channelTag)
        .putInt(sourceTag)
        .putInt(keyTag)
        .putLong(sessionId)
        .putInt(sequence)
        .putShort(ciphertext.size.toShort())
        .array()

    fun encode(): ByteArray = headerBytes() + ciphertext

    fun isStart(): Boolean = flags and FLAG_START != 0
    fun isEnd(): Boolean = flags and FLAG_END != 0

    companion object {
        private val MAGIC = byteArrayOf('M'.code.toByte(), 'V'.code.toByte())
        const val VERSION = 1
        const val FLAG_START = 0x01
        const val FLAG_END = 0x02
        const val FLAG_MASK = FLAG_START or FLAG_END
        const val GCM_TAG_BYTES = 16
        const val AUDIO_FRAME_BYTES = 160 // G.711 mu-law: 20 ms at 8 kHz mono.
        const val MAX_CIPHERTEXT_BYTES = AUDIO_FRAME_BYTES + GCM_TAG_BYTES
        const val HEADER_BYTES = 30
        const val MAX_PACKET_BYTES = HEADER_BYTES + MAX_CIPHERTEXT_BYTES

        fun decode(bytes: ByteArray): EspMeshLiveVoicePacket {
            require(bytes.size in HEADER_BYTES + GCM_TAG_BYTES..MAX_PACKET_BYTES) { "Voice packet size is invalid" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(2).also(buffer::get)
            require(magic.contentEquals(MAGIC)) { "Not a MethodMesh live-voice packet" }
            val version = buffer.get().toInt() and 0xff
            require(version == VERSION) { "Unsupported live-voice version $version" }
            val flags = buffer.get().toInt() and 0xff
            val channelTag = buffer.int
            val sourceTag = buffer.int
            val keyTag = buffer.int
            val sessionId = buffer.long
            val sequence = buffer.int
            val cipherLength = buffer.short.toInt() and 0xffff
            require(cipherLength in GCM_TAG_BYTES..MAX_CIPHERTEXT_BYTES) { "Voice ciphertext length is invalid" }
            require(buffer.remaining() == cipherLength) { "Voice packet length/header mismatch" }
            val ciphertext = ByteArray(cipherLength).also(buffer::get)
            return EspMeshLiveVoicePacket(flags, channelTag, sourceTag, keyTag, sessionId, sequence, ciphertext)
        }

        /** 96-bit AES-GCM nonce: random talk-session ID + monotonic frame sequence. */
        fun nonce(sessionId: Long, sequence: Int): ByteArray = ByteBuffer.allocate(12)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(sessionId)
            .putInt(sequence)
            .array()
    }
}

data class EspMeshDecryptedVoiceFrame(
    val flags: Int,
    val channelTag: Int,
    val sourceTag: Int,
    val sessionId: Long,
    val sequence: Int,
    val audioMuLaw: ByteArray
) {
    val isStart: Boolean get() = flags and EspMeshLiveVoicePacket.FLAG_START != 0
    val isEnd: Boolean get() = flags and EspMeshLiveVoicePacket.FLAG_END != 0
}

/** Small deterministic G.711 mu-law codec; ESP nodes never encode/decode audio. */
object EspMeshMuLawCodec {
    private const val BIAS = 0x84
    private const val CLIP = 32635

    fun encode(samples: ShortArray, count: Int = samples.size): ByteArray {
        require(count in 0..samples.size)
        return ByteArray(count) { encodeSample(samples[it]) }
    }

    fun decode(bytes: ByteArray): ShortArray = ShortArray(bytes.size) { decodeSample(bytes[it]) }

    private fun encodeSample(sample: Short): Byte {
        var pcm = sample.toInt()
        val mask: Int
        if (pcm < 0) {
            pcm = -pcm
            mask = 0x7f
        } else {
            mask = 0xff
        }
        if (pcm > CLIP) pcm = CLIP
        pcm += BIAS

        var exponent = 7
        var expMask = 0x4000
        while (exponent > 0 && pcm and expMask == 0) {
            exponent--
            expMask = expMask shr 1
        }
        val mantissa = (pcm shr (exponent + 3)) and 0x0f
        return ((exponent shl 4 or mantissa) xor mask).toByte()
    }

    private fun decodeSample(value: Byte): Short {
        val ulaw = value.toInt().inv() and 0xff
        val sign = ulaw and 0x80
        val exponent = (ulaw shr 4) and 0x07
        val mantissa = ulaw and 0x0f
        var sample = ((mantissa shl 3) + BIAS) shl exponent
        sample -= BIAS
        if (sign != 0) sample = -sample
        return sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
