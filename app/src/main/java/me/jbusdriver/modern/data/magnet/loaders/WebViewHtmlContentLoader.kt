package me.jbusdriver.modern.data.magnet.loaders

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
import me.jbusdriver.modern.core.JBusManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


private const val TAG = "WebContentLoader"

/**
 * 基于 WebView 的 HTML 内容加载器，用于获取需要 JavaScript 动态渲染的页面内容。
 *
 * 职责：创建并管理 WebView 实例，加载目标 URL，等待页面中的 `#magnet-table` 元素
 * 渲染完成后通过 JavaScript 接口提取 HTML 内容。
 *
 * 使用场景：[DefaultLoaderImpl] 调用此加载器获取磁力搜索页面的动态内容，
 * 因为目标站点的磁力表格通过 JavaScript 异步加载，纯 HTTP 请求无法获取完整内容。
 *
 * 线程：[startLoad] 会阻塞调用线程（通过 [CountDownLatch]）最多 30 秒等待结果。
 * WebView 操作在主线程执行（通过 [mainH] Handler 调度）。
 */
class WebViewHtmlContentLoader {
    /** 懒加载的 WebView 实例，使用 [JBusManager.context] 创建。 */
    private val webView by lazy { WebView(JBusManager.context) }

    /**
     * 同步加载指定 URL 并返回渲染后的 HTML 内容。
     *
     * 工作流程：
     * 1. 在主线程配置 WebView 设置（启用 JS、DOM Storage 等）
     * 2. 注册 [HtmlContentProvider] JavaScript 接口用于内容回调
     * 3. 加载 URL，等待页面进度达到 70% 或页面加载完成时注入轮询 JS
     * 4. 轮询 JS 检测 `#magnet-table` 元素出现后通过接口返回内容
     * 5. 调用线程通过 CountDownLatch 阻塞等待，最多 30 秒超时
     *
     * @param url 目标页面 URL
     * @return 渲染后的 HTML 内容，超时或异常时返回空字符串
     */
    fun startLoad(url: String): String {
        val countDownLatch = CountDownLatch(1)
        val provider = HtmlContentProvider(countDownLatch)
        mainH.post {
            webView.settings?.javaScriptEnabled = true
            webView.settings?.allowContentAccess = true
            webView.settings?.allowFileAccess = true
            webView.settings?.allowUniversalAccessFromFileURLs = true
            webView.settings?.databaseEnabled = true
            webView.settings?.domStorageEnabled = true
            webView.settings?.mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW
            webView.settings?.cacheMode = WebSettings.LOAD_DEFAULT
            webView.stopLoading()

            webView.webViewClient = HtmlContentClient(countDownLatch)
            webView.webChromeClient = HtmlLoaderChromeClient(countDownLatch)

            webView.addJavascriptInterface(provider, "html_content")

            webView.loadUrl(url)

        }
        val time = 30L
        return try {
            countDownLatch.await(time, TimeUnit.SECONDS)
            provider.htmlContent
        } catch (e: Exception) {
            ""
        } finally {
            stopLoad()
        }
    }

    /** 在主线程停止加载并销毁 WebView，释放资源。 */
    fun stopLoad() {
        mainH.post {
            webView.stopLoading()
            webView.destroy()
        }
    }

    companion object {
        /**
         * 轮询 JavaScript 代码片段。
         * 在页面中反复执行，最多 30 次（每次间隔 500ms），
         * 直到检测到 `#magnet-table` 元素且包含子元素后，通过 `html_content.getSource()` 回调结果。
         */
        private const val POLL_JS = """
  javascript: (function(){
    var attempts = 0;
    var maxAttempts = 30;
    function tryExtract() {
      var m = document.querySelector("#magnet-table");
      if (m && m.childElementCount > 1) {
        window.html_content.getSource(m.outerHTML);
      } else if (attempts < maxAttempts) {
        attempts++;
        setTimeout(tryExtract, 500);
      }
    }
    tryExtract();
  })()
"""

        /** 主线程 Handler，用于调度 WebView 操作。 */
        val mainH = Handler(Looper.getMainLooper())
    }

    /**
     * JavaScript 接口提供者，接收 WebView 中注入的 JS 回调并返回 HTML 内容。
     *
     * 当轮询 JS 检测到目标元素后，调用 `window.html_content.getSource(html)`，
     * 本类的 [getSource] 方法被触发，将内容保存并解除 CountDownLatch 阻塞。
     */
    class HtmlContentProvider(private val countDownLatch: CountDownLatch) {

        /** 从 WebView JavaScript 回调获取的 HTML 内容。 */
        var htmlContent: String = ""

        /**
         * 由 JavaScript 调用的回调方法，接收 HTML 内容。
         * 内容非空时解除 [countDownLatch] 阻塞。
         *
         * @param html 目标元素的 outerHTML 字符串
         */
        @JavascriptInterface
        fun getSource(html: String) {
            Log.e(TAG, "getSource: ${html.length}")
            if (html.isNotBlank()) {
                htmlContent = "<html>$html</html>"
                countDownLatch.countDown()
            }
        }
    }

    /**
     * Chrome 客户端，监听页面加载进度并在合适时机注入轮询 JS。
     *
     * 当页面加载进度达到 70% 时注入 [POLL_JS]，因为目标内容可能在页面主体加载完成后
     * 才通过 AJAX 请求填充，需要等待足够的加载进度再开始检测。
     */
    class HtmlLoaderChromeClient(private val countDownLatch: CountDownLatch) :
        android.webkit.WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            Log.e(TAG, "onProgressChanged: $newProgress ... ")
            if (countDownLatch.count > 0 && newProgress >= 70) {
                Log.e(TAG, "onProgressChanged: getSource $POLL_JS")
                view?.loadUrl(POLL_JS)
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            Log.e(TAG, "onReceivedTitle: $title ")
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            Log.e(TAG, "onConsoleMessage: ${consoleMessage?.message()}")
            return super.onConsoleMessage(consoleMessage)
        }

    }

    /**
     * WebView 客户端，处理页面加载生命周期事件。
     *
     * 在页面加载完成时注入轮询 JS 作为备用触发点（与 Chrome Client 的进度触发互补），
     * 同时拦截图片请求以减少不必要的流量消耗。
     */
    class HtmlContentClient(private val countDownLatch: CountDownLatch) : WebViewClient() {

        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            Log.e(TAG, "onReceivedError: $errorCode $description $failingUrl")
            super.onReceivedError(view, errorCode, description, failingUrl)
        }


        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            Log.e(TAG, "onPageStarted: $url")
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            Log.e(TAG, "onPageFinished: $view , $url")
            if (countDownLatch.count > 0) {
                Log.e(TAG, "onProgressChanged: getSource")
                view?.loadUrl(POLL_JS)
            }
            super.onPageFinished(view, url)
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            Log.e(TAG, "shouldInterceptRequest ${request?.url}")
            if (request?.url?.path?.contains("jpg|png|gif".toRegex() )== true) {
                Log.e(TAG, "shouldInterceptRequest NONE")
                return null
            }
            return super.shouldInterceptRequest(view, request)
        }

    }


}
