package com.example.methodmesh.transport.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Public Android/ODK entry point for MethodMesh.
 *
 * Android callers use the normal Activity result channel. Browser/Enketo
 * launches are transient BROWSABLE deep links. The router has an empty task
 * affinity in the manifest, so after the child workflow completes the safest
 * way back to the exact browser tab is to remove this transient task and let
 * Android reveal the task that was underneath it. Do not relaunch the browser
 * package: doing so can create a new tab or browser start page.
 */
class IntentRouterActivity : ComponentActivity() {
    private var browserLaunch: Boolean = false

    private val workflowLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        setResult(result.resultCode, result.data)
        if (browserLaunch) {
            returnToPreviousTask()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        browserLaunch = intent.action == Intent.ACTION_VIEW &&
            intent.categories?.contains(Intent.CATEGORY_BROWSABLE) == true &&
            intent.data?.scheme.equals("methodmesh", ignoreCase = true)

        workflowLauncher.launch(workflowIntentFor(intent))
    }

    /**
     * Build a payload-equivalent child intent without inheriting the browser's
     * task-navigation flags. Browsers normally enter an external application
     * with FLAG_ACTIVITY_NEW_TASK. Passing that flag to an activity launched
     * for result makes Android place the workflow in a different task and can
     * report an immediate cancelled result.
     */
    private fun workflowIntentFor(incoming: Intent): Intent =
        Intent(this, ExternalWorkflowActivity::class.java).apply {
            action = incoming.action
            setDataAndType(incoming.data, incoming.type)
            clipData = incoming.clipData
            incoming.extras?.let { putExtras(it) }
            flags = incoming.flags and URI_GRANT_FLAGS
        }

    private fun returnToPreviousTask() {
        // The browser deep-link lives in a transient MethodMesh task. Removing
        // that task immediately can cause Android to promote an older
        // MethodMesh MainActivity task instead of the browser task that launched
        // us. Move this transient task to the back *first*: that restores the
        // existing Chrome/Enketo task (and therefore the exact live form/tab)
        // without launching a browser activity or reloading the URL. Once it is
        // no longer foreground, finish the router normally.
        moveTaskToBack(true)
        finish()
    }

    private companion object {
        val URI_GRANT_FLAGS: Int =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}
