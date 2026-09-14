package com.example.methodmesh.modules.webactions

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebStorage
import android.webkit.WebViewClient

/**
 * Keeps a live WebView instance across Activity configuration recreation.
 *
 * The WebView uses a MutableContextWrapper so an Activity context is never
 * retained after the capability leaves the composition. Active DOM state can
 * therefore survive rotation while the transaction itself remains durably
 * recorded by WebActionsRepository.
 *
 * ODK Web Forms/Enketo need ordinary browser affordances such as file upload
 * and geolocation. Those permission-bearing actions are delegated back to the
 * active Compose screen so Android runtime permission and ActivityResult
 * handling remain explicit and lifecycle-aware.
 */
internal object WebActionWebViewPool {
    private const val LOG_TAG = "MethodMeshWebAction"

    private data class Entry(
        val wrapper: MutableContextWrapper,
        val webView: WebView,
        val applicationContext: Context,
        var onCallback: (WebActionTransaction) -> Unit,
        var onStatus: (String) -> Unit,
        var onFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
        var onGeolocationRequest: (String, GeolocationPermissions.Callback) -> Unit
    )

    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun obtain(
        context: Context,
        transactionId: String,
        launchUrl: String,
        onCallback: (WebActionTransaction) -> Unit,
        onStatus: (String) -> Unit,
        onFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
        onGeolocationRequest: (String, GeolocationPermissions.Callback) -> Unit
    ): WebView {
        val appContext = context.applicationContext
        val existing = entries[transactionId]
        if (existing != null) {
            existing.wrapper.setBaseContext(context)
            existing.onCallback = onCallback
            existing.onStatus = onStatus
            existing.onFileChooser = onFileChooser
            existing.onGeolocationRequest = onGeolocationRequest
            (existing.webView.parent as? ViewGroup)?.removeView(existing.webView)
            if (existing.webView.url.isNullOrBlank() && launchUrl.isNotBlank()) {
                existing.webView.loadUrl(launchUrl)
            }
            return existing.webView
        }

        val wrapper = MutableContextWrapper(context)
        val entryRef = arrayOfNulls<Entry>(1)
        val webView = WebView(wrapper)
        val entry = Entry(
            wrapper = wrapper,
            webView = webView,
            applicationContext = appContext,
            onCallback = onCallback,
            onStatus = onStatus,
            onFileChooser = onFileChooser,
            onGeolocationRequest = onGeolocationRequest
        )
        entryRef[0] = entry
        val transactionForLaunch = WebActionsRepository.get(appContext, transactionId)
        val onlineOnly = transactionForLaunch?.methodId?.let(::isHostedWebFormRoundtripMethod) == true
        val chromeUserAgent = transactionForLaunch?.methodId == As100OdkEnketoRoundtripMethod.ID &&
            transactionForLaunch.originalUrl.contains("methodmesh_chrome_user_agent=true", ignoreCase = true)
        logDiagnostic(transactionForLaunch, "create-webview launch=${safeLogUrl(launchUrl)} ${credentialShape(launchUrl)} onlineOnly=$onlineOnly chromeUserAgent=$chromeUserAgent")
        configure(webView, onlineOnly, chromeUserAgent)
        if (onlineOnly) {
            webView.addJavascriptInterface(
                ProviderSuccessBridge(entry, transactionId),
                "MethodMeshWebAction"
            )
        }
        if (onlineOnly) prepareOnlineOnlySession(webView)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val navigation = request ?: return false
                // Completion must be a top-level redirect. Subframes/resources must never
                // be able to finish a MethodMesh protocol or ODK roundtrip.
                if (!navigation.isForMainFrame) return false
                logDiagnostic(
                    WebActionsRepository.get(appContext, transactionId),
                    "navigate ${safeLogUrl(navigation.url.toString())}"
                )
                return handleNavigation(
                    entryRef[0] ?: return false,
                    transactionId,
                    navigation.url.toString()
                )
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                logDiagnostic(WebActionsRepository.get(appContext, transactionId), "page-start ${safeLogUrl(url.orEmpty())}")
                val host = hostOf(url.orEmpty())
                entryRef[0]?.onStatus?.invoke(
                    if (host.isBlank()) "Loading web form…" else "Loading $host…"
                )
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val active = entryRef[0]
                val transaction = active?.let { WebActionsRepository.get(it.applicationContext, transactionId) }
                logDiagnostic(transaction, "page-finished ${safeLogUrl(url.orEmpty())}")
                if (transaction?.methodId?.let(::isHostedWebFormRoundtripMethod) == true) {
                    val bootstrap = if (transaction.methodId == As100OdkEnketoRoundtripMethod.ID) {
                        ODK_ENKETO_BOOTSTRAP_SCRIPT
                    } else {
                        ONLINE_ONLY_BOOTSTRAP_SCRIPT
                    }
                    view?.evaluateJavascript(bootstrap, null)
                    emitOdkEnketoPageMetrics(view, transaction)
                    applyZeroSizedEnketoImageMapFallback(view, transaction)
                    emitEnketoImageMapMetrics(view, transaction)
                }
                val host = hostOf(url.orEmpty())
                active?.onStatus?.invoke(
                    if (host.isBlank()) "Online form ready" else "$host · online only"
                )
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                logDiagnostic(
                    WebActionsRepository.get(appContext, transactionId),
                    "ssl-error primary=${error?.primaryError} url=${safeLogUrl(error?.url.orEmpty())}"
                )
                handler?.cancel()
                entryRef[0]?.onStatus?.invoke("Secure connection failed. The page was blocked.")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.description?.toString().orEmpty()
                    } else {
                        ""
                    }
                    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error?.errorCode?.toString().orEmpty() else ""
                    logDiagnostic(
                        WebActionsRepository.get(appContext, transactionId),
                        "main-frame-error code=$code description=${description.ifBlank { "unknown" }} url=${safeLogUrl(request.url?.toString().orEmpty())}"
                    )
                    entryRef[0]?.onStatus?.invoke(
                        description.ifBlank { "The web page could not be loaded. Check connectivity and retry." }
                    )
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                val transaction = WebActionsRepository.get(appContext, transactionId)
                val status = errorResponse?.statusCode ?: 0
                if (request?.isForMainFrame == true) {
                    logDiagnostic(
                        transaction,
                        "main-frame-http status=$status reason=${errorResponse?.reasonPhrase.orEmpty()} url=${safeLogUrl(request.url?.toString().orEmpty())}"
                    )
                } else if (transaction?.methodId == As100OdkEnketoRoundtripMethod.ID && status >= 400) {
                    logDiagnostic(
                        transaction,
                        "resource-http status=$status reason=${errorResponse?.reasonPhrase.orEmpty()} url=${safeLogUrl(request?.url?.toString().orEmpty())}"
                    )
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val message = consoleMessage ?: return false
                logDiagnostic(
                    WebActionsRepository.get(appContext, transactionId),
                    "console level=${message.messageLevel()?.name.orEmpty()} line=${message.lineNumber()} source=${safeLogUrl(message.sourceId().orEmpty())}"
                )
                return false
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                val callback = filePathCallback ?: return false
                val params = fileChooserParams ?: run {
                    callback.onReceiveValue(null)
                    return true
                }
                return entryRef[0]?.onFileChooser?.invoke(callback, params) ?: false
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                val safeOrigin = origin.orEmpty()
                val permissionCallback = callback ?: return
                val active = entryRef[0]
                val transaction = active?.let { WebActionsRepository.get(it.applicationContext, transactionId) }
                val originUri = runCatching { Uri.parse(safeOrigin) }.getOrNull()
                val allowedScheme = originUri?.scheme.equals("https", ignoreCase = true) ||
                    (transaction?.allowInsecureHttp == true && originUri?.scheme.equals("http", ignoreCase = true))
                val sameHost = !transaction?.launchHost.isNullOrBlank() &&
                    originUri?.host.equals(transaction?.launchHost, ignoreCase = true)
                if (!allowedScheme || !sameHost) {
                    permissionCallback.invoke(safeOrigin, false, false)
                    active?.onStatus?.invoke("Blocked location request from an unexpected web origin.")
                    return
                }
                active.onGeolocationRequest(safeOrigin, permissionCallback)
            }
        }

        entries[transactionId] = entry
        armProviderSuccessMonitor(entry, transactionId)
        if (launchUrl.isNotBlank()) {
            logDiagnostic(WebActionsRepository.get(appContext, transactionId), "load-url ${safeLogUrl(launchUrl)} ${credentialShape(launchUrl)}")
            webView.loadUrl(launchUrl)
        }
        return webView
    }


    @Synchronized
    fun refresh(transactionId: String) {
        val entry = entries[transactionId] ?: return
        val transaction = WebActionsRepository.get(entry.applicationContext, transactionId) ?: return
        entry.webView.stopLoading()
        if (isHostedWebFormRoundtripMethod(transaction.methodId)) {
            purgeOnlineOnlySession(entry.webView)
            prepareOnlineOnlySession(entry.webView)
        }
        val url = transaction.launchUrl.ifBlank { transaction.originalUrl }
        logDiagnostic(transaction, "refresh-load ${safeLogUrl(url)} ${credentialShape(url)}")
        entry.webView.loadUrl(url)
        entry.onStatus("Refreshing form…")
    }

    @Synchronized
    fun detach(transactionId: String) {
        entries[transactionId]?.let { entry ->
            (entry.webView.parent as? ViewGroup)?.removeView(entry.webView)
            entry.wrapper.setBaseContext(entry.applicationContext)
        }
    }

    @Synchronized
    fun destroy(transactionId: String) {
        entries.remove(transactionId)?.let { entry ->
            val onlineOnly = WebActionsRepository.get(entry.applicationContext, transactionId)?.methodId?.let(::isHostedWebFormRoundtripMethod) == true
            (entry.webView.parent as? ViewGroup)?.removeView(entry.webView)
            entry.webView.stopLoading()
            if (onlineOnly) purgeOnlineOnlySession(entry.webView)
            entry.webView.webViewClient = WebViewClient()
            entry.webView.webChromeClient = WebChromeClient()
            entry.webView.loadUrl("about:blank")
            entry.webView.clearHistory()
            entry.webView.removeAllViews()
            entry.webView.destroy()
        }
    }

    /**
     * Some Kobo/legacy Enketo links are multi-submission links. In that mode the
     * provider can acknowledge a successful upload in a modal and immediately
     * reset the page for the next blank record instead of following return_url.
     *
     * The return URL remains the primary completion mechanism. This monitor is a
     * deliberately narrow fallback for the Central capability: it only accepts a
     * visible provider confirmation dialog containing a strong success phrase.
     * It never treats a page reload, browser Back, or a blank/new record as success.
     */
    private fun armProviderSuccessMonitor(entry: Entry, transactionId: String) {
        val webView = entry.webView
        val poll = object : Runnable {
            override fun run() {
                val transaction = WebActionsRepository.get(entry.applicationContext, transactionId) ?: return
                if (transaction.state != WebActionTransactionState.WAITING) return
                if (!isHostedWebFormRoundtripMethod(transaction.methodId)) {
                    webView.postDelayed(this, 500L)
                    return
                }
                // The page installs a MutationObserver that latches provider success
                // the instant it appears. Poll the latch rather than trying to catch a
                // short-lived modal after Enketo has already started its next record.
                webView.evaluateJavascript(PROVIDER_SUCCESS_LATCH_SCRIPT) { raw ->
                    if (raw == "true") {
                        val completed = WebActionsRepository.markProviderCompletion(
                            entry.applicationContext,
                            transactionId,
                            "provider_submission_confirmation"
                        )
                        if (completed != null) {
                            freezeSubmittedPage(webView)
                            entry.onStatus("Submission complete — returning to MethodMesh")
                            entry.onCallback(completed)
                            return@evaluateJavascript
                        }
                    }
                }
                webView.postDelayed(this, 100L)
            }
        }
        webView.postDelayed(poll, 350L)
    }

    private class ProviderSuccessBridge(
        private val entry: Entry,
        private val transactionId: String
    ) {
        @JavascriptInterface
        fun providerSubmitted() {
            entry.webView.post {
                completeProviderSubmission(entry, transactionId)
            }
        }
    }

    private fun completeProviderSubmission(entry: Entry, transactionId: String) {
        val transaction = WebActionsRepository.get(entry.applicationContext, transactionId) ?: return
        if (transaction.state != WebActionTransactionState.WAITING) return
        if (!isHostedWebFormRoundtripMethod(transaction.methodId)) return
        val completed = WebActionsRepository.markProviderCompletion(
            entry.applicationContext,
            transactionId,
            "provider_submission_confirmation"
        ) ?: return
        freezeSubmittedPage(entry.webView)
        entry.onStatus("Submission complete — returning to MethodMesh")
        entry.onCallback(completed)
    }

    private fun freezeSubmittedPage(webView: WebView) {
        webView.evaluateJavascript(
            """
            (function(){
              try {
                window.stop();
                document.documentElement.innerHTML = '<body style="font-family:sans-serif;padding:32px;background:#fff;color:#102a43"><h2>Submitted</h2><p>Returning to MethodMesh…</p></body>';
                return true;
              } catch (e) { return false; }
            })();
            """.trimIndent(),
            null
        )
        webView.stopLoading()
        webView.onPause()
    }

    private val PROVIDER_SUCCESS_LATCH_SCRIPT = """
        (function() {
          try {
            function textOf(el) {
              return ((el && (el.innerText || el.textContent)) || '').replace(/\s+/g, ' ').trim().toLowerCase();
            }
            function visible(el) {
              if (!el) return false;
              var s = window.getComputedStyle(el);
              var r = el.getBoundingClientRect();
              return s.display !== 'none' && s.visibility !== 'hidden' && s.opacity !== '0' && r.width > 0 && r.height > 0;
            }
            function strongSuccessVisible() {
              var nodes = Array.prototype.slice.call(document.querySelectorAll('body *'));
              for (var i = 0; i < nodes.length; i++) {
                var el = nodes[i];
                if (!visible(el)) continue;
                var t = textOf(el);
                if (!t || t.length > 700) continue;
                if (t.indexOf('unsaved record found') >= 0 || t.indexOf('submission failed') >= 0) continue;
                if (/submission\s+successful/i.test(t) ||
                    /your\s+data\s+was\s+submitted/i.test(t) ||
                    /successfully\s+submitted/i.test(t) ||
                    /was\s+successfully\s+submitted/i.test(t) ||
                    /thank\s+you\s+for\s+(?:participating|completing|submitting)/i.test(t) ||
                    /thanks\s+for\s+(?:completing|submitting)/i.test(t) ||
                    /you\s+can\s+close\s+this\s+window\s+now/i.test(t)) return true;
              }
              return false;
            }
            if (window.__methodmeshSubmissionSuccess === true) return true;
            if (strongSuccessVisible()) {
              window.__methodmeshSubmissionSuccess = true;
              return true;
            }
            return false;
          } catch (e) { return false; }
        })();
    """.trimIndent()

    private val ONLINE_ONLY_BOOTSTRAP_SCRIPT = """
        (function() {
          try {
            if (!window.__methodmeshOnlineOnlyInstalled) {
              window.__methodmeshOnlineOnlyInstalled = true;
              window.__methodmeshSubmissionSuccess = false;
              window.__methodmeshSubmitSeen = false;

              function textOf(el) {
                return ((el && (el.innerText || el.textContent)) || '').replace(/\\s+/g, ' ').trim().toLowerCase();
              }
              function visible(el) {
                if (!el) return false;
                var s = window.getComputedStyle(el);
                var r = el.getBoundingClientRect();
                return s.display !== 'none' && s.visibility !== 'hidden' && r.width > 0 && r.height > 0;
              }
              function hideOnlineOnlyControls() {
                var all = Array.prototype.slice.call(document.querySelectorAll('button, a, [role="button"], [aria-label], [title], input[type="button"], input[type="submit"]'));
                all.forEach(function(el) {
                  var t = (textOf(el) + ' ' + (el.getAttribute('aria-label') || '') + ' ' + (el.getAttribute('title') || '') + ' ' + (el.getAttribute('value') || '')).toLowerCase();
                  var draft = /\bsave\s+(?:as\s+)?draft\b/.test(t);
                  var offlineUi = /\boffline\b/.test(t) || (/\brecords?\b/.test(t) && !/submit/.test(t));
                  if (draft || offlineUi) el.style.setProperty('display', 'none', 'important');
                });
                var selectors = [
                  '.save-draft', '.save-draft-btn', '.btn-save-draft',
                  '[class*="save-draft"]', '[data-i18n*="savedraft"]',
                  '.record-list-toggle', '[class*="record-list"]'
                ];
                selectors.forEach(function(sel) {
                  try { document.querySelectorAll(sel).forEach(function(el){ el.style.setProperty('display','none','important'); }); } catch(e) {}
                });
              }
              function notifyProviderSubmitted() {
                try {
                  if (window.MethodMeshWebAction && window.MethodMeshWebAction.providerSubmitted) {
                    window.MethodMeshWebAction.providerSubmitted();
                  }
                } catch (e) {}
              }

              function strongSuccessVisible() {
                var nodes = Array.prototype.slice.call(document.querySelectorAll('body *'));
                for (var i = 0; i < nodes.length; i++) {
                  var el = nodes[i];
                  if (!visible(el)) continue;
                  var t = textOf(el);
                  if (!t || t.length > 500) continue;
                  if (t.indexOf('unsaved record found') >= 0) continue;
                  if (/thank\\s+you\\s+for\\s+(?:completing|participating|submitting)/i.test(t) ||
                      /thanks\\s+for\\s+(?:completing|submitting)/i.test(t) ||
                      /your\\s+data\\s+was\\s+submitted/i.test(t) ||
                      /submission\\s+successful/i.test(t) ||
                      /successfully\\s+submitted/i.test(t) ||
                      /you\\s+can\\s+close\\s+this\\s+window\\s+now/i.test(t)) {
                    return true;
                  }
                }
                return false;
              }

              document.addEventListener('click', function(ev) {
                var el = ev.target;
                while (el && el !== document.body && !/^(BUTTON|INPUT|A)$/.test(el.tagName || '')) el = el.parentElement;
                var t = textOf(el) + ' ' + ((el && el.getAttribute && (el.getAttribute('aria-label') || el.getAttribute('value') || el.getAttribute('title'))) || '');
                if (/\\bsubmit\\b|\\bcomplete\\b|\\bfinali[sz]e\\b/i.test(t)) window.__methodmeshSubmitSeen = true;
                if (window.__methodmeshSubmissionSuccess === true && /^\\s*(ok|okay|close|done|continue)\\s*$/i.test(t)) {
                  try {
                    ev.preventDefault();
                    ev.stopPropagation();
                    notifyProviderSubmitted();
                  } catch (e) {}
                }
              }, true);

              var scan = function() {
                hideOnlineOnlyControls();
                if (window.__methodmeshSubmitSeen === true && strongSuccessVisible()) {
                  window.__methodmeshSubmissionSuccess = true;
                  notifyProviderSubmitted();
                }
              };
              scan();
              new MutationObserver(scan).observe(document.documentElement || document.body, {subtree:true, childList:true, characterData:true, attributes:true});
              [50,150,350,750,1500,3000].forEach(function(ms){ setTimeout(scan, ms); });
            }

            // Best-effort removal of offline browser machinery. The active form
            // remains fully online and DOM storage is purged when the kiosk closes.
            if (navigator.serviceWorker && navigator.serviceWorker.getRegistrations) {
              navigator.serviceWorker.getRegistrations().then(function(rs){ rs.forEach(function(r){ try { r.unregister(); } catch(e) {} }); });
            }
            if (window.caches && caches.keys) {
              caches.keys().then(function(keys){ keys.forEach(function(k){ try { caches.delete(k); } catch(e) {} }); });
            }
            return true;
          } catch (e) {
            return false;
          }
        })();
    """.trimIndent()


    private val ODK_ENKETO_BOOTSTRAP_SCRIPT = """
        (function() {
          try {
            if (!window.__methodmeshOdkEnketoInstalled) {
              window.__methodmeshOdkEnketoInstalled = true;
              window.__methodmeshSubmissionSuccess = false;
              window.__methodmeshSubmitSeen = false;

              function textOf(el) {
                return ((el && (el.innerText || el.textContent)) || '').replace(/\s+/g, ' ').trim().toLowerCase();
              }
              function visible(el) {
                if (!el) return false;
                var s = window.getComputedStyle(el);
                var r = el.getBoundingClientRect();
                return s.display !== 'none' && s.visibility !== 'hidden' && r.width > 0 && r.height > 0;
              }
              function notifyProviderSubmitted() {
                try {
                  if (window.MethodMeshWebAction && window.MethodMeshWebAction.providerSubmitted) {
                    window.MethodMeshWebAction.providerSubmitted();
                  }
                } catch (e) {}
              }
              function strongSuccessVisible() {
                var nodes = Array.prototype.slice.call(document.querySelectorAll('body *'));
                for (var i = 0; i < nodes.length; i++) {
                  var el = nodes[i];
                  if (!visible(el)) continue;
                  var t = textOf(el);
                  if (!t || t.length > 500) continue;
                  if (t.indexOf('unsaved record found') >= 0 || t.indexOf('submission failed') >= 0) continue;
                  if (/thank\s+you\s+for\s+(?:completing|participating|submitting)/i.test(t) ||
                      /your\s+data\s+was\s+submitted/i.test(t) ||
                      /submission\s+successful/i.test(t) ||
                      /successfully\s+submitted/i.test(t) ||
                      /you\s+can\s+close\s+this\s+window\s+now/i.test(t)) return true;
                }
                return false;
              }
              document.addEventListener('click', function(ev) {
                var el = ev.target;
                while (el && el !== document.body && !/^(BUTTON|INPUT|A)$/.test(el.tagName || '')) el = el.parentElement;
                var t = textOf(el) + ' ' + ((el && el.getAttribute && (el.getAttribute('aria-label') || el.getAttribute('value') || el.getAttribute('title'))) || '');
                if (/\bsubmit\b|\bcomplete\b|\bfinali[sz]e\b/i.test(t)) window.__methodmeshSubmitSeen = true;
                if (window.__methodmeshSubmissionSuccess === true && /^\s*(ok|okay|close|done|continue)\s*$/i.test(t)) {
                  try { ev.preventDefault(); ev.stopPropagation(); notifyProviderSubmitted(); } catch (e) {}
                }
              }, true);
              var scan = function() {
                if (window.__methodmeshSubmitSeen === true && strongSuccessVisible()) {
                  window.__methodmeshSubmissionSuccess = true;
                  notifyProviderSubmitted();
                }
              };
              scan();
              new MutationObserver(scan).observe(document.documentElement || document.body, {subtree:true, childList:true, characterData:true, attributes:true});
              [50,150,350,750,1500,3000].forEach(function(ms){ setTimeout(scan, ms); });
            }
            return true;
          } catch (e) {
            return false;
          }
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    private fun prepareOnlineOnlySession(webView: WebView) {
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.settings.setSupportMultipleWindows(false)
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        WebStorage.getInstance().deleteAllData()
    }

    private fun purgeOnlineOnlySession(webView: WebView) {
        runCatching { webView.evaluateJavascript("(function(){try{localStorage.clear();sessionStorage.clear();if(window.caches&&caches.keys){caches.keys().then(function(keys){keys.forEach(function(k){caches.delete(k);});});}if(navigator.serviceWorker&&navigator.serviceWorker.getRegistrations){navigator.serviceWorker.getRegistrations().then(function(rs){rs.forEach(function(r){r.unregister();});});}return true;}catch(e){return false;}})();", null) }
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        WebStorage.getInstance().deleteAllData()
    }

    private fun handleNavigation(entry: Entry, transactionId: String, rawUrl: String): Boolean {
        val transaction = WebActionsRepository.get(entry.applicationContext, transactionId)
        if (rawUrl.isBlank()) {
            logDiagnostic(transaction, "blank-navigation")
            return false
        }
        val completed = WebActionsRepository.consumeCallback(entry.applicationContext, transactionId, rawUrl)
        if (completed != null) {
            logDiagnostic(completed, "completion-callback ${safeLogUrl(rawUrl)}")
            freezeSubmittedPage(entry.webView)
            entry.onStatus("Completion received — returning to MethodMesh")
            entry.onCallback(completed)
            return true
        }

        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: run {
            logDiagnostic(transaction, "blocked invalid-url ${safeLogUrl(rawUrl)}")
            return true
        }
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme == "https") return false
        if (scheme == "http") {
            val transaction = WebActionsRepository.get(entry.applicationContext, transactionId)
            return if (transaction?.allowInsecureHttp == true) {
                false
            } else {
                logDiagnostic(transaction, "blocked-http ${safeLogUrl(rawUrl)}")
                entry.onStatus("Blocked insecure HTTP navigation.")
                true
            }
        }

        val externallyAllowed = scheme in setOf("mailto", "tel", "sms", "geo")
        if (!externallyAllowed) {
            logDiagnostic(transaction, "blocked-external scheme=$scheme url=${safeLogUrl(rawUrl)}")
            entry.onStatus("Blocked unsupported external link.")
            return true
        }

        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            entry.applicationContext.startActivity(intent)
            entry.onStatus("Opened $scheme link")
            true
        }.getOrElse {
            entry.onStatus("This link cannot be opened on this device.")
            true
        }
    }


    /**
     * Android WebView fallback for a narrow Enketo/SVG layout failure.
     *
     * Some image-map SVGs legitimately arrive with width="100%" and height="100%" plus a
     * valid viewBox. Chromium normally derives a box from Enketo's responsive CSS, but some
     * Android WebView builds resolve that percentage-height chain to 0px after the SVG has been
     * inlined. In that state Enketo has successfully created the widget and its hit regions, but
     * there is literally no rendered surface to see or tap.
     *
     * This fallback is intentionally conditional and layout-only: it runs only for initialized
     * Enketo image maps whose SVG has a valid viewBox but a zero rendered width/height. It never
     * changes viewBox, path geometry, IDs, preserveAspectRatio, or selection state. Width is taken
     * from the containing question (falling back to the viewport), and height is derived from the
     * SVG's own viewBox aspect ratio. A ResizeObserver/window resize listener keeps the fallback
     * responsive after rotation or container changes.
     */
    private fun applyZeroSizedEnketoImageMapFallback(webView: WebView?, transaction: WebActionTransaction?) {
        if (webView == null || transaction == null) return
        if (transaction.methodId != As100OdkEnketoRoundtripMethod.ID &&
            transaction.methodId != As100KoboEnketoRoundtripMethod.ID
        ) return

        val script = """
            (function(){
              try {
                if (window.__methodMeshEnketoImageMapSizingInstalled) {
                  return JSON.stringify({installed:true, reused:true});
                }
                window.__methodMeshEnketoImageMapSizingInstalled = true;

                function validViewBox(svg) {
                  try {
                    var vb = svg && svg.viewBox && svg.viewBox.baseVal;
                    return vb && isFinite(vb.width) && isFinite(vb.height) && vb.width > 0 && vb.height > 0 ? vb : null;
                  } catch (_) { return null; }
                }

                function availableWidth(svg) {
                  var node = svg.parentElement;
                  while (node && node !== document.body) {
                    var r = node.getBoundingClientRect ? node.getBoundingClientRect() : null;
                    if (r && r.width > 1) return r.width;
                    node = node.parentElement;
                  }
                  return Math.max(1, document.documentElement.clientWidth || window.innerWidth || 320);
                }

                function sizeOne(svg) {
                  if (!svg || !svg.closest || !svg.closest('.image-map')) return false;
                  var before = svg.getBoundingClientRect();
                  if (before.width > 1 && before.height > 1) return false;
                  var vb = validViewBox(svg);
                  if (!vb) return false;

                  var width = availableWidth(svg);
                  var height = width * vb.height / vb.width;
                  if (!(width > 1 && height > 1)) return false;

                  var widget = svg.closest('.image-map');
                  if (widget) {
                    widget.style.setProperty('width', '100%', 'important');
                    widget.style.setProperty('max-width', '100%', 'important');
                  }
                  svg.style.setProperty('display', 'block', 'important');
                  svg.style.setProperty('width', Math.round(width) + 'px', 'important');
                  svg.style.setProperty('height', Math.round(height) + 'px', 'important');
                  svg.style.setProperty('max-width', '100%', 'important');
                  svg.style.setProperty('min-height', '1px', 'important');
                  svg.setAttribute('data-methodmesh-zero-box-fallback', 'true');
                  return true;
                }

                function repair() {
                  var fixed = 0;
                  document.querySelectorAll('.image-map svg').forEach(function(svg) {
                    if (sizeOne(svg)) fixed += 1;
                  });
                  return fixed;
                }

                var totalFixed = repair();
                var observer = null;
                if (window.ResizeObserver) {
                  observer = new ResizeObserver(function() { repair(); });
                  document.querySelectorAll('.or-image-map-initialized, .image-map').forEach(function(el) {
                    try { observer.observe(el); } catch (_) {}
                  });
                  window.__methodMeshEnketoImageMapResizeObserver = observer;
                }
                window.addEventListener('resize', repair, {passive:true});

                // Enketo builds image maps asynchronously after the document load event.
                [400, 1000, 2000, 3500, 5000].forEach(function(ms) {
                  setTimeout(repair, ms);
                });

                return JSON.stringify({installed:true, initiallyFixed:totalFixed});
              } catch (e) {
                return JSON.stringify({installed:false, errorType:String(e && e.name || 'error')});
              }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            logDiagnostic(transaction, "enketo-image-map-zero-box-fallback $raw")
        }
    }

    /**
     * Read-only diagnostics for Enketo image-map widgets.
     *
     * Do not rewrite SVG viewBox, width, height, preserveAspectRatio, or child geometry here.
     * Enketo's image-map widget owns those values and recalculates its own viewBox when needed.
     * Mutating the provider DOM from MethodMesh can distort hit regions and responsive sizing.
     */
    private fun emitEnketoImageMapMetrics(webView: WebView?, transaction: WebActionTransaction?) {
        if (webView == null || transaction == null) return
        if (transaction.methodId != As100OdkEnketoRoundtripMethod.ID &&
            transaction.methodId != As100KoboEnketoRoundtripMethod.ID
        ) return

        val script = """
            (function(){
              try {
                var widget = document.querySelector('.image-map');
                var svg = widget && widget.querySelector('svg');
                var rect = svg && svg.getBoundingClientRect ? svg.getBoundingClientRect() : null;
                var supported = svg ? svg.querySelectorAll('path[id], g[id], circle[id]').length : 0;
                var unsupported = svg ? svg.querySelectorAll('rect[id], ellipse[id], polygon[id], polyline[id], line[id]').length : 0;
                return JSON.stringify({
                  initializedQuestions: document.querySelectorAll('.or-image-map-initialized').length,
                  widgets: document.querySelectorAll('.image-map').length,
                  errors: document.querySelectorAll('.image-map__error').length,
                  pendingImages: document.querySelectorAll('.or-appearance-image-map img').length,
                  svgPresent: !!svg,
                  viewBox: svg ? (svg.getAttribute('viewBox') || '') : '',
                  widthAttribute: svg ? (svg.getAttribute('width') || '') : '',
                  heightAttribute: svg ? (svg.getAttribute('height') || '') : '',
                  clientWidth: rect ? Math.round(rect.width) : 0,
                  clientHeight: rect ? Math.round(rect.height) : 0,
                  parentWidth: widget && widget.getBoundingClientRect ? Math.round(widget.getBoundingClientRect().width) : 0,
                  computedWidth: svg ? window.getComputedStyle(svg).width : '',
                  computedHeight: svg ? window.getComputedStyle(svg).height : '',
                  fallbackApplied: svg ? svg.getAttribute('data-methodmesh-zero-box-fallback') === 'true' : false,
                  fallbackCount: document.querySelectorAll('svg[data-methodmesh-zero-box-fallback="true"]').length,
                  supportedSelectableIds: supported,
                  unsupportedShapeIds: unsupported
                });
              } catch (e) {
                return JSON.stringify({errorType: String(e && e.name || 'error')});
              }
            })();
        """.trimIndent()

        // Image-map initialization fetches and parses the SVG asynchronously. Sample after
        // the page has had time to construct the widget, without changing provider state.
        listOf(500L, 1500L, 3000L).forEach { delayMs ->
            webView.postDelayed({
                val current = WebActionsRepository.get(webView.context.applicationContext, transaction.id)
                if (current?.state == WebActionTransactionState.PREPARING || current?.state == WebActionTransactionState.WAITING) {
                    webView.evaluateJavascript(script) { raw ->
                        logDiagnostic(current, "enketo-image-map-metrics delayMs=$delayMs $raw")
                    }
                }
            }, delayMs)
        }
    }

    private fun emitOdkEnketoPageMetrics(webView: WebView?, transaction: WebActionTransaction?) {
        if (webView == null || transaction == null || transaction.methodId != As100OdkEnketoRoundtripMethod.ID) return
        webView.evaluateJavascript(
            """
            (function(){
              try {
                var body = document.body;
                var style = body ? window.getComputedStyle(body) : null;
                return JSON.stringify({
                  readyState: document.readyState,
                  hrefHost: location.host || '',
                  hrefPath: location.pathname || '',
                  bodyLength: body ? (body.innerText || body.textContent || '').length : -1,
                  bodyChildren: body ? body.children.length : -1,
                  documentHeight: document.documentElement ? document.documentElement.scrollHeight : -1,
                  bodyDisplay: style ? style.display : '',
                  bodyVisibility: style ? style.visibility : '',
                  scripts: document.scripts ? document.scripts.length : -1
                });
              } catch (e) {
                return JSON.stringify({errorType: String(e && e.name || 'error')});
              }
            })();
            """.trimIndent()
        ) { raw ->
            logDiagnostic(transaction, "odk-enketo-page-metrics $raw")
        }
    }

    private fun logDiagnostic(transaction: WebActionTransaction?, message: String) {
        val method = transaction?.methodId.orEmpty().ifBlank { "unknown" }
        val id = transaction?.id?.take(8).orEmpty().ifBlank { "no-txn" }
        Log.d(LOG_TAG, "method=$method txn=$id $message")
    }

    private fun safeLogUrl(raw: String): String = runCatching { redactedWebUrl(raw) }.getOrDefault(raw.take(300))


    private fun credentialShape(raw: String): String {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return "credential=unparseable"
        val names = uri.queryParameterNames.sorted()
        val st = runCatching { uri.getQueryParameter("st") }.getOrNull().orEmpty()
        val encodedQuery = uri.encodedQuery.orEmpty()
        val hasRawDollar = raw.contains('$')
        val hasEncodedDollar = encodedQuery.contains("%24", ignoreCase = true)
        val hasBang = raw.contains('!') || encodedQuery.contains("%21", ignoreCase = true)
        return "queryNames=${names.joinToString(",")} stLength=${st.length} rawDollar=$hasRawDollar encodedDollar=$hasEncodedDollar bang=$hasBang fragmentPresent=${!uri.fragment.isNullOrBlank()}"
    }


    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(webView: WebView, onlineOnly: Boolean, chromeUserAgent: Boolean = false) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true)
            allowFileAccess = false
            allowContentAccess = true // required by Android's content:// file chooser results
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = if (onlineOnly) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            if (chromeUserAgent) {
                userAgentString = userAgentString
                    .replace("; wv", "")
                    .replace(Regex(" Version/\\d+(?:\\.\\d+)*"), "")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setSafeBrowsingEnabled(true)
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, onlineOnly)
            }
        }
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = true
    }
}
