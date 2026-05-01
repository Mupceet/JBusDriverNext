package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * 演员头像可组合组件。
 *
 * 职责：展示圆形裁剪的演员头像图片。当头像 URL 为空或图片尚未加载时，
 * 显示一个人形占位图标作为兜底显示。
 *
 * 使用场景：被多个页面复用，包括影片详情页的演员区域、演员列表页、搜索结果页和关联影片列表页等。
 *
 * @param avatarUrl 演员头像的图片 URL，为空时仅显示占位图标
 * @param contentDescription 无障碍描述文本
 * @param size 头像的尺寸（宽高相同），默认 96.dp
 * @param modifier 应用于头像外层的 Modifier
 */
@Composable
fun ActressAvatar(
    avatarUrl: String,
    contentDescription: String?,
    size: Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.8f),
            tint = MaterialTheme.colorScheme.outline
        )

        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        }
    }
}
