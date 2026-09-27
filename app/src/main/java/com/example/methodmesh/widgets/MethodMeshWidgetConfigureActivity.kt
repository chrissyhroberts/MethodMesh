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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.ui.theme.MethodMeshTheme

class MethodMeshWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.getIntExtra(
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
                        MethodMeshWidgetProvider.updateWidget(
                            this,
                            AppWidgetManager.getInstance(this),
                            appWidgetId
                        )
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

private data class TargetChoice(
    val target: MethodMeshWidgetTarget,
    val name: String
)

private fun targetKey(target: MethodMeshWidgetTarget): String =
    "${target.type.name}:${target.id}"

@Composable
private fun ConfigureWidgetScreen(
    appWidgetId: Int,
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val existing = remember(appWidgetId) {
        MethodMeshWidgetRepository.get(context, appWidgetId)
    }

    val choices = remember {
        mutableListOf<TargetChoice>().apply {
            ProtocolLibraryRepository.presets(context).forEach { preset ->
                add(
                    TargetChoice(
                        target = MethodMeshWidgetTarget(
                            type = MethodMeshWidgetTargetType.PRESET,
                            id = preset.id,
                            fallbackLabel = preset.name
                        ),
                        name = preset.name
                    )
                )
            }
            ProtocolLibraryRepository.protocols(context).forEach { protocol ->
                add(
                    TargetChoice(
                        target = MethodMeshWidgetTarget(
                            type = MethodMeshWidgetTargetType.PROTOCOL,
                            id = protocol.id,
                            fallbackLabel = protocol.name
                        ),
                        name = protocol.name
                    )
                )
            }
            MethodMeshWidgetRepository.scheduleTargets(context).forEach { schedule ->
                add(
                    TargetChoice(
                        target = MethodMeshWidgetTarget(
                            type = MethodMeshWidgetTargetType.SCHEDULE,
                            id = schedule.id,
                            fallbackLabel = schedule.name
                        ),
                        name = schedule.name
                    )
                )
            }
        }
    }

    val choicesByKey = remember(choices) {
        choices.associateBy { targetKey(it.target) }
    }
    val existingBundleTargetsByKey = remember(existing) {
        existing?.bundleTargets?.associateBy(::targetKey).orEmpty()
    }

    var targetType by rememberSaveable {
        mutableStateOf(existing?.targetType ?: MethodMeshWidgetTargetType.PRESET)
    }
    var selectedId by rememberSaveable {
        mutableStateOf(existing?.targetId.orEmpty())
    }
    var label by rememberSaveable {
        mutableStateOf(existing?.label.orEmpty())
    }
    var iconKey by rememberSaveable {
        mutableStateOf(existing?.iconKey ?: MethodMeshWidgetIconKey.AUTO)
    }
    var colour by rememberSaveable {
        mutableStateOf(existing?.colour ?: MethodMeshWidgetColour.TEAL)
    }
    var appearance by rememberSaveable {
        mutableStateOf(existing?.appearance ?: MethodMeshWidgetAppearance.FROSTED)
    }
    var search by rememberSaveable {
        mutableStateOf("")
    }
    var bundleKeys: ArrayList<String> by rememberSaveable {
        mutableStateOf(
            ArrayList(
                existing?.bundleTargets
                    ?.map(::targetKey)
                    .orEmpty()
            )
        )
    }

    val selectedSingle = choices.firstOrNull {
        it.target.type == targetType && it.target.id == selectedId
    }

    val filteredSingles = choices.filter {
        it.target.type == targetType &&
            (search.isBlank() || it.name.contains(search, ignoreCase = true))
    }

    val bundleTargets = bundleKeys.mapNotNull { key ->
        choicesByKey[key]?.target ?: existingBundleTargetsByKey[key]
    }

    fun setBundleKeys(updated: List<String>) {
        bundleKeys = ArrayList(updated.distinct())
    }

    fun removeBundleKey(key: String) {
        setBundleKeys(bundleKeys.filterNot { it == key })
    }

    fun moveBundleKey(key: String, delta: Int) {
        val currentIndex = bundleKeys.indexOf(key)
        if (currentIndex < 0) return
        val newIndex = currentIndex + delta
        if (newIndex !in bundleKeys.indices) return

        val reordered = bundleKeys.toMutableList()
        val item = reordered.removeAt(currentIndex)
        reordered.add(newIndex, item)
        setBundleKeys(reordered)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (existing == null) "Add MethodMesh widget" else "Edit MethodMesh widget",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text("Solid keeps the full tile. Frosted and Compact use a round shortcut with the label underneath. Bundles open a shortcut-style pop-out menu.")

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = {
                Text(
                    if (targetType == MethodMeshWidgetTargetType.BUNDLE) {
                        "Bundle name"
                    } else {
                        "Widget label"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(MethodMeshWidgetTargetType.entries) { type ->
                FilterChip(
                    selected = targetType == type,
                    onClick = {
                        targetType = type
                        if (
                            type != MethodMeshWidgetTargetType.BUNDLE &&
                            choices.none { it.target.type == type && it.target.id == selectedId }
                        ) {
                            selectedId = choices
                                .firstOrNull { it.target.type == type }
                                ?.target
                                ?.id
                                .orEmpty()
                        }
                    },
                    label = {
                        Text(
                            if (type == MethodMeshWidgetTargetType.BUNDLE) {
                                "Bundle / stack"
                            } else {
                                type.name.lowercase().replaceFirstChar { it.uppercase() }
                            }
                        )
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MethodMeshWidgetAppearance.entries.forEach { option ->
                FilterChip(
                    selected = appearance == option,
                    onClick = { appearance = option },
                    label = { Text(option.title) }
                )
            }
        }

        Text("Icon", fontWeight = FontWeight.Bold)
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(MethodMeshWidgetIconKey.entries) { key ->
                Surface(
                    modifier = Modifier.clickable { iconKey = key },
                    shape = RoundedCornerShape(10.dp),
                    color = if (iconKey == key) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(key.emoji)
                        Text(
                            text = key.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            MethodMeshWidgetColour.entries.forEach { option ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { colour = option },
                    shape = RoundedCornerShape(50),
                    color = Color(option.argb)
                ) {
                    Text(
                        text = if (colour == option) "✓" else "●",
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        color = if (option == MethodMeshWidgetColour.DARK) {
                            Color.White
                        } else {
                            Color(0xFF302A28)
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search tools") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (targetType == MethodMeshWidgetTargetType.BUNDLE) {
            val availableChoices = choices.filter { choice ->
                val key = targetKey(choice.target)
                key !in bundleKeys &&
                    (search.isBlank() || choice.name.contains(search, ignoreCase = true))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "selected-heading") {
                    Text(
                        text = "Bundle contents · ${bundleKeys.size} selected",
                        fontWeight = FontWeight.Bold
                    )
                }

                items(bundleKeys, key = { "selected:$it" }) { key ->
                    val currentChoice = choicesByKey[key]
                    val target = currentChoice?.target ?: existingBundleTargetsByKey[key]
                    if (target != null) {
                        val displayName = currentChoice?.name
                            ?: MethodMeshWidgetRepository.resolveTargetName(context, target)
                                .ifBlank { target.fallbackLabel.ifBlank { target.id } }
                        val available = currentChoice != null
                        val index = bundleKeys.indexOf(key)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (available) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = { checked ->
                                        if (!checked) removeBundleKey(key)
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(displayName)
                                    Text(
                                        text = if (available) {
                                            target.type.name.lowercase()
                                                .replaceFirstChar { it.uppercase() }
                                        } else {
                                            "Unavailable · remove or replace"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                IconButton(
                                    onClick = { moveBundleKey(key, -1) },
                                    enabled = index > 0
                                ) {
                                    Text("↑")
                                }
                                IconButton(
                                    onClick = { moveBundleKey(key, 1) },
                                    enabled = index >= 0 && index < bundleKeys.lastIndex
                                ) {
                                    Text("↓")
                                }
                            }
                        }
                    }
                }

                if (availableChoices.isNotEmpty()) {
                    item(key = "available-heading") {
                        Text(
                            text = "Add tools",
                            modifier = Modifier.padding(top = 6.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items(
                    items = availableChoices,
                    key = { "available:${targetKey(it.target)}" }
                ) { choice ->
                    val key = targetKey(choice.target)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                setBundleKeys(bundleKeys + key)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = false,
                                onCheckedChange = { checked ->
                                    if (checked) setBundleKeys(bundleKeys + key)
                                }
                            )
                            Column {
                                Text(choice.name)
                                Text(
                                    text = choice.target.type.name.lowercase()
                                        .replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = filteredSingles,
                    key = { it.target.id }
                ) { choice ->
                    val selected = selectedId == choice.target.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = choice.target.id },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { selectedId = choice.target.id }
                            )
                            Text(choice.name)
                        }
                    }
                }
            }
        }

        val valid = if (targetType == MethodMeshWidgetTargetType.BUNDLE) {
            label.isNotBlank() && bundleTargets.isNotEmpty()
        } else {
            selectedSingle != null
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val finalLabel = label.trim().ifBlank {
                        selectedSingle?.name.orEmpty()
                    }
                    MethodMeshWidgetRepository.save(
                        context,
                        MethodMeshWidgetConfig(
                            appWidgetId = appWidgetId,
                            label = finalLabel,
                            targetType = targetType,
                            targetId = if (targetType == MethodMeshWidgetTargetType.BUNDLE) {
                                ""
                            } else {
                                selectedId
                            },
                            iconKey = iconKey,
                            colour = colour,
                            appearance = appearance,
                            bundleTargets = if (targetType == MethodMeshWidgetTargetType.BUNDLE) {
                                bundleTargets
                            } else {
                                emptyList()
                            }
                        )
                    )
                    onSaved()
                },
                enabled = valid,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (existing == null) "Add widget" else "Save")
            }
        }
    }
}
