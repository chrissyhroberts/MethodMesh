package com.example.methodmesh.ui.kobo

import android.content.Context
import android.util.Base64
import com.example.methodmesh.ui.odk.OdkTemplateDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class KoboApiException(
    val statusCode: Int,
    message: String,
    val details: String? = null,
    val rawResponse: String? = null,
    val requestMethod: String? = null,
    val requestPath: String? = null,
    val errorType: String? = null,
    val compatibilityPlan: KoboXlsFormCompatibility.Plan? = null
) : IllegalStateException(message)

object KoboClient {
    private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    suspend fun login(serverUrl: String, username: String, password: String): KoboSession = withContext(Dispatchers.IO) {
        val server = KoboConnectionRepository.normalizeServerUrl(serverUrl)
        val credentials = Base64.encodeToString("${username.trim()}:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val json = requestJson(
            server, "GET", "/token/?format=json",
            authorization = "Basic $credentials"
        )
        KoboSession(json.getString("token"))
    }

    suspend fun currentUser(serverUrl: String, token: String): JSONObject = withContext(Dispatchers.IO) {
        requestJson(KoboConnectionRepository.normalizeServerUrl(serverUrl), "GET", "/me/", token = token)
    }

    suspend fun deploy(
        context: Context,
        profile: KoboProfile,
        token: String,
        template: OdkTemplateDescriptor
    ): KoboTemplateDeployment = deployInternal(context, profile, token, template, forceCompatibilityCopy = false)

    suspend fun deployCompatibleCopy(
        context: Context,
        profile: KoboProfile,
        token: String,
        template: OdkTemplateDescriptor
    ): KoboTemplateDeployment = deployInternal(context, profile, token, template, forceCompatibilityCopy = true)

    private suspend fun deployInternal(
        context: Context,
        profile: KoboProfile,
        token: String,
        template: OdkTemplateDescriptor,
        forceCompatibilityCopy: Boolean
    ): KoboTemplateDeployment = withContext(Dispatchers.IO) {
        val server = KoboConnectionRepository.normalizeServerUrl(profile.serverUrl)
        val blockingValidationErrors = template.validationIssues.filter {
            it.severity == com.example.methodmesh.ui.odk.OdkValidationSeverity.Error
        }
        require(blockingValidationErrors.isEmpty()) {
            "XLSForm has ${blockingValidationErrors.size} validation error${if (blockingValidationErrors.size == 1) "" else "s"}; warnings and style findings are non-blocking."
        }

        val canonicalBytes = context.assets.open(template.assetPath).use { it.readBytes() }
        val digest = sha256(canonicalBytes)
        var existing = KoboDeploymentStore.get(context, server, template.id)

        if (existing != null && !assetExists(server, token, existing.assetUid)) {
            KoboDeploymentStore.remove(context, server, template.id)
            existing = null
        }

        val compatibilityRequested = forceCompatibilityCopy || existing?.namespacedCompatibilityCopy == true
        val requestedPlan = if (compatibilityRequested) {
            runCatching { KoboXlsFormCompatibility.plan(canonicalBytes) }.getOrNull()
        } else null
        // If a later canonical revision removes the Kobo-only collision, retire the
        // compatibility transform automatically and update the same remote asset.
        val useCompatibilityCopy = compatibilityRequested && requestedPlan?.collisions?.isNotEmpty() == true
        val uploadBytes = if (useCompatibilityCopy) {
            runCatching { KoboXlsFormCompatibility.repair(canonicalBytes).bytes }.getOrElse { repairError ->
                val plan = requestedPlan ?: runCatching { KoboXlsFormCompatibility.plan(canonicalBytes) }.getOrNull()
                throw KoboApiException(
                    statusCode = 422,
                    message = "MethodMesh could not safely create a Kobo-compatible copy.",
                    details = plan?.guidance() ?: repairError.message,
                    errorType = "KoboCompatibilityRepairUnsafe",
                    compatibilityPlan = plan
                )
            }
        } else canonicalBytes

        fun attachCompatibilityPlan(error: KoboApiException): KoboApiException {
            if (!error.errorType.equals("DuplicateNameException", ignoreCase = true)) return error
            val plan = runCatching { KoboXlsFormCompatibility.plan(canonicalBytes) }.getOrNull() ?: return error
            return KoboApiException(
                statusCode = error.statusCode,
                message = error.message ?: "Kobo XLSForm import failed.",
                details = error.details,
                rawResponse = error.rawResponse,
                requestMethod = error.requestMethod,
                requestPath = error.requestPath,
                errorType = error.errorType,
                compatibilityPlan = plan
            )
        }

        val shouldUpload = existing == null || existing?.localSha256 != digest ||
            forceCompatibilityCopy || existing?.namespacedCompatibilityCopy != useCompatibilityCopy
        val current = existing

        val uid = try {
            if (current == null) {
                importXlsForm(server, token, template.sourceFileName, uploadBytes, destination = null)
            } else {
                if (shouldUpload) {
                    importXlsForm(
                        server,
                        token,
                        template.sourceFileName,
                        uploadBytes,
                        destination = "/api/v2/assets/${current.assetUid}/"
                    )
                }
                current.assetUid
            }
        } catch (error: KoboApiException) {
            throw attachCompatibilityPlan(error)
        }

        val asset = requestJson(server, "GET", "/api/v2/assets/$uid/?format=json", token = token)
        val versionId = asset.optString("version_id")
        val fields = linkedMapOf("active" to "true")
        if (versionId.isNotBlank()) fields["version_id"] = versionId
        requestForm(server, "POST", "/api/v2/assets/$uid/deployment/?format=json", token, fields)

        KoboTemplateDeployment(
            serverUrl = server,
            templateId = template.id,
            assetUid = uid,
            localSha256 = digest,
            active = true,
            namespacedCompatibilityCopy = useCompatibilityCopy,
            lastSyncedAt = Instant.now().toString()
        ).also { KoboDeploymentStore.put(context, it) }
    }

    suspend fun deactivate(
        context: Context,
        profile: KoboProfile,
        token: String,
        template: OdkTemplateDescriptor
    ): KoboTemplateDeployment? = withContext(Dispatchers.IO) {
        val server = KoboConnectionRepository.normalizeServerUrl(profile.serverUrl)
        val existing = KoboDeploymentStore.get(context, server, template.id) ?: return@withContext null
        // POST is the deploy/create action. Kobo v2 exposes PATCH for changing an
        // existing deployment. Using POST with active=false can leave the deployment
        // active, which makes the UI checkbox appear impossible to untick.
        requestJson(
            server = server,
            method = "PATCH",
            path = "/api/v2/assets/${existing.assetUid}/deployment/?format=json",
            token = token,
            body = JSONObject().put("active", false).toString().toByteArray(Charsets.UTF_8),
            contentType = "application/json"
        )
        val remote = requestJson(
            server, "GET", "/api/v2/assets/${existing.assetUid}/deployment/?format=json", token = token
        )
        if (remote.optBoolean("active", true)) {
            throw KoboApiException(
                statusCode = 409,
                message = "KoboToolbox still reports this deployment as active after the deactivate request.",
                details = remote.toString(2),
                requestMethod = "PATCH",
                requestPath = "/api/v2/assets/${existing.assetUid}/deployment/"
            )
        }
        existing.copy(active = false, lastSyncedAt = Instant.now().toString()).also {
            KoboDeploymentStore.put(context, it)
        }
    }

    suspend fun removeRemote(
        context: Context,
        profile: KoboProfile,
        token: String,
        template: OdkTemplateDescriptor
    ) = withContext(Dispatchers.IO) {
        val server = KoboConnectionRepository.normalizeServerUrl(profile.serverUrl)
        val existing = KoboDeploymentStore.get(context, server, template.id)
            ?: error("This template is not mapped to a KoboToolbox project on this server.")
        request(server, "DELETE", "/api/v2/assets/${existing.assetUid}/", token = token)
        KoboDeploymentStore.remove(context, server, template.id)
    }

    suspend fun remoteSubmissionCount(
        context: Context,
        profile: KoboProfile,
        token: String,
        template: OdkTemplateDescriptor
    ): Int = withContext(Dispatchers.IO) {
        val server = KoboConnectionRepository.normalizeServerUrl(profile.serverUrl)
        val existing = KoboDeploymentStore.get(context, server, template.id) ?: return@withContext 0
        runCatching {
            requestJson(server, "GET", "/api/v2/assets/${existing.assetUid}/?format=json", token = token)
                .optInt("deployment__submission_count", 0)
        }.getOrDefault(0)
    }

    fun localDigest(context: Context, template: OdkTemplateDescriptor): String =
        context.assets.open(template.assetPath).use { sha256(it.readBytes()) }

    private fun assetExists(server: String, token: String, uid: String): Boolean = runCatching {
        requestJson(server, "GET", "/api/v2/assets/$uid/?format=json", token = token)
        true
    }.getOrElse { error ->
        if (error is KoboApiException && error.statusCode == 404) false else throw error
    }

    private suspend fun importXlsForm(
        server: String,
        token: String,
        filename: String,
        bytes: ByteArray,
        destination: String?
    ): String {
        val parts = mutableListOf(
            MultipartPart.Field("library", "false"),
            MultipartPart.File("file", filename.ifBlank { "methodmesh_form.xlsx" }, XLSX_MIME, bytes)
        )
        destination?.let { parts += MultipartPart.Field("destination", it) }
        val started = requestMultipart(server, "POST", "/api/v2/imports/", token, parts)
        val importUid = started.optString("uid").ifBlank { error("Kobo import did not return an import UID.") }

        repeat(40) {
            val state = requestJson(server, "GET", "/api/v2/imports/$importUid/", token = token)
            when (state.optString("status").lowercase()) {
                "complete", "completed", "success" -> {
                    return extractAssetUid(state) ?: destination?.trim('/')?.substringAfterLast('/')
                        ?: error("Kobo import completed but no asset UID was returned.")
                }
                "failed", "error" -> {
                    // Kobo can surface advisory converter warnings while still returning a
                    // created/updated asset. MethodMesh forms are disposable examples, so if
                    // the server confirms the asset exists and the response is warning-only,
                    // continue. Never manufacture success when no asset was produced.
                    val recoveredUid = extractAssetUid(state)
                    if (recoveredUid != null && isWarningOnlyImportFailure(state)) return recoveredUid

                    val details = collectKoboErrorDetails(state)
                    val message = firstKoboError(state) ?: "Kobo XLSForm import failed."
                    throw KoboApiException(
                        statusCode = 422,
                        message = message,
                        details = details,
                        rawResponse = state.toString(2),
                        requestMethod = "GET",
                        requestPath = "/api/v2/imports/$importUid/",
                        errorType = koboErrorType(state)
                    )
                }
            }
            delay(500)
        }
        throw KoboApiException(408, "Kobo XLSForm import did not finish in time.")
    }


    private fun isWarningOnlyImportFailure(root: JSONObject): Boolean {
        val messages = root.optJSONObject("messages")
        val explicitWarnings = root.has("warnings") || messages?.has("warnings") == true
        val explicitErrors = root.has("errors") || root.has("validation_errors") ||
            messages?.has("errors") == true || messages?.has("validation_errors") == true
        if (explicitErrors) return false

        val errorType = messages?.optString("error_type").orEmpty().lowercase()
        if (errorType.contains("syntax") || errorType.contains("parse") || errorType.contains("invalid")) return false

        val advisoryText = listOf(
            root.optString("warning"),
            root.optString("message"),
            messages?.optString("warning").orEmpty(),
            messages?.optString("error").orEmpty()
        ).joinToString(" ").lowercase()

        return explicitWarnings || ("warning" in advisoryText &&
            "syntax error" !in advisoryText &&
            "parse error" !in advisoryText &&
            "invalid xform" !in advisoryText)
    }

    private fun extractAssetUid(root: JSONObject): String? {
        root.optString("asset_uid").takeIf(String::isNotBlank)?.let { return it }
        root.optString("uid").takeIf { it.isNotBlank() && root.optString("status").isBlank() }?.let { return it }
        val messages = root.optJSONObject("messages")
        val arrays = listOf("created", "updated", "modified")
        arrays.forEach { key ->
            val arr = messages?.optJSONArray(key) ?: return@forEach
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                item.optString("uid").takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return null
    }

    private sealed class MultipartPart {
        data class Field(val name: String, val value: String) : MultipartPart()
        data class File(val name: String, val filename: String, val contentType: String, val bytes: ByteArray) : MultipartPart()
    }

    private fun requestMultipart(
        server: String,
        method: String,
        path: String,
        token: String,
        parts: List<MultipartPart>
    ): JSONObject {
        val boundary = "----MethodMesh${UUID.randomUUID().toString().replace("-", "")}" 
        val crlf = "\r\n"
        val body = ByteArrayOutputStream().apply {
            parts.forEach { part ->
                write("--$boundary$crlf".toByteArray())
                when (part) {
                    is MultipartPart.Field -> {
                        write("Content-Disposition: form-data; name=\"${part.name}\"$crlf$crlf".toByteArray())
                        write(part.value.toByteArray())
                        write(crlf.toByteArray())
                    }
                    is MultipartPart.File -> {
                        write("Content-Disposition: form-data; name=\"${part.name}\"; filename=\"${part.filename.replace("\"", "")}\"$crlf".toByteArray())
                        write("Content-Type: ${part.contentType}$crlf$crlf".toByteArray())
                        write(part.bytes)
                        write(crlf.toByteArray())
                    }
                }
            }
            write("--$boundary--$crlf".toByteArray())
        }.toByteArray()
        return requestJson(
            server, method, path, token = token, body = body,
            contentType = "multipart/form-data; boundary=$boundary"
        )
    }

    private fun requestForm(
        server: String,
        method: String,
        path: String,
        token: String,
        fields: Map<String, String>
    ): JSONObject {
        val body = fields.entries.joinToString("&") {
            "${urlEncode(it.key)}=${urlEncode(it.value)}"
        }.toByteArray(Charsets.UTF_8)
        return requestJson(
            server, method, path, token = token, body = body,
            contentType = "application/x-www-form-urlencoded"
        )
    }

    private fun requestJson(
        server: String,
        method: String,
        path: String,
        token: String? = null,
        authorization: String? = null,
        body: ByteArray? = null,
        contentType: String? = null
    ): JSONObject {
        val text = request(server, method, path, token, authorization, body, contentType)
        return if (text.isBlank()) JSONObject().put("success", true) else JSONObject(text)
    }

    private fun request(
        server: String,
        method: String,
        path: String,
        token: String? = null,
        authorization: String? = null,
        body: ByteArray? = null,
        contentType: String? = null
    ): String {
        val base = KoboConnectionRepository.normalizeServerUrl(server)
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            when {
                !authorization.isNullOrBlank() -> setRequestProperty("Authorization", authorization)
                !token.isNullOrBlank() -> setRequestProperty("Authorization", "Token $token")
            }
            contentType?.let { setRequestProperty("Content-Type", it) }
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
        }
        body?.let { connection.outputStream.use { out -> out.write(it) } }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) {
            val root = runCatching { JSONObject(text) }.getOrNull()
            val message = root?.let(::firstKoboError) ?: "KoboToolbox request failed ($status)."
            throw KoboApiException(
                statusCode = status,
                message = message,
                details = root?.let(::collectKoboErrorDetails),
                rawResponse = text.takeIf(String::isNotBlank)?.take(20_000),
                requestMethod = method,
                requestPath = path,
                errorType = root?.let(::koboErrorType)
            )
        }
        return text
    }

    private fun koboErrorType(root: JSONObject): String? =
        root.optJSONObject("messages")?.optString("error_type")?.takeIf(String::isNotBlank)
            ?: root.optString("error_type").takeIf(String::isNotBlank)

    private fun firstKoboError(root: JSONObject): String? {
        listOf("detail", "message", "error").forEach { key ->
            root.optString(key).takeIf(String::isNotBlank)?.let { return it }
        }
        listOf("errors", "validation_errors", "messages").forEach { key ->
            firstJsonLeaf(root.opt(key))?.let { return it }
        }
        return null
    }

    private fun collectKoboErrorDetails(root: JSONObject): String? {
        val keys = listOf("detail", "message", "error", "errors", "validation_errors", "messages", "status")
        val selected = JSONObject()
        keys.forEach { key -> if (root.has(key) && !root.isNull(key)) selected.put(key, root.opt(key)) }
        return if (selected.length() == 0) null else selected.toString(2)
    }

    private fun firstJsonLeaf(value: Any?): String? = when (value) {
        null, JSONObject.NULL -> null
        is String -> value.takeIf(String::isNotBlank)
        is JSONObject -> value.keys().asSequence().mapNotNull { key -> firstJsonLeaf(value.opt(key)) }.firstOrNull()
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { firstJsonLeaf(value.opt(it)) }.firstOrNull()
        else -> value.toString().takeIf(String::isNotBlank)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
