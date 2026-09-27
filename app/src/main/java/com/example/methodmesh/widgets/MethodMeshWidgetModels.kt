package com.example.methodmesh.widgets

import android.content.Context
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.core.scheduling.SchedulePlanStore
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import org.json.JSONArray
import org.json.JSONObject

enum class MethodMeshWidgetIconKey(val title: String, val emoji: String) {
    AUTO("Auto", "✨"), METHODMESH("MethodMesh", "🕸️"), DOCUMENT("Document", "📄"), FILES("Files", "🗂️"),
    BOOK("Reference", "📚"), FORM("Form", "📝"), ATTACHMENT("Attachment", "📎"), LOCATION("Location", "📍"),
    COMPASS("Compass", "🧭"), MAP("Map", "🗺️"), GPS("GPS", "🛰️"), TERRAIN("Terrain", "🏔️"),
    LANGUAGE("Language", "💬"), SPEECH("Speech", "🗣️"), TRANSLATE("Translate", "🌐"), HARDWARE("Hardware", "🔧"),
    BLUETOOTH("Bluetooth", "🔵"), NETWORK("Network", "📡"), PHONE("Phone", "📱"), NFC("NFC", "📳"),
    RANDOM("Random", "🎲"), COIN("Coin toss", "🪙"), GAME("Game", "🎮"), PUZZLE("Puzzle", "🧩"),
    SCHEDULE("Schedule", "⏱️"), CALENDAR("Calendar", "📅"), ALARM("Alarm", "⏰"), TOOL("Tool", "🧰"),
    SETTINGS("Settings", "⚙️"), CONSENT("Consent", "✍️"), CAMERA("Camera", "📷"), IMAGE("Image", "🖼️"),
    VIDEO("Video", "🎥"), AUDIO("Audio", "🎙️"), MAGNIFY("Magnifier", "🔍"), SCAN("Scan", "▣"),
    MEDICAL("Medical", "🩺"), HEALTH("Health", "❤️"), VISION("Vision", "👁️"), SAFETY("Safety", "🛡️"),
    EMERGENCY("Emergency", "🆘"), WEATHER("Weather", "🌦️"), WATER("Water", "💧"), DIVING("Diving", "🤿"),
    PLANT("Field", "🌿"), ASTRONOMY("Astronomy", "🔭"), AVIATION("Aviation", "✈️"), CALCULATE("Calculate", "🧮"),
    DATA("Data", "📊"), STATISTICS("Statistics", "📈"), MEASURE("Measure", "📏"), STOPWATCH("Stopwatch", "⏱️"),
    LAB("Laboratory", "🧪"), ELECTRICAL("Electrical", "⚡"), SOUND("Sound", "🔊"), MUSIC("Music", "🎵"),
    TEXT("Text", "🔤"), EMAIL("Email", "✉️"), SHARE("Share", "↗️"), SIGNING("Signing", "🖊️"),
    SECURITY("Security", "🔐"), IDENTITY("Identity", "🪪"), QR("QR code", "▦"), PRINT("Print", "🖨️"),
    TARGET("Target", "🎯"), COUNTER("Counter", "🔢"), CHECKLIST("Checklist", "☑️"), ALERT("Alert", "⚠️"),
    EDUCATION("Teaching", "🎓"), PET("Care", "🐾");

    companion object {
        fun normalize(value: String): MethodMeshWidgetIconKey =
            entries.firstOrNull { it.name.equals(value, true) } ?: entries.firstOrNull { it.title.equals(value, true) } ?: AUTO
    }
}

enum class MethodMeshWidgetColour(val title: String, val argb: Int) {
    TEAL("Teal", 0xFFD7F0EC.toInt()), BLUE("Blue", 0xFFDDE9FF.toInt()), PURPLE("Purple", 0xFFEDE1FF.toInt()),
    AMBER("Amber", 0xFFFFEDC7.toInt()), CORAL("Coral", 0xFFFFDFD6.toInt()), SLATE("Slate", 0xFFE4E8ED.toInt()), DARK("Dark", 0xFF30343B.toInt());
    companion object { fun normalize(value: String) = entries.firstOrNull { it.name.equals(value, true) } ?: TEAL }
}

enum class MethodMeshWidgetAppearance(val title: String) {
    SOLID("Solid"),
    FROSTED("Frosted"),
    COMPACT("Compact");

    companion object {
        fun normalize(value: String): MethodMeshWidgetAppearance = when (value.uppercase()) {
            "FROSTED", "GLASS" -> FROSTED
            "COMPACT", "TRANSPARENT", "MINIMAL" -> COMPACT
            "SOLID" -> SOLID
            else -> SOLID
        }
    }
}
enum class MethodMeshWidgetTargetType { PRESET, PROTOCOL, SCHEDULE, BUNDLE }

data class MethodMeshWidgetTarget(val type: MethodMeshWidgetTargetType, val id: String, val fallbackLabel: String = "") {
    init { require(type != MethodMeshWidgetTargetType.BUNDLE) { "Bundles cannot contain bundles" }; require(id.isNotBlank()) { "Target id cannot be blank" } }
}

data class MethodMeshWidgetConfig(
    val appWidgetId: Int,
    val label: String,
    val targetType: MethodMeshWidgetTargetType,
    val targetId: String,
    val iconKey: MethodMeshWidgetIconKey = MethodMeshWidgetIconKey.AUTO,
    val colour: MethodMeshWidgetColour = MethodMeshWidgetColour.TEAL,
    val appearance: MethodMeshWidgetAppearance = MethodMeshWidgetAppearance.SOLID,
    val bundleTargets: List<MethodMeshWidgetTarget> = emptyList()
)

object MethodMeshWidgetRepository {
    private const val PREFS = "methodmesh_home_widgets"
    private const val PREFIX = "widget_"

    fun save(context: Context, config: MethodMeshWidgetConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(config.appWidgetId), encode(config).toString()).apply()
    }
    fun get(context: Context, id: Int): MethodMeshWidgetConfig? = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(id), null) ?: return null
        decode(JSONObject(raw))
    }.getOrNull()
    fun delete(context: Context, id: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(id))
            .remove("expanded_$id")
            .remove("position_$id")
            .apply()
    }

    fun resolveTitle(context: Context, c: MethodMeshWidgetConfig) = c.label.ifBlank { if (c.targetType == MethodMeshWidgetTargetType.BUNDLE) "MethodMesh" else resolveTargetName(context, MethodMeshWidgetTarget(c.targetType, c.targetId)).ifBlank { "MethodMesh" } }
    fun resolveSubtitle(context: Context, c: MethodMeshWidgetConfig): String = when (c.targetType) {
        MethodMeshWidgetTargetType.PRESET -> ""
        MethodMeshWidgetTargetType.PROTOCOL -> ""
        MethodMeshWidgetTargetType.SCHEDULE -> if (isScheduleRunning(context, c.targetId)) "Schedule on" else "Schedule off"
        MethodMeshWidgetTargetType.BUNDLE -> ""
    }
    fun resolveTargetName(context: Context, target: MethodMeshWidgetTarget): String = when (target.type) {
        MethodMeshWidgetTargetType.PRESET -> ProtocolLibraryRepository.preset(context, target.id)?.name
        MethodMeshWidgetTargetType.PROTOCOL -> ProtocolLibraryRepository.protocol(context, target.id)?.name
        MethodMeshWidgetTargetType.SCHEDULE -> SchedulePlanStore.plan(context, target.id)?.name
        MethodMeshWidgetTargetType.BUNDLE -> null
    }.orEmpty().ifBlank { target.fallbackLabel }
    fun targetExists(context: Context, target: MethodMeshWidgetTarget) = when (target.type) {
        MethodMeshWidgetTargetType.PRESET -> ProtocolLibraryRepository.preset(context, target.id) != null
        MethodMeshWidgetTargetType.PROTOCOL -> ProtocolLibraryRepository.protocol(context, target.id) != null
        MethodMeshWidgetTargetType.SCHEDULE -> SchedulePlanStore.plan(context, target.id) != null
        MethodMeshWidgetTargetType.BUNDLE -> false
    }
    fun targetSubtitle(context: Context, target: MethodMeshWidgetTarget): String = if (!targetExists(context, target)) "Unavailable" else when (target.type) {
        MethodMeshWidgetTargetType.PRESET -> ""
        MethodMeshWidgetTargetType.PROTOCOL -> ""
        MethodMeshWidgetTargetType.SCHEDULE -> if (isScheduleRunning(context, target.id)) "Schedule on" else "Schedule off"
        MethodMeshWidgetTargetType.BUNDLE -> ""
    }
    fun resolveIconKey(context: Context, c: MethodMeshWidgetConfig): MethodMeshWidgetIconKey {
        if (c.iconKey != MethodMeshWidgetIconKey.AUTO) return c.iconKey
        if (c.targetType == MethodMeshWidgetTargetType.BUNDLE) return MethodMeshWidgetIconKey.METHODMESH
        return resolveTargetIconKey(context, MethodMeshWidgetTarget(c.targetType, c.targetId))
    }
    fun resolveTargetIconKey(context: Context, target: MethodMeshWidgetTarget): MethodMeshWidgetIconKey = when (target.type) {
        MethodMeshWidgetTargetType.SCHEDULE -> MethodMeshWidgetIconKey.SCHEDULE
        MethodMeshWidgetTargetType.PROTOCOL -> MethodMeshWidgetIconKey.METHODMESH
        MethodMeshWidgetTargetType.PRESET -> {
            val preset = ProtocolLibraryRepository.preset(context, target.id)
            val module = preset?.methodId?.let { id -> MethodMeshModuleRegistry.all().firstOrNull { m -> m.as100Methods().any { it.id == id } || m.capabilityScreens().any { it.capabilityId == id } } }
            iconKeyFor(module?.iconKey ?: preset?.methodId.orEmpty())
        }
        MethodMeshWidgetTargetType.BUNDLE -> MethodMeshWidgetIconKey.METHODMESH
    }
    fun scheduleTargets(context: Context) = SchedulePlanStore.allPlans(context).sortedBy { it.name.lowercase() }
    private fun isScheduleRunning(context: Context, id: String) = SchedulePlanStore.allInstances(context).any { it.planId == id && it.stoppedAt == null }

    private fun encode(c: MethodMeshWidgetConfig) = JSONObject().apply {
        put("appWidgetId", c.appWidgetId); put("label", c.label); put("targetType", c.targetType.name); put("targetId", c.targetId)
        put("iconKey", c.iconKey.name); put("colour", c.colour.name); put("appearance", c.appearance.name)
        put("bundleTargets", JSONArray().apply { c.bundleTargets.forEach { t -> put(JSONObject().put("type", t.type.name).put("id", t.id).put("fallbackLabel", t.fallbackLabel)) } })
    }
    private fun decode(root: JSONObject): MethodMeshWidgetConfig {
        val type = runCatching { MethodMeshWidgetTargetType.valueOf(root.optString("targetType")) }.getOrDefault(MethodMeshWidgetTargetType.PRESET)
        val targets = buildList {
            val a = root.optJSONArray("bundleTargets") ?: JSONArray()
            for (i in 0 until a.length()) runCatching {
                val o = a.getJSONObject(i); val t = MethodMeshWidgetTargetType.valueOf(o.getString("type")); val id = o.getString("id")
                if (t != MethodMeshWidgetTargetType.BUNDLE && id.isNotBlank()) add(MethodMeshWidgetTarget(t, id, o.optString("fallbackLabel")))
            }
        }.distinctBy { it.type to it.id }
        return MethodMeshWidgetConfig(root.optInt("appWidgetId"), root.optString("label"), type, root.optString("targetId"), MethodMeshWidgetIconKey.normalize(root.optString("iconKey")), MethodMeshWidgetColour.normalize(root.optString("colour")), MethodMeshWidgetAppearance.normalize(root.optString("appearance")), targets)
    }
    private fun key(id: Int) = "$PREFIX$id"
    private fun iconKeyFor(value: String): MethodMeshWidgetIconKey { val l=value.lowercase(); return when {
        "reference" in l||"library" in l->MethodMeshWidgetIconKey.BOOK; "document" in l||"pdf" in l->MethodMeshWidgetIconKey.DOCUMENT; "scan" in l||"barcode" in l||"qr" in l->MethodMeshWidgetIconKey.SCAN
        "photo" in l||"image" in l||"camera" in l->MethodMeshWidgetIconKey.IMAGE; "gps" in l||"location" in l||"plus" in l->MethodMeshWidgetIconKey.GPS; "compass" in l||"survey" in l||"geocach" in l->MethodMeshWidgetIconKey.COMPASS
        "translate" in l||"language" in l->MethodMeshWidgetIconKey.TRANSLATE; "speech" in l||"conversation" in l||"voice" in l->MethodMeshWidgetIconKey.SPEECH; "bluetooth" in l||"sensor" in l||"esp" in l->MethodMeshWidgetIconKey.BLUETOOTH
        "network" in l||"api" in l||"web" in l->MethodMeshWidgetIconKey.NETWORK; "printer" in l->MethodMeshWidgetIconKey.PRINT; "nfc" in l->MethodMeshWidgetIconKey.NFC; "random" in l||"dice" in l||"sampling" in l||"chance" in l->MethodMeshWidgetIconKey.RANDOM
        "coin" in l->MethodMeshWidgetIconKey.COIN; "game" in l||"arcade" in l->MethodMeshWidgetIconKey.GAME; "schedule" in l||"time" in l->MethodMeshWidgetIconKey.SCHEDULE; "medical" in l||"clinical" in l->MethodMeshWidgetIconKey.MEDICAL
        "emergency" in l->MethodMeshWidgetIconKey.EMERGENCY; "weather" in l->MethodMeshWidgetIconKey.WEATHER; "statistics" in l||"stats" in l->MethodMeshWidgetIconKey.STATISTICS; "laboratory" in l||"lab" in l->MethodMeshWidgetIconKey.LAB
        "calculation" in l||"conversion" in l->MethodMeshWidgetIconKey.CALCULATE; "data" in l||"json" in l||"csv" in l->MethodMeshWidgetIconKey.DATA; "music" in l||"acoustic" in l||"sound" in l->MethodMeshWidgetIconKey.MUSIC
        "text" in l->MethodMeshWidgetIconKey.TEXT; "sign" in l||"attestation" in l||"timestamp" in l->MethodMeshWidgetIconKey.SIGNING; "security" in l||"fingerprint" in l->MethodMeshWidgetIconKey.SECURITY; "counter" in l||"scoring" in l->MethodMeshWidgetIconKey.COUNTER
        "magnif" in l->MethodMeshWidgetIconKey.MAGNIFY; "teaching" in l||"tamagotchi" in l->MethodMeshWidgetIconKey.EDUCATION; "inspector" in l||"tool" in l||"utility" in l->MethodMeshWidgetIconKey.TOOL; else->MethodMeshWidgetIconKey.METHODMESH }
    }
}
