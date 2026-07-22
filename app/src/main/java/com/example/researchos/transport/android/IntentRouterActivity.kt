package com.example.researchos.transport.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Public Android/ODK entry point for ResearchOS.
 *
 * This activity is intentionally thin. It preserves the external result channel
 * for callers, then hands the request to ExternalWorkflowActivity, which owns
 * generic action-chain execution, capability-specific capture screens,
 * retry/confirm flow and final return summary.
 */
class IntentRouterActivity : ComponentActivity() {

    private val workflowLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        setResult(result.resultCode, result.data)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workflowLauncher.launch(
            Intent(intent).apply {
                setClass(this@IntentRouterActivity, ExternalWorkflowActivity::class.java)
            }
        )
    }
}
