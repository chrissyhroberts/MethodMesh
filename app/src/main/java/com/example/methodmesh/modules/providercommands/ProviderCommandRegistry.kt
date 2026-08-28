package com.example.methodmesh.modules.providercommands

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class ProviderCommand(
    val commandId: String = "",
    val displayName: String = "",
    val providerId: String = "",
    val packageName: String = "",
    val interfaceType: String = "intent",
    val stability: String = "experimental",
    val offlineSupported: Boolean = true,
    val action: String = "android.intent.action.VIEW",
    val dataUriTemplate: String = "",
    val mimeType: String = "",
    val extrasTemplate: Map<String, String> = emptyMap(),
    val defaultValues: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 60000L,
    val enabled: Boolean = true,
    val lastTestStatus: String = "",
    val lastTestTimeIso: String = "",
    val lastTestError: String = ""
) {
    val stableId: String get() = commandId.trim().ifBlank { "${providerId.ifBlank { "provider" }}::${displayName.ifBlank { UUID.randomUUID().toString().take(8) }}" }
}

object ProviderCommandRegistry {
    private const val PREFS = "methodmesh_provider_command_registry"
    private const val KEY = "commands"
    const val VERSION = "1"

    fun all(context: Context): List<ProviderCommand> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        val saved = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.toCommand() }
        (builtInCommands() + saved)
            .associateBy { it.commandId }
            .values
            .toList()
    }.getOrDefault(emptyList()).sortedBy { it.commandId.lowercase() }

    fun save(context: Context, command: ProviderCommand) {
        val normalized = command.copy(commandId = command.stableId)
        persist(context, all(context).filterNot { it.commandId == normalized.commandId } + normalized)
    }

    fun remove(context: Context, commandId: String) {
        persist(context, savedOnly(context).filterNot { it.commandId == commandId })
    }

    fun setEnabled(context: Context, commandId: String, enabled: Boolean) {
        persist(context, all(context).map { if (it.commandId == commandId) it.copy(enabled = enabled) else it })
    }

    fun recordTest(context: Context, commandId: String, status: String, error: String = "") {
        persist(context, all(context).map {
            if (it.commandId == commandId) it.copy(lastTestStatus = status, lastTestTimeIso = Instant.now().toString(), lastTestError = error) else it
        })
    }

    fun exportBundle(context: Context, onlyCommandId: String? = null): String {
        val commands = all(context).filter { onlyCommandId.isNullOrBlank() || it.commandId == onlyCommandId }
        val payload = JSONObject().apply {
            put("methodmesh_provider_command_registry_version", VERSION)
            put("exported_time_iso", Instant.now().toString())
            put("command_count", commands.size)
            put("commands", JSONArray().apply { commands.forEach { put(it.toJson(includeTestState = false)) } })
        }
        val canonical = payload.toString()
        payload.put("payload_sha256", sha256Hex(canonical))
        return payload.toString()
    }

    fun importBundle(context: Context, payload: String): Int {
        val root = JSONObject(payload)
        val commands = root.optJSONArray("commands") ?: JSONArray().apply {
            root.optJSONObject("command")?.let { put(it) }
        }
        val imported = (0 until commands.length()).mapNotNull { i -> commands.optJSONObject(i)?.toCommand() }
        imported.forEach { save(context, it) }
        return imported.size
    }

    fun restoreBuiltIn(context: Context, commandId: String) {
        if (commandId in builtInCommandIds()) remove(context, commandId)
    }

    private fun savedOnly(context: Context): List<ProviderCommand> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.toCommand() }
    }.getOrDefault(emptyList())

    private fun builtInCommandIds(): Set<String> = builtInCommands().mapTo(mutableSetOf()) { it.commandId }

    private fun persist(context: Context, commands: List<ProviderCommand>) {
        val array = JSONArray().apply { commands.sortedBy { it.commandId.lowercase() }.forEach { put(it.toJson()) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun builtInCommands(): List<ProviderCommand> = listOf(
        ProviderCommand(
            commandId = "OsmAnd::show_pin",
            displayName = "OsmAnd: show map pin",
            providerId = "osmand",
            packageName = "net.osmand|net.osmand.plus",
            interfaceType = "launch_only_intent",
            stability = "documented",
            offlineSupported = true,
            action = "android.intent.action.VIEW",
            dataUriTemplate = "https://osmand.net/map/?pin={latitude},{longitude}#{zoom}/{latitude}/{longitude}",
            defaultValues = linkedMapOf(
                "latitude" to "51.5074",
                "longitude" to "-0.1278",
                "zoom" to "16"
            )
        ),
        ProviderCommand(
            commandId = "OsmAnd::show_location",
            displayName = "OsmAnd: centre map on coordinates",
            providerId = "osmand",
            packageName = "net.osmand|net.osmand.plus",
            interfaceType = "launch_only_intent",
            stability = "documented",
            offlineSupported = true,
            action = "android.intent.action.VIEW",
            dataUriTemplate = "https://osmand.net/map/#{zoom}/{latitude}/{longitude}",
            defaultValues = linkedMapOf(
                "latitude" to "51.5074",
                "longitude" to "-0.1278",
                "zoom" to "16"
            )
        ),
        ProviderCommand(
            commandId = "OsmAnd::navigate_to",
            displayName = "OsmAnd: route between coordinates",
            providerId = "osmand",
            packageName = "net.osmand|net.osmand.plus",
            interfaceType = "launch_only_intent",
            stability = "documented",
            offlineSupported = true,
            action = "android.intent.action.VIEW",
            dataUriTemplate = "https://osmand.net/map/?start={start_latitude},{start_longitude}&finish={finish_latitude},{finish_longitude}&profile={profile}&pin={finish_latitude},{finish_longitude}#{zoom}/{finish_latitude}/{finish_longitude}",
            defaultValues = linkedMapOf(
                "start_latitude" to "51.5014",
                "start_longitude" to "-0.1419",
                "finish_latitude" to "51.5074",
                "finish_longitude" to "-0.1278",
                "profile" to "pedestrian",
                "zoom" to "16"
            )
        ),
        ProviderCommand(
            commandId = "OsmAnd::geo_search",
            displayName = "OsmAnd: search map",
            providerId = "osmand",
            packageName = "net.osmand|net.osmand.plus",
            interfaceType = "launch_only_intent",
            stability = "documented",
            offlineSupported = true,
            action = "android.intent.action.VIEW",
            dataUriTemplate = "geo:0,0?q={query_plus}",
            defaultValues = linkedMapOf(
                "query" to "clinic near me"
            )
        ),
        ProviderCommand(
            commandId = "AndroidMaps::geo_point",
            displayName = "Any maps app: show coordinates",
            providerId = "android_maps",
            packageName = "",
            interfaceType = "launch_only_intent",
            stability = "platform",
            offlineSupported = false,
            action = "android.intent.action.VIEW",
            dataUriTemplate = "geo:{latitude},{longitude}?z={zoom}",
            defaultValues = linkedMapOf(
                "latitude" to "51.5074",
                "longitude" to "-0.1278",
                "zoom" to "16"
            )
        ),
        ProviderCommand(
            commandId = "AndroidMaps::geo_search",
            displayName = "Any maps app: search",
            providerId = "android_maps",
            packageName = "",
            interfaceType = "launch_only_intent",
            stability = "platform",
            offlineSupported = false,
            action = "android.intent.action.VIEW",
            dataUriTemplate = "geo:0,0?q={query_plus}",
            defaultValues = linkedMapOf(
                "query" to "clinic near me"
            )
        ),
        ProviderCommand(
            commandId = "Browser::open_url",
            displayName = "Browser: open URL",
            providerId = "browser",
            packageName = "",
            interfaceType = "launch_only_intent",
            stability = "platform",
            offlineSupported = false,
            action = "android.intent.action.VIEW",
            dataUriTemplate = "{url}",
            defaultValues = linkedMapOf(
                "url" to "https://example.org"
            )
        ),
        ProviderCommand(
            commandId = "Phone::dial_number",
            displayName = "Phone: open dialler",
            providerId = "phone",
            packageName = "",
            interfaceType = "launch_only_intent",
            stability = "platform",
            offlineSupported = false,
            action = "android.intent.action.DIAL",
            dataUriTemplate = "tel:{phone}",
            defaultValues = linkedMapOf(
                "phone" to "+441234567890"
            )
        ),
        ProviderCommand(
            commandId = "SMS::compose_message",
            displayName = "SMS: compose message",
            providerId = "sms",
            packageName = "",
            interfaceType = "launch_only_intent",
            stability = "platform",
            offlineSupported = false,
            action = "android.intent.action.SENDTO",
            dataUriTemplate = "smsto:{phone}",
            extrasTemplate = linkedMapOf(
                "sms_body" to "{message}"
            ),
            defaultValues = linkedMapOf(
                "phone" to "+441234567890",
                "message" to "Hello from MethodMesh"
            )
        ),
        ProviderCommand(
            commandId = "Email::compose_message",
            displayName = "Email: compose message",
            providerId = "email",
            packageName = "",
            interfaceType = "launch_only_intent",
            stability = "platform",
            offlineSupported = false,
            action = "android.intent.action.SENDTO",
            dataUriTemplate = "mailto:{email}?subject={subject_uri}&body={body_uri}",
            defaultValues = linkedMapOf(
                "email" to "research@example.org",
                "subject" to "MethodMesh message",
                "body" to "Hello from MethodMesh"
            )
        ),
        ProviderCommand(
            commandId = "AndroidShare::share_text",
            displayName = "Android share sheet: share text",
            providerId = "android_share",
            packageName = "",
            interfaceType = "launch_only_intent",
            stability = "platform",
            offlineSupported = false,
            action = "android.intent.action.SEND",
            mimeType = "text/plain",
            extrasTemplate = linkedMapOf(
                "android.intent.extra.SUBJECT" to "{subject}",
                "android.intent.extra.TEXT" to "{text}"
            ),
            defaultValues = linkedMapOf(
                "subject" to "MethodMesh",
                "text" to "Shared from MethodMesh"
            )
        ),
        ProviderCommand(
            commandId = "WhatsApp::send_message",
            displayName = "WhatsApp: compose message",
            providerId = "whatsapp",
            packageName = "com.whatsapp|com.whatsapp.w4b",
            interfaceType = "launch_only_intent",
            stability = "public_url",
            offlineSupported = false,
            action = "android.intent.action.VIEW",
            dataUriTemplate = "https://wa.me/{phone}?text={message_uri}",
            defaultValues = linkedMapOf(
                "phone" to "441234567890",
                "message" to "Hello from MethodMesh"
            )
        ),
        ProviderCommand(
            commandId = "Telegram::open_user",
            displayName = "Telegram: open username",
            providerId = "telegram",
            packageName = "org.telegram.messenger|org.telegram.messenger.web",
            interfaceType = "launch_only_intent",
            stability = "public_url",
            offlineSupported = false,
            action = "android.intent.action.VIEW",
            dataUriTemplate = "https://t.me/{username}",
            defaultValues = linkedMapOf(
                "username" to "telegram"
            )
        ),
        ProviderCommand(
            commandId = "WHOeyes::near_vision_test",
            displayName = "WHOeyes: near vision test",
            providerId = "whoeyes",
            packageName = "org.who.whoeyes",
            interfaceType = "activity_result_intent",
            stability = "documented_by_example",
            offlineSupported = true,
            action = "org.who.whoeyes.share.ACTION_GET_VISION_TEST",
            extrasTemplate = linkedMapOf(
                "visiontype" to "near"
            )
        ),
        ProviderCommand(
            commandId = "WHOeyes::distance_vision_test",
            displayName = "WHOeyes: distance vision test",
            providerId = "whoeyes",
            packageName = "org.who.whoeyes",
            interfaceType = "activity_result_intent",
            stability = "documented_by_example",
            offlineSupported = true,
            action = "org.who.whoeyes.share.ACTION_GET_VISION_TEST",
            extrasTemplate = linkedMapOf(
                "visiontype" to "distant"
            )
        )
    )
}

fun ProviderCommand.toJson(includeTestState: Boolean = true): JSONObject = JSONObject().apply {
    put("command_id", commandId)
    put("display_name", displayName)
    put("provider_id", providerId)
    put("package_name", packageName)
    put("interface_type", interfaceType)
    put("stability", stability)
    put("offline_supported", offlineSupported)
    put("action", action)
    put("data_uri_template", dataUriTemplate)
    put("mime_type", mimeType)
    put("extras_template", JSONObject(extrasTemplate))
    put("default_values", JSONObject(defaultValues))
    put("timeout_ms", timeoutMs)
    put("enabled", enabled)
    if (includeTestState) {
        put("last_test_status", lastTestStatus)
        put("last_test_time_iso", lastTestTimeIso)
        put("last_test_error", lastTestError)
    }
}

private fun JSONObject.toCommand(): ProviderCommand = ProviderCommand(
    commandId = optString("command_id"),
    displayName = optString("display_name"),
    providerId = optString("provider_id"),
    packageName = optString("package_name"),
    interfaceType = optString("interface_type").ifBlank { "intent" },
    stability = optString("stability").ifBlank { "experimental" },
    offlineSupported = optBoolean("offline_supported", true),
    action = optString("action").ifBlank { "android.intent.action.VIEW" },
    dataUriTemplate = optString("data_uri_template"),
    mimeType = optString("mime_type"),
    extrasTemplate = optJSONObject("extras_template")?.toStringMap().orEmpty(),
    defaultValues = optJSONObject("default_values")?.toStringMap().orEmpty(),
    timeoutMs = optLong("timeout_ms", 60000L).coerceAtLeast(1000L),
    enabled = optBoolean("enabled", true),
    lastTestStatus = optString("last_test_status"),
    lastTestTimeIso = optString("last_test_time_iso"),
    lastTestError = optString("last_test_error")
)

private fun JSONObject.toStringMap(): Map<String, String> =
    keys().asSequence().associateWith { key -> optString(key) }

fun parseKeyValueLines(text: String): Map<String, String> =
    text.lineSequence()
        .map(String::trim)
        .filter { it.isNotBlank() && "=" in it }
        .associate { line ->
            val key = line.substringBefore("=").trim()
            val value = line.substringAfter("=").trim()
            key to value
        }

fun formatKeyValueLines(values: Map<String, String>): String =
    values.entries.joinToString("\n") { (key, value) -> "$key=$value" }

fun applyTemplate(template: String, values: Map<String, String>): String {
    var out = template
    values.forEach { (key, value) ->
        val encoded = urlEncode(value)
        out = out
            .replace("{$key}", value)
            .replace("\${$key}", value)
            .replace("{${key}_plus}", encoded)
            .replace("\${${key}_plus}", encoded)
            .replace("{${key}_uri}", encoded)
            .replace("\${${key}_uri}", encoded)
    }
    return out
}

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name())

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
