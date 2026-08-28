package com.example.methodmesh.settings

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val Context.displaySettingsDataStore by preferencesDataStore(
    name = "display_settings"
)

data class DisplaySettings(
    val textScale: Float = 1.3f
)

object DisplaySettingsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val textScaleKey = floatPreferencesKey("text_scale")
    private var appContext: Context? = null

    var settings = mutableStateOf(DisplaySettings())
        private set

    fun initialise(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            settings.value = load(context)
        }
    }

    fun initialiseBlocking(context: Context) {
        appContext = context.applicationContext
        settings.value = runBlocking(Dispatchers.IO) { load(context) }
    }

    fun updateTextScale(scale: Float) {
        val updated = DisplaySettings(textScale = scale.coerceIn(1.0f, 1.9f))
        settings.value = updated
        val context = appContext ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                context.displaySettingsDataStore.edit { preferences ->
                    preferences[textScaleKey] = updated.textScale
                }
            }
        }
    }

    private suspend fun load(context: Context): DisplaySettings {
        val preferences = context.displaySettingsDataStore.data.first()
        return DisplaySettings(
            textScale = (preferences[textScaleKey] ?: DisplaySettings().textScale).coerceIn(1.0f, 1.9f)
        )
    }
}
