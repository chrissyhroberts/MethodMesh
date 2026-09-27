package com.example.methodmesh.modules.livestreamtranslate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Module-owned ODK Integration Card required by the v1.29 module contract. */
@Composable
internal fun LiveStreamTranslateOdkIntegrationCard(mode: LiveStreamMode) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable(mode.wireValue) { mutableStateOf(false) }
    val methodId = when (mode) {
        LiveStreamMode.FIXED -> As100LiveStreamTranslateFixedMethod.ID
        LiveStreamMode.AUTO -> As100LiveStreamTranslateAutoMethod.ID
        LiveStreamMode.STREAMING -> As100LiveStreamTranslateStreamingMethod.ID
    }
    val intent = when (mode) {
        LiveStreamMode.FIXED -> "com.example.methodmesh.EXECUTE_METHOD(method_id='$methodId',input_source_language=\${source_language_input},input_target_language=\${target_language_input},input_speech_engine=\${speech_engine_input},input_prefer_offline=\${prefer_offline_input},input_transcript_on_start=\${transcript_on_start_input},input_payload_mode='FULL',return_mode='flat')"
        LiveStreamMode.AUTO -> "com.example.methodmesh.EXECUTE_METHOD(method_id='$methodId',input_target_language=\${target_language_input},input_allowed_languages=\${allowed_languages_input},input_switch_sensitivity=\${switch_sensitivity_input},input_speech_engine=\${speech_engine_input},input_prefer_offline=\${prefer_offline_input},input_transcript_on_start=\${transcript_on_start_input},input_payload_mode='FULL',return_mode='flat')"
        LiveStreamMode.STREAMING -> "com.example.methodmesh.EXECUTE_METHOD(method_id='$methodId',input_source_language=\${source_language_input},input_target_language=\${target_language_input},input_speech_engine=\${speech_engine_input},input_stream_response=\${stream_response_input},input_transcript_on_start=\${transcript_on_start_input},input_payload_mode='FULL',return_mode='flat')"
    }
    val inputs = when (mode) {
        LiveStreamMode.FIXED -> "source_language · target_language · speech_engine · prefer_offline · transcript_on_start"
        LiveStreamMode.AUTO -> "target_language · allowed_languages · switch_sensitivity · speech_engine · prefer_offline · transcript_on_start"
        LiveStreamMode.STREAMING -> "source_language · target_language · speech_engine · stream_response · transcript_on_start"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("ODK integration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Method ID", style = MaterialTheme.typography.labelSmall)
            Text(methodId, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text("Canonical inputs", style = MaterialTheme.typography.labelSmall)
            Text(inputs, style = MaterialTheme.typography.bodySmall)
            Text("XLSForm body::intent", style = MaterialTheme.typography.labelSmall)
            Text(
                intent,
                modifier = Modifier.fillMaxWidth().clickable {
                    clipboard.setText(AnnotatedString(intent))
                    copied = true
                },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
            Text(if (copied) "Copied intent." else "Tap the intent to copy.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("Canonical beef returns", style = MaterialTheme.typography.labelSmall)
            Text("live_translation_transcript · live_translation_segment_count", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth()) {
                Text("Always capture methodmesh_status and methodmesh_full_json. No return namespace.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
