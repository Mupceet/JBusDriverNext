package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.jbusdriver.R
import me.jbusdriver.modern.core.http.LtxWebSession
import me.jbusdriver.modern.core.http.WebViewHelper
import me.jbusdriver.modern.core.http.WebViewHelper.currentWebSession
import me.jbusdriver.modern.core.http.WebViewHelper.probeStatusAwait
import me.jbusdriver.modern.data.ltx.LTX_ITEMS_PATH
import me.jbusdriver.modern.KLog
import android.webkit.WebView

private const val TAG = "LtxVerifyDialog"

/**
 * LTX 人机验证弹窗：以可见 WebView 加载 LTX 首页，SPA 的 Turnstile 门禁在真实
 * 渲染环境下自动通过；出现交互式复选框时由用户手动完成。
 *
 * 页面内 XHR 探测 `/api/items`：旧协议站点 200（可直接抓取）；迁移站加密协议下
 * 428（cryptoSession）即代表边缘与 Turnstile 门禁均已通过。两种状态都导出会话
 * （Cookie+UA）交还调用方并自动关闭——弹窗只负责拿到验证 Cookie，加密会话由
 * 仓库层随后原生建立。
 */
@Composable
fun LtxVerificationDialog(
    viewModel: LtxListViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val webView = remember { WebViewHelper.createWebView(context) }
    var origin by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val target = viewModel.verificationOrigin()
        origin = target
        KLog.t(TAG).i("Manual verification: loading $target")
        webView.loadUrl(target)
    }

    // 探测目标接口：200（旧协议可直接抓取）或 428（新站门禁已过）均视为验证完成；
    // 轮询无超时上限，由用户关闭弹窗或离开页面取消
    LaunchedEffect(origin) {
        val target = origin ?: return@LaunchedEffect
        val itemsUrl = target + LTX_ITEMS_PATH
        while (isActive) {
            val probe = runCatching { webView.probeStatusAwait(itemsUrl) }.getOrNull()
            if (probe != null && (probe.status == 200 || probe.status == 428)) {
                val session: LtxWebSession? = webView.currentWebSession(itemsUrl)
                if (session != null) {
                    KLog.t(TAG).i("Manual verification passed (status=${probe.status})")
                    viewModel.onVerificationSuccess(session)
                    onDismiss()
                    return@LaunchedEffect
                }
            }
            delay(3_000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ltx_verify_title)) },
        text = {
            Column {
                // 提示在验证区域上方：文案「於下方頁面完成驗證」与布局一致
                Text(
                    stringResource(R.string.ltx_verify_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                AndroidView(
                    factory = { webView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ltx_verify_close))
            }
        }
    )
}
