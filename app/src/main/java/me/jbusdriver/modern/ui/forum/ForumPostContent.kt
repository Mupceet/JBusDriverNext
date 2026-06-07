package me.jbusdriver.modern.ui.forum

import android.graphics.Color.parseColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import kotlin.math.abs
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
    val inlineContent = remember(paragraph) {
        paragraph.toInlineContent()
    }
    if (paragraph.hasVisibleContent()) {
        Text(
            text = text,
            inlineContent = inlineContent,
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
    parts.forEachIndexed { index, part ->
        if (part.inlineImageUrl.isNotEmpty()) {
            appendInlineContent(part.inlineContentId(index), part.inlineImageAlt.ifBlank { " " })
        } else {
            withStyle(part.toSpanStyle(onSurface, surface, primary)) {
                append(part.text)
            }
        }
    }
}

private fun RichParagraph.toInlineContent(): Map<String, InlineTextContent> =
    parts.mapIndexedNotNull { index, part ->
        val url = part.inlineImageUrl.takeIf(String::isNotEmpty) ?: return@mapIndexedNotNull null
        part.inlineContentId(index) to InlineTextContent(
            Placeholder(
                width = INLINE_SMILIE_SIZE_EM.em,
                height = INLINE_SMILIE_SIZE_EM.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            AsyncImage(
                model = url,
                contentDescription = part.inlineImageAlt.takeIf(String::isNotBlank),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }.toMap()

private fun RichParagraph.hasVisibleContent(): Boolean =
    parts.any { it.text.isNotBlank() || it.inlineImageUrl.isNotEmpty() }

private fun TextPart.inlineContentId(index: Int): String = "forum_inline_image_$index"

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
    return adaptForumTextColor(candidate, background)
}

internal fun adaptForumTextColor(source: Color, background: Color): Color {
    val opaqueSource = source.copy(alpha = 1f)
    if (forumContrastRatio(opaqueSource, background) >= MIN_TEXT_CONTRAST) return opaqueSource

    val hsl = opaqueSource.toHsl()
    val darker = findReadableLightness(hsl, background, lighter = false)
    val lighter = findReadableLightness(hsl, background, lighter = true)
    return when {
        darker == null -> lighter ?: opaqueSource
        lighter == null -> darker
        abs(darker.toHsl().lightness - hsl.lightness) <=
            abs(lighter.toHsl().lightness - hsl.lightness) -> darker
        else -> lighter
    }
}

internal fun forumContrastRatio(foreground: Color, background: Color): Float {
    val lighter = max(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun findReadableLightness(
    source: HslColor,
    background: Color,
    lighter: Boolean
): Color? {
    var low = if (lighter) source.lightness else 0f
    var high = if (lighter) 1f else source.lightness
    val endpoint = source.copy(lightness = if (lighter) 1f else 0f).toColor()
    if (forumContrastRatio(endpoint, background) < MIN_TEXT_CONTRAST) return null

    repeat(COLOR_SEARCH_ITERATIONS) {
        val middle = (low + high) / 2f
        val candidate = source.copy(lightness = middle).toColor()
        val readable = forumContrastRatio(candidate, background) >= MIN_TEXT_CONTRAST
        if (lighter) {
            if (readable) high = middle else low = middle
        } else {
            if (readable) low = middle else high = middle
        }
    }
    return source.copy(lightness = if (lighter) high else low).toColor()
}

private data class HslColor(
    val hue: Float,
    val saturation: Float,
    val lightness: Float
) {
    fun toColor(): Color {
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val segment = hue / 60f
        val secondary = chroma * (1f - abs(segment % 2f - 1f))
        val (red, green, blue) = when {
            segment < 1f -> Triple(chroma, secondary, 0f)
            segment < 2f -> Triple(secondary, chroma, 0f)
            segment < 3f -> Triple(0f, chroma, secondary)
            segment < 4f -> Triple(0f, secondary, chroma)
            segment < 5f -> Triple(secondary, 0f, chroma)
            else -> Triple(chroma, 0f, secondary)
        }
        val match = lightness - chroma / 2f
        return Color(red + match, green + match, blue + match, 1f)
    }
}

private fun Color.toHsl(): HslColor {
    val maximum = maxOf(red, green, blue)
    val minimum = minOf(red, green, blue)
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2f
    if (delta == 0f) return HslColor(0f, 0f, lightness)

    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val rawHue = when (maximum) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }
    return HslColor(if (rawHue < 0f) rawHue + 360f else rawHue, saturation, lightness)
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
                .fillMaxWidth()
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
private const val COLOR_SEARCH_ITERATIONS = 24
private const val MAX_LIST_DEPTH = 2
private const val LIST_INDENT_DP = 16
private const val INLINE_SMILIE_SIZE_EM = 1.25f
