package com.example.methodmesh.modules.astronomy

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal data class AstronomySessionReturn(
    val action: String, val requestContext: Map<String, String>, val invocation: InvocationContext
)

/** One UI run, owned by the Activity. Workers retain application storage and immutable data only. */
internal class AstronomySessionState(private val repo: AstronomyRepository, private val scope: CoroutineScope) {
    var active by mutableStateOf<AstronomyRepository.ActiveSession?>(null)
        private set
    var result by mutableStateOf<ExecutionResult?>(null)
        private set
    var initialized by mutableStateOf(false)
        private set
    var loadVersion by mutableStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("Loading unfinished session…")
        private set
    private val serial = Mutex()

    private data class Change(val active: AstronomyRepository.ActiveSession?, val result: ExecutionResult? = null,
        val message: String, val reload: Boolean = false)

    private fun transition(label: String, work: (AstronomyRepository.ActiveSession?) -> Change) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                serial.withLock {
                    val current = active
                    val changed = withContext(Dispatchers.IO) { work(current) }
                    currentCoroutineContext().ensureActive()
                    active = changed.active
                    result = changed.result
                    initialized = true
                    if (changed.reload) loadVersion++
                    status = changed.message
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { status = label + ": " + (failure.message ?: "storage unavailable") }
            finally { busy = false }
        }
    }

    fun load() = transition("Session load failed") {
        val stored = repo.loadActiveSession()
        Change(stored, message = if (stored == null) "Ready to start." else "Unfinished session restored: " + stored.target, reload = true)
    }

    private fun metadata(session: AstronomyRepository.ActiveSession, fields: Map<String, String>) =
        session.copy(target = fields["target"] ?: session.target,
            equipment = fields["equipment"].orEmpty(), notes = fields["notes"].orEmpty())

    fun saveFields(expectedId: String, fields: Map<String, String>) {
        val frozen = fields.toMap()
        scope.launch {
            serial.withLock {
                val current = active?.takeIf { it.id == expectedId } ?: return@withLock
                val changed = metadata(current, frozen)
                if (changed == current) return@withLock
                try {
                    val saved = withContext(Dispatchers.IO) { repo.updateActiveSession(changed) }
                    currentCoroutineContext().ensureActive()
                    active = saved
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { status = "Session edits not saved: " + failure.message }
            }
        }
    }

    fun start(fields: Map<String, String>) {
        if (!initialized || active != null || result != null) return
        val frozen = fields.toMap()
        transition("Start failed") {
            Change(repo.startSession(frozen["target"].orEmpty().ifBlank { "Unspecified target" },
                frozen["equipment"].orEmpty(), frozen["notes"].orEmpty()), message = "Session started.")
        }
    }

    fun pause(fields: Map<String, String>) {
        val frozen = fields.toMap()
        transition("Pause failed") { current ->
            Change(repo.pauseSession(metadata(checkNotNull(current) { "No active session." }, frozen)), message = "Session paused.")
        }
    }

    fun resume(fields: Map<String, String>) {
        val frozen = fields.toMap()
        transition("Resume failed") { current ->
            Change(repo.resumeSession(metadata(checkNotNull(current) { "No active session." }, frozen)), message = "Session resumed.")
        }
    }

    fun stop(fields: Map<String, String>, target: AstronomySessionReturn) {
        val frozen = fields.toMap()
        val saveHistory = frozen["save_session"]?.toBooleanStrictOrNull() ?: false
        transition("Stop/save failed") { current ->
            val execution = repo.completeSession(metadata(checkNotNull(current) { "No active session." }, frozen), saveHistory) { finished ->
                val calculation = frozen + mapOf("session_id" to finished.getString("session_id"),
                    "session_state" to "finished", "target" to finished.optString("target"),
                    "start_iso" to finished.getString("start_iso"), "end_iso" to finished.getString("end_iso"),
                    "active_duration_seconds" to finished.getDouble("active_duration_seconds").toString(),
                    "equipment" to finished.optString("equipment"), "notes" to finished.optString("notes"),
                    "save_session" to saveHistory.toString())
                val values = As100SessionMethod.calculate(calculation)
                val request = As100SessionMethod.request(target.action, target.requestContext + calculation, emptyList(), emptyList())
                val original = As100SessionMethod.result(request, values, target.invocation)
                JSONObject(values.getValue(SessionFields.JSON)) to original
            }
            Change(null, execution, "Session stopped.")
        }
    }

    fun discard() {
        transition("Discard failed") { current ->
            repo.discardActiveSession(checkNotNull(current) { "No active session." }.id)
            Change(null, message = "Unfinished session discarded.")
        }
    }

    fun newRun() { if (!busy) { result = null; status = "Ready." } }
}

internal class AstronomySessionStore : ViewModel() {
    private val runs = mutableMapOf<String, AstronomySessionState>()
    fun run(key: String, repo: AstronomyRepository) = runs.getOrPut(key) { AstronomySessionState(repo, viewModelScope) }
    fun forget(key: String) { runs.remove(key) }
    fun snapshot() = runs.toMap()
}
