package me.jbusdriver.modern.ui.forum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import me.jbusdriver.R
import me.jbusdriver.modern.data.ForumFloorOrder
import me.jbusdriver.modern.domain.model.Comment
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumReply
import me.jbusdriver.modern.domain.model.ForumThreadDetail

@Composable
internal fun ThreadHeader(detail: ForumThreadDetail) {
    Column {
        ForumThreadTitle(
            title = detail.title,
            typeName = detail.typeName,
            typeColor = detail.typeColor,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            badgeStyle = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${detail.author} · ${detail.postTime}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                forumViewReplyCountText(detail.viewCount, detail.replyCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun RepliesHeader(
    replyCount: Int,
    floorOrder: ForumFloorOrder,
    onFloorOrderSelected: (ForumFloorOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.forum_reply_count, replyCount),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Box {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painterResource(R.drawable.sort_24px),
                    contentDescription = stringResource(R.string.floor_sort),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                FloorOrderMenuItem(
                    text = stringResource(R.string.floor_order_regular),
                    selected = floorOrder == ForumFloorOrder.REGULAR,
                    onClick = {
                        expanded = false
                        onFloorOrderSelected(ForumFloorOrder.REGULAR)
                    }
                )
                FloorOrderMenuItem(
                    text = stringResource(R.string.floor_order_reverse),
                    selected = floorOrder == ForumFloorOrder.REVERSE,
                    onClick = {
                        expanded = false
                        onFloorOrderSelected(ForumFloorOrder.REVERSE)
                    }
                )
            }
        }
    }
}

@Composable
private fun FloorOrderMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = {
            if (selected) {
                Icon(
                    painterResource(R.drawable.check_24px),
                    contentDescription = null
                )
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        onClick = onClick
    )
}

@Composable
internal fun CommentsSection(comments: List<Comment>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.comment),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            comments.forEach { comment ->
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    AsyncImage(
                        model = comment.authorAvatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "${comment.author}  ${comment.time}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            comment.content,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReplyItem(
    reply: ForumReply,
    onImageClick: (List<String>, Int) -> Unit,
    loadedGifUrls: Set<String> = emptySet(),
    autoLoadGifs: Boolean = false,
    onLoadGif: (String) -> Unit = {},
    onLoadAllGifs: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = reply.authorAvatar,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        reply.author,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "${
                            forumFloorLabel(
                                reply.floor,
                                reply.isPinned,
                                stringResource(R.string.pinned)
                            )
                        } · ${reply.postTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (reply.authorGroup.isNotEmpty()) {
                    Text(
                        reply.authorGroup,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                ForumPostContent(
                    blocks = reply.contentBlocks,
                    onImageClick = onImageClick,
                    modifier = Modifier.padding(top = 4.dp),
                    loadedGifUrls = loadedGifUrls,
                    autoLoadGifs = autoLoadGifs,
                    onLoadGif = onLoadGif,
                    onLoadAllGifs = onLoadAllGifs,
                    onLongClick = onLongClick
                )
            }
        }
    }
}

@Composable
internal fun FloorContentDialog(
    blocks: List<ContentBlock>,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.content_preview),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 20.dp,
                        bottom = 8.dp
                    )
                )

                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    ForumPostContent(
                        blocks = blocks,
                        onImageClick = { _, _ -> },
                        showImages = false
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                    TextButton(onClick = onCopyAll) {
                        Text(stringResource(R.string.copy_all))
                    }
                }
            }
        }
    }
}
