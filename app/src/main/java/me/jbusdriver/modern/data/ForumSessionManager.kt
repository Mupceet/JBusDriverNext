package me.jbusdriver.modern.data

import android.app.Activity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ForumSession"

/**
 * WebView-based forum session initializer.
 *
 * Loads the main site in a hidden WebView to establish cookies
 * (including JS-triggered ones), then OkHttp reuses those cookies
 * via the shared CookieManagerCookieJar.
 */
@Singleton
class ForumSessionManager @Inject constructor() {

    private val initialized = AtomicBoolean(false)

    fun isInitialized(): Boolean = initialized.get()

    fun reset() {
        initialized.set(false)
    }

    /**
     * Ensure forum session is established via WebView.
     *
     * Flow:
     * 1. Load forum URL → if no redirect, done
     * 2. If redirected to member.php (login page), load main site homepage
     * 3. Then load forum URL again → should succeed this time
     *
     * Must be called from a coroutine scope that provides an Activity
     * via JBusManager.
     */
    suspend fun ensureSession(activity: Activity) {
        if (initialized.get()) return

        withTimeout(15_000) {
            withContext(Dispatchers.Main) {
                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                val webView = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    visibility = android.view.View.INVISIBLE
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                rootView.addView(webView, FrameLayout.LayoutParams(1, 1))

                try {
                    // Step 1: Try loading forum directly
                    val forumUrl = "${NetClient.defaultFastUrl}/forum/"
                    val firstUrl = loadPage(webView, forumUrl)
                    KLog.d("[Forum] First load landed at: $firstUrl", TAG)

                    if (firstUrl.contains("member.php")) {
                        // Redirected to login — need main site warmup
                        KLog.d("[Forum] Login redirect detected, loading main site", TAG)
                        val mainUrl = "${NetClient.defaultFastUrl}/"
                        loadPage(webView, mainUrl)
                        KLog.d("[Forum] Main site loaded, retrying forum", TAG)

                        // Retry forum
                        val retryUrl = loadPage(webView, forumUrl)
                        KLog.d("[Forum] Retry landed at: $retryUrl", TAG)

                        if (retryUrl.contains("member.php")) {
                            throw IOException("Forum still redirects to login after main site warmup")
                        }
                    }

                    initialized.set(true)
                    KLog.d("[Forum] Session initialization complete", TAG)
                } finally {
                    webView.stopLoading()
                    rootView.removeView(webView)
                    webView.destroy()
                }
            }
        }
    }

    /**
     * Load a URL in the WebView and wait for onPageFinished.
     * Returns the final URL after any redirects.
     */
    private suspend fun loadPage(webView: WebView, url: String): String {
        return suspendCancellableCoroutine { cont ->
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    if (cont.isActive) {
                        KLog.d("[Forum] Page finished: $pageUrl", TAG)
                        cont.resume(pageUrl ?: url) {}
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true && cont.isActive) {
                        KLog.e("[Forum] WebView error: ${error?.description}", TAG)
                        cont.resumeWith(
                            Result.failure(
                                IOException("WebView error loading $url: ${error?.description}")
                            )
                        )
                    }
                }
            }
            webView.loadUrl(url)
        }
    }
}
