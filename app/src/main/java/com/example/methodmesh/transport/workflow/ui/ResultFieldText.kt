package com.example.methodmesh.transport.workflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import java.net.URI

/** Renders the canonical barcode URL field as a visible, user-initiated link. */
@Composable
fun ResultFieldText(key: String, value: Any?, style: TextStyle) {
    val valueText = value?.toString().orEmpty()
    val link = valueText.takeIf { key == "barcode_payload_url" && isSafeHttpUrl(it) }
    val uriHandler = LocalUriHandler.current
    Text(
        text = "$key = $valueText",
        modifier = if (link == null) Modifier else Modifier.clickable {
            runCatching { uriHandler.openUri(link) }
        },
        color = if (link == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        fontFamily = FontFamily.Monospace,
        style = style
    )
}

internal fun isSafeHttpUrl(value: String): Boolean {
    if (value != value.trim()) return false
    val parsed = runCatching { URI(value) }.getOrNull() ?: return false
    return !parsed.isOpaque && !parsed.host.isNullOrBlank() && parsed.scheme?.lowercase() in setOf("http", "https")
}
