package com.example.methodmesh.settings

import androidx.compose.runtime.Composable

/**
 * Declarative contribution to the shared MethodMesh Settings screen.
 *
 * Core UI code owns the visual presentation. Modules contribute only an ID,
 * title, ordering hint, initial expansion preference and composable content.
 * This keeps HomeScreen unaware of individual feature modules.
 */
data class SettingsSectionSpec(
    val id: String,
    val title: String,
    val order: Int = 1000,
    val initiallyExpanded: Boolean = false,
    val content: @Composable () -> Unit
)
