package com.example.methodmesh.modules.apiget

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class ApiInputSavedStateTest {
    private val scope = SaverScope { true }
    @Test fun editedInputsRestoreAsExactStrings() {
        val inputs = mutableStateMapOf("latitude" to "52.077900", "note" to "Field α\nsecond line", "optional" to "")
        val saver = apiInputValuesSaver()
        val restored = saver.restore(with(saver) { scope.save(inputs) }!!)!!
        assertEquals(inputs.toMap(), restored.toMap())
        assertNotSame(inputs, restored)
    }
    @Test fun savedInputSnapshotIsNotChangedByLaterEdits() {
        val inputs = mutableStateMapOf("note" to "Original")
        val saver = apiInputValuesSaver()
        val saved = with(saver) { scope.save(inputs) }!!
        inputs["note"] = "Changed"
        assertEquals("Original", saver.restore(saved)!!["note"])
    }
}
