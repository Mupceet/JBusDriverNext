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
import me.jbusdriver.modern.ui.theme.JBusTheme

@AndroidEntryPoint
class ModernMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val deepLinkUri = resolveDeepLink(intent)
        setContent {
            JBusTheme {
                JBusNavigation(deepLinkUri = deepLinkUri)
            }
        }
    }

    private fun resolveDeepLink(intent: android.content.Intent?): String? {
        val javbusUrl = when (intent?.action) {
            android.content.Intent.ACTION_VIEW -> intent.data?.toString()
            android.content.Intent.ACTION_SEND ->
                JBUS_URL_REGEX.find(intent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: "")?.value
            else -> null
        }
        if (javbusUrl != null) {
            return "movie_detail/${java.net.URLEncoder.encode(javbusUrl, "UTF-8")}"
        }
        return null
    }

    companion object {
        private val JBUS_URL_REGEX = Regex("""https?://(?:www\.)?javbus\.com/\S+""")

        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, ModernMainActivity::class.java)
            context.startActivity(intent)
        }
    }
}
