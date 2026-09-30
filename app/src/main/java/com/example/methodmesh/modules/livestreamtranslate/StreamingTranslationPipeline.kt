package com.example.methodmesh.modules.livestreamtranslate

import android.os.Handler
import android.os.Looper

/**
 * Processor 2 + processor 3 for the experimental streaming capability.
 *
 * Speech recognition (processor 1) emits a revisable stream of partial text.
 * This pipeline conflates those revisions before translation, then admits only
 * the translation belonging to the newest source revision. A slow translation
 * callback can therefore never overwrite a newer phrase.
 *
 * Partial hypotheses are working state only. Final recognizer responses are
 * still handled by the canonical segment path and become the only transcript
 * material eligible for Commit.
 */
internal class StreamingTranslationPipeline(
    private val translationEngine: LiveStreamTranslationEngine,
    private val onProjection: (StreamingTranslationProjection) -> Unit,
    private val onStatus: (String) -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private val revisionGate = StreamingRevisionGate()
    private var pending: Runnable? = null
    private var closed = false

    var submittedRevisionCount: Long = 0
        private set
    var projectedTranslationCount: Long = 0
        private set

    fun submit(sourceLanguage: String, targetLanguage: String, sourceText: String, responseDelayMs: Long) {
        if (closed) return
        val normalized = sourceText.trim()
        if (normalized.isBlank()) {
            clearWorkingProjection()
            return
        }

        val thisRevision = revisionGate.nextRevision()
        submittedRevisionCount += 1
        pending?.let(handler::removeCallbacks)

        onProjection(
            StreamingTranslationProjection(
                revision = thisRevision,
                sourceText = normalized,
                translatedText = "",
                translating = sourceLanguage != targetLanguage
            )
        )

        if (sourceLanguage == targetLanguage) {
            projectedTranslationCount += 1
            onProjection(
                StreamingTranslationProjection(
                    revision = thisRevision,
                    sourceText = normalized,
                    translatedText = normalized,
                    translating = false
                )
            )
            return
        }

        val task = Runnable {
            if (closed || !revisionGate.isCurrent(thisRevision)) return@Runnable
            translationEngine.translate(sourceLanguage, targetLanguage, normalized) { result ->
                handler.post {
                    if (closed || !revisionGate.isCurrent(thisRevision)) return@post
                    result.onSuccess { translated ->
                        projectedTranslationCount += 1
                        onProjection(
                            StreamingTranslationProjection(
                                revision = thisRevision,
                                sourceText = normalized,
                                translatedText = translated,
                                translating = false
                            )
                        )
                    }.onFailure { error ->
                        onProjection(
                            StreamingTranslationProjection(
                                revision = thisRevision,
                                sourceText = normalized,
                                translatedText = "",
                                translating = false
                            )
                        )
                        onStatus("Streaming translation update failed: ${error.message.orEmpty().ifBlank { error.javaClass.simpleName }}")
                    }
                }
            }
        }
        pending = task
        handler.postDelayed(task, responseDelayMs.coerceIn(80L, 1000L))
    }

    /** Invalidates all in-flight partial translations at a final-recognition boundary. */
    fun clearWorkingProjection() {
        val invalidationRevision = revisionGate.invalidate()
        pending?.let(handler::removeCallbacks)
        pending = null
        onProjection(StreamingTranslationProjection(revision = invalidationRevision, sourceText = "", translatedText = "", translating = false))
    }

    fun close() {
        closed = true
        revisionGate.invalidate()
        pending?.let(handler::removeCallbacks)
        pending = null
    }
}

internal data class StreamingTranslationProjection(
    val revision: Long,
    val sourceText: String,
    val translatedText: String,
    val translating: Boolean
)

internal fun streamingResponseDelayMs(setting: String): Long = when (setting.trim().lowercase()) {
    "fast" -> 120L
    "stable" -> 450L
    else -> 240L
}
