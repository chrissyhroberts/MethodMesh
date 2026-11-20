package com.example.methodmesh.transport

/** SAF cannot promise atomic replacement. Keep a readable recovery copy before any overwrite. */
internal object RecoverableExportReplace {
    fun write(previous: ByteArray, replacement: ByteArray, backup: () -> String,
              overwrite: (ByteArray) -> Unit, removeBackup: (String) -> Unit) {
        val recovery = backup() // If this fails, do not touch the previous document.
        try {
            overwrite(replacement)
        } catch (failure: Exception) {
            try {
                overwrite(previous)
            } catch (restoreFailure: Exception) {
                failure.addSuppressed(restoreFailure)
                throw IllegalStateException("Protocol export failed. A recovery copy of the previous history was retained at $recovery", failure)
            }
            runCatching { removeBackup(recovery) }
            throw IllegalStateException("Protocol export failed. The previous history was restored.", failure)
        }
        // A leftover recovery copy is safer than reporting a completed write as failed
        // and inviting a repeat of an already committed protocol step.
        runCatching { removeBackup(recovery) }
    }
}
