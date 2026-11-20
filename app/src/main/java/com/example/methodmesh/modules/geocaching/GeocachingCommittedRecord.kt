package com.example.methodmesh.modules.geocaching

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.methodmesh.core.methodmesh.ExecutionResult

/** One frozen execution per reviewed snapshot; never replay a lost record on restoration. */
internal class GeocachingCommittedRecord {
    private var snapshotKey: Any? = null
    var result by mutableStateOf<ExecutionResult?>(null)
        private set

    fun resolve(committed: Boolean, key: Any?, allowNew: Boolean, create: (() -> ExecutionResult)?): ExecutionResult? {
        if (!committed) { snapshotKey = null; result = null; return null }
        if (result != null && snapshotKey == key) return result
        if (result == null && !allowNew) return null
        result = create?.invoke()
        snapshotKey = key
        return result
    }
}

internal class GeocachingCommittedRecords : ViewModel() {
    private val records = mutableMapOf<String, GeocachingCommittedRecord>()
    fun slot(key: String) = records.getOrPut(key) { GeocachingCommittedRecord() }
    fun forget(key: String) { records.remove(key) }
}
