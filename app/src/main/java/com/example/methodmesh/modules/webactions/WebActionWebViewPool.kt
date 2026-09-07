package com.example.methodmesh.modules.webactions

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
        val onlineOnly = WebActionsRepository.get(appContext, transactionId)?.methodId == As100OdkCentralRoundtripMethod.ID
        configure(webView, onlineOnly)
        if (onlineOnly) prepareOnlineOnlySession(webView)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val navigation = request ?: return false
                // Completion must be a top-level redirect. Subframes/resources must never
                // be able to finish a MethodMesh protocol or ODK roundtrip.
                if (!navigation.isForMainFrame) return false
                return handleNavigation(
                    entryRef[0] ?: return false,
                    transactionId,
                    navigation.url.toString()
                )
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                val host = hostOf(url.orEmpty())
                entryRef[0]?.onStatus?.invoke(
                    if (host.isBlank()) "Loading web form…" else "Loading $host…"
                )
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val active = entryRef[0]
                val transaction = active?.let { WebActionsRepository.get(it.applicationContext, transactionId) }
                if (transaction?.methodId == As100OdkCentralRoundtripMethod.ID) {
                    view?.evaluateJavascript(ONLINE_ONLY_BOOTSTRAP_SCRIPT, null)
                }
                val host = hostOf(url.orEmpty())
                active?.onStatus?.invoke(
                    if (host.isBlank()) "Online form ready" else "$host · online only"
                )
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
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
                    entryRef[0]?.onStatus?.invoke(
                        description.ifBlank { "The web page could not be loaded. Check connectivity and retry." }
                    )
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
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
        if (launchUrl.isNotBlank()) webView.loadUrl(launchUrl)
        return webView
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
            val onlineOnly = WebActionsRepository.get(entry.applicationContext, transactionId)?.methodId == As100OdkCentralRoundtripMethod.ID
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
                if (transaction.methodId != As100OdkCentralRoundtripMethod.ID) {
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
              }, true);

              var scan = function() {
                hideOnlineOnlyControls();
                if (strongSuccessVisible()) {
                  window.__methodmeshSubmissionSuccess = true;
                }
              };
              scan();
              new MutationObserver(scan).observe(document.documentElement || document.body, {subtree:true, childList:true, characterData:true, attributes:true});
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
        if (rawUrl.isBlank()) return false
        val completed = WebActionsRepository.consumeCallback(entry.applicationContext, transactionId, rawUrl)
        if (completed != null) {
            freezeSubmittedPage(entry.webView)
            entry.onStatus("Completion received — returning to MethodMesh")
            entry.onCallback(completed)
            return true
        }

        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return true
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme == "https") return false
        if (scheme == "http") {
            val transaction = WebActionsRepository.get(entry.applicationContext, transactionId)
            return if (transaction?.allowInsecureHttp == true) {
                false
            } else {
                entry.onStatus("Blocked insecure HTTP navigation.")
                true
            }
        }

        val externallyAllowed = scheme in setOf("mailto", "tel", "sms", "geo")
        if (!externallyAllowed) {
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(webView: WebView, onlineOnly: Boolean) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setSafeBrowsingEnabled(true)
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, false)
            }
        }
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = true
    }
}
