package com.example.methodmesh.modules.espmesh

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.methodmesh.core.transport.MethodMeshTransportEnvelope
import com.example.methodmesh.core.transport.TransportEndpoint
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Phone-to-phone E2E envelope. ESP gateways may route/store this object but never
 * receive the group key and therefore never see plaintext MethodMesh payloads.
 */
data class EspMeshSecureWire(
    val version: Int = VERSION,
    val messageId: String,
    val keyId: String,
    val originPhoneId: String,
    val source: TransportEndpoint,
    val destination: TransportEndpoint,
    val createdAt: Long,
    val expiresAt: Long?,
    val ttl: Int,
    val nonce: String,
    val ciphertext: String
) {
    init {
        require(version == VERSION)
        require(messageId.isNotBlank() && messageId.length <= 128)
        require(keyId.matches(Regex("[0-9a-f]{16}")))
        require(originPhoneId.isNotBlank() && originPhoneId.length <= 128)
        require(ttl in 0..32)
    }

    fun headerJson(): JSONObject = JSONObject().apply {
        put("v", version)
        put("id", messageId)
        put("kid", keyId)
        put("op", originPhoneId)
        put("sk", source.kind)
        put("s", source.id)
        put("dk", destination.kind)
        put("d", destination.id)
        put("c", createdAt)
        putOpt("x", expiresAt)
        put("ttl", ttl)
    }

    /** Canonical length-prefixed header encoding used as AEAD associated data. */
    fun aad(): ByteArray {
        fun part(value: String): String = "${value.toByteArray(Charsets.UTF_8).size}:$value"
        return listOf(
            version.toString(), messageId, keyId, originPhoneId, source.kind, source.id, destination.kind, destination.id,
            createdAt.toString(), expiresAt?.toString().orEmpty(), ttl.toString()
        ).joinToString("|") { part(it) }.toByteArray(Charsets.UTF_8)
    }

    fun toJson(): JSONObject = headerJson().apply {
        put("n", nonce)
        put("ct", ciphertext)
    }

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = expiresAt?.let { it <= now } == true

    companion object {
        const val VERSION = 1
        fun fromJson(root: JSONObject): EspMeshSecureWire = EspMeshSecureWire(
            version = root.getInt("v"),
            messageId = root.getString("id"),
            keyId = root.getString("kid"),
            originPhoneId = root.getString("op"),
            source = TransportEndpoint(root.getString("sk"), root.getString("s")),
            destination = TransportEndpoint(root.getString("dk"), root.getString("d")),
            createdAt = root.getLong("c"),
            expiresAt = root.optLong("x").takeIf { root.has("x") },
            ttl = root.optInt("ttl", 8),
            nonce = root.getString("n"),
            ciphertext = root.getString("ct")
        )
    }
}

/**
 * E2E group key storage. The group key is exportable only on the phone (for
 * enrolling another phone) and is wrapped at rest by a non-exportable Android
 * Keystore AES key. It is never included in BLE CONFIG or ESP-NOW traffic.
 */
class EspMeshCryptoManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    @Volatile private var cachedVoiceKeyId: String = ""
    @Volatile private var cachedVoiceKey: ByteArray? = null

    fun hasGroupKey(): Boolean = prefs.contains(KEY_WRAPPED) && runCatching { groupKey() }.isSuccess
    fun keyId(): String = prefs.getString(KEY_ID, "").orEmpty()

    fun generateAndStore(): String {
        val raw = ByteArray(32).also(SecureRandom()::nextBytes)
        storeRaw(raw)
        return encode(raw)
    }

    fun importAndStore(encoded: String): String {
        val raw = decode(encoded.trim())
        require(raw.size == 32) { "E2E group key must decode to exactly 32 bytes" }
        storeRaw(raw)
        return keyId()
    }

    /** Explicitly user-facing export used to enroll another phone. */
    fun exportGroupKey(): String = encode(groupKey())

    fun clear() {
        prefs.edit().remove(KEY_WRAPPED).remove(KEY_IV).remove(KEY_ID).apply()
        invalidateVoiceKeyCache()
    }

    fun encrypt(envelope: MethodMeshTransportEnvelope, originPhoneId: String, ttl: Int = 8): EspMeshSecureWire {
        envelope.validate()
        val key = groupKey()
        val wireBase = EspMeshSecureWire(
            messageId = envelope.messageId,
            keyId = keyIdFor(key),
            originPhoneId = originPhoneId,
            source = envelope.source,
            destination = envelope.destination,
            createdAt = envelope.createdAt,
            expiresAt = envelope.expiresAt,
            ttl = ttl.coerceIn(1, 32),
            nonce = "",
            ciphertext = ""
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        cipher.updateAAD(wireBase.aad())
        val encrypted = cipher.doFinal(envelope.toJson().toString().toByteArray(Charsets.UTF_8))
        return wireBase.copy(nonce = encode(cipher.iv), ciphertext = encode(encrypted))
    }

    fun decrypt(wire: EspMeshSecureWire): MethodMeshTransportEnvelope {
        require(!wire.isExpired()) { "Encrypted mesh message has expired" }
        val key = groupKey()
        require(wire.keyId == keyIdFor(key)) { "E2E key ID does not match this phone" }
        val iv = decode(wire.nonce)
        require(iv.size == 12) { "Invalid AES-GCM nonce" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(wire.aad())
        val plain = cipher.doFinal(decode(wire.ciphertext))
        val envelope = MethodMeshTransportEnvelope.fromJson(JSONObject(plain.toString(Charsets.UTF_8)))
        envelope.validate()
        require(envelope.messageId == wire.messageId) { "Encrypted message ID/header mismatch" }
        require(envelope.source == wire.source) { "Encrypted source/header mismatch" }
        require(envelope.destination == wire.destination) { "Encrypted destination/header mismatch" }
        require(envelope.createdAt == wire.createdAt) { "Encrypted timestamp/header mismatch" }
        require(envelope.expiresAt == wire.expiresAt) { "Encrypted expiry/header mismatch" }
        return envelope
    }

    /**
     * Live voice uses a domain-separated key derived from the E2E group key.
     * ESP nodes receive only the resulting compact ciphertext packet.
     */
    fun voiceChannelTag(channel: String): Int = tag32(voiceKey(), "channel:${normalizeVoiceChannel(channel)}")

    fun voiceSourceTag(phoneId: String): Int = tag32(voiceKey(), "source:$phoneId")

    fun voiceKeyTag(): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(voiceKey())
        return int32(digest)
    }

    fun encryptVoiceFrame(
        channel: String,
        phoneId: String,
        sessionId: Long,
        sequence: Int,
        flags: Int,
        audioMuLaw: ByteArray
    ): ByteArray {
        require(sequence >= 0) { "Voice sequence must be non-negative" }
        require(audioMuLaw.size <= EspMeshLiveVoicePacket.AUDIO_FRAME_BYTES) { "Voice frame is too large" }
        if (flags and EspMeshLiveVoicePacket.FLAG_END == 0) {
            require(audioMuLaw.size == EspMeshLiveVoicePacket.AUDIO_FRAME_BYTES) { "Live voice audio frames must be exactly 20 ms" }
        }
        // Live voice runs at 50 frames/s. Resolve/unwrap the group key once per
        // in-memory key epoch, then derive all tags from that cached voice key.
        // The derived key is never persisted and is invalidated on key change.
        val key = voiceKey()
        val cipherLength = audioMuLaw.size + EspMeshLiveVoicePacket.GCM_TAG_BYTES
        val base = EspMeshLiveVoicePacket(
            flags = flags,
            channelTag = tag32(key, "channel:${normalizeVoiceChannel(channel)}"),
            sourceTag = tag32(key, "source:$phoneId"),
            keyTag = int32(MessageDigest.getInstance("SHA-256").digest(key)),
            sessionId = sessionId,
            sequence = sequence,
            ciphertext = ByteArray(cipherLength)
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, EspMeshLiveVoicePacket.nonce(sessionId, sequence))
        )
        cipher.updateAAD(base.headerBytes())
        val encrypted = cipher.doFinal(audioMuLaw)
        return base.copy(ciphertext = encrypted).encode()
    }

    fun decryptVoiceFrame(packetBytes: ByteArray): EspMeshDecryptedVoiceFrame {
        val packet = EspMeshLiveVoicePacket.decode(packetBytes)
        val key = voiceKey()
        require(packet.keyTag == int32(MessageDigest.getInstance("SHA-256").digest(key))) { "Live voice key ID does not match this phone" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, EspMeshLiveVoicePacket.nonce(packet.sessionId, packet.sequence))
        )
        cipher.updateAAD(packet.headerBytes())
        val plain = cipher.doFinal(packet.ciphertext)
        if (!packet.isEnd()) require(plain.size == EspMeshLiveVoicePacket.AUDIO_FRAME_BYTES) { "Live voice frame length is invalid" }
        return EspMeshDecryptedVoiceFrame(
            flags = packet.flags,
            channelTag = packet.channelTag,
            sourceTag = packet.sourceTag,
            sessionId = packet.sessionId,
            sequence = packet.sequence,
            audioMuLaw = plain
        )
    }

    private fun voiceKey(): ByteArray {
        val currentId = keyId()
        require(currentId.isNotBlank()) { "No ESP mesh E2E group key is configured" }
        val cached = cachedVoiceKey
        if (cached != null && cachedVoiceKeyId == currentId) return cached
        return synchronized(this) {
            val again = cachedVoiceKey
            if (again != null && cachedVoiceKeyId == currentId) again
            else {
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(groupKey(), "HmacSHA256"))
                mac.doFinal("MethodMesh ESP mesh live voice v1".toByteArray(Charsets.UTF_8)).also { derived ->
                    cachedVoiceKeyId = currentId
                    cachedVoiceKey = derived
                }
            }
        }
    }

    private fun invalidateVoiceKeyCache() {
        cachedVoiceKey?.fill(0)
        cachedVoiceKey = null
        cachedVoiceKeyId = ""
    }

    private fun tag32(key: ByteArray, text: String): Int {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return int32(mac.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    private fun int32(bytes: ByteArray): Int {
        require(bytes.size >= 4)
        return ((bytes[0].toInt() and 0xff) shl 24) or
            ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or
            (bytes[3].toInt() and 0xff)
    }

    private fun normalizeVoiceChannel(channel: String): String = channel.trim().lowercase().ifBlank { "field-group" }.take(64)

    private fun storeRaw(raw: ByteArray) {
        require(raw.size == 32)
        invalidateVoiceKeyCache()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val wrapped = cipher.doFinal(raw)
        prefs.edit()
            .putString(KEY_WRAPPED, encode(wrapped))
            .putString(KEY_IV, encode(cipher.iv))
            .putString(KEY_ID, keyIdFor(raw))
            .apply()
    }

    private fun groupKey(): ByteArray {
        val wrapped = prefs.getString(KEY_WRAPPED, null) ?: error("No ESP mesh E2E group key is configured")
        val iv = prefs.getString(KEY_IV, null) ?: error("ESP mesh E2E key wrapper metadata is missing")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, decode(iv)))
        val raw = cipher.doFinal(decode(wrapped))
        require(raw.size == 32) { "Stored ESP mesh E2E key is invalid" }
        return raw
    }

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(WRAP_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAP_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "methodmesh_espmesh_e2e"
        private const val KEY_WRAPPED = "group_key_wrapped"
        private const val KEY_IV = "group_key_wrap_iv"
        private const val KEY_ID = "group_key_id"
        private const val WRAP_ALIAS = "methodmesh_espmesh_group_wrap_v1"

        fun keyIdFor(raw: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(raw).take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }

        fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        fun decode(text: String): ByteArray = Base64.decode(text, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
