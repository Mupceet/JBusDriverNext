package me.jbusdriver.modern.ui

/**
 * 职责：应用唯一的 Activity 入口，承载所有 Compose UI
 *
 * 使用场景：AndroidManifest 中声明的 launcher Activity，
 * 通过 setContent 设置 Compose 根组件 JBusNavigation
 *
 * 线程：生命周期方法在主线程
 */

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.jbusdriver.modern.ui.theme.JBusTheme

@AndroidEntryPoint
class ModernMainActivity : ComponentActivity() {

    private val _deepLink = MutableStateFlow<String?>(null)
    private val deepLink: StateFlow<String?> = _deepLink

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        handleIntent(intent)
        setContent {
            JBusTheme {
                JBusNavigation(
                    deepLinkFlow = deepLink,
                    onDeepLinkConsumed = { _deepLink.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val route = resolveDeepLink(intent)
        _deepLink.value = route
    }

    private fun resolveDeepLink(intent: android.content.Intent?): String? {
        val javbusUrl = when (intent?.action) {
            android.content.Intent.ACTION_VIEW -> intent.data?.toString()
            android.content.Intent.ACTION_SEND ->
                JBUS_URL_REGEX.find(intent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: "")?.value
            else -> null
        }
        if (javbusUrl != null) {
            return resolveJavbusRoute(javbusUrl)
        }
        return null
    }

    private fun resolveJavbusRoute(url: String): String {
        val path = java.net.URL(url).path.orEmpty().trimEnd('/')
        val segments = path.split("/").filter { it.isNotBlank() }

        // Root or section pages (/uncensored, /xyz) → main
        if (segments.isEmpty() || segments.singleOrNull() in listOf("uncensored", "xyz")) {
            return "main"
        }

        // Listing index pages (/genre, /actresses, /uncensored/genre, etc.) → main
        if (segments.last() in listOf("genre", "actresses")) {
            return "main"
        }

        // Single-segment movie code like /ABCD-123 → movie detail
        if (segments.size == 1) {
            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
            return "movie_detail/$encodedUrl"
        }

        // Two+ segments with a known sub-path → link_movies
        // e.g. /star/xxx, /genre/xxx, /director/xxx, /uncensored/star/xxx, etc.
        val subPath = if (segments[0] in listOf("uncensored", "xyz")) segments[1] else segments[0]
        if (subPath in listOf("star", "genre", "director", "studio", "label", "series", "publisher")) {
            val type = when (subPath) {
                "star" -> "actress"
                "genre" -> "genre"
                else -> "header"
            }
            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
            val title = segments.last()
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            return "link_movies/$encodedUrl?title=$encodedTitle&type=$type"
        }

        // Unknown pattern → main
        return "main"
    }

    companion object {
        private val JBUS_URL_REGEX = Regex("""https?://(?:www\.)?javbus\.com/\S+""")

        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, ModernMainActivity::class.java)
            context.startActivity(intent)
        }
    }
}
