package com.example.methodmesh.modules.livestreamtranslate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

internal class LiveStreamTranslationEngine {
    private val translators = linkedMapOf<Pair<String, String>, Translator>()
    private val readyModels = mutableSetOf<String>()
    private val waitingModels = mutableMapOf<String, MutableList<(Result<Unit>) -> Unit>>()

    fun ensureModel(language: String, callback: (Result<Unit>) -> Unit) {
        if (language.isBlank()) {
            callback(Result.failure(IllegalArgumentException("Missing language code")))
            return
        }
        if (language in readyModels) {
            callback(Result.success(Unit))
            return
        }
        waitingModels[language]?.let {
            it += callback
            return
        }
        waitingModels[language] = mutableListOf(callback)
        val model = TranslateRemoteModel.Builder(language).build()
        RemoteModelManager.getInstance()
            .download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener {
                readyModels += language
                waitingModels.remove(language).orEmpty().forEach { waiter -> waiter(Result.success(Unit)) }
            }
            .addOnFailureListener { error ->
                waitingModels.remove(language).orEmpty().forEach { waiter -> waiter(Result.failure(error)) }
            }
    }

    fun ensurePair(source: String, target: String, callback: (Result<Unit>) -> Unit) {
        if (source == target) {
            callback(Result.success(Unit))
            return
        }
        ensureModel(source) { sourceResult ->
            if (sourceResult.isFailure) {
                callback(sourceResult)
            } else {
                ensureModel(target, callback)
            }
        }
    }

    fun translate(source: String, target: String, text: String, callback: (Result<String>) -> Unit) {
        if (text.isBlank()) {
            callback(Result.success(""))
            return
        }
        if (source == target) {
            callback(Result.success(text))
            return
        }
        ensurePair(source, target) { prepared ->
            if (prepared.isFailure) {
                callback(Result.failure(prepared.exceptionOrNull() ?: IllegalStateException("Language model preparation failed")))
                return@ensurePair
            }
            val key = source to target
            val translator = translators.getOrPut(key) {
                Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(source)
                        .setTargetLanguage(target)
                        .build()
                )
            }
            translator.translate(text)
                .addOnSuccessListener { callback(Result.success(it)) }
                .addOnFailureListener { callback(Result.failure(it)) }
        }
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
        waitingModels.clear()
    }
}
