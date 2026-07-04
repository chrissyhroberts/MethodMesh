package com.example.researchos

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import com.example.researchos.calibration.CalibrationRepository
import com.example.researchos.core.DemoResearchGraph
import com.example.researchos.core.ResearchRuntime
import com.example.researchos.transport.android.IntentRouterActivity
import com.example.researchos.ui.HomeScreen
import com.example.researchos.ui.theme.ResearchOSTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (routeExternalResearchOsIntent(intent)) return

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
                //ResearchGraphScreen()
                HomeScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeExternalResearchOsIntent(intent)
    }

    /**
     * Defensive bridge for external callers.
     *
     * The public EXECUTE_METHOD action should resolve directly to
     * IntentRouterActivity via AndroidManifest.xml. On some installed builds or
     * after manifest merges, Android may still deliver the action to
     * MainActivity. If that happens, do not silently ignore the ODK/third-party
     * request: forward the exact same intent to the transport router.
     */
    private fun routeExternalResearchOsIntent(incoming: Intent?): Boolean {
        if (incoming?.action != ACTION_EXECUTE_METHOD) return false
        val routed = Intent(incoming).apply {
            setClass(this@MainActivity, IntentRouterActivity::class.java)
        }
        startActivity(routed)
        finish()
        return true
    }

    companion object {
        private const val ACTION_EXECUTE_METHOD = "com.example.researchos.EXECUTE_METHOD"
    }
}
