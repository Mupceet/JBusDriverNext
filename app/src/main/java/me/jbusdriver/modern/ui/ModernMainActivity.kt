package me.jbusdriver.modern.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.BrowserSessionClient
import me.jbusdriver.modern.data.localvideo.LocalVideoForegroundScanner
import me.jbusdriver.modern.data.localvideo.LocalVideoMetadataEnricher
import me.jbusdriver.modern.ui.theme.JBusTheme
import javax.inject.Inject

@AndroidEntryPoint
class ModernMainActivity : ComponentActivity() {

    @Inject
    lateinit var browserSessionClient: BrowserSessionClient

    @Inject
    lateinit var localVideoForegroundScanner: LocalVideoForegroundScanner

    @Inject
    lateinit var localVideoMetadataEnricher: LocalVideoMetadataEnricher

    @Inject
    lateinit var appUpdateAutoChecker: AppUpdateAutoChecker

    private val _deepLink = MutableStateFlow<NavKey?>(null)
    private val deepLink: StateFlow<NavKey?> = _deepLink

    // 就绪窗口测量：对比 onCreate 与 Navigation.kt 中 NavDisplay ready 两行日志的时间差，
    // 即为“侧滑返回会被系统直接退出”的窗口时长。
    private val timingLog = KLog.t("BackReady")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        timingLog.d("onCreate recreate=${savedInstanceState != null} at=${SystemClock.elapsedRealtime()}ms")
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        // 从 Recent/进程重建时系统会重投原始启动 Intent；此时 savedInstanceState 已恢复了
        // 返回栈，不能再按新深链分发——RouteMain 类深链会清空恢复的栈，其他深链会重复压栈。
        val fromHistory = savedInstanceState != null ||
            (intent.flags and android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        handleIntent(intent, fromHistory)
        ProcessLifecycleOwner.get().lifecycle.addObserver(localVideoForegroundScanner)
        ProcessLifecycleOwner.get().lifecycle.addObserver(appUpdateAutoChecker)
        localVideoMetadataEnricher.start()
        setContent {
            JBusTheme {
                JBusNavigation(
                    deepLinkFlow = deepLink,
                    onDeepLinkConsumed = { _deepLink.value = null }
                )
                // 全局更新提示弹框：任意界面停留时自动检查到新版本都会弹出
                AppUpdatePromptHost()
            }
        }
        lifecycleScope.launch {
            // WebView 预热会在主线程创建 WebView 并初始化会话，会推迟首帧与 NavDisplay
            // 返回回调的注册；让出前两帧，缩短重建/冷启后侧滑返回直接退出的就绪窗口。
            awaitFrame()
            awaitFrame()
            runCatching { browserSessionClient.warmUp() }
                .onFailure { KLog.w("Browser session warm-up failed: ${it.message}") }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            lifecycleScope.launch {
                browserSessionClient.destroy()
            }
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // onNewIntent 只会由真实的新 Intent（浏览器深链/分享）触发，Recent 恢复不经过这里。
        handleIntent(intent, fromHistory = false)
    }

    private fun handleIntent(intent: android.content.Intent?, fromHistory: Boolean) {
        if (fromHistory) return
        // 深链解析失败（异常格式的分享文本等）不能拖垮启动流程，回退到主界面。
        _deepLink.value = runCatching { resolveDeepLink(intent) }.getOrNull()
    }

    private fun resolveDeepLink(intent: android.content.Intent?): NavKey? {
        val javbusUrl = when (intent?.action) {
            android.content.Intent.ACTION_VIEW -> intent.data?.toString()
            android.content.Intent.ACTION_SEND ->
                JBUS_URL_REGEX.find(
                    intent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
                )?.value

            else -> null
        }
        if (javbusUrl != null) {
            return resolveJavbusRoute(javbusUrl)
        }
        return null
    }

    private fun resolveJavbusRoute(url: String): NavKey {
        val path = runCatching { java.net.URL(url).path.orEmpty().trimEnd('/') }
            .getOrElse { return RouteMain }
        val segments = path.split("/").filter { it.isNotBlank() }
        val isUncensored = "/uncensored/" in url || path.startsWith("/uncensored")
        val censorType = if (isUncensored) "UNCENSORED" else null

        if (segments.isEmpty() || segments.singleOrNull() in listOf("uncensored", "xyz")) {
            return RouteMain
        }
        if (segments.last() in listOf("genre", "actresses")) {
            return RouteMain
        }
        if (segments.size == 1) {
            return RouteMovieDetail(movieUrl = url, censorType = censorType)
        }

        val isPrefixSegment = segments[0] in listOf("uncensored", "xyz")
        val contentSegment = if (isPrefixSegment) segments.getOrNull(1) else segments[0]

        if (contentSegment in listOf(
                "star",
                "genre",
                "director",
                "studio",
                "label",
                "series",
                "publisher"
            )
        ) {
            val type = when (contentSegment) {
                "star" -> "actress"
                "genre" -> "genre"
                else -> "header"
            }
            val title = segments.last()
            return RouteLinkMovies(
                linkUrl = url,
                title = title,
                type = type,
                censorType = censorType
            )
        }

        if (isPrefixSegment && segments.size == 2) {
            return RouteMovieDetail(movieUrl = url, censorType = censorType)
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
