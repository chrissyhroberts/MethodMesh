package com.example.researchos.transport.android

import android.content.Intent
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.transport.GraphSelectorParser
import com.example.researchos.transport.LaunchConfigParser
import com.example.researchos.transport.ParsedLaunchConfig
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.ril.RilTransportAdapter
import com.example.researchos.transport.workflow.ExternalWorkflowRequest

object AndroidIntentRequestReader {
    /**
     * Parse both the explicit intent URI and Android extras.
     *
     * ODK group intents can provide caller controls in the body::intent URI while
     * also sending child question values as extras. Some of those child fields are
     * blank return placeholders. Reading only one source, or allowing blank extras
     * to overwrite URI parameters, causes ResearchOS to fall back to dashboard demo
     * defaults. Explicit non-blank URI controls are therefore authoritative; extras
     * fill values that were not supplied in the URI.
     */
    fun parse(intent: Intent): ParsedLaunchConfig {
        val fromExtras = parseExtras(intent)
        val fromAction = parseActionInvocation(intent.action)
        val extrasAndAction = mergeParsed(
            fromExtras = fromExtras,
            fromUri = fromAction
        )

        val dataString = intent.dataString?.takeIf { it.isNotBlank() } ?: return extrasAndAction
        val fromUri = LaunchConfigParser.parse(dataString)
        return mergeParsed(fromExtras = extrasAndAction, fromUri = fromUri)
    }


    /**
     * ODK multi-field intents place the complete function-style invocation in
     * Intent.action, for example:
     *
     * com.example.researchos.EXECUTE_METHOD(
     *   method_id='attestation.create',
     *   study_id='study_01',
     *   event_type='form_submission',
     *   trusted_timestamp='required'
     * )
     *
     * Android extras contain the group child fields, but parameters that live
     * only in body::intent are otherwise lost. Parse this action form and make
     * its non-blank values authoritative over blank/duplicate child extras.
     */
    private fun parseActionInvocation(action: String?): ParsedLaunchConfig {
        if (action.isNullOrBlank() || !action.contains('(') || !action.endsWith(')')) {
            return ParsedLaunchConfig(
                methodId = null,
                actionIds = emptyList(),
                returnMode = null,
                settings = emptyMap(),
                context = emptyMap(),
                source = "android_action"
            )
        }

        val inside = action.substringAfter('(').dropLast(1)
        val values = linkedMapOf<String, String>()
        splitArguments(inside).forEach { argument ->
            val separator = argument.indexOf('=')
            if (separator <= 0) return@forEach
            val key = argument.substring(0, separator).trim()
            val value = argument.substring(separator + 1).trim().trim('\'', '"')
            if (key.isNotBlank()) values[key] = value
        }

        return RilTransportAdapter.parse(values, source = "android_action")
    }

    private fun splitArguments(raw: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false

        raw.forEach { character ->
            when {
                escaped -> {
                    current.append(character)
                    escaped = false
                }
                character == '\\' -> {
                    current.append(character)
                    escaped = true
                }
                quote != null -> {
                    current.append(character)
                    if (character == quote) quote = null
                }
                character == '\'' || character == '"' -> {
                    quote = character
                    current.append(character)
                }
                character == ',' -> {
                    parts += current.toString().trim()
                    current.clear()
                }
                else -> current.append(character)
            }
        }
        if (current.isNotBlank()) parts += current.toString().trim()
        return parts
    }

    private fun mergeParsed(
        fromExtras: ParsedLaunchConfig,
        fromUri: ParsedLaunchConfig
    ): ParsedLaunchConfig {
        fun mergeNonBlank(
            fallback: Map<String, String>,
            authoritative: Map<String, String>
        ): Map<String, String> = buildMap {
            fallback.forEach { (key, value) -> if (value.isNotBlank()) put(key, value) }
            authoritative.forEach { (key, value) ->
                if (value.isNotBlank() || key !in this) put(key, value)
            }
        }

        return ParsedLaunchConfig(
            methodId = fromUri.methodId ?: fromExtras.methodId,
            actionIds = fromUri.actionIds.ifEmpty { fromExtras.actionIds },
            returnMode = fromUri.returnMode ?: fromExtras.returnMode,
            settings = mergeNonBlank(fromExtras.settings, fromUri.settings),
            context = mergeNonBlank(fromExtras.context, fromUri.context),
            returnSelectors = fromUri.returnSelectors.ifEmpty { fromExtras.returnSelectors },
            warnings = (fromExtras.warnings + fromUri.warnings).distinct(),
            source = fromUri.source ?: fromExtras.source
        )
    }

    fun workflowRequest(intent: Intent): ExternalWorkflowRequest {
        val parsed = parse(intent)
        return ExternalWorkflowRequest.from(
            parsed = parsed,
            invocationContext = invocationContextFrom(parsed)
        )
    }

    fun invocationContextFrom(parsed: ParsedLaunchConfig): InvocationContext {
        val merged = parsed.settings + parsed.context
        val entityType = merged["entity_type"]
            ?: merged["context_entity_type"]
            ?: when {
                merged["specimen_id"] != null -> "specimen"
                else -> "participant"
            }
        val entityId = merged["entity_id"]
            ?: merged["context_entity_id"]
            ?: merged["subject_id"]
            ?: merged["participant_id"]
            ?: merged["specimen_id"]
            ?: "P001"

        return InvocationContext(
            caller = merged["caller"] ?: parsed.source ?: "external_app",
            entityType = entityType,
            entityId = entityId,
            visitId = merged["visit_id"].orEmpty(),
            formId = merged["form_id"].orEmpty(),
            operatorId = merged["operator_id"].orEmpty()
        )
    }

    private fun parseExtras(intent: Intent): ParsedLaunchConfig {
        val values = mutableMapOf<String, String>()
        val extras = intent.extras
        extras?.keySet()?.forEach { key -> values[key] = extras.get(key)?.toString().orEmpty() }
        intent.action?.let { values.putIfAbsent("action", it) }

        return RilTransportAdapter.parse(values, source = "android_extras")
    }

    private fun parseActionIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', '>', '|', '\n').map { it.trim() }.filter { it.isNotBlank() }
    }
}
