package com.example.methodmesh.modules.countertracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class CounterItem(
    val id: String,
    val name: String,
    val value: Long,
    val step: Long = 1,
    val minimum: Long? = null,
    val maximum: Long? = null,
    val kind: String = "tally"
)

data class StatusFlag(val id: String, val name: String, val value: Boolean)

data class CounterWorkspaceState(
    val counters: List<CounterItem> = listOf(CounterItem("counter-1", "Counter", 0)),
    val flags: List<StatusFlag> = emptyList()
)

object CounterTrackerRepository {
    private const val PREFS = "methodmesh_counter_tracker"
    private const val STATE = "workspace_state"
    private const val HISTORY = "history"

    fun load(context: Context): CounterWorkspaceState = runCatching {
        decode(JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(STATE, "") ?: ""))
    }.getOrElse { CounterWorkspaceState() }

    fun save(context: Context, state: CounterWorkspaceState, recordHistory: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(STATE, encode(state).toString()).apply()
        if (recordHistory) {
            val history = runCatching { JSONArray(prefs.getString(HISTORY, "[]")) }.getOrDefault(JSONArray())
            history.put(JSONObject().apply {
                put("time_iso", Instant.now().toString())
                put("snapshot", encode(state))
            })
            while (history.length() > 200) history.remove(0)
            prefs.edit().putString(HISTORY, history.toString()).apply()
        }
    }

    fun history(context: Context): JSONArray = runCatching {
        JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(HISTORY, "[]"))
    }.getOrDefault(JSONArray())

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(HISTORY).apply()
    }

    fun encode(state: CounterWorkspaceState): JSONObject = JSONObject().apply {
        put("counters", JSONArray().apply {
            state.counters.forEach { c -> put(JSONObject().apply {
                put("id", c.id); put("name", c.name); put("value", c.value); put("step", c.step)
                if (c.minimum != null) put("minimum", c.minimum)
                if (c.maximum != null) put("maximum", c.maximum)
                put("kind", c.kind)
            }) }
        })
        put("flags", JSONArray().apply {
            state.flags.forEach { f -> put(JSONObject().apply { put("id", f.id); put("name", f.name); put("value", f.value) }) }
        })
    }

    fun decode(json: JSONObject): CounterWorkspaceState {
        val countersArray = json.optJSONArray("counters") ?: JSONArray()
        val counters = (0 until countersArray.length()).mapNotNull { i -> countersArray.optJSONObject(i) }.map { o ->
            CounterItem(
                id = o.optString("id", "counter-${o.hashCode()}"), name = o.optString("name", "Counter"), value = o.optLong("value", 0),
                step = o.optLong("step", 1).coerceAtLeast(1),
                minimum = if (o.has("minimum") && !o.isNull("minimum")) o.optLong("minimum") else null,
                maximum = if (o.has("maximum") && !o.isNull("maximum")) o.optLong("maximum") else null,
                kind = o.optString("kind", "tally")
            )
        }
        val flagsArray = json.optJSONArray("flags") ?: JSONArray()
        val flags = (0 until flagsArray.length()).mapNotNull { i -> flagsArray.optJSONObject(i) }.map { o ->
            StatusFlag(o.optString("id", "flag-${o.hashCode()}"), o.optString("name", "Flag"), o.optBoolean("value", false))
        }
        return CounterWorkspaceState(counters.ifEmpty { listOf(CounterItem("counter-1", "Counter", 0)) }, flags)
    }
}
