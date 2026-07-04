package com.example.researchos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.researchos.core.ResearchRuntime

@Composable
fun ResearchGraphScreen() {
    val graph = ResearchRuntime.session.graph()
    Column(modifier = Modifier.padding(16.dp)) {
        Text("ResearchOS graph")
        Spacer(modifier = Modifier.height(12.dp))

        Text("AS observations: ${graph.asObservations.size}")
        graph.asObservations.values.forEach { observation ->
            Text("${observation.phenomenon}: ${observation.id.value}")
            observation.values.forEach { (key, value) ->
                Text("  • $key = $value")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("AS transformations: ${graph.transformations.size}")
        graph.transformations.values.forEach { transformation ->
            Text("${transformation.action}: ${transformation.status}")
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Legacy observations: ${graph.observations.size}")
        graph.entities.values.forEach { entity ->
            Text("${entity.type}: ${entity.label ?: entity.id}")
            val observations = graph.observationsForEntity(entity.id)
            observations.forEach { observation ->
                observation.output.fields.forEach { (key, value) ->
                    Text("  • $key = $value")
                }
            }
        }
    }
}
