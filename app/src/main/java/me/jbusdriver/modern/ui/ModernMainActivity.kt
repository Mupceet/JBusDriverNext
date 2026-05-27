package me.jbusdriver.modern.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.BrowserSessionClient
import me.jbusdriver.modern.ui.theme.JBusTheme
import javax.inject.Inject

@AndroidEntryPoint
class ModernMainActivity : ComponentActivity() {

    @Inject
    lateinit var browserSessionClient: BrowserSessionClient

    private val _deepLink = MutableStateFlow<NavKey?>(null)
    private val deepLink: StateFlow<NavKey?> = _deepLink

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
        lifecycleScope.launch {
            runCatching { browserSessionClient.warmUp() }
                .onFailure { KLog.w("Browser session warm-up failed: ${it.message}") }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            browserSessionClient.destroy()
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val key = resolveDeepLink(intent)
        _deepLink.value = key
    }

    private fun resolveDeepLink(intent: android.content.Intent?): NavKey? {
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

    private fun resolveJavbusRoute(url: String): NavKey {
        val path = java.net.URL(url).path.orEmpty().trimEnd('/')
        val segments = path.split("/").filter { it.isNotBlank() }

        if (segments.isEmpty() || segments.singleOrNull() in listOf("uncensored", "xyz")) {
            return RouteMain
        }
        if (segments.last() in listOf("genre", "actresses")) {
            return RouteMain
        }
        if (segments.size == 1) {
            return RouteMovieDetail(movieUrl = url)
        }

        val subPath = if (segments[0] in listOf("uncensored", "xyz")) segments[1] else segments[0]
        if (subPath in listOf("star", "genre", "director", "studio", "label", "series", "publisher")) {
            val type = when (subPath) {
                "star" -> "actress"
                "genre" -> "genre"
                else -> "header"
            }
            val title = segments.last()
            return RouteLinkMovies(linkUrl = url, title = title, type = type)
        }

        return RouteMain
    }

    companion object {
        private val JBUS_URL_REGEX = Regex("""https?://(?:www\.)?javbus\.com/\S+""")

        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, ModernMainActivity::class.java)
            context.startActivity(intent)
        }
    }
}
