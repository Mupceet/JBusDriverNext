package me.jbusdriver.modern.ui.forum.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.ForumLoginState
import me.jbusdriver.modern.ui.components.AppAsyncImage

/**
 * 论坛账号状态条：固定在玻璃头搜索栏下方（MainScreen 论坛分支 chips 槽）。
 * 无独立背景、与玻璃面融合；行高经 [Modifier.minimumInteractiveComponentSize]
 * 与其它 tab 的 chips 容器（FilterChip 32dp 视觉 + 最小触控目标 48dp）完全同构，
 * 保证玻璃区高度/切换一致性。内容（32dp 头像 + 双行文字）在容器内垂直居中。
 * 匿名态 = 占位头像 + "匿名瀏覽"，点击弹登录；登录态 = 头像 + 用户名，点击进用户中心。
 */
@Composable
fun ForumAccountBar(
    state: ForumLoginState,
    onLoginClick: () -> Unit,
    onUserClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, subtitle) = when (state) {
        is ForumLoginState.LoggedIn -> state.username to (state.groupName ?: "")
        ForumLoginState.Anonymous -> stringResource(R.string.forum_anonymous_title) to
            stringResource(R.string.forum_anonymous_hint)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 与 chips 容器同高：FilterChip 实际布局 = 32dp 视觉 + 48dp 最小触控目标；
            // 用同一机制取高，框架常量变化时自动同步
            .minimumInteractiveComponentSize()
            .padding(horizontal = 14.dp)
            .clickable(
                onClick = when (state) {
                    is ForumLoginState.LoggedIn -> { { onUserClick(state.uid) } }
                    ForumLoginState.Anonymous -> onLoginClick
                }
            )
            .semantics { contentDescription = title },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Avatar(state)
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Avatar(state: ForumLoginState) {
    val url = (state as? ForumLoginState.LoggedIn)?.avatarUrl
    if (url.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.person_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    } else {
        AppAsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
        )
    }
}
