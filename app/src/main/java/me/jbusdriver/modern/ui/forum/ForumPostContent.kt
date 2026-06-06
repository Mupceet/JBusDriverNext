package me.jbusdriver.modern.ui.forum

import android.graphics.Color.parseColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumTextSize
import me.jbusdriver.modern.domain.model.RichList
import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart
import me.jbusdriver.modern.ui.components.GifPlaceholder
import kotlin.math.max

@Composable
internal fun ForumPostContent(
    blocks: List<ContentBlock>,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
    loadedGifUrls: Set<String> = emptySet(),
    autoLoadGifs: Boolean = false,
    onLoadGif: (String) -> Unit = {},
    onLoadAllGifs: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    showImages: Boolean = true
) {
    val viewableImageUrls = remember(blocks, loadedGifUrls, autoLoadGifs, showImages) {
        if (!showImages) {
            emptyList()
        } else {
            blocks.filterIsInstance<ContentBlock.Image>()
                .filter { !it.isGif || autoLoadGifs || it.url in loadedGifUrls }
                .map { it.url }
        }
    }

    Column(
        modifier = modifier.then(
            if (onLongClick != null) {
                Modifier.pointerInput(onLongClick) {
                    detectTapGestures(onLongPress = { onLongClick() })
                }
            } else {
                Modifier
            }
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var viewableIndex = 0
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.RichText -> block.paragraphs.forEach { paragraph ->
                    StyledParagraph(paragraph)
                }
                is ContentBlock.ListBlock -> RichListContent(block.list)
                is ContentBlock.Image -> if (showImages) {
                    if (block.isGif && !autoLoadGifs && block.url !in loadedGifUrls) {
                        GifPlaceholder(
                            onClick = { onLoadGif(block.url) },
                            onLoadAllGifs = onLoadAllGifs,
                            modifier = if (block.isFullSize) {
                                Modifier.fillMaxWidth().height(180.dp)
                            } else {
                                Modifier.size(48.dp)
                            }
                        )
                    } else {
                        val currentIndex = viewableIndex++
                        ForumImage(
                            block = block,
                            onClick = { onImageClick(viewableImageUrls, currentIndex) }
                        )
                    }
                }
                is ContentBlock.Quote -> QuoteContent(block)
                is ContentBlock.RestrictedNotice -> RestrictedNotice(block.message)
            }
        }
    }
}

@Composable
private fun StyledParagraph(paragraph: RichParagraph, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val text = remember(paragraph, onSurface, surface, primary) {
        paragraph.toAnnotatedString(onSurface, surface, primary)
    }
    if (text.isNotBlank()) {
        Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 22.sp,
                color = onSurface
            )
        )
    }
}

private fun RichParagraph.toAnnotatedString(
    onSurface: Color,
    surface: Color,
    primary: Color
): AnnotatedString = buildAnnotatedString {
    parts.forEach { part ->
        withStyle(part.toSpanStyle(onSurface, surface, primary)) {
            append(part.text)
        }
    }
}

private fun TextPart.toSpanStyle(onSurface: Color, surface: Color, primary: Color): SpanStyle {
    val decorations = buildList {
        if (underline || isLink) add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        color = if (isLink) primary else readableColor(color, surface, onSurface),
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        fontSize = when (size) {
            ForumTextSize.BODY -> 14.sp
            ForumTextSize.EMPHASIS -> 17.sp
            ForumTextSize.HEADING -> 20.sp
        },
        textDecoration = when (decorations.size) {
            0 -> null
            1 -> decorations.first()
            else -> TextDecoration.combine(decorations)
        }
    )
}

private fun readableColor(value: String?, background: Color, fallback: Color): Color {
    if (value.isNullOrBlank()) return fallback
    val candidate = runCatching { Color(parseColor(value)).copy(alpha = 1f) }.getOrNull()
        ?: return fallback
    return if (contrastRatio(candidate, background) >= MIN_TEXT_CONTRAST) candidate else fallback
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = max(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

@Composable
private fun RichListContent(list: RichList, depth: Int = 0) {
    Column(
        modifier = Modifier.padding(start = forumListIndentStep(depth).dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        list.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (list.ordered) "${list.start + index}." else "\u2022",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(modifier = Modifier.weight(1f)) {
                    item.paragraphs.forEach { paragraph -> StyledParagraph(paragraph) }
                }
            }
            item.children.forEach { child ->
                RichListContent(child, depth + 1)
            }
        }
    }
}

@Composable
private fun ForumImage(block: ContentBlock.Image, onClick: () -> Unit) {
    if (block.isFullSize) {
        SubcomposeAsyncImage(
            model = block.url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentScale = ContentScale.FillWidth,
            loading = {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        )
    } else {
        var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
        AsyncImage(
            model = block.url,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .then(
                    if (imageState is AsyncImagePainter.State.Loading ||
                        imageState is AsyncImagePainter.State.Empty
                    ) {
                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick),
            contentScale = ContentScale.Fit,
            onState = { imageState = it }
        )
    }
}

@Composable
private fun QuoteContent(block: ContentBlock.Quote) {
    val accentColor = MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .drawBehind {
                    drawLine(
                        color = accentColor,
                        start = Offset.Zero,
                        end = Offset(0f, size.height),
                        strokeWidth = 3.dp.toPx()
                    )
                }
                .padding(start = 10.dp, top = 6.dp, end = 6.dp, bottom = 6.dp)
        ) {
            Column {
                if (block.author.isNotEmpty()) {
                    Text(
                        text = "${block.author}：",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = block.content,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RestrictedNotice(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("restricted_notice"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.lock_24px),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun buildForumPlainText(blocks: List<ContentBlock>): String =
    blocks.flatMap(ContentBlock::toPlainLines)
        .filter(String::isNotBlank)
        .joinToString("\n")

internal fun forumFloorLabel(floor: Int, isPinned: Boolean): String =
    if (isPinned) "置頂 · $floor#" else "$floor#"

internal fun forumListIndentStep(depth: Int): Int {
    val currentIndent = depth.coerceIn(0, MAX_LIST_DEPTH) * LIST_INDENT_DP
    val parentIndent = (depth - 1).coerceIn(0, MAX_LIST_DEPTH) * LIST_INDENT_DP
    return currentIndent - parentIndent
}

private fun ContentBlock.toPlainLines(): List<String> = when (this) {
    is ContentBlock.RichText -> paragraphs.map(RichParagraph::plainText)
    is ContentBlock.ListBlock -> list.toPlainLines(depth = 0)
    is ContentBlock.Image -> emptyList()
    is ContentBlock.Quote -> listOf(if (author.isEmpty()) content else "$author：$content")
    is ContentBlock.RestrictedNotice -> listOf(message)
}

private fun RichParagraph.plainText(): String = parts.joinToString("") { it.text }.trim()

private fun RichList.toPlainLines(depth: Int): List<String> = items.flatMapIndexed { index, item ->
    val indent = "  ".repeat(depth)
    val marker = if (ordered) "${start + index}. " else "\u2022 "
    val ownLines = item.paragraphs.mapIndexed { paragraphIndex, paragraph ->
        val prefix = if (paragraphIndex == 0) marker else "  "
        "$indent$prefix${paragraph.plainText()}"
    }
    ownLines + item.children.flatMap { it.toPlainLines(depth + 1) }
}

private const val MIN_TEXT_CONTRAST = 4.5f
private const val MAX_LIST_DEPTH = 2
private const val LIST_INDENT_DP = 16
