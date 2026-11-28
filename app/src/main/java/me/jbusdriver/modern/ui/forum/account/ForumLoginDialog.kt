package me.jbusdriver.modern.ui.forum.account

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R

/**
 * 原生登录弹窗（JbForum-ref 同构流程）：账号/密码/seccode 验证码；
 * 失败文案来自站点 showmessage，提交后以服务端实测判定成败。
 * 不保存账号密码（D6），登录态仅 auth cookie。
 *
 * 验证码槽位（固定 96×40）三态：刷新中（spinner）/ 验证码图（点击刷新）/ 刷新入口（点击拉取）；
 * 不在下方另设信息行，避免弹窗高度随状态变化跳动；错误显示在标题位（单行等高）。
 */
@Composable
fun ForumLoginDialog(
    state: ForumLoginUiState,
    onDismiss: () -> Unit,
    onSubmit: (username: String, password: String, seccode: String) -> Unit,
    onRefreshCaptcha: () -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var seccode by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            // 错误复用标题位（单行，等高不跳动）；正常时为标题文案
            if (state.error != null) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(stringResource(R.string.forum_login_title))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.forum_login_username)) },
                    singleLine = true,
                    enabled = !state.isSubmitting
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.forum_login_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !state.isSubmitting
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = seccode,
                        onValueChange = { seccode = it },
                        label = { Text(stringResource(R.string.forum_login_captcha)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSubmitting
                    )
                    CaptchaSlot(state = state, onRefresh = onRefreshCaptcha)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(username, password, seccode) },
                enabled = !state.isSubmitting && state.challenge != null &&
                    username.isNotBlank() && password.isNotBlank() && seccode.isNotBlank()
            ) {
                Text(
                    stringResource(
                        if (state.isSubmitting) R.string.forum_login_submitting
                        else R.string.forum_login_submit
                    )
                )
            }
        },
        dismissButton = {}
    )
}

/** 验证码槽位（固定 96×40）：刷新中 spinner / 验证码图 / 刷新入口，三态同尺寸零跳动。 */
@Composable
private fun CaptchaSlot(state: ForumLoginUiState, onRefresh: () -> Unit) {
    val bitmap = remember(state.challenge) {
        state.challenge?.captchaImage?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
    val slotModifier = Modifier
        .size(width = 96.dp, height = 40.dp)
        .clip(MaterialTheme.shapes.small)
    when {
        state.isLoading -> Box(
            modifier = slotModifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }

        bitmap != null -> Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.forum_login_captcha_refresh),
            modifier = slotModifier.clickable(enabled = !state.isSubmitting) { onRefresh() }
        )

        else -> Box(
            modifier = slotModifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = !state.isSubmitting) { onRefresh() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.refresh_24px),
                contentDescription = stringResource(R.string.forum_login_captcha_refresh),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 全局登录弹窗宿主：Navigation 根层渲染一次，弹窗浮在任意论坛界面之上。
 * [viewModel] 须为 activity 级共享实例（JBusNavigation 顶层 hiltViewModel() 取得）。
 */
@Composable
fun ForumLoginDialogHost(viewModel: ForumLoginDialogViewModel) {
    val loginState by viewModel.loginUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message.format(context), Toast.LENGTH_SHORT).show()
        }
    }
    loginState?.let { state ->
        ForumLoginDialog(
            state = state,
            onDismiss = viewModel::dismissLoginDialog,
            onSubmit = viewModel::submitLogin,
            onRefreshCaptcha = viewModel::requestLoginChallenge
        )
    }
}
