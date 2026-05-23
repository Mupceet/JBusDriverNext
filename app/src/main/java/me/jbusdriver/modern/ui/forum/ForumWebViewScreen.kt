package me.jbusdriver.modern.ui.forum

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import me.jbusdriver.modern.core.site.SiteConfigStore
import me.jbusdriver.modern.data.SessionCookieStore

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ForumWebViewScreen(
    onThreadClick: (Int) -> Unit = {},
    sessionCookieStore: SessionCookieStore = SessionCookieStore()
) {
    val baseUrl = SiteConfigStore.baseUrl
    val forumUrl = "${baseUrl}/forum/"
    var isLoading by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = false
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false

                    sessionCookieStore.restoreCookies(forumUrl)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val url = request.url.toString()
                            val tidRegex = Regex("""[?&]tid=(\d+)""")
                            tidRegex.find(url)?.groupValues?.get(1)?.toIntOrNull()?.let { tid ->
                                if (url.contains("mod=viewthread")) {
                                    onThreadClick(tid)
                                    return true
                                }
                            }
                            return false
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            isLoading = false

                            val css = context.assets.open("forum_mobile.css")
                                .bufferedReader().use { it.readText() }
                            view.evaluateJavascript(
                                "(function(){var s=document.createElement('style');s.textContent=${escapeForJs(css)};document.head.appendChild(s);})();",
                                null
                            )

                            val js = context.assets.open("forum_mobile.js")
                                .bufferedReader().use { it.readText() }
                            view.evaluateJavascript(js, null)

                            url?.let { sessionCookieStore.saveCookies(it) }
                        }
                    }

                    loadUrl(forumUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun escapeForJs(text: String): String {
    return "`" + text.replace("\\", "\\\\").replace("`", "\\`").replace("\$", "\\\$") + "`"
}
