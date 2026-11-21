package me.jbusdriver.modern.ui.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ValueCallback
import androidx.core.net.toUri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.jbusdriver.R
import me.jbusdriver.modern.ui.components.hazeTopBar
import me.jbusdriver.modern.ui.components.rememberAppHazeState

/** 长按命中的目标（hitTestResult 分类）。 */
private sealed interface LongPressTarget {
    val url: String

    data class Link(override val url: String) : LongPressTarget
    data class Image(override val url: String) : LongPressTarget
}

/**
 * 内置浏览器（兜底打开网页，替代跳系统浏览器以免污染浏览历史）。
 *
 * - 与隐藏抓取会话共享进程级 CookieManager：论坛页自动带匿名反爬会话
 * - 返回键先退 WebView 历史，退无可退再退出页面
 * - 下载走系统 DownloadManager（带 Cookie/Referer/UA）；文件选择器支持上传；
 *   长按链接/图片弹菜单（图片可进应用内查看器）
 * - WebView 由 [BrowserViewModel] 持有，旋转时页面与历史保留
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    url: String,
    title: String,
    onImageClick: (List<String>, Int) -> Unit,
    onBack: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val webView = viewModel.webView
    val hazeState = rememberAppHazeState()
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current

    val fallbackTitle = stringResource(R.string.browser_title)
    var pageTitle by remember {
        mutableStateOf(title.ifBlank { BrowserUrlPolicy.hostOf(url) ?: fallbackTitle })
    }
    var progress by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var longPressTarget by remember { mutableStateOf<LongPressTarget?>(null) }
    var pendingFileChooser by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val copiedLinkMessage = stringResource(R.string.copied_link)
    val noHandlerMessage = stringResource(R.string.no_handler)
    val loadFailedMessage = stringResource(R.string.load_failed)
    val copyLink: (String) -> Unit = { link ->
        clipboard.setText(AnnotatedString(link))
        Toast.makeText(context, copiedLinkMessage, Toast.LENGTH_SHORT).show()
    }
    val openInSystemBrowser: (String) -> Unit = { link ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri())) }
            .onFailure { Toast.makeText(context, noHandlerMessage, Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message.format(context), Toast.LENGTH_SHORT).show()
        }
    }

    // 旋转重建后从 WebView 实例恢复标题；首次进入加载初始 URL
    LaunchedEffect(Unit) {
        webView.title?.takeIf { it.isNotBlank() && it != "about:blank" }?.let { pageTitle = it }
        if (webView.url.isNullOrBlank()) webView.loadUrl(url)
    }

    val fileChooserLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pendingFileChooser
            pendingFileChooser = null
            callback ?: return@rememberLauncherForActivityResult
            callback.onReceiveValue(parseFileChooserResult(result.data))
        }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                loadError = null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val target = request ?: return false
                if (!target.isForMainFrame) return false
                return when (BrowserUrlPolicy.decide(target.url.toString())) {
                    BrowserUrlDecision.LoadInWebView -> false
                    BrowserUrlDecision.LaunchExternal -> {
                        openInSystemBrowser(target.url.toString())
                        true
                    }
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    loadError = error?.description?.toString()?.takeIf { it.isNotBlank() }
                        ?: loadFailedMessage
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress
            }

            override fun onReceivedTitle(view: WebView?, newTitle: String?) {
                if (!newTitle.isNullOrBlank() && newTitle != "about:blank") pageTitle = newTitle
            }

            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                val callback = filePathCallback ?: return false
                // 回收上一次未决回调，否则 WebView 不再触发新的选择流程
                pendingFileChooser?.onReceiveValue(null)
                pendingFileChooser = callback
                val intent = runCatching {
                    (fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }).apply { putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }
                }.getOrNull()
                if (intent == null) {
                    pendingFileChooser = null
                    callback.onReceiveValue(null)
                    return false
                }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (_: Exception) {
                    // 无可用选择器时必须回传 null 释放回调
                    pendingFileChooser = null
                    callback.onReceiveValue(null)
                    false
                }
            }
        }
        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
            if (!downloadUrl.isNullOrBlank()) {
                viewModel.enqueueDownload(
                    url = downloadUrl,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimetype,
                    referer = webView.url,
                )
            }
        }
        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            val hitUrl = result.extra
            when {
                result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE && !hitUrl.isNullOrBlank() -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    longPressTarget = LongPressTarget.Link(hitUrl)
                    true
                }

                (result.type == WebView.HitTestResult.IMAGE_TYPE ||
                    result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) &&
                    !hitUrl.isNullOrBlank() -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    longPressTarget = LongPressTarget.Image(hitUrl)
                    true
                }

                else -> false
            }
        }
        onDispose {
            // WebView 实例归 ViewModel 所有，这里只停加载；销毁在 onCleared
            webView.stopLoading()
        }
    }

    val goBack: () -> Unit = {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }
    BackHandler(onBack = goBack)

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.hazeTopBar(hazeState),
                    title = {
                        Text(pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        // 直接关闭页面（与全 App 左上角=离开本页一致）；逐页回退交给系统返回键
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.close_24px),
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    painterResource(R.drawable.more_vert_24px),
                                    contentDescription = stringResource(R.string.browser_more)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.browser_open_in_browser)) },
                                    onClick = {
                                        menuExpanded = false
                                        webView.url?.let(openInSystemBrowser)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share)) },
                                    onClick = {
                                        menuExpanded = false
                                        shareUrl(context, webView.url)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.refresh)) },
                                    onClick = {
                                        menuExpanded = false
                                        webView.reload()
                                    }
                                )
                            }
                        }
                    }
                )
                // 用范围判断代替 start/finished 配对控制，加载完自动隐藏
                if (progress in 1..99 && loadError == null) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = Color.Transparent,
                        // M3 会在轨道末端画"终止点"圆点，透明轨道下像多出的一截，去掉
                        drawStopIndicator = {},
                    )
                }
            }
        }
    ) { innerPadding ->
        // WebView 是完整文档、没有列表那样的 contentPadding 可把首屏垫到玻璃栏下，
        // 穿透模式会遮住页面顶部：浏览器页整体置于顶栏下方（顶栏为半透明栏）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )

            loadError?.let { errorText ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.load_failed),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (errorText.isNotBlank()) {
                        Text(
                            errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                        )
                    }
                    Button(onClick = {
                        loadError = null
                        webView.reload()
                    }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            // 长按菜单：无按压位置信息，从屏幕中心展开
            longPressTarget?.let { target ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(1.dp)
                    ) {
                        DropdownMenu(
                            expanded = true,
                            onDismissRequest = { longPressTarget = null },
                        ) {
                            if (target is LongPressTarget.Image) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.browser_preview_image)) },
                                    onClick = {
                                        longPressTarget = null
                                        onImageClick(listOf(target.url), 0)
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.browser_open)) },
                                    onClick = {
                                        longPressTarget = null
                                        webView.loadUrl(target.url)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_link)) },
                                onClick = {
                                    longPressTarget = null
                                    copyLink(target.url)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.open_external)) },
                                onClick = {
                                    longPressTarget = null
                                    openInSystemBrowser(target.url)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseFileChooserResult(data: Intent?): Array<Uri>? {
    data ?: return null
    val uris = buildList {
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri)
        }
        if (isEmpty()) data.data?.let { add(it) }
    }
    return uris.takeIf { it.isNotEmpty() }?.toTypedArray()
}

private fun shareUrl(context: Context, url: String?) {
    if (url.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
        .onFailure {
            Toast.makeText(context, R.string.no_handler, Toast.LENGTH_SHORT).show()
        }
}
