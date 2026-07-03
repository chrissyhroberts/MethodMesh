package com.example.researchos.settings

import androidx.compose.runtime.mutableStateMapOf

class SettingsState(
    settings: List<MethodSetting>,
    private val onValueChanged: (String, Any) -> Unit = { _, _ -> }
) {
    private val values = mutableStateMapOf<String, Any>()

    init {
        settings.forEach { setting ->
            values[setting.id] = setting.defaultValue()
        }
    }

    fun getString(id: String): String =
        values[id] as? String ?: ""

    fun setString(id: String, value: String) {
        setValue(id, value)
    }

    fun getBoolean(id: String): Boolean =
        values[id] as? Boolean ?: false

    fun setBoolean(id: String, value: Boolean) {
        setValue(id, value)
    }

    fun getFloat(id: String): Float =
        values[id] as? Float ?: 0f

    fun setFloat(id: String, value: Float) {
        setValue(id, value)
    }

    fun getInt(id: String): Int =
        values[id] as? Int ?: 0

    fun setInt(id: String, value: Int) {
        setValue(id, value)
    }

    fun restore(
        restoredValues: Map<String, Any>
    ) {
        restoredValues.forEach { (id, value) ->
            values[id] = value
        }
    }

    fun asMap(): Map<String, Any> =
        values.toMap()

    private fun setValue(
        id: String,
        value: Any
    ) {
        values[id] = value
        onValueChanged(id, value)
    }
}

fun MethodSetting.defaultValue(): Any {
    return when (this) {
        is MethodSetting.BooleanSetting -> defaultValue
        is MethodSetting.IntSetting -> defaultValue
        is MethodSetting.FloatSetting -> defaultValue
        is MethodSetting.TextSetting -> defaultValue
        is MethodSetting.ChoiceSetting -> defaultValue
    }
}
