package me.jbusdriver.modern.ui.forum.account

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.ForumLoginState
import me.jbusdriver.modern.ui.components.AppAsyncImage

/**
 * 论坛账号头像（搜索栏尾部，取代原玻璃头账号条）：账号开关打开时替换论坛页右上角的
 * 设置图标。匿名 = 占位头像，点击弹全局登录框；已登录 = 用户头像，点击进用户中心；
 * 长按始终打开设置（设置入口保留在长按上）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ForumAccountAvatar(
    state: ForumLoginState,
    onLoginClick: () -> Unit,
    onUserClick: (Int) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logged = state as? ForumLoginState.LoggedIn
    val description = logged?.username ?: stringResource(R.string.forum_login_title)
    Box(
        modifier = modifier
            // 与设置图标位（IconButton 32dp）同构，tab 切换/图标互换时行高零跳动
            .size(32.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = {
                    if (logged != null) onUserClick(logged.uid) else onLoginClick()
                },
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.settings_title)
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        val avatarUrl = logged?.avatarUrl
        if (avatarUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Icon(
                    painter = painterResource(R.drawable.person_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.Center)
                )
            }
        } else {
            AppAsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
