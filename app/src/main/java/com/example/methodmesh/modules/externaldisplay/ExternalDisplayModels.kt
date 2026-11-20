package com.example.methodmesh.modules.externaldisplay

import android.view.Display

data class ExternalDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val isPresentationDisplay: Boolean
)

internal fun Display.toExternalDisplayInfo() = ExternalDisplayInfo(
    displayId = displayId,
    name = name.ifBlank { "External display" },
    width = mode.physicalWidth,
    height = mode.physicalHeight,
    isPresentationDisplay = flags and Display.FLAG_PRESENTATION != 0
)

enum class ExternalDisplayMode { PRESENTATION, SYSTEM_HANDOFF }

data class ExternalDisplayState(
    val connected: Boolean = false,
    val display: ExternalDisplayInfo? = null,
    val mode: ExternalDisplayMode? = null,
    val blanked: Boolean = false,
    val message: String = "No external display detected."
)
