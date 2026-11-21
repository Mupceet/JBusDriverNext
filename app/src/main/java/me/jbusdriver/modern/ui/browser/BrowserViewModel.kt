package me.jbusdriver.modern.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.BrowserSessionClient
import me.jbusdriver.modern.core.http.WebViewHelper
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.ui.UserMessage
import java.net.URI
import javax.inject.Inject

/**
 * 内置浏览器的 WebView 持有者与动作入口。
 *
 * WebView 由 ViewModel 持有（applicationContext 创建，无窗口依赖，与隐藏抓取会话
 * 共享进程级 CookieManager，论坛页自动带匿名反爬会话）：配置变更（旋转）时页面与
 * 历史原样保留；路由条目出栈时 ViewModelStore 清理触发 [onCleared] 销毁；
 * 进程被杀后重建退化为重新加载初始 URL。
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    private val browserSessionClient: BrowserSessionClient,
    private val siteConfig: SiteConfig,
) : ViewModel() {

    // WebView 以 applicationContext 创建，无 Activity 泄漏；生命周期随路由条目的 ViewModelStore
    @SuppressLint("StaticFieldLeak")
    val webView: WebView = WebViewHelper.createWebView(appContext).apply {
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
    }

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UserMessage> = _messages

    /**
     * 页面触发的下载：走系统 DownloadManager。请求头带 Cookie/Referer/UA
     * （复用 [BrowserUrlPolicy.downloadHeaders]），否则带鉴权的站点会拒绝下载。
     * 进度与结果由系统下载通知呈现，无需轮询。
     */
    fun enqueueDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        referer: String?,
    ) {
        viewModelScope.launch {
            val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
            val headers = BrowserUrlPolicy.downloadHeaders(cookie, referer, userAgent)
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val downloadManager =
                webView.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val ok = runCatching {
                val request = DownloadManager.Request(url.toUri())
                    .setTitle(fileName)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                for ((name, value) in headers) request.addRequestHeader(name, value)
                downloadManager.enqueue(request) > 0
            }.onFailure { KLog.t(TAG).e("enqueue web download failed", it) }.isSuccess
            _messages.emit(
                if (ok) {
                    UserMessage(R.string.browser_download_queued)
                } else {
                    UserMessage(R.string.browser_download_failed)
                }
            )
        }
    }

    /** 供 Screen 透传一次性消息（如复制成功）。 */
    fun sendMessage(message: UserMessage) {
        viewModelScope.launch { _messages.emit(message) }
    }

    /**
     * 站内 URL 预热反爬会话：CookieManager 没有会话 cookie 时 WebView 直连会被站点
     * 节流（图片极慢，release 新装/冷启动实测）。会话有效时仅做恢复（毫秒级），
     * 失效时走一次隐藏 WebView 引导后再加载页面。
     */
    suspend fun prepareSessionFor(url: String) {
        siteConfig.awaitReady()
        val host = runCatching { URI(url).host }.getOrNull() ?: return
        val siteHost = runCatching { URI(siteConfig.referer()).host }.getOrNull() ?: return
        if (host.equals(siteHost, ignoreCase = true)) {
            runCatching { browserSessionClient.warmUp() }
        }
    }

    override fun onCleared() {
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private companion object {
        const val TAG = "BrowserViewModel"
    }
}
