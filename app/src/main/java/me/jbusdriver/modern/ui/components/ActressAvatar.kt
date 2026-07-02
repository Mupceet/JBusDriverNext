package me.jbusdriver.modern.ui.components

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.jbusdriver.R
import me.jbusdriver.modern.core.copy

/**
 * 演员头像可组合组件。
 *
 * 职责：展示圆形裁剪的演员头像图片。当头像 URL 为空或图片尚未加载时，
 * 显示一个人形占位图标作为兜底显示。支持长按弹出 AlertDialog 复制演员名称。
 *
 * 使用场景：被多个页面复用，包括影片详情页的演员区域、演员列表页、搜索结果页和关联影片列表页等。
 *
 * @param avatarUrl 演员头像的图片 URL，为空时仅显示占位图标
 * @param contentDescription 无障碍描述文本，也用作长按弹窗中显示的演员名称
 * @param size 头像的尺寸（宽高相同），默认 96.dp
 * @param onClick 点击回调，传入后头像支持点击和长按交互
 * @param modifier 应用于头像外层的 Modifier
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActressAvatar(
    avatarUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    onClick: (() -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val copiedMessage = stringResource(R.string.copied)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(
                if (onClick != null) Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = { showDialog = true }
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.person_24px),
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.8f),
            tint = MaterialTheme.colorScheme.outline
        )

        if (avatarUrl.isNotBlank()) {
            AppAsyncImage(
                model = avatarUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        }
    }

    if (showDialog && contentDescription != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.actresses)) },
            text = { SelectionContainer { Text(contentDescription) } },
            confirmButton = {
                TextButton(onClick = {
                    ctx.copy(contentDescription)
                    Toast.makeText(ctx, copiedMessage, Toast.LENGTH_SHORT).show()
                    showDialog = false
                }) { Text(stringResource(R.string.copy)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}
