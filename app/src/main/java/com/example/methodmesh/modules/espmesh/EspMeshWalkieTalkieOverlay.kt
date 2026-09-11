package com.example.methodmesh.modules.espmesh

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal object EspMeshVoiceOverlay : com.example.methodmesh.modules.ModuleOverlaySpec {
    override val id = "espmesh.voice"
    @Composable override fun Render(modifier: Modifier) = EspMeshWalkieTalkieOverlay(modifier)
}

/** Global in-app walkie-talkie control layered above normal MethodMesh UI. */
@Composable
fun EspMeshWalkieTalkieOverlay(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext
    val haptics = LocalHapticFeedback.current
    val provider = remember { EspMeshTransportProvider.get(app) }
    val controller = remember { EspMeshWalkieTalkieController.get(app) }
    val transport by provider.snapshot.collectAsState()
    val voice by controller.state.collectAsState()
    var localMessage by remember { mutableStateOf("") }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        localMessage = if (granted) "Hold TALK to transmit" else "Microphone permission denied"
    }

    if (!transport.enabled) return

    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, tonalElevation = 4.dp, shadowElevation = 4.dp) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (voice.receiving) {
                Text("${voice.channel} · ${voice.activeSpeaker}", style = MaterialTheme.typography.labelSmall)
            } else if (localMessage.isNotBlank()) {
                Text(localMessage, style = MaterialTheme.typography.labelSmall)
            } else {
                Text(voice.channel, style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = {
                        controller.toggleListening()
                        localMessage = if (voice.listening) "Voice muted" else "Listening"
                    },
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 2.dp
                ) {
                    Text(if (voice.listening) "MUTE" else "LISTEN", modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                }

                Surface(
                    modifier = Modifier
                        .size(66.dp)
                        .pointerInput(transport.connected, transport.e2eKeyId, voice.receiving) {
                            detectTapGestures(
                                onPress = press@{
                                    if (!controller.hasMicrophonePermission()) {
                                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                        return@press
                                    }
                                    val started = controller.startTransmit()
                                    if (started.isFailure) {
                                        localMessage = started.exceptionOrNull()?.message.orEmpty()
                                        return@press
                                    }
                                    localMessage = ""
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        controller.stopTransmit()
                                    }
                                }
                            )
                        },
                    shape = CircleShape,
                    tonalElevation = if (voice.transmitting) 8.dp else 3.dp,
                    shadowElevation = if (voice.transmitting) 8.dp else 3.dp
                ) {
                    Column(
                        modifier = Modifier.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(if (voice.transmitting) "TX" else "TALK", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        if (!transport.connected) Text("OFF", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
