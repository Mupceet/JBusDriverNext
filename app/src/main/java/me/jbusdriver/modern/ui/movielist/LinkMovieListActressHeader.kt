package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.jbusdriver.modern.ui.ActressDetailUiModel
import me.jbusdriver.modern.ui.components.ActressAvatar

/**
 * 演员详情卡片。
 *
 * 职责：在 Surface 卡片中展示演员的头像、名称和附加信息。
 * 当有附加信息时采用左右布局（头像+名称在左，信息在右）；
 * 无附加信息时居中展示头像和名称。
 *
 * 使用场景：作为 [LinkMovieListScreen] 中演员类型页面的顶部 header 卡片。
 *
 * @param actress 演员详情数据模型
 */
@Composable
fun ActressDetailCard(actress: ActressDetailUiModel) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        if (actress.info.isNotEmpty()) {
            // Layout: avatar+name on left, info on right, vertically centered
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ActressAvatar(
                        avatarUrl = actress.avatar,
                        contentDescription = actress.name,
                        size = 100.dp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = actress.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(140.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    actress.info.forEach { infoLine ->
                        Text(
                            text = infoLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // No info: avatar + name horizontally centered
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ActressAvatar(
                    avatarUrl = actress.avatar,
                    contentDescription = actress.name,
                    size = 100.dp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = actress.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(140.dp)
                )
            }
        }
    }
}

/**
 * 演员详情加载中的占位卡片。
 *
 * 职责：在演员详情数据加载期间，展示与 [ActressDetailCard] 相同布局的骨架占位符，
 * 包含圆形头像占位和文本行占位。
 *
 * 使用场景：作为 [LinkMovieListScreen] 中演员类型页面的顶部 header，当演员详情正在加载时显示。
 */
@Composable
fun ActressDetailLoadingPlaceholder() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder (same size)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
            // Text placeholder
            Column {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                )
            }
        }
    }
}

/**
 * 演员详情加载失败的错误提示卡片。
 *
 * 职责：以错误容器样式的 Surface 展示演员详情加载失败时的错误信息。
 *
 * 使用场景：作为 [LinkMovieListScreen] 中演员类型页面的顶部 header，当演员详情加载失败时显示。
 *
 * @param error 错误信息文本
 */
@Composable
fun ActressDetailErrorCard(error: String) {
    Surface(
        shape = RoundedCornerShape(1.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(14.dp)
        )
    }
}
