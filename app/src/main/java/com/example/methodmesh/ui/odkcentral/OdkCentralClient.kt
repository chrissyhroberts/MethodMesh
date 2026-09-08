package com.example.methodmesh.ui.odkcentral

import android.content.Context
import com.example.methodmesh.ui.odk.OdkTemplateDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

class OdkCentralApiException(
    val statusCode: Int,
    message: String
) : IllegalStateException(message)

object OdkCentralClient {
    private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    suspend fun login(serverUrl: String, email: String, password: String): OdkCentralSession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email.trim()).put("password", password).toString().toByteArray()
        val json = requestJson(
            serverUrl = serverUrl,
            method = "POST",
            path = "/v1/sessions",
            body = body,
            contentType = "application/json"
        )
        OdkCentralSession(json.getString("token"), json.optString("expiresAt"))
    }

    suspend fun currentUser(serverUrl: String, token: String): JSONObject = withContext(Dispatchers.IO) {
        requestJson(serverUrl, "GET", "/v1/users/current", token = token)
    }

    suspend fun listProjects(serverUrl: String, token: String): List<OdkCentralProject> = withContext(Dispatchers.IO) {
        requestArray(serverUrl, "GET", "/v1/projects", token = token).objects().map {
            OdkCentralProject(it.getLong("id"), it.optString("name").ifBlank { "Project ${it.getLong("id")}" })
        }
    }

    suspend fun listAppUsers(serverUrl: String, token: String, projectId: Long): List<OdkCentralAppUser> = withContext(Dispatchers.IO) {
        requestArray(serverUrl, "GET", "/v1/projects/$projectId/app-users", token = token).objects().map {
            OdkCentralAppUser(
                id = it.getLong("id"),
                displayName = it.optString("displayName").ifBlank { "App User ${it.getLong("id")}" },
                tokenActive = it.has("token") && !it.isNull("token") && it.optString("token").isNotBlank()
            )
        }
    }

    suspend fun listForms(serverUrl: String, token: String, projectId: Long): List<OdkCentralForm> = withContext(Dispatchers.IO) {
        requestArray(
            serverUrl,
            "GET",
            "/v1/projects/$projectId/forms",
            token = token,
            extraHeaders = mapOf("X-Extended-Metadata" to "true")
        ).objects().map {
            OdkCentralForm(
                xmlFormId = it.getString("xmlFormId"),
                name = it.optString("name").ifBlank { it.getString("xmlFormId") },
                publishedAt = it.optString("publishedAt").takeIf(String::isNotBlank),
                submissions = it.optInt("submissions", 0)
            )
        }
    }

    suspend fun assignedAppUsers(serverUrl: String, token: String, projectId: Long, xmlFormId: String): Set<Long> = withContext(Dispatchers.IO) {
        requestArray(
            serverUrl,
            "GET",
            "/v1/projects/$projectId/forms/${encodePath(xmlFormId)}/assignments/app-user",
            token = token
        ).objects().mapNotNull { it.optLong("id", -1L).takeIf { id -> id >= 0L } }.toSet()
    }

    suspend fun deployAndAssign(
        context: Context,
        profile: OdkCentralProfile,
        token: String,
        template: OdkTemplateDescriptor
    ): OdkTemplateDeployment = withContext(Dispatchers.IO) {
        val projectId = requireNotNull(profile.projectId) { "Choose an ODK Central project first." }
        val appUserId = requireNotNull(profile.appUserId) { "Choose an ODK App User first." }
        val server = OdkCentralConnectionRepository.normalizeServerUrl(profile.serverUrl)
        val bytes = context.assets.open(template.assetPath).use { it.readBytes() }
        val digest = sha256(bytes)
        var deployment = OdkCentralDeploymentStore.get(context, server, projectId, template.id)

        val forms = runCatching { listForms(server, token, projectId) }.getOrDefault(emptyList())
        val mappedFormStillExists = deployment?.xmlFormId?.let { id -> forms.any { it.xmlFormId == id } } == true
        if (deployment != null && !mappedFormStillExists) {
            OdkCentralDeploymentStore.remove(context, server, projectId, template.id)
            deployment = null
        }

        val xmlFormId = if (deployment == null) {
            val fallback = template.centralFormIdHint()
            val discovered = forms.firstOrNull { it.xmlFormId == fallback }
            if (discovered != null) {
                requestJson(
                    serverUrl = server,
                    method = "POST",
                    path = "/v1/projects/$projectId/forms/${encodePath(discovered.xmlFormId)}/draft",
                    token = token,
                    body = bytes,
                    contentType = XLSX_MIME,
                    extraHeaders = mapOf("X-XlsForm-FormId-Fallback" to percentEncode(discovered.xmlFormId))
                )
                publishDraft(server, token, projectId, discovered.xmlFormId)
                discovered.xmlFormId
            } else {
                val created = requestJson(
                    serverUrl = server,
                    method = "POST",
                    path = "/v1/projects/$projectId/forms",
                    token = token,
                    body = bytes,
                    contentType = XLSX_MIME,
                    extraHeaders = mapOf("X-XlsForm-FormId-Fallback" to percentEncode(fallback))
                )
                val createdId = created.getString("xmlFormId")
                publishDraft(server, token, projectId, createdId)
                createdId
            }
        } else {
            if (deployment.localSha256 != digest) {
                requestJson(
                    serverUrl = server,
                    method = "POST",
                    path = "/v1/projects/$projectId/forms/${encodePath(deployment.xmlFormId)}/draft",
                    token = token,
                    body = bytes,
                    contentType = XLSX_MIME,
                    extraHeaders = mapOf("X-XlsForm-FormId-Fallback" to percentEncode(deployment.xmlFormId))
                )
                publishDraft(server, token, projectId, deployment.xmlFormId)
            }
            deployment.xmlFormId
        }

        val previouslyManagedUser = deployment?.assignedAppUserId
        if (previouslyManagedUser != null && previouslyManagedUser != appUserId) {
            runCatching {
                requestJson(
                    server,
                    "DELETE",
                    "/v1/projects/$projectId/forms/${encodePath(xmlFormId)}/assignments/app-user/$previouslyManagedUser",
                    token = token
                )
            }
        }
        requestJson(
            server,
            "POST",
            "/v1/projects/$projectId/forms/${encodePath(xmlFormId)}/assignments/app-user/$appUserId",
            token = token
        )
        OdkTemplateDeployment(
            serverUrl = server,
            projectId = projectId,
            templateId = template.id,
            xmlFormId = xmlFormId,
            localSha256 = digest,
            assignedAppUserId = appUserId,
            lastSyncedAt = Instant.now().toString()
        ).also { OdkCentralDeploymentStore.put(context, it) }
    }

    suspend fun revokeTesterAccess(
        context: Context,
        profile: OdkCentralProfile,
        token: String,
        template: OdkTemplateDescriptor
    ): OdkTemplateDeployment? = withContext(Dispatchers.IO) {
        val projectId = requireNotNull(profile.projectId) { "Choose an ODK Central project first." }
        val appUserId = requireNotNull(profile.appUserId) { "Choose an ODK App User first." }
        val server = OdkCentralConnectionRepository.normalizeServerUrl(profile.serverUrl)
        val existing = OdkCentralDeploymentStore.get(context, server, projectId, template.id) ?: return@withContext null
        requestJson(
            server,
            "DELETE",
            "/v1/projects/$projectId/forms/${encodePath(existing.xmlFormId)}/assignments/app-user/$appUserId",
            token = token
        )
        existing.copy(assignedAppUserId = null, lastSyncedAt = Instant.now().toString()).also {
            OdkCentralDeploymentStore.put(context, it)
        }
    }

    suspend fun removeRemoteForm(
        context: Context,
        profile: OdkCentralProfile,
        token: String,
        template: OdkTemplateDescriptor
    ) = withContext(Dispatchers.IO) {
        val projectId = requireNotNull(profile.projectId) { "Choose an ODK Central project first." }
        val server = OdkCentralConnectionRepository.normalizeServerUrl(profile.serverUrl)
        val existing = OdkCentralDeploymentStore.get(context, server, projectId, template.id)
            ?: error("This template is not mapped to a Central form on the selected project.")
        requestJson(server, "DELETE", "/v1/projects/$projectId/forms/${encodePath(existing.xmlFormId)}", token = token)
        OdkCentralDeploymentStore.remove(context, server, projectId, template.id)
    }

    suspend fun remoteSubmissionCount(
        context: Context,
        profile: OdkCentralProfile,
        token: String,
        template: OdkTemplateDescriptor
    ): Int = withContext(Dispatchers.IO) {
        val projectId = profile.projectId ?: return@withContext 0
        val server = OdkCentralConnectionRepository.normalizeServerUrl(profile.serverUrl)
        val existing = OdkCentralDeploymentStore.get(context, server, projectId, template.id) ?: return@withContext 0
        listForms(server, token, projectId).firstOrNull { it.xmlFormId == existing.xmlFormId }?.submissions ?: 0
    }

    fun localDigest(context: Context, template: OdkTemplateDescriptor): String =
        context.assets.open(template.assetPath).use { sha256(it.readBytes()) }

    private fun publishDraft(serverUrl: String, token: String, projectId: Long, xmlFormId: String) {
        // Rapid test deployments must not stall because a module author forgot to bump
        // the XLSForm version while iterating. Central explicitly supports supplying a
        // version at publish time.
        val testVersion = "mm-${Instant.now().toEpochMilli()}"
        requestJson(
            serverUrl,
            "POST",
            "/v1/projects/$projectId/forms/${encodePath(xmlFormId)}/draft/publish?version=${percentEncode(testVersion)}",
            token = token
        )
    }

    private fun OdkTemplateDescriptor.centralFormIdHint(): String = centralFormId.takeIf { it.isNotBlank() } ?: id
        .lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "_")
        .trim('_')
        .take(120)
        .ifBlank { sourceFileName.removeSuffix(".xlsx").replace(Regex("[^A-Za-z0-9._-]+"), "_") }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun percentEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    private fun encodePath(value: String): String = percentEncode(value)

    private fun requestJson(
        serverUrl: String,
        method: String,
        path: String,
        token: String? = null,
        body: ByteArray? = null,
        contentType: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): JSONObject {
        val response = request(serverUrl, method, path, token, body, contentType, extraHeaders)
        return if (response.isBlank()) JSONObject().put("success", true) else JSONObject(response)
    }

    private fun requestArray(
        serverUrl: String,
        method: String,
        path: String,
        token: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): JSONArray {
        val response = request(serverUrl, method, path, token, null, null, extraHeaders)
        return if (response.isBlank()) JSONArray() else JSONArray(response)
    }

    private fun request(
        serverUrl: String,
        method: String,
        path: String,
        token: String?,
        body: ByteArray?,
        contentType: String?,
        extraHeaders: Map<String, String>
    ): String {
        val base = OdkCentralConnectionRepository.normalizeServerUrl(serverUrl)
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/json")
            token?.takeIf(String::isNotBlank)?.let { setRequestProperty("Authorization", "Bearer $it") }
            contentType?.let { setRequestProperty("Content-Type", it) }
            extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
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
            val message = runCatching {
                val root = JSONObject(text)
                root.optString("message").ifBlank { root.optString("code") }
            }.getOrNull().orEmpty().ifBlank { "ODK Central request failed ($status)." }
            throw OdkCentralApiException(status, message)
        }
        return text
    }

    private fun JSONArray.objects(): List<JSONObject> = buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }
}
