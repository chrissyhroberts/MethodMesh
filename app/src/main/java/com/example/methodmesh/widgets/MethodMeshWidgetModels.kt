package com.example.methodmesh.widgets

import android.content.Context
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.core.scheduling.SchedulerRepository
import com.example.methodmesh.core.scheduling.SchedulerTarget
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import org.json.JSONObject

enum class MethodMeshWidgetIconKey(val title: String) {
    AUTO("Auto"),
    METHODMESH("MethodMesh"),
    DOCUMENT("Document"),
    LOCATION("Location"),
    LANGUAGE("Language"),
    HARDWARE("Hardware"),
    RANDOM("Random"),
    SCHEDULE("Schedule"),
    TOOL("Tool");

    companion object {
        fun normalize(value: String): MethodMeshWidgetIconKey =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: entries.firstOrNull { it.title.equals(value, ignoreCase = true) }
                ?: AUTO
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
    val iconKey: MethodMeshWidgetIconKey = MethodMeshWidgetIconKey.AUTO
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
            val schedule = SchedulerRepository.get(context, config.targetId)
            if (schedule?.enabled == true) "Schedule on" else "Schedule off"
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

    fun resolveTargetName(context: Context, config: MethodMeshWidgetConfig): String = when (config.targetType) {
        MethodMeshWidgetTargetType.PRESET ->
            ProtocolLibraryRepository.preset(context, config.targetId)?.name.orEmpty()
        MethodMeshWidgetTargetType.PROTOCOL ->
            ProtocolLibraryRepository.protocol(context, config.targetId)?.name.orEmpty()
        MethodMeshWidgetTargetType.SCHEDULE ->
            SchedulerRepository.get(context, config.targetId)?.let { schedule ->
                if (schedule.chainId.isBlank()) schedule.name
                else SchedulerRepository.all(context)
                    .filter { it.chainId == schedule.chainId }
                    .minByOrNull { it.chainOrder }
                    ?.name
                    ?: schedule.name
            }.orEmpty()
    }

    fun scheduleTargets(context: Context) =
        SchedulerRepository.all(context)
            .filter { it.chainId.isBlank() || it.chainOrder <= 0 }
            .filter { it.target != SchedulerTarget.CLIPBOARD || it.name.isNotBlank() }
            .sortedBy { it.name.lowercase() }

    private fun encode(config: MethodMeshWidgetConfig) = JSONObject().apply {
        put("appWidgetId", config.appWidgetId)
        put("label", config.label)
        put("targetType", config.targetType.name)
        put("targetId", config.targetId)
        put("iconKey", config.iconKey.name)
    }

    private fun decode(root: JSONObject): MethodMeshWidgetConfig =
        MethodMeshWidgetConfig(
            appWidgetId = root.optInt("appWidgetId"),
            label = root.optString("label"),
            targetType = runCatching {
                MethodMeshWidgetTargetType.valueOf(root.optString("targetType"))
            }.getOrDefault(MethodMeshWidgetTargetType.PRESET),
            targetId = root.optString("targetId"),
            iconKey = MethodMeshWidgetIconKey.normalize(root.optString("iconKey"))
        )

    private fun key(appWidgetId: Int): String = "$PREFIX$appWidgetId"

    private fun iconKeyFor(value: String): MethodMeshWidgetIconKey {
        val lower = value.lowercase()
        return when {
            "document" in lower || "scan" in lower || "photo" in lower || "image" in lower ->
                MethodMeshWidgetIconKey.DOCUMENT
            "gps" in lower || "location" in lower || "plus" in lower || "compass" in lower ->
                MethodMeshWidgetIconKey.LOCATION
            "translate" in lower || "language" in lower || "speech" in lower || "conversation" in lower ->
                MethodMeshWidgetIconKey.LANGUAGE
            "sensor" in lower || "bluetooth" in lower || "printer" in lower || "nfc" in lower || "esp" in lower ->
                MethodMeshWidgetIconKey.HARDWARE
            "random" in lower || "dice" in lower || "sampling" in lower ->
                MethodMeshWidgetIconKey.RANDOM
            "schedule" in lower ->
                MethodMeshWidgetIconKey.SCHEDULE
            "inspector" in lower || "tool" in lower || "utility" in lower ->
                MethodMeshWidgetIconKey.TOOL
            else -> MethodMeshWidgetIconKey.METHODMESH
        }
    }
}
