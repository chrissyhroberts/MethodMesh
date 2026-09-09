package com.example.methodmesh.modules.signals

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Optional transparent DEFLATE layer for slow acoustic text links.
 *
 * MMS/1 still supplies Reed-Solomon recovery and CRC integrity. This layer only
 * reduces the number of useful bytes before MMS/1 is built. If compression is
 * not actually smaller, the original UTF-8 bytes are sent unchanged so short
 * messages do not pay an envelope penalty.
 */
object SignalAcousticPayloadCodec {
    private val MAGIC = byteArrayOf(0xF3.toByte(), 'M'.code.toByte(), 'M'.code.toByte(), 'Z'.code.toByte())
    private const val HEADER_BYTES = 8
    private const val MAX_TEXT_BYTES = 256 * 1024

    data class Encoded(
        val bytes: ByteArray,
        val originalBytes: Int,
        val wireBytes: Int,
        val compressed: Boolean
    ) {
        val ratio: Double get() = if (originalBytes <= 0) 1.0 else wireBytes.toDouble() / originalBytes.toDouble()
    }

    fun encodeText(text: String): Encoded {
        val raw = text.toByteArray(StandardCharsets.UTF_8)
        require(raw.size <= MAX_TEXT_BYTES) { "Acoustic text is limited to 256 KiB." }
        if (raw.size < 24) return Encoded(raw, raw.size, raw.size, false)

        val deflater = Deflater(6, true)
        val compressed = try {
            deflater.setInput(raw)
            deflater.finish()
            val out = ByteArrayOutputStream(raw.size)
            val buffer = ByteArray(2048)
            while (!deflater.finished()) {
                val n = deflater.deflate(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }

        // Require a meaningful gain after the 8-byte compression header.
        if (compressed.size + HEADER_BYTES >= raw.size - 4) return Encoded(raw, raw.size, raw.size, false)
        val wrapped = ByteBuffer.allocate(HEADER_BYTES + compressed.size).order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC)
            .putInt(raw.size)
            .put(compressed)
            .array()
        return Encoded(wrapped, raw.size, wrapped.size, true)
    }

    fun decodeText(bytes: ByteArray): String? {
        if (!isCompressed(bytes)) return runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull()
        if (bytes.size < HEADER_BYTES) return null
        return runCatching {
            val expected = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.BIG_ENDIAN).int
            require(expected in 0..MAX_TEXT_BYTES)
            val inflater = Inflater(true)
            val raw = try {
                inflater.setInput(bytes, HEADER_BYTES, bytes.size - HEADER_BYTES)
                val out = ByteArray(expected)
                var offset = 0
                while (!inflater.finished() && offset < out.size) {
                    val n = inflater.inflate(out, offset, out.size - offset)
                    if (n <= 0) {
                        if (inflater.needsInput() || inflater.needsDictionary()) break
                        error("Compressed acoustic payload stalled during inflate.")
                    }
                    offset += n
                }
                require(inflater.finished() && offset == expected)
                out
            } finally {
                inflater.end()
            }
            String(raw, StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun isCompressed(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /** Balance padding against per-frame physical header overhead. */
    fun recommendedShardBytes(wireBytes: Int): Int = when {
        wireBytes <= 24 -> 24
        wireBytes <= 96 -> 48
        wireBytes <= 384 -> 64
        else -> 96
    }
}
