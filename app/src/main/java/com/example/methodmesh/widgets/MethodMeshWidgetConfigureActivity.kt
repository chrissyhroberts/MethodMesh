package com.example.methodmesh.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.ui.theme.MethodMeshTheme

class MethodMeshWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            MethodMeshTheme {
                ConfigureWidgetScreen(
                    appWidgetId = appWidgetId,
                    onCancel = { finish() },
                    onSaved = {
                        val manager = AppWidgetManager.getInstance(this)
                        MethodMeshWidgetProvider.updateWidget(this, manager, appWidgetId)
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        )
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun ConfigureWidgetScreen(
    appWidgetId: Int,
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val presets = remember { ProtocolLibraryRepository.presets(context) }
    val protocols = remember { ProtocolLibraryRepository.protocols(context) }
    val schedules = remember { MethodMeshWidgetRepository.scheduleTargets(context) }
    var targetType by rememberSaveable { mutableStateOf(MethodMeshWidgetTargetType.PRESET) }
    var iconKey by rememberSaveable { mutableStateOf(MethodMeshWidgetIconKey.AUTO) }
    val currentItems = when (targetType) {
        MethodMeshWidgetTargetType.PRESET -> presets.map { it.id to it.name }
        MethodMeshWidgetTargetType.PROTOCOL -> protocols.map { it.id to it.name }
        MethodMeshWidgetTargetType.SCHEDULE -> schedules.map { it.id to it.name }
    }
    var selectedId by rememberSaveable(targetType) { mutableStateOf(currentItems.firstOrNull()?.first.orEmpty()) }
    val selectedName = currentItems.firstOrNull { it.first == selectedId }?.second.orEmpty()
    var label by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Add MethodMesh widget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Choose what this 1×1 home-screen square should do.")
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Widget label") },
            placeholder = { Text(selectedName.ifBlank { "Short name" }) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MethodMeshWidgetTargetType.entries.forEach { type ->
                OutlinedButton(
                    onClick = {
                        targetType = type
                        selectedId = when (type) {
                            MethodMeshWidgetTargetType.PRESET -> presets.firstOrNull()?.id.orEmpty()
                            MethodMeshWidgetTargetType.PROTOCOL -> protocols.firstOrNull()?.id.orEmpty()
                            MethodMeshWidgetTargetType.SCHEDULE -> schedules.firstOrNull()?.id.orEmpty()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(type.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
        Text(
            when (targetType) {
                MethodMeshWidgetTargetType.PRESET -> "Preset widgets run once."
                MethodMeshWidgetTargetType.PROTOCOL -> "Protocol widgets run the chain."
                MethodMeshWidgetTargetType.SCHEDULE -> "Schedule widgets toggle on/off."
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Icon", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MethodMeshWidgetIconKey.entries.chunked(3).first().forEach { key ->
                OutlinedButton(
                    onClick = { iconKey = key },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (iconKey == key) "✓ ${key.title}" else key.title)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MethodMeshWidgetIconKey.entries.drop(3).take(3).forEach { key ->
                OutlinedButton(
                    onClick = { iconKey = key },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (iconKey == key) "✓ ${key.title}" else key.title)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MethodMeshWidgetIconKey.entries.drop(6).forEach { key ->
                OutlinedButton(
                    onClick = { iconKey = key },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (iconKey == key) "✓ ${key.title}" else key.title)
                }
            }
        }
        if (currentItems.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("No ${targetType.name.lowercase()}s are available yet.", modifier = Modifier.padding(16.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(currentItems, key = { it.first }) { (id, name) ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (id == selectedId) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = id }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = id == selectedId, onClick = { selectedId = id })
                            Text(name, fontWeight = if (id == selectedId) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    MethodMeshWidgetRepository.save(
                        context,
                        MethodMeshWidgetConfig(
                            appWidgetId = appWidgetId,
                            label = label.trim().ifBlank { selectedName },
                            targetType = targetType,
                            targetId = selectedId,
                            iconKey = iconKey
                        )
                    )
                    onSaved()
                },
                enabled = selectedId.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Add widget")
            }
        }
    }
}
