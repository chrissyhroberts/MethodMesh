package com.example.methodmesh.modules.externaldisplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.methodmesh.ui.theme.MethodMeshTheme

class ExternalDisplayActivity : ComponentActivity() {
    private var blanked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        val state = ExternalDisplayManager.currentState(this)
        blanked = state.blanked
        if (!state.connected || state.mode == null) finish()
        else ExternalDisplayManager.setBlanked(this, state.blanked)
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.action == ExternalDisplayManager.ACTION_STOP) {
            ExternalDisplayManager.stop(this)
            finish()
        }
    }

    private fun render() {
        setContent {
            MethodMeshTheme {
                if (blanked) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("MethodMesh", style = MaterialTheme.typography.displaySmall)
                            Text("Presentation surface", style = MaterialTheme.typography.headlineMedium)
                            Text("Ready for a capability dashboard", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
