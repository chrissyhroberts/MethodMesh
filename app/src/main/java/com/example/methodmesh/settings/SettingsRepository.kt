package com.example.methodmesh.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.methodSettingsDataStore by preferencesDataStore(
    name = "method_settings"
)

object SettingsRepository {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    private var appContext: Context? = null

    fun initialise(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun load(
        methodId: String,
        settings: List<MethodSetting>
    ): Map<String, Any> {
        val context = appContext ?: return emptyMap()

        return withContext(Dispatchers.IO) {
            val preferences = context.methodSettingsDataStore.data.first()

            settings.mapNotNull { setting ->
                val key = stringPreferencesKey(keyFor(methodId, setting.id))
                val storedValue = preferences[key] ?: return@mapNotNull null
                parseStoredValue(setting, storedValue)?.let { parsed ->
                    setting.id to parsed
                }
            }.toMap()
        }
    }

    fun save(
        methodId: String,
        settingId: String,
        value: Any
    ) {
        val context = appContext ?: return
        val key = stringPreferencesKey(keyFor(methodId, settingId))
        val storedValue = value.toString()

        scope.launch {
            withContext(Dispatchers.IO) {
                context.methodSettingsDataStore.edit { preferences ->
                    preferences[key] = storedValue
                }
            }
        }
    }

    private fun keyFor(
        methodId: String,
        settingId: String
    ): String = "$methodId.$settingId"

    private fun parseStoredValue(
        setting: MethodSetting,
        storedValue: String
    ): Any? {
        return when (setting) {
            is MethodSetting.BooleanSetting -> storedValue.toBooleanStrictOrNull()
            is MethodSetting.IntSetting -> storedValue.toIntOrNull()?.coerceIn(
                setting.minimum ?: Int.MIN_VALUE,
                setting.maximum ?: Int.MAX_VALUE
            )
            is MethodSetting.FloatSetting -> storedValue.toFloatOrNull()?.coerceIn(
                setting.minimum ?: -Float.MAX_VALUE,
                setting.maximum ?: Float.MAX_VALUE
            )
            is MethodSetting.TextSetting -> storedValue
            is MethodSetting.ChoiceSetting -> {
                if (storedValue in setting.choices) storedValue else setting.defaultValue
            }
            is MethodSetting.MultiChoiceSetting -> storedValue
        }
    }
}
