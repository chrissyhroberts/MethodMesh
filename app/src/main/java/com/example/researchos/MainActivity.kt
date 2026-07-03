package com.example.researchos

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import com.example.researchos.calibration.CalibrationRepository
import com.example.researchos.core.DemoResearchGraph
import com.example.researchos.core.ResearchRuntime
import com.example.researchos.ui.ResearchGraphScreen
import com.example.researchos.ui.theme.ResearchOSTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CalibrationRepository.initialise(applicationContext)

        val session = ResearchRuntime.session
        if (session.entities.isEmpty()) {
            val demoGraph = DemoResearchGraph.create()
            demoGraph.entities.values.forEach { session.add(it) }
            demoGraph.observations.values.forEach { session.add(it) }
            demoGraph.relationships.forEach {
                session.relate(
                    source = it.source,
                    type = it.type,
                    target = it.target
                )
            }
        }

        enableEdgeToEdge()
        setContent {
            ResearchOSTheme {
                ResearchGraphScreen()
                // HomeScreen()
            }
        }
    }
}