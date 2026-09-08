package com.example.methodmesh.modules.webactions

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class WebActionTransactionState {
    PREPARING,
    WAITING,
    CALLBACK_RECEIVED,
    FAILED
}

data class WebActionTransaction(
    val id: String,
    val methodId: String,
    val originalUrl: String,
    val launchHost: String,
    val callbackParameter: String,
    val formId: String,
    val serverUrl: String,
    val startedTimeIso: String,
    val completedTimeIso: String,
    val state: WebActionTransactionState,
    val error: String,
    val allowInsecureHttp: Boolean,
    val completionSignal: String,
    internal val callbackToken: String,
    internal val launchUrl: String
) {
    val startedEpochMillis: Long
        get() = runCatching { Instant.parse(startedTimeIso).toEpochMilli() }.getOrDefault(0L)

    val completedEpochMillis: Long
        get() = runCatching { Instant.parse(completedTimeIso).toEpochMilli() }.getOrDefault(0L)

    val durationMillis: Long
        get() = if (completedEpochMillis > 0L && startedEpochMillis > 0L) {
            (completedEpochMillis - startedEpochMillis).coerceAtLeast(0L)
        } else {
            0L
        }
}

object WebActionsRepository {
    private const val PREFS = "methodmesh_web_actions_active_sessions_v1"
    private const val KEY_ALIAS = "methodmesh_web_actions_session_key_v1"
    private const val CALLBACK_HOST = "methodmesh.invalid"
    private const val CALLBACK_PREFIX = "/web/complete/"
    private const val SESSION_PREFIX = "txn_"
    private const val GCM_TAG_BITS = 128
    private val secureRandom = SecureRandom()

    @Synchronized
    fun create(
        context: Context,
        methodId: String,
        originalUrl: String = "",
        launchHost: String = "",
        callbackParameter: String = "",
        formId: String = "",
        serverUrl: String = "",
        allowInsecureHttp: Boolean = false
    ): WebActionTransaction {
        val transaction = WebActionTransaction(
            id = UUID.randomUUID().toString(),
            methodId = methodId,
            originalUrl = originalUrl,
            launchHost = launchHost,
            callbackParameter = callbackParameter,
            formId = formId,
            serverUrl = serverUrl,
            startedTimeIso = Instant.now().toString(),
            completedTimeIso = "",
            state = WebActionTransactionState.PREPARING,
            error = "",
            allowInsecureHttp = allowInsecureHttp,
            completionSignal = "",
            callbackToken = newToken(),
            launchUrl = ""
        )
        write(context, transaction)
        return transaction
    }

    @Synchronized
    fun get(context: Context, id: String): WebActionTransaction? {
        if (id.isBlank()) return null
        val raw = prefs(context).getString(SESSION_PREFIX + id, null) ?: return null
        return decode(context, raw)
    }

    @Synchronized
    fun newestPending(context: Context, methodId: String): WebActionTransaction? =
        all(context)
            .filter { it.methodId == methodId && it.state != WebActionTransactionState.FAILED }
            .maxByOrNull { it.startedEpochMillis }

    @Synchronized
    fun all(context: Context): List<WebActionTransaction> = prefs(context).all
        .asSequence()
        .filter { (key, value) -> key.startsWith(SESSION_PREFIX) && value is String }
        .mapNotNull { (_, value) -> decode(context, value as String) }
        .toList()

    @Synchronized
    fun setLaunch(
        context: Context,
        id: String,
        launchUrl: String,
        launchHost: String
    ): WebActionTransaction? = update(context, id) { current ->
        current.copy(
            launchUrl = launchUrl,
            launchHost = launchHost,
            state = WebActionTransactionState.WAITING,
            error = ""
        )
    }

    @Synchronized
    fun markWaiting(context: Context, id: String): WebActionTransaction? = update(context, id) {
        it.copy(state = WebActionTransactionState.WAITING, error = "")
    }

    @Synchronized
    fun markFailed(context: Context, id: String, error: String): WebActionTransaction? = update(context, id) {
        it.copy(state = WebActionTransactionState.FAILED, error = error.take(1000))
    }

    @Synchronized
    fun consumeCallback(context: Context, id: String, callbackUrl: String): WebActionTransaction? {
        val current = get(context, id) ?: return null
        if (current.state == WebActionTransactionState.CALLBACK_RECEIVED) return null
        if (current.state != WebActionTransactionState.WAITING) return null
        if (!matchesCallback(current, callbackUrl)) return null
        return update(context, id) {
            it.copy(
                state = WebActionTransactionState.CALLBACK_RECEIVED,
                completedTimeIso = Instant.now().toString(),
                completionSignal = "central_return_url",
                error = ""
            )
        }
    }

    @Synchronized
    fun markProviderCompletion(context: Context, id: String, signal: String): WebActionTransaction? {
        val current = get(context, id) ?: return null
        if (current.state != WebActionTransactionState.WAITING) return null
        return update(context, id) {
            it.copy(
                state = WebActionTransactionState.CALLBACK_RECEIVED,
                completedTimeIso = Instant.now().toString(),
                completionSignal = signal.take(80),
                error = ""
            )
        }
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        if (id.isNotBlank()) prefs(context).edit().remove(SESSION_PREFIX + id).apply()
    }

    fun callbackUrl(transaction: WebActionTransaction): String =
        "https://$CALLBACK_HOST$CALLBACK_PREFIX${transaction.id}?k=${transaction.callbackToken}"

    fun callbackUrl(context: Context, id: String): String? = get(context, id)?.let(::callbackUrl)

    private fun matchesCallback(transaction: WebActionTransaction, rawUrl: String): Boolean {
        val uri = runCatching { android.net.Uri.parse(rawUrl) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (!uri.host.equals(CALLBACK_HOST, ignoreCase = true)) return false
        if (uri.path != CALLBACK_PREFIX + transaction.id) return false
        val supplied = uri.getQueryParameter("k").orEmpty()
        if (supplied.isBlank()) return false
        return MessageDigest.isEqual(
            supplied.toByteArray(Charsets.UTF_8),
            transaction.callbackToken.toByteArray(Charsets.UTF_8)
        )
    }

    private fun update(
        context: Context,
        id: String,
        transform: (WebActionTransaction) -> WebActionTransaction
    ): WebActionTransaction? {
        val current = get(context, id) ?: return null
        val updated = transform(current)
        write(context, updated)
        return updated
    }

    private fun write(context: Context, transaction: WebActionTransaction) {
        val publicJson = JSONObject().apply {
            put("id", transaction.id)
            put("method_id", transaction.methodId)
            put("launch_host", transaction.launchHost)
            put("callback_parameter", transaction.callbackParameter)
            put("form_id", transaction.formId)
            put("server_url", transaction.serverUrl)
            put("started_time_iso", transaction.startedTimeIso)
            put("completed_time_iso", transaction.completedTimeIso)
            put("state", transaction.state.name)
            put("error", transaction.error)
            put("allow_insecure_http", transaction.allowInsecureHttp)
            put("completion_signal", transaction.completionSignal)
            put(
                "secret",
                encrypt(
                    JSONObject().apply {
                        put("callback_token", transaction.callbackToken)
                        put("launch_url", transaction.launchUrl)
                        put("original_url", transaction.originalUrl)
                    }.toString()
                )
            )
        }
        prefs(context).edit().putString(SESSION_PREFIX + transaction.id, publicJson.toString()).apply()
    }

    private fun decode(context: Context, raw: String): WebActionTransaction? = runCatching {
        val json = JSONObject(raw)
        val secret = JSONObject(decrypt(json.getString("secret")))
        WebActionTransaction(
            id = json.getString("id"),
            methodId = json.getString("method_id"),
            originalUrl = secret.optString("original_url"),
            launchHost = json.optString("launch_host"),
            callbackParameter = json.optString("callback_parameter"),
            formId = json.optString("form_id"),
            serverUrl = json.optString("server_url"),
            startedTimeIso = json.optString("started_time_iso"),
            completedTimeIso = json.optString("completed_time_iso"),
            state = runCatching { WebActionTransactionState.valueOf(json.optString("state")) }
                .getOrDefault(WebActionTransactionState.FAILED),
            error = json.optString("error"),
            allowInsecureHttp = json.optBoolean("allow_insecure_http", false),
            completionSignal = json.optString("completion_signal"),
            callbackToken = secret.getString("callback_token"),
            launchUrl = secret.optString("launch_url")
        )
    }.getOrNull()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun newToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val blob = ByteArray(1 + cipher.iv.size + cipherText.size)
        blob[0] = cipher.iv.size.toByte()
        System.arraycopy(cipher.iv, 0, blob, 1, cipher.iv.size)
        System.arraycopy(cipherText, 0, blob, 1 + cipher.iv.size, cipherText.size)
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val blob = Base64.decode(encoded, Base64.DEFAULT)
        require(blob.isNotEmpty()) { "Encrypted web session is empty." }
        val ivSize = blob[0].toInt() and 0xff
        require(ivSize in 12..32 && blob.size > 1 + ivSize) { "Encrypted web session is invalid." }
        val iv = blob.copyOfRange(1, 1 + ivSize)
        val cipherText = blob.copyOfRange(1 + ivSize, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
