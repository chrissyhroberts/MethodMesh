package com.example.methodmesh.modules.screentools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.rememberRetainedExecutionResult
import java.time.Instant

private fun CapabilityScreenContext.webSetting(key: String, default: String = ""): String =
    action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"] ?: default

private fun validHttps(raw: String): Uri? = runCatching {
    Uri.parse(raw.trim()).takeIf { it.scheme.equals("https", true) && !it.host.isNullOrBlank() }
}.getOrNull()

object ScreenWebCaptureCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = ScreenWebCaptureMethod.ID
    override val title = "Capture webpage"
    override val description = "Render and capture an HTTPS webpage viewport into volatile memory."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val appContext = LocalContext.current
        var url by rememberSaveable { mutableStateOf(context.webSetting("url")) }
        var javascript by rememberSaveable { mutableStateOf(context.webSetting("enable_javascript", "false").equals("true", true)) }
        var status by rememberSaveable { mutableStateOf("Enter an HTTPS URL and load it.") }
        var loadedUrl by rememberSaveable { mutableStateOf("") }
        var loadedTitle by rememberSaveable { mutableStateOf("") }
        var failed by rememberSaveable { mutableStateOf(false) }
        var webView by remember { mutableStateOf<WebView?>(null) }
        var result by rememberRetainedExecutionResult("${context.action.canonicalId}:web")

        fun load() {
            val uri = validHttps(url)
            if (uri == null) {
                failed = true
                status = "Only HTTPS URLs with a host are allowed."
                return
            }
            failed = false
            result = null
            loadedUrl = uri.toString()
            status = "Loading HTTPS page…"
            webView?.settings?.javaScriptEnabled = javascript
            webView?.loadUrl(uri.toString())
        }

        fun capture() {
            val view = webView
            if (view == null || view.width <= 0 || view.height <= 0 || loadedUrl.isBlank() || failed) {
                status = "Load a successful HTTPS page before capturing."
                return
            }
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val frame = ScreenToolsSession.add(bitmap)
            val request = ScreenWebCaptureMethod.request(
                action = ScreenWebCaptureMethod.ID,
                context = context.request.invocationContext.asMap(ScreenWebCaptureMethod.ID) + context.action.settings,
                signals = emptyList(), inputs = emptyList()
            )
            val values = mapOf(
                ScreenWebCaptureFields.STATUS to "succeeded",
                ScreenWebCaptureFields.ERROR to "",
                ScreenWebCaptureFields.URL to loadedUrl,
                ScreenWebCaptureFields.TITLE to loadedTitle,
                ScreenWebCaptureFields.FRAME_REF to frame.ref,
                ScreenWebCaptureFields.WIDTH_PX to bitmap.width.toString(),
                ScreenWebCaptureFields.HEIGHT_PX to bitmap.height.toString(),
                ScreenWebCaptureFields.RETENTION to "volatile_process_memory"
            )
            val execution = ScreenWebCaptureMethod.result(request, values, context.request.invocationContext)
            result = execution
            status = "Web viewport captured. The frame is volatile and ready for Commit."
            if (context.submitsImmediately) onConfirmed(execution)
        }

        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context, canGoBack = context.stepNumber > 1,
            capturedResult = result, resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack, onRetry = { result = null }, onConfirm = { result?.let(onConfirmed) }, onCancel = onCancel
        ) {
            Text("Only HTTPS is permitted. Capture is the visible WebView viewport, not a whole-document export.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(url, { url = it }, label = { Text("HTTPS URL") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = ::load) { Text("Load page") }
            Button(onClick = ::capture, enabled = loadedUrl.isNotBlank() && !failed) { Text("Capture viewport") }
            Text(status, style = MaterialTheme.typography.bodySmall)
            AndroidView(
                factory = { context: Context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = javascript
                        settings.domStorageEnabled = javascript
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, pageUrl: String) {
                                if (validHttps(pageUrl) == null) {
                                    failed = true
                                    status = "The page redirected away from HTTPS; capture refused."
                                } else {
                                    failed = false
                                    loadedUrl = pageUrl
                                    loadedTitle = view.title.orEmpty()
                                    status = "Page loaded. Review the visible viewport, then capture."
                                }
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) {
                                    failed = true
                                    status = "Page load failed; capture refused."
                                }
                            }
                        }
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 520.dp)
            )
        }
    }
}

object ScreenAppOpenCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = ScreenAppOpenMethod.ID
    override val title = "Open application"
    override val description = "Dispatch an installed app launcher or HTTPS deep link through Android."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val appContext = LocalContext.current
        var packageName by rememberSaveable { mutableStateOf(context.webSetting("package_name")) }
        var url by rememberSaveable { mutableStateOf(context.webSetting("url")) }
        var status by rememberSaveable { mutableStateOf("Choose an installed application or HTTPS deep link.") }
        var result by rememberRetainedExecutionResult("${context.action.canonicalId}:open")

        fun open() {
            val intent = when {
                packageName.isNotBlank() -> appContext.packageManager.getLaunchIntentForPackage(packageName.trim())
                validHttps(url) != null -> Intent(Intent.ACTION_VIEW, validHttps(url))
                else -> null
            }
            if (intent == null) {
                status = "No launchable installed app or valid HTTPS deep link was found."
                return
            }
            runCatching {
                appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                val request = ScreenAppOpenMethod.request(
                    action = ScreenAppOpenMethod.ID,
                    context = context.request.invocationContext.asMap(ScreenAppOpenMethod.ID) + context.action.settings,
                    signals = emptyList(), inputs = emptyList()
                )
                val values = mapOf(
                    ScreenAppOpenFields.STATUS to "succeeded",
                    ScreenAppOpenFields.ERROR to "",
                    ScreenAppOpenFields.PACKAGE_NAME to packageName.trim(),
                    ScreenAppOpenFields.URL to url.trim(),
                    ScreenAppOpenFields.TARGET to (packageName.trim().ifBlank { validHttps(url)?.toString().orEmpty() }),
                    ScreenAppOpenFields.DISPATCHED_TIME_ISO to Instant.now().toString()
                )
                val execution = ScreenAppOpenMethod.result(request, values, context.request.invocationContext)
                result = execution
                status = "Android launch dispatched. The target application owns its next screen."
                if (context.submitsImmediately) onConfirmed(execution)
            }.onFailure { status = "Could not dispatch target: ${it.message.orEmpty()}" }
        }

        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context, canGoBack = context.stepNumber > 1,
            capturedResult = result, resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack, onRetry = { result = null }, onConfirm = { result?.let(onConfirmed) }, onCancel = onCancel
        ) {
            Text("MethodMesh reports dispatch, not private state or completion inside the target application.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(packageName, { packageName = it }, label = { Text("Installed app package (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, label = { Text("HTTPS deep link (optional)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = ::open) { Text("Open target") }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
