package com.example.methodmesh.modules.livestreamtranslate

/** Pure revision gate used to prevent stale asynchronous stream work from winning. */
internal class StreamingRevisionGate {
    private var currentRevision = 0L

    fun nextRevision(): Long {
        currentRevision += 1
        return currentRevision
    }

    fun invalidate(): Long = nextRevision()

    fun isCurrent(revision: Long): Boolean = revision == currentRevision

    fun current(): Long = currentRevision
}
