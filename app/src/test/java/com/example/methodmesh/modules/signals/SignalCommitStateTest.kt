package com.example.methodmesh.modules.signals

import androidx.compose.runtime.mutableStateOf
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SignalCommitStateTest {
    private val methods = SignalsModule.as100Methods().filter { it.id != As100SignalSensorScopeMethod.id }
    private fun result(method: SignalsMethod): ExecutionResult {
        val fields = method.descriptor.outputs.associateWith { key ->
            when {
                key.endsWith("_status") -> if (method.id.endsWith("transmit")) "sent" else "received"
                key.endsWith("_error") -> ""
                else -> "Frozen fixture"
            }
        }
        return method.result(ExecutionRequest(action = method.id, method = method.ref), fields, null)
    }
    private fun full(result: ExecutionResult) = OutputFormatter.format(result, ReturnMode.Json,
        includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL)

    @Test fun `all sixteen physical contracts retain the original envelope and only canonical fields`() {
        assertEquals(16, methods.size)
        methods.forEach { method ->
            val commit = SignalCommitState(mutableStateOf(null), mutableStateOf(false))
            val original = result(method)
            val json = full(original)
            assertSame(original, commit.capture(original))
            assertSame(original, commit.result)
            assertEquals(method.descriptor.outputs.toSet(), commit.fields.keys)
            assertSame(original.observations.single().values, commit.fields)
            assertEquals(json, full(requireNotNull(commit.result)))
            assertFalse(commit.missing)
        }
    }

    @Test fun `recreating the holder over retained state cannot reconstruct an execution`() {
        methods.forEach { method ->
            val retained = mutableStateOf<ExecutionResult?>(null)
            val marker = mutableStateOf(false)
            val original = SignalCommitState(retained, marker).capture(result(method))
            val json = full(original)
            val recreated = SignalCommitState(retained, marker)
            assertSame(original, recreated.result)
            assertEquals(json, full(requireNotNull(recreated.result)))
            assertFalse(recreated.missing)
        }
    }

    @Test fun `explicit identical-value recommit replaces identity without mutating the previous capture`() {
        methods.forEach { method ->
            val commit = SignalCommitState(mutableStateOf(null), mutableStateOf(false))
            val original = commit.capture(result(method))
            val json = full(original)
            val next = commit.capture(result(method))
            assertEquals(original.observations.single().values, next.observations.single().values)
            assertNotEquals(original.request.id, next.request.id)
            assertSame(next, commit.result)
            assertEquals(json, full(original))
        }
    }

    @Test fun `a saved marker with a lost envelope is explicitly missing with no fabricated result`() {
        methods.forEach { method ->
            val retained = mutableStateOf<ExecutionResult?>(null)
            val marker = mutableStateOf(false)
            SignalCommitState(retained, marker).capture(result(method))
            retained.value = null // Simulated process/store loss, not a physical session.
            val recovered = SignalCommitState(retained, marker)
            assertTrue(recovered.missing)
            assertNull(recovered.result)
            assertTrue(recovered.fields.isEmpty())
            assertEquals("", fullJson(recovered.result))
            val newExecution = recovered.capture(result(method))
            assertFalse(recovered.missing)
            assertSame(newExecution, recovered.result)
        }
    }

    @Test fun `explicit reset clears both the result and its marker`() {
        methods.forEach { method ->
            val retained = mutableStateOf<ExecutionResult?>(null)
            val marker = mutableStateOf(false)
            val commit = SignalCommitState(retained, marker)
            commit.capture(result(method))
            commit.clear()
            assertNull(commit.result)
            assertTrue(commit.fields.isEmpty())
            assertFalse(commit.missing)
            assertFalse(marker.value)
        }
    }

    @Test fun `one screen cannot replace or clear another screens captured evidence`() {
        val first = SignalCommitState(mutableStateOf(null), mutableStateOf(false))
        val second = SignalCommitState(mutableStateOf(null), mutableStateOf(false))
        val original = first.capture(result(methods.first()))
        second.capture(result(methods.last()))
        second.clear()
        assertSame(original, first.result)
        assertNull(second.result)
    }

    @Test fun `all affected screen bodies use the retained boundary rather than saved JSON reconstruction`() {
        val root = listOf(File("."), File("app")).first { File(it, "src/main/java/com/example/methodmesh/modules/signals").isDirectory }
        val names = listOf("Morse", "Fsk", "Qr", "QrBurst", "Optical", "Surface")
        val sources = names.map { File(root, "src/main/java/com/example/methodmesh/modules/signals/Signal${it}CapabilityScreen.kt").readText() }
        sources.forEach { source -> assertFalse(source.contains("committedJson")) }
        fun count(pattern: String) = sources.sumOf { Regex(pattern).findAll(it).count() }
        assertEquals(14, count("rememberSignalCommit\\(context.action.canonicalId\\)"))
        assertEquals(14, count("SignalCommitRecoveryNotice\\(committed\\)"))
        assertEquals(15, count("committed.capture\\(signalResult\\(")) // Optical receive has two deliberate Commit branches.
    }
}
