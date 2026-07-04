package com.example.researchos.transport.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Public Android/ODK entry point for ResearchOS.
 *
 * This activity is intentionally thin. It preserves the external result channel
 * for callers, then hands the request to ExternalWorkflowActivity, which owns
 * generic action-chain execution, capability-specific capture screens,
 * retry/confirm flow and final return summary.
 */
class IntentRouterActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivityForResult(
            Intent(intent).apply {
                setClass(this@IntentRouterActivity, ExternalWorkflowActivity::class.java)
            },
            REQUEST_EXTERNAL_WORKFLOW
        )
    }

    @Deprecated("Deprecated in Android API, but sufficient for this transport bridge.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EXTERNAL_WORKFLOW) {
            setResult(resultCode, data)
            finish()
        }
    }

    companion object {
        private const val REQUEST_EXTERNAL_WORKFLOW = 4101
    }
}
