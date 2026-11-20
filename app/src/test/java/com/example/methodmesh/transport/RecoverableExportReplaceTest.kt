package com.example.methodmesh.transport

import org.junit.Assert.*
import org.junit.Test

class RecoverableExportReplaceTest {
    private val old = "original history".toByteArray()
    private val next = "complete next history".toByteArray()

    @Test fun `backup failure leaves original untouched`() {
        var touched = false
        assertTrue(runCatching { RecoverableExportReplace.write(old, next, { error("backup unavailable") }, { touched = true }, {}) }.isFailure)
        assertFalse(touched)
    }

    @Test fun `success replaces bytes only after readable backup exists`() {
        var backup = false
        var bytes = old
        RecoverableExportReplace.write(old, next, { backup = true; "recovery" }, { assertTrue(backup); bytes = it }, { backup = false })
        assertArrayEquals(next, bytes)
        assertFalse(backup)
    }

    @Test fun `partial replacement failure restores original and reports failure`() {
        var bytes = old
        var calls = 0
        var backup = false
        val failure = runCatching { RecoverableExportReplace.write(old, next, { backup = true; "recovery" }, {
            calls++
            if (calls == 1) { bytes = "partial".toByteArray(); error("disk full") }
            bytes = it
        }, { backup = false }) }.exceptionOrNull()
        assertTrue(failure!!.message!!.contains("previous history was restored"))
        assertArrayEquals(old, bytes)
        assertFalse(backup)
    }

    @Test fun `failed restoration retains recovery copy and reports its location`() {
        var backup = false
        val failure = runCatching { RecoverableExportReplace.write(old, next, { backup = true; "content://review/recovery" }, { error("provider unavailable") }, { backup = false }) }.exceptionOrNull()
        assertTrue(backup)
        assertTrue(failure!!.message!!.contains("content://review/recovery"))
        assertEquals(1, failure.cause!!.suppressed.size)
    }

    @Test fun `failed backup cleanup never destroys or repeats successful replacement`() {
        var writes = 0
        var bytes = old
        RecoverableExportReplace.write(old, next, { "recovery" }, { writes++; bytes = it }, { error("cleanup denied") })
        assertEquals(1, writes)
        assertArrayEquals(next, bytes)
    }
}
