package com.example.methodmesh.modules.cryptography

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import org.json.JSONObject
import java.io.File

object CryptoScreenSupport {
    private val sensitiveContextKeys = setOf(
        "input_text", "input_uri", "password", "secret", "otpauth_uri",
        "jwk", "signature", "challenge", "account_label"
    )

    fun request(
        method: AbstractCryptoMethod,
        context: CapabilityScreenContext,
        settings: Map<String, String>
    ) = method.request(
        action = method.id,
        context = context.request.invocationContext.asMap(method.id) +
            context.action.settings.filterKeys { it !in sensitiveContextKeys } +
            mapOf("crypto_sensitive_context_scrubbed" to "true"),
        signals = emptyList(),
        inputs = emptyList()
    )

    fun result(
        method: AbstractCryptoMethod,
        context: CapabilityScreenContext,
        values: Map<String, String>
    ): ExecutionResult = method.result(
        request(method, context, values),
        values,
        context.request.invocationContext
    )

    fun succeeded(
        method: AbstractCryptoMethod,
        context: CapabilityScreenContext,
        values: Map<String, String>
    ): ExecutionResult = result(method, context, linkedMapOf(CryptoFields.STATUS to "succeeded") + values)

    fun failed(
        method: AbstractCryptoMethod,
        context: CapabilityScreenContext,
        error: Throwable
    ): ExecutionResult = result(
        method,
        context,
        mapOf(
            CryptoFields.STATUS to "failed",
            CryptoFields.ERROR to (error.message ?: error::class.java.simpleName)
        )
    )

    fun sourceName(resolver: ContentResolver, uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "content"

    fun cacheFile(context: Context, name: String): File {
        val safe = name.replace(Regex("""[\\/:*?\"<>|]"""), "_").ifBlank { "crypto-output" }
        return File(context.cacheDir, safe)
    }

    fun shareableUri(context: Context, file: File): String = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    ).toString()

    fun provenanceValue(
        methodId: String,
        source: ByteArray? = null,
        output: ByteArray? = null,
        format: String? = null,
        protection: String? = null,
        fingerprints: List<String> = emptyList(),
        extra: Map<String, Any?> = emptyMap()
    ): String = CryptoJson.provenance(
        methodId = methodId,
        status = "succeeded",
        sourceSha256 = source?.let { HashEngine.hex(it) },
        outputSha256 = output?.let { HashEngine.hex(it) },
        format = format,
        protection = protection,
        keyFingerprints = fingerprints,
        extra = extra
    )

    fun restoreValues(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        val obj = JSONObject(json)
        return CryptoFields.common.filter(obj::has).associateWith { obj.optString(it, "") }
    }

    fun saveValues(values: Map<String, String>): String = JSONObject(values as Map<*, *>).toString()
}
