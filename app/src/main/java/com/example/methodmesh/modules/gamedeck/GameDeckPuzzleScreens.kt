package com.example.methodmesh.modules.gamedeck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun SudokuScreen(
    stateJson: String,
    rngMode: String,
    seed: String,
    onStateChange: (String) -> Unit
) {
    val s = JSONObject(stateJson)
    val grid = s.getJSONArray("grid")
    val givens = s.getJSONArray("givens")
    val difficulty = GameDeckPuzzleDifficulty.normalize(s.optString("difficulty"))
    val nonce = s.optInt("nonce", 0)
    val moves = s.optInt("moves", 0)
    val terminal = s.optString("winner").isNotBlank()
    val conflicts = remember(stateJson) {
        GameDeckPuzzleEngine.sudokuConflictCells(stateJson)
    }

    var selected by rememberSaveable { mutableStateOf(-1) }

    fun setValue(value: Int) {
        val index = selected
        if (index in 0..80 && givens.optInt(index) == 0 && !terminal) {
            onStateChange(GameDeckPuzzleEngine.sudokuSetCell(stateJson, index, value))
        }
    }

    SoloFrame(
        title = "SUDOKU",
        subtitle = "Tap a blank square, then choose a number.",
        detail = "${GameDeckPuzzleDifficulty.label(difficulty)} • ${s.optInt("clues")} clues"
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val controlReserve = 176.dp
            val boardSize = minOf(
                maxWidth,
                (maxHeight - controlReserve).coerceAtLeast(210.dp)
            )

            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(boardSize)) {
                    SquareBoard(9) { cell, side ->
                        val value = grid.optInt(cell)
                        val given = givens.optInt(cell) == 1
                        val isSelected = cell == selected
                        val isConflict = cell in conflicts

                        Surface(
                            modifier = Modifier
                                .size(side)
                                .padding(1.dp)
                                .clickable(enabled = !given && !terminal) {
                                    selected = cell
                                },
                            shape = RoundedCornerShape(4.dp),
                            color = when {
                                isConflict -> MaterialTheme.colorScheme.errorContainer
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                given -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surface
                            },
                            tonalElevation = if (given || isSelected) 2.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (value != 0) {
                                    Text(
                                        value.toString(),
                                        fontWeight = if (given) FontWeight.Black else FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isConflict) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Canvas(Modifier.fillMaxSize()) {
                        val step = size.width / 9f
                        for (i in 0..9) {
                            val thick = i % 3 == 0
                            val stroke = if (thick) 4f else 1f
                            val alpha = if (thick) .75f else .22f
                            drawLine(
                                color = Color.Black.copy(alpha = alpha),
                                start = Offset(i * step, 0f),
                                end = Offset(i * step, size.height),
                                strokeWidth = stroke
                            )
                            drawLine(
                                color = Color.Black.copy(alpha = alpha),
                                start = Offset(0f, i * step),
                                end = Offset(size.width, i * step),
                                strokeWidth = stroke
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (1..5).forEach { value ->
                        SudokuKey(value.toString(), !terminal) { setValue(value) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (6..9).forEach { value ->
                        SudokuKey(value.toString(), !terminal) { setValue(value) }
                    }
                    SudokuKey("⌫", !terminal) { setValue(0) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val next = GameDeckPuzzleDifficulty.next(difficulty)
                            selected = -1
                            onStateChange(
                                GameDeckPuzzleEngine.sudokuInitial(
                                    rngMode = rngMode,
                                    seed = seed,
                                    difficulty = next,
                                    nonce = nonce + 1
                                )
                            )
                        },
                        enabled = moves == 0 && !terminal
                    ) {
                        Text("DIFFICULTY: ${GameDeckPuzzleDifficulty.label(difficulty).uppercase()} ›")
                    }
                }

                if (conflicts.isNotEmpty()) {
                    Text(
                        "There is a duplicate in a row, column or 3×3 box.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SudokuKey(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp)
    ) {
        Text(label, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun TakuzuScreen(
    stateJson: String,
    rngMode: String,
    seed: String,
    onStateChange: (String) -> Unit
) {
    val s = JSONObject(stateJson)
    val grid = s.getJSONArray("grid")
    val givens = s.getJSONArray("givens")
    val difficulty = GameDeckPuzzleDifficulty.normalize(s.optString("difficulty"))
    val nonce = s.optInt("nonce", 0)
    val moves = s.optInt("moves", 0)
    val terminal = s.optString("winner").isNotBlank()
    val conflicts = remember(stateJson) {
        GameDeckPuzzleEngine.takuzuConflictCells(stateJson)
    }

    SoloFrame(
        title = "TAKUZU",
        subtitle = "Tap a blank: empty → 0 → 1 → empty.",
        detail = "${GameDeckPuzzleDifficulty.label(difficulty)} • ${s.optInt("clues")} clues"
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val controlReserve = 140.dp
            val boardSize = minOf(
                maxWidth,
                (maxHeight - controlReserve).coerceAtLeast(210.dp)
            )

            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(boardSize)) {
                    SquareBoard(6) { cell, side ->
                        val value = grid.optInt(cell)
                        val given = givens.optInt(cell) == 1
                        val conflict = cell in conflicts

                        Surface(
                            modifier = Modifier
                                .size(side)
                                .padding(2.dp)
                                .clickable(enabled = !given && !terminal) {
                                    onStateChange(
                                        GameDeckPuzzleEngine.takuzuCycleCell(
                                            stateJson,
                                            cell
                                        )
                                    )
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                conflict -> MaterialTheme.colorScheme.errorContainer
                                value == 1 -> MaterialTheme.colorScheme.primaryContainer
                                value == 2 -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
                            },
                            tonalElevation = if (value != 0) 3.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (value != 0) {
                                    Text(
                                        if (value == 1) "0" else "1",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = if (given) FontWeight.Black else FontWeight.SemiBold,
                                        color = if (conflict) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    "Each row and column has three 0s and three 1s • no three identical together • no duplicate completed rows or columns.",
                    style = MaterialTheme.typography.labelSmall
                )

                OutlinedButton(
                    onClick = {
                        val next = GameDeckPuzzleDifficulty.next(difficulty)
                        onStateChange(
                            GameDeckPuzzleEngine.takuzuInitial(
                                rngMode = rngMode,
                                seed = seed,
                                difficulty = next,
                                nonce = nonce + 1
                            )
                        )
                    },
                    enabled = moves == 0 && !terminal
                ) {
                    Text("DIFFICULTY: ${GameDeckPuzzleDifficulty.label(difficulty).uppercase()} ›")
                }

                if (conflicts.isNotEmpty()) {
                    Text(
                        "One or more current entries break a Takuzu rule.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
