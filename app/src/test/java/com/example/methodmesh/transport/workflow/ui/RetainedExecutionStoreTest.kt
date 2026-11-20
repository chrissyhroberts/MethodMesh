package com.example.methodmesh.transport.workflow.ui

import com.example.methodmesh.core.methodmesh.*
import org.junit.Assert.*
import org.junit.Test

class RetainedExecutionStoreTest {
    private fun result() = ExecutionResult(
        ExecutionRequest(action = "fixture", method = ArchitectureRef(ArchitectureId("fixture"), "Method")),
        TransformationStatus.Succeeded
    )

    @Test fun `the same run retains the original object rather than reconstructing it`() {
        val store = RetainedExecutionStore()
        val original = result()
        store.slot("one").value = original
        assertSame(original, store.slot("one").value)
        assertSame(original, store.snapshot()["one"])
    }

    @Test fun `different run slots cannot reuse each others result`() {
        val store = RetainedExecutionStore()
        store.slot("one").value = result()
        assertNull(store.slot("two").value)
    }

    @Test fun `replacing a working result does not mutate a separately committed execution`() {
        val store = RetainedExecutionStore()
        val original = result()
        store.slot("working").value = original
        store.slot("committed").value = original
        store.slot("working").value = result()
        assertSame(original, store.slot("committed").value)
        assertNotEquals(original.request.id, store.slot("working").value!!.request.id)
    }

    @Test fun `normal screen disposal releases only its own slot and does not restore a fabricated result`() {
        val store = RetainedExecutionStore()
        store.slot("one").value = result()
        val other = result()
        store.slot("two").value = other
        store.forget("one")
        assertSame(other, store.slot("two").value)
        assertNull(store.slot("one").value)
        assertNull(RetainedExecutionStore().slot("two").value)
    }
}
