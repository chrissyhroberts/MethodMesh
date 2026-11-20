package com.example.methodmesh.transport.workflow

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PresetLogRecorderTest {
    private class MemoryStorage : PresetLogRecorder.Storage {
        override val lock = Any()
        val text = mutableMapOf("log" to "", "summary" to "")
        val media = mutableListOf<JSONObject>()
        var failAppend: String? = null
        var failAfterAppend = false
        var failMedia = false
        override fun read(ref: String) = text.getValue(ref)
        override fun append(ref: String, text: String) {
            if (ref == failAppend && !failAfterAppend) error("storage failure")
            this.text[ref] = this.text.getValue(ref) + text
            if (ref == failAppend) error("metadata failure after write")
        }
        override fun copyMedia(source: String, name: String, collectionId: String, entryId: String, mediaId: String): JSONObject {
            if (failMedia) error("unreadable media")
            return JSONObject().put("artifact_ref", "media-${media.size}").put("entry_id", entryId)
                .put("media_id", mediaId).put("filename", name).also { media += it }
        }
        fun entries() = text.getValue("log").lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()
    }
    private fun result(media: Boolean = false, answer: String = "Captured answer") = ExecutionResult(
        ExecutionRequest(action = "test.log", method = ArchitectureRef(ArchitectureId("test.log"), "Method")),
        TransformationStatus.Succeeded, observations = listOf(Observation(phenomenon = "test",
            values = if (media) mapOf("answer" to answer, "file_uri" to "content://test/file.pdf") else mapOf("answer" to answer),
            provenance = ProvenanceContext("test"))))

    @Test fun `transient default performs no persistence`() {
        val store = MemoryStorage()
        assertNull(PresetLogRecorder(store).record("", "summary", result(true)).warning)
        assertTrue(store.entries().isEmpty())
        assertTrue(store.media.isEmpty())
    }

    @Test fun `misconfigured summary cannot append plain text into the JSONL artifact`() {
        val store = MemoryStorage()
        assertNotNull(PresetLogRecorder(store).record("log", "log", result(true)).warning)
        assertTrue(store.entries().isEmpty())
        assertTrue(store.media.isEmpty())
    }

    @Test fun `legacy incomplete entry is not silently claimed complete or rewritten`() {
        val store = MemoryStorage()
        val result = result(true)
        val legacy = JSONObject().put("entry_id", result.request.id.value).put("fields", JSONObject().put("answer", "Captured answer")).toString() + "\n"
        store.text["log"] = legacy
        assertTrue(PresetLogRecorder(store).record("log", "summary", result).warning!!.contains("incomplete"))
        assertEquals(legacy, store.text.getValue("log"))
        assertTrue(store.media.isEmpty())
    }

    @Test fun `entry retains exact full evidence and linked media with stable execution identity`() {
        val store = MemoryStorage()
        val result = result(true)
        assertNull(PresetLogRecorder(store).record("log", "summary", result).warning)
        val entry = store.entries().single()
        assertEquals(result.request.id.value, entry.getString("entry_id"))
        assertEquals(OutputFormatter.format(result, ReturnMode.Json, true, payloadMode = OutputFormatter.PayloadMode.FULL), entry.getString("result_json"))
        assertEquals(1, entry.getJSONArray("media").length())
        assertEquals(result.request.id.value, store.media.single().getString("entry_id"))
        assertTrue(store.text.getValue("summary").contains("Captured answer"))
    }

    @Test fun `repeated execution is idempotent across independent writers`() {
        val store = MemoryStorage()
        val result = result(true)
        repeat(3) { assertNull(PresetLogRecorder(store).record("log", "summary", result).warning) }
        assertEquals(1, store.entries().size)
        assertEquals(1, store.media.size)
        assertEquals(1, store.text.getValue("summary").lines().count { it.isNotBlank() })
    }

    @Test fun `concurrent delivery serializes read copy append for a shared store`() {
        val store = MemoryStorage()
        val result = result(true)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..12).map { pool.submit<PresetLogRecorder.Outcome> { PresetLogRecorder(store).record("log", "summary", result) } }
            tasks.forEach { assertNull(it.get(5, TimeUnit.SECONDS).warning) }
            assertEquals(1, store.entries().size)
            assertEquals(1, store.media.size)
        } finally { pool.shutdownNow() }
    }

    @Test fun `media failure is recorded and remains an explicit warning on repeated delivery`() {
        val store = MemoryStorage().apply { failMedia = true }
        val recorder = PresetLogRecorder(store)
        val result = result(true)
        repeat(2) { assertTrue(recorder.record("log", "summary", result).warning!!.contains("missing media")) }
        assertEquals(1, store.entries().size)
        assertEquals(1, store.entries().single().getJSONArray("media_errors").length())
        assertEquals(TransformationStatus.Succeeded, result.status)
    }

    @Test fun `summary failure can be repaired without duplicate entry or media`() {
        val store = MemoryStorage().apply { failAppend = "summary" }
        val result = result(true)
        val recorder = PresetLogRecorder(store)
        assertNotNull(recorder.record("log", "summary", result).warning)
        val original = store.text.getValue("log")
        store.failAppend = null
        assertNull(recorder.record("log", "summary", result).warning)
        assertEquals(original, store.text.getValue("log"))
        assertEquals(1, store.media.size)
        assertTrue(store.text.getValue("summary").contains("Captured answer"))
    }

    @Test fun `append then throw does not delete linked media or duplicate committed execution`() {
        val store = MemoryStorage().apply { failAppend = "log"; failAfterAppend = true }
        val result = result(true)
        val recorder = PresetLogRecorder(store)
        assertNotNull(recorder.record("log", "summary", result).warning)
        assertEquals(1, store.entries().size)
        assertEquals(1, store.media.size)
        store.failAppend = null
        assertNull(recorder.record("log", "summary", result).warning)
        assertEquals(1, store.entries().size)
        assertEquals(1, store.media.size)
    }

    @Test fun `primary append failure is explicit without changing original evidence`() {
        val store = MemoryStorage().apply { failAppend = "log" }
        val result = result()
        val full = OutputFormatter.format(result, ReturnMode.Json)
        assertNotNull(PresetLogRecorder(store).record("log", "summary", result).warning)
        assertTrue(store.entries().isEmpty())
        assertEquals(full, OutputFormatter.format(result, ReturnMode.Json))
        assertTrue(store.text.getValue("summary").isEmpty())
    }

    @Test fun `malformed or unterminated log is not extended and produces a warning`() {
        listOf("broken\n", "{\"entry_id\":\"old\"}").forEach { damaged ->
            val store = MemoryStorage().apply { text["log"] = damaged }
            assertNotNull(PresetLogRecorder(store).record("log", "summary", result(true)).warning)
            assertEquals(damaged, store.text.getValue("log"))
            assertTrue(store.media.isEmpty())
        }
    }

    @Test fun `multiline result summary stays one idempotent human readable line`() {
        val store = MemoryStorage()
        val result = result(answer = "first\nsecond\rthird")
        val recorder = PresetLogRecorder(store)
        repeat(2) { assertNull(recorder.record("log", "summary", result).warning) }
        assertEquals(1, store.text.getValue("summary").lines().count { it.isNotBlank() })
        assertTrue(store.text.getValue("summary").contains("first\\nsecond\\rthird"))
    }
}
