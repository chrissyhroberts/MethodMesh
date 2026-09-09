package com.example.methodmesh.widgets

import android.content.Context
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.core.scheduling.SchedulePlanStore
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import org.json.JSONObject

enum class MethodMeshWidgetIconKey(val title: String, val emoji: String) {
    AUTO("Auto", "✨"),
    METHODMESH("MethodMesh", "🕸️"),
    DOCUMENT("Document", "📄"),
    FILES("Files", "🗂️"),
    BOOK("Reference", "📚"),
    FORM("Form", "📝"),
    ATTACHMENT("Attachment", "📎"),
    LOCATION("Location", "📍"),
    COMPASS("Compass", "🧭"),
    MAP("Map", "🗺️"),
    GPS("GPS", "🛰️"),
    TERRAIN("Terrain", "🏔️"),
    LANGUAGE("Language", "💬"),
    SPEECH("Speech", "🗣️"),
    TRANSLATE("Translate", "🌐"),
    HARDWARE("Hardware", "🔧"),
    BLUETOOTH("Bluetooth", "🔵"),
    NETWORK("Network", "📡"),
    PHONE("Phone", "📱"),
    NFC("NFC", "📳"),
    RANDOM("Random", "🎲"),
    COIN("Coin toss", "🪙"),
    GAME("Game", "🎮"),
    PUZZLE("Puzzle", "🧩"),
    SCHEDULE("Schedule", "⏱️"),
    CALENDAR("Calendar", "📅"),
    ALARM("Alarm", "⏰"),
    TOOL("Tool", "🧰"),
    SETTINGS("Settings", "⚙️"),
    CONSENT("Consent", "✍️"),
    CAMERA("Camera", "📷"),
    IMAGE("Image", "🖼️"),
    VIDEO("Video", "🎥"),
    AUDIO("Audio", "🎙️"),
    MAGNIFY("Magnifier", "🔍"),
    SCAN("Scan", "▣"),
    MEDICAL("Medical", "🩺"),
    HEALTH("Health", "❤️"),
    VISION("Vision", "👁️"),
    SAFETY("Safety", "🛡️"),
    EMERGENCY("Emergency", "🆘"),
    WEATHER("Weather", "🌦️"),
    WATER("Water", "💧"),
    DIVING("Diving", "🤿"),
    PLANT("Field", "🌿"),
    ASTRONOMY("Astronomy", "🔭"),
    AVIATION("Aviation", "✈️"),
    CALCULATE("Calculate", "🧮"),
    DATA("Data", "📊"),
    STATISTICS("Statistics", "📈"),
    MEASURE("Measure", "📏"),
    STOPWATCH("Stopwatch", "⏱️"),
    LAB("Laboratory", "🧪"),
    ELECTRICAL("Electrical", "⚡"),
    SOUND("Sound", "🔊"),
    MUSIC("Music", "🎵"),
    TEXT("Text", "🔤"),
    EMAIL("Email", "✉️"),
    SHARE("Share", "↗️"),
    SIGNING("Signing", "🖊️"),
    SECURITY("Security", "🔐"),
    IDENTITY("Identity", "🪪"),
    QR("QR code", "▦"),
    PRINT("Print", "🖨️"),
    TARGET("Target", "🎯"),
    COUNTER("Counter", "🔢"),
    CHECKLIST("Checklist", "☑️"),
    ALERT("Alert", "⚠️"),
    EDUCATION("Teaching", "🎓"),
    PET("Care", "🐾");

    companion object {
        fun normalize(value: String): MethodMeshWidgetIconKey =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: entries.firstOrNull { it.title.equals(value, ignoreCase = true) }
                ?: AUTO
    }
}

enum class MethodMeshWidgetColour(val title: String, val argb: Int) {
    TEAL("Teal", 0xFFD7F0EC.toInt()),
    BLUE("Blue", 0xFFDDE9FF.toInt()),
    PURPLE("Purple", 0xFFEDE1FF.toInt()),
    AMBER("Amber", 0xFFFFEDC7.toInt()),
    CORAL("Coral", 0xFFFFDFD6.toInt()),
    SLATE("Slate", 0xFFE4E8ED.toInt()),
    DARK("Dark", 0xFF30343B.toInt());

    companion object {
        fun normalize(value: String): MethodMeshWidgetColour =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TEAL
    }
}

enum class MethodMeshWidgetTargetType {
    PRESET,
    PROTOCOL,
    SCHEDULE
}

data class MethodMeshWidgetConfig(
    val appWidgetId: Int,
    val label: String,
    val targetType: MethodMeshWidgetTargetType,
    val targetId: String,
    val iconKey: MethodMeshWidgetIconKey = MethodMeshWidgetIconKey.AUTO,
    val colour: MethodMeshWidgetColour = MethodMeshWidgetColour.TEAL
)

object MethodMeshWidgetRepository {
    private const val PREFS = "methodmesh_home_widgets"
    private const val PREFIX = "widget_"

    fun save(context: Context, config: MethodMeshWidgetConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(config.appWidgetId), encode(config).toString())
            .apply()
    }

    fun get(context: Context, appWidgetId: Int): MethodMeshWidgetConfig? =
        runCatching {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(key(appWidgetId), null)
                ?: return null
            decode(JSONObject(raw))
        }.getOrNull()

    fun delete(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(appWidgetId))
            .apply()
    }

    fun resolveTitle(context: Context, config: MethodMeshWidgetConfig): String =
        config.label.ifBlank { resolveTargetName(context, config).ifBlank { "MethodMesh" } }

    fun resolveSubtitle(context: Context, config: MethodMeshWidgetConfig): String = when (config.targetType) {
        MethodMeshWidgetTargetType.PRESET -> "Preset"
        MethodMeshWidgetTargetType.PROTOCOL -> "Protocol"
        MethodMeshWidgetTargetType.SCHEDULE -> {
            val running = SchedulePlanStore.allInstances(context).any { it.planId == config.targetId && it.stoppedAt == null }
            if (running) "Schedule on" else "Schedule off"
        }
    }

    fun resolveIconKey(context: Context, config: MethodMeshWidgetConfig): MethodMeshWidgetIconKey {
        if (config.iconKey != MethodMeshWidgetIconKey.AUTO) return config.iconKey
        return when (config.targetType) {
            MethodMeshWidgetTargetType.SCHEDULE -> MethodMeshWidgetIconKey.SCHEDULE
            MethodMeshWidgetTargetType.PROTOCOL -> MethodMeshWidgetIconKey.METHODMESH
            MethodMeshWidgetTargetType.PRESET -> {
                val preset = ProtocolLibraryRepository.preset(context, config.targetId)
                val module = preset?.methodId?.let { methodId ->
                    MethodMeshModuleRegistry.all().firstOrNull { module ->
                        module.as100Methods().any { it.id == methodId } ||
                            module.capabilityScreens().any { it.capabilityId == methodId }
                    }
                }
                iconKeyFor(module?.iconKey ?: preset?.methodId.orEmpty())
            }
        }
    }

    fun resolveColour(config: MethodMeshWidgetConfig): MethodMeshWidgetColour = config.colour

    fun resolveTargetName(context: Context, config: MethodMeshWidgetConfig): String = when (config.targetType) {
        MethodMeshWidgetTargetType.PRESET ->
            ProtocolLibraryRepository.preset(context, config.targetId)?.name.orEmpty()
        MethodMeshWidgetTargetType.PROTOCOL ->
            ProtocolLibraryRepository.protocol(context, config.targetId)?.name.orEmpty()
        MethodMeshWidgetTargetType.SCHEDULE ->
            SchedulePlanStore.plan(context, config.targetId)?.name.orEmpty()
    }

    fun scheduleTargets(context: Context) =
        SchedulePlanStore.allPlans(context).sortedBy { it.name.lowercase() }

    private fun encode(config: MethodMeshWidgetConfig) = JSONObject().apply {
        put("appWidgetId", config.appWidgetId)
        put("label", config.label)
        put("targetType", config.targetType.name)
        put("targetId", config.targetId)
        put("iconKey", config.iconKey.name)
        put("colour", config.colour.name)
    }

    private fun decode(root: JSONObject): MethodMeshWidgetConfig =
        MethodMeshWidgetConfig(
            appWidgetId = root.optInt("appWidgetId"),
            label = root.optString("label"),
            targetType = runCatching {
                MethodMeshWidgetTargetType.valueOf(root.optString("targetType"))
            }.getOrDefault(MethodMeshWidgetTargetType.PRESET),
            targetId = root.optString("targetId"),
            iconKey = MethodMeshWidgetIconKey.normalize(root.optString("iconKey")),
            colour = MethodMeshWidgetColour.normalize(root.optString("colour"))
        )

    private fun key(appWidgetId: Int): String = "$PREFIX$appWidgetId"

    private fun iconKeyFor(value: String): MethodMeshWidgetIconKey {
        val lower = value.lowercase()
        return when {
            "reference" in lower || "library" in lower -> MethodMeshWidgetIconKey.BOOK
            "media" in lower -> MethodMeshWidgetIconKey.VIDEO
            "document" in lower || "pdf" in lower -> MethodMeshWidgetIconKey.DOCUMENT
            "scan" in lower || "barcode" in lower || "qr" in lower -> MethodMeshWidgetIconKey.SCAN
            "photo" in lower || "image" in lower || "camera" in lower -> MethodMeshWidgetIconKey.IMAGE
            "gps" in lower || "location" in lower || "plus" in lower -> MethodMeshWidgetIconKey.GPS
            "compass" in lower || "survey" in lower || "geocach" in lower -> MethodMeshWidgetIconKey.COMPASS
            "translate" in lower || "language" in lower -> MethodMeshWidgetIconKey.TRANSLATE
            "speech" in lower || "conversation" in lower || "voice" in lower -> MethodMeshWidgetIconKey.SPEECH
            "bluetooth" in lower || "sensor" in lower || "esp" in lower -> MethodMeshWidgetIconKey.BLUETOOTH
            "network" in lower || "api" in lower || "web" in lower -> MethodMeshWidgetIconKey.NETWORK
            "printer" in lower -> MethodMeshWidgetIconKey.PRINT
            "nfc" in lower -> MethodMeshWidgetIconKey.NFC
            "random" in lower || "dice" in lower || "sampling" in lower || "chance" in lower -> MethodMeshWidgetIconKey.RANDOM
            "coin" in lower -> MethodMeshWidgetIconKey.COIN
            "game" in lower || "arcade" in lower -> MethodMeshWidgetIconKey.GAME
            "schedule" in lower || "time" in lower -> MethodMeshWidgetIconKey.SCHEDULE
            "medical" in lower || "clinical" in lower -> MethodMeshWidgetIconKey.MEDICAL
            "emergency" in lower -> MethodMeshWidgetIconKey.EMERGENCY
            "diving" in lower -> MethodMeshWidgetIconKey.DIVING
            "aquatic" in lower || "water" in lower -> MethodMeshWidgetIconKey.WATER
            "weather" in lower -> MethodMeshWidgetIconKey.WEATHER
            "astronomy" in lower -> MethodMeshWidgetIconKey.ASTRONOMY
            "aviation" in lower -> MethodMeshWidgetIconKey.AVIATION
            "statistics" in lower || "stats" in lower -> MethodMeshWidgetIconKey.STATISTICS
            "laboratory" in lower || "lab" in lower -> MethodMeshWidgetIconKey.LAB
            "calculation" in lower || "conversion" in lower -> MethodMeshWidgetIconKey.CALCULATE
            "data" in lower || "json" in lower || "csv" in lower -> MethodMeshWidgetIconKey.DATA
            "electrical" in lower -> MethodMeshWidgetIconKey.ELECTRICAL
            "music" in lower || "acoustic" in lower || "sound" in lower -> MethodMeshWidgetIconKey.MUSIC
            "text" in lower -> MethodMeshWidgetIconKey.TEXT
            "sign" in lower || "attestation" in lower || "timestamp" in lower -> MethodMeshWidgetIconKey.SIGNING
            "security" in lower || "fingerprint" in lower -> MethodMeshWidgetIconKey.SECURITY
            "counter" in lower || "scoring" in lower -> MethodMeshWidgetIconKey.COUNTER
            "magnif" in lower -> MethodMeshWidgetIconKey.MAGNIFY
            "teaching" in lower || "tamagotchi" in lower -> MethodMeshWidgetIconKey.EDUCATION
            "inspector" in lower || "tool" in lower || "utility" in lower -> MethodMeshWidgetIconKey.TOOL
            else -> MethodMeshWidgetIconKey.METHODMESH
        }
    }
}
