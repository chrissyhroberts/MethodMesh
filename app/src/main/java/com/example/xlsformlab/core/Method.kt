package com.example.xlsformlab.core

import androidx.compose.runtime.Composable
import com.example.xlsformlab.settings.MethodSetting
import com.example.xlsformlab.settings.SettingsState

/**
 * A Method is a repeatable research procedure capable of fulfilling one or more Intents.
 *
 * Methods are transport-independent and may be executed from ODK, demonstrated within
 * ResearchOS, or invoked by future orchestrators and protocol runners.
 *
 * A Method defines *how* work is performed. Execution of a Method may produce one or more
 * Observation objects together with provenance describing the execution.
 */
interface Method {

    /**
     * Static description of the Method and its capabilities.
     */
    val manifest: MethodManifest

    /**
     * User-configurable settings exposed by the Method.
     */
    val settings: List<MethodSetting>

    /**
     * Schema describing the observations this Method may produce.
     */
    val outputSchema: MethodOutputSchema
        get() = MethodOutputSchema()

    /**
     * Interactive demonstration surface.
     */
    @Composable
    fun Demo(
        settingsState: SettingsState
    )

    /**
     * Human-readable documentation for the Method.
     */
    @Composable
    fun Help()

    /**
     * Builds a deterministic preview of the expected output from the current settings.
     *
     * This function performs no acquisition or execution and may be used by user interfaces,
     * protocol builders and validation tools.
     */
    fun buildOutput(
        settingsState: SettingsState
    ): MethodOutput {
        return MethodOutput()
    }

    /**
     * Executes the Method.
     *
     * The request represents a runtime execution request rather than a ResearchOS Intent.
     * Higher-level runtime components are responsible for translating Intents into
     * executable requests.
     */
    fun execute(
        request: MethodRequest
    ): MethodResult {
        return MethodResult(
            success = true,
            fields = emptyMap()
        )
    }
}
