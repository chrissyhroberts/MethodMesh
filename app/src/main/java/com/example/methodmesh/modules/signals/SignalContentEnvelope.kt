package com.example.methodmesh.modules.signals

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * End-to-end content envelope carried inside MMS/1.
 *
 * MMS/1 CRC/Reed-Solomon protect transport and reconstruction. This envelope
 * separately carries a SHA-256 digest of the original useful object so the
 * receiver can prove the final reconstructed text/file is byte-for-byte equal
 * to what the sender committed to before transmission.
 */
object SignalContentEnvelope {
    private val MAGIC = byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())
    private const val TYPE_TEXT: Byte = 1
    private const val TYPE_FILE: Byte = 2
    private const val SHA_BYTES = 32
    private const val MAX_METADATA_BYTES = 4096
    const val MAX_FILE_BYTES = 1024 * 1024

    data class Decoded(
        val type: String,
        val payload: ByteArray,
        val text: String?,
        val fileName: String?,
        val mimeType: String?,
        val expectedSha256: String,
        val reconstructedSha256: String,
        val checksumVerified: Boolean
    )

    fun encodeText(text: String): ByteArray = encode(
        type = TYPE_TEXT,
        payload = text.toByteArray(StandardCharsets.UTF_8),
        fileName = "",
        mimeType = "text/plain; charset=utf-8"
    )

    fun encodeFile(bytes: ByteArray, fileName: String, mimeType: String?): ByteArray {
        require(bytes.size <= MAX_FILE_BYTES) { "QR file transfer is limited to 1 MiB in this revision." }
        return encode(TYPE_FILE, bytes, safeFileName(fileName), mimeType.orEmpty().take(255))
    }

    fun decode(bytes: ByteArray): Decoded? {
        if (bytes.size < MAGIC.size + 1 + 2 + 2 + 4 + SHA_BYTES) return null
        if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        return runCatching {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(MAGIC.size); buffer.get(magic)
            val type = buffer.get()
            val nameLength = buffer.short.toInt() and 0xFFFF
            val mimeLength = buffer.short.toInt() and 0xFFFF
            val payloadLength = buffer.int
            require(nameLength + mimeLength <= MAX_METADATA_BYTES)
            require(payloadLength >= 0)
            require(buffer.remaining() == SHA_BYTES + nameLength + mimeLength + payloadLength)
            val expectedSha = ByteArray(SHA_BYTES); buffer.get(expectedSha)
            val nameBytes = ByteArray(nameLength); buffer.get(nameBytes)
            val mimeBytes = ByteArray(mimeLength); buffer.get(mimeBytes)
            val payload = ByteArray(payloadLength); buffer.get(payload)
            val reconstructed = sha256(payload)
            val verified = MessageDigest.isEqual(expectedSha, reconstructed)
            val name = String(nameBytes, StandardCharsets.UTF_8).ifBlank { null }
            val mime = String(mimeBytes, StandardCharsets.UTF_8).ifBlank { null }
            Decoded(
                type = if (type == TYPE_FILE) "file" else "text",
                payload = payload,
                text = if (type == TYPE_TEXT) String(payload, StandardCharsets.UTF_8) else null,
                fileName = name,
                mimeType = mime,
                expectedSha256 = expectedSha.toHex(),
                reconstructedSha256 = reconstructed.toHex(),
                checksumVerified = verified
            )
        }.getOrNull()
    }

    fun sha256Hex(bytes: ByteArray): String = sha256(bytes).toHex()

    private fun encode(type: Byte, payload: ByteArray, fileName: String, mimeType: String): ByteArray {
        val nameBytes = fileName.toByteArray(StandardCharsets.UTF_8)
        val mimeBytes = mimeType.toByteArray(StandardCharsets.UTF_8)
        require(nameBytes.size + mimeBytes.size <= MAX_METADATA_BYTES)
        val digest = sha256(payload)
        val buffer = ByteBuffer.allocate(MAGIC.size + 1 + 2 + 2 + 4 + SHA_BYTES + nameBytes.size + mimeBytes.size + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.put(MAGIC)
        buffer.put(type)
        buffer.putShort(nameBytes.size.toShort())
        buffer.putShort(mimeBytes.size.toShort())
        buffer.putInt(payload.size)
        buffer.put(digest)
        buffer.put(nameBytes)
        buffer.put(mimeBytes)
        buffer.put(payload)
        return buffer.array()
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun safeFileName(raw: String): String = raw
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        .trim()
        .take(180)
        .ifBlank { "received_file.bin" }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
