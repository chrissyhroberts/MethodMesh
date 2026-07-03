package com.example.researchos

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import com.example.researchos.calibration.CalibrationRepository
import com.example.researchos.ui.HomeScreen
import com.example.researchos.ui.theme.ResearchOSTheme
import com.example.researchos.ui.ResearchGraphScreen

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CalibrationRepository.initialise(applicationContext)

        enableEdgeToEdge()
        setContent {
            ResearchOSTheme {
                ResearchGraphScreen()
                //HomeScreen()
            }
        }
    }
}
