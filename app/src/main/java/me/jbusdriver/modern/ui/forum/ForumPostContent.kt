package me.jbusdriver.modern.ui.forum

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import android.content.Intent
import android.net.Uri
import me.jbusdriver.R
import me.jbusdriver.modern.core.copy
import me.jbusdriver.modern.ui.components.AppAsyncImage
import me.jbusdriver.modern.ui.components.dimColorFilter
import me.jbusdriver.modern.ui.components.rememberSiteImageRequest
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumLinkKind
import me.jbusdriver.modern.domain.model.ForumTextSize
import me.jbusdriver.modern.domain.model.RichList
import me.jbusdriver.modern.domain.model.RichListItem
import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart
import me.jbusdriver.modern.domain.model.findBareLinkMatches
import me.jbusdriver.modern.domain.model.findMovieCodeMatches
import me.jbusdriver.modern.domain.model.normalizeMovieCode
import me.jbusdriver.modern.domain.model.toExternalUrl
import me.jbusdriver.modern.ui.browser.rememberWebOpener
import me.jbusdriver.modern.ui.components.GifPlaceholder
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 首帖分段:把连续的非图片块聚合成 [FirstPostSection.Text],每个图片块单独成 [FirstPostSection.ImageBlock]。
 * 供 [ForumThreadDetailScreen] 把首帖拆成多个 LazyColumn items,让图片可随滚动被回收(解决单楼层多图 OOM)。
 */
internal sealed interface FirstPostSection {
    /** 该段在原始 blocks 列表中的起始下标,用作 LazyColumn item key(负载不可变,故稳定)。 */
    val startBlockIndex: Int

    @Immutable
    data class Text(
        override val startBlockIndex: Int,
        val blocks: List<ContentBlock>
    ) : FirstPostSection

    @Immutable
    data class ImageBlock(
        override val startBlockIndex: Int,
        val block: ContentBlock.Image,
        /** 在全局可查看图片列表中的下标;-1 表示未加载的 GIF(占位,不进入查看器分页)。 */
        val viewableIndex: Int
    ) : FirstPostSection
}

/**
 * 按顺序把首帖 [blocks] 聚合为「文本段 + 单图」分段,并算出全局可查看图片 URL 列表。
 * viewable 判定与 [ForumPostContent] 内部的 viewableImageUrls 一致:
 * 非 GIF 总是可查看;GIF 仅在 autoLoadGifs 或已加载([loadedGifUrls])时可查看。
 */
internal fun groupFirstPostBlocks(
    blocks: List<ContentBlock>,
    autoLoadGifs: Boolean,
    loadedGifUrls: Set<String>
): Pair<List<FirstPostSection>, List<String>> {
    val sections = mutableListOf<FirstPostSection>()
    val viewableImageUrls = mutableListOf<String>()
    val textBuffer = mutableListOf<ContentBlock>()
    var textStart = -1
    fun flushText() {
        if (textBuffer.isNotEmpty()) {
            sections += FirstPostSection.Text(textStart, textBuffer.toList())
            textBuffer.clear()
            textStart = -1
        }
    }
    blocks.forEachIndexed { index, block ->
        if (block is ContentBlock.Image) {
            flushText()
            val viewable = !block.isGif || autoLoadGifs || block.url in loadedGifUrls
            val viewableIndex = if (viewable) {
                viewableImageUrls += block.url
                viewableImageUrls.size - 1
            } else {
                -1
            }
            sections += FirstPostSection.ImageBlock(index, block, viewableIndex)
        } else {
            if (textBuffer.isEmpty()) textStart = index
            textBuffer += block
        }
    }
    flushText()
    return sections to viewableImageUrls
}

@Composable
internal fun ForumPostContent(
    blocks: List<ContentBlock>,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
    loadedGifUrls: Set<String> = emptySet(),
    autoLoadGifs: Boolean = false,
    onLoadGif: (String) -> Unit = {},
    onLoadAllGifs: () -> Unit = {},
    onQuoteClick: (Int) -> Unit = {},
    /** 点击正文/引用块中识别出的影片番号；null 表示不做番号识别（如长按复制预览）。 */
    onMovieCodeClick: ((String) -> Unit)? = null,
    /**
     * 点击正文行内链接时回调（含系统默认打开的兜底）。null 表示不拦截，
     * 链接维持 LinkAnnotation 默认行为（UriHandler 打开浏览器），如长按复制预览。
     */
    onLinkClick: ((String) -> Unit)? = null,
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
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var viewableIndex = 0
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.RichText -> block.paragraphs.forEach { paragraph ->
                    StyledParagraph(paragraph, onMovieCodeClick = onMovieCodeClick, onLinkClick = onLinkClick)
                }

                is ContentBlock.ListBlock -> RichListContent(
                    block.list,
                    onMovieCodeClick = onMovieCodeClick,
                    onLinkClick = onLinkClick
                )
                is ContentBlock.Image -> if (showImages) {
                    if (block.isGif && !autoLoadGifs && block.url !in loadedGifUrls) {
                        GifPlaceholder(
                            onClick = { onLoadGif(block.url) },
                            onLoadAllGifs = onLoadAllGifs,
                            modifier = if (block.isFullSize) {
                                Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
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

                is ContentBlock.Quote -> QuoteContent(block, onQuoteClick, onMovieCodeClick)
                is ContentBlock.RestrictedNotice -> RestrictedNotice(block.message)
            }
        }
    }
}

/**
 * 可选中复制的帖子正文:内部包一层 [SelectionContainer],纯文本点击无反应、长按进入
 * 系统选区(工具栏复制/全选、拖拽扩展),链接与番号区域由 [StyledParagraph] 的长按菜单接管。
 *
 * 每个文本段各持有独立 [SelectionState](API 要求一个 state 只绑定一个容器),图片不参与
 * 选区,保持首帖分段「图片随滚动回收」的内存设计;有选区时返回键先取消选区再交给导航退出。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SelectableForumPostContent(
    blocks: List<ContentBlock>,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
    loadedGifUrls: Set<String> = emptySet(),
    autoLoadGifs: Boolean = false,
    onLoadGif: (String) -> Unit = {},
    onLoadAllGifs: () -> Unit = {},
    onQuoteClick: (Int) -> Unit = {},
    onMovieCodeClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
    showImages: Boolean = true
) {
    val selectionState = rememberSelectionState()
    BackHandler(enabled = selectionState.selectedTexts.isNotEmpty()) {
        selectionState.clear()
    }
    SelectionContainer(state = selectionState, modifier = modifier) {
        ForumPostContent(
            blocks = blocks,
            onImageClick = onImageClick,
            loadedGifUrls = loadedGifUrls,
            autoLoadGifs = autoLoadGifs,
            onLoadGif = onLoadGif,
            onLoadAllGifs = onLoadAllGifs,
            onQuoteClick = onQuoteClick,
            onMovieCodeClick = onMovieCodeClick,
            onLinkClick = onLinkClick,
            showImages = showImages
        )
    }
}

@Composable
private fun StyledParagraph(
    paragraph: RichParagraph,
    modifier: Modifier = Modifier,
    onMovieCodeClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val renderData = remember(paragraph, onSurface, surface, primary, onMovieCodeClick, onLinkClick) {
        paragraph.toRenderData(onSurface, surface, primary, onMovieCodeClick, onLinkClick)
    }
    val inlineContent = remember(paragraph) {
        paragraph.toInlineContent()
    }
    if (!paragraph.hasVisibleContent()) return

    val context = LocalContext.current
    val openWeb = rememberWebOpener()
    val haptics = LocalHapticFeedback.current
    val copiedMessage = stringResource(R.string.copied)
    val copiedLinkMessage = stringResource(R.string.copied_link)
    val noHandlerMessage = stringResource(R.string.no_handler)
    val currentOnMovieCodeClick by rememberUpdatedState(onMovieCodeClick)
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    // 磁力/ED2K/网盘等非 http 链接交给能处理的应用(下载器/播放器/网盘客户端),无处理应用时提示
    val openExternal: (String) -> Unit = { link ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        }.onFailure {
            Toast.makeText(context, noHandlerMessage, Toast.LENGTH_SHORT).show()
        }
    }

    var linkMenuRange by remember { mutableStateOf<ParagraphLinkRange?>(null) }
    var linkMenuExpanded by remember { mutableStateOf(false) }
    var linkMenuOffset by remember { mutableStateOf(Offset.Zero) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = modifier) {
        Text(
            text = renderData.text,
            inlineContent = inlineContent,
            onTextLayout = { layoutResult = it },
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 22.sp,
                color = onSurface
            ),
            modifier = Modifier.pointerInput(renderData.linkRanges) {
                awaitEachGesture {
                    val press = awaitFirstDownAtInitial() ?: return@awaitEachGesture
                    val layout = layoutResult ?: return@awaitEachGesture
                    val offset = layout.getOffsetForPosition(press.position)
                    val hit = renderData.linkRanges.firstOrNull { it.contains(offset) }
                        ?: return@awaitEachGesture // 非链接区域不拦截:点击无反应,长按进入选区

                    // 命中链接:立即消费按下事件,SelectionContainer 的选区检测与
                    // LinkAnnotation 的点击检测都不会再响应本次手势
                    press.consume()

                    when (
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            awaitLinkPress(press.id, press.position)
                        }
                    ) {
                        LinkPressOutcome.Tap -> when (hit.kind) {
                            ForumLinkKind.MOVIE_CODE -> currentOnMovieCodeClick?.invoke(hit.value)

                            ForumLinkKind.URL -> currentOnLinkClick?.invoke(hit.value)
                                ?: openWeb(hit.value)

                            // 磁力/ED2K/网盘:交给系统应用(下载器/播放器/网盘客户端)或浏览器
                            ForumLinkKind.MAGNET,
                            ForumLinkKind.ED2K,
                            ForumLinkKind.CLOUD -> openExternal(hit.toExternalLinkUrl())
                        }

                        // withTimeoutOrNull 超时返回 null = 按住超过长按时限;
                        // 若此时指针仍按住,即为长按手势
                        null -> {
                            if (currentEvent.changes.any { it.pressed }) {
                                // 先立即弹菜单(手指未抬起即生效),再持续消费直至抬起,
                                // 保证选区绝不启动
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                linkMenuOffset = press.position
                                linkMenuRange = hit
                                linkMenuExpanded = true
                                while (currentEvent.changes.any { it.pressed }) {
                                    currentEvent.changes.forEach { it.consume() }
                                    awaitPointerEvent(PointerEventPass.Initial)
                                }
                            }
                        }

                        LinkPressOutcome.Scrolled -> Unit // 滚动手势,停止拦截
                    }
                }
            }
        )
        // 链接/番号长按菜单:零尺寸 Box 按按压点(像素)定位,菜单锚定其上;
        // DisableSelection 避免菜单文本注册进外层选区容器
        DisableSelection {
            linkMenuRange?.let { range ->
                Box(
                    modifier = Modifier.offset {
                        IntOffset(linkMenuOffset.x.roundToInt(), linkMenuOffset.y.roundToInt())
                    }
                ) {
                    DropdownMenu(
                        expanded = linkMenuExpanded,
                        onDismissRequest = { linkMenuExpanded = false }
                ) {
                    when (range.kind) {
                        ForumLinkKind.MOVIE_CODE -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_movie_code)) },
                                onClick = {
                                    context.copy(range.value)
                                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                                    linkMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.open_movie)) },
                                onClick = {
                                    currentOnMovieCodeClick?.invoke(range.value)
                                    linkMenuExpanded = false
                                }
                            )
                        }

                        ForumLinkKind.URL -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_text)) },
                                onClick = {
                                    context.copy(range.displayText)
                                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                                    linkMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_link)) },
                                onClick = {
                                    context.copy(range.value)
                                    Toast.makeText(context, copiedLinkMessage, Toast.LENGTH_SHORT).show()
                                    linkMenuExpanded = false
                                }
                            )
                        }

                        ForumLinkKind.MAGNET,
                        ForumLinkKind.ED2K,
                        ForumLinkKind.CLOUD -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy)) },
                                onClick = {
                                    context.copy(range.value)
                                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                                    linkMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.open_external)) },
                                onClick = {
                                    openExternal(range.toExternalLinkUrl())
                                    linkMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

/** 链接区域按下手势的分类结果。 */
private enum class LinkPressOutcome {
    /** 未超过长按时限即抬起:点击链接。 */
    Tap,

    /** 滑动超出触摸斜率:滚动手势,放弃拦截。 */
    Scrolled
}

/**
 * Initial pass 等待首次按下(祖先节点先于本节点收到 Initial 事件)。
 * 返回 null 表示本手势开始前所有指针已抬起(如连击的收尾事件)。
 */
private suspend fun AwaitPointerEventScope.awaitFirstDownAtInitial(): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val down = event.changes.firstOrNull { it.changedToDown() }
        if (down != null) return down
        if (event.changes.all { it.changedToUpIgnoreConsumed() }) return null
    }
}

/**
 * 按住链接期间仅在 Initial pass 观察手势,不消费事件,保证 LazyColumn 滚动可正常接管。
 * 超过长按时限的情形由调用方用 withTimeoutOrNull 判定(返回 null 且指针仍按住)。
 */
private suspend fun AwaitPointerEventScope.awaitLinkPress(
    pointerId: PointerId,
    pressPosition: Offset
): LinkPressOutcome {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
        when {
            (change.position - pressPosition).getDistance() > viewConfiguration.touchSlop -> {
                return LinkPressOutcome.Scrolled
            }

            change.changedToUpIgnoreConsumed() -> return LinkPressOutcome.Tap
        }
    }
    return LinkPressOutcome.Scrolled
}

/** 段落内的可长按链接区间(裸链接/番号),offset 相对整段 [ParagraphRenderData.text]。 */
@Immutable
internal data class ParagraphLinkRange(
    val start: Int,
    val endExclusive: Int,
    /** 链接类型,决定点击路由与长按菜单。 */
    val kind: ForumLinkKind,
    /** 链接值:URL/磁力/ED2K/网盘为原文(去尾标点),番号为规范化形式。 */
    val value: String,
    /** 链接的显示文本,「复制链接文本」用。 */
    val displayText: String
) {
    operator fun contains(offset: Int): Boolean = offset in start until endExclusive

    /** 网盘裸域名补全 scheme,供外部打开;其余类型原样返回。 */
    fun toExternalLinkUrl(): String = when (kind) {
        ForumLinkKind.CLOUD -> if (value.startsWith("http", true)) value else "https://$value"
        else -> value
    }
}

/** 段落渲染产物:带样式的 [AnnotatedString] 与可长按链接区间。 */
@Immutable
internal data class ParagraphRenderData(
    val text: AnnotatedString,
    val linkRanges: List<ParagraphLinkRange>
)

private fun RichParagraph.toRenderData(
    onSurface: Color,
    surface: Color,
    primary: Color,
    onMovieCodeClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null
): ParagraphRenderData {
    val linkRanges = mutableListOf<ParagraphLinkRange>()
    val text = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (part.inlineImageUrl.isNotEmpty()) {
                appendInlineContent(part.inlineContentId(index), part.inlineImageAlt.ifBlank { " " })
            } else {
                withStyle(part.toSpanStyle(onSurface, surface, primary)) {
                    if (part.isLink && part.linkUrl.isNotBlank()) {
                        val start = length
                        val link = if (onLinkClick != null) {
                            // 挂 listener 接管默认 UriHandler 行为，由调用方决定站内路由或回落浏览器
                            val onLinkClickNotNull = onLinkClick
                            LinkAnnotation.Url(
                                part.linkUrl,
                                linkInteractionListener = LinkInteractionListener { annotation ->
                                    (annotation as? LinkAnnotation.Url)?.let {
                                        onLinkClickNotNull(it.url)
                                    }
                                }
                            )
                        } else {
                            LinkAnnotation.Url(part.linkUrl)
                        }
                        withLink(link) {
                            append(part.text)
                        }
                        linkRanges += ParagraphLinkRange(
                            start = start,
                            endExclusive = length,
                            kind = ForumLinkKind.URL,
                            value = part.linkUrl,
                            displayText = part.text
                        )
                    } else {
                        appendContentLinkSegments(
                            text = part.text,
                            primary = primary,
                            recognizeMovieCodes = onMovieCodeClick != null,
                            onMovieCodeClick = onMovieCodeClick,
                            onLinkRange = { start, endExclusive, kind, value, displayText ->
                                linkRanges += ParagraphLinkRange(
                                    start = start,
                                    endExclusive = endExclusive,
                                    kind = kind,
                                    value = value,
                                    displayText = displayText
                                )
                            }
                        )
                    }
                }
            }
        }
    }
    return ParagraphRenderData(text, linkRanges)
}

/**
 * 把非链接文本按裸链接命中（URL/磁力/ED2K/网盘，可选番号）分段追加：
 * 命中片段渲染为 primary 色 + 下划线并收集区间，普通片段原样继承外层样式。
 * 帖子标题（[ForumThreadTitle]）不经此路径，仍只用 [appendMovieCodeSegments]。
 */
private fun AnnotatedString.Builder.appendContentLinkSegments(
    text: String,
    primary: Color,
    recognizeMovieCodes: Boolean,
    onMovieCodeClick: ((String) -> Unit)?,
    onLinkRange: (start: Int, endExclusive: Int, kind: ForumLinkKind, value: String, displayText: String) -> Unit
) {
    val matches = findBareLinkMatches(text).filter { match ->
        recognizeMovieCodes || match.kind != ForumLinkKind.MOVIE_CODE
    }
    if (matches.isEmpty()) {
        append(text)
        return
    }
    var cursor = 0
    matches.forEach { match ->
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        val start = length
        val displayText = text.substring(match.range)
        withStyle(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)) {
            if (match.kind == ForumLinkKind.MOVIE_CODE) {
                val code = normalizeMovieCode(displayText) ?: match.value
                withLink(
                    LinkAnnotation.Clickable(tag = MOVIE_CODE_LINK_TAG) {
                        onMovieCodeClick?.invoke(code)
                    }
                ) {
                    append(displayText)
                }
                onLinkRange(start, length, match.kind, code, displayText)
            } else {
                withLink(LinkAnnotation.Url(match.value)) {
                    append(displayText)
                }
                onLinkRange(start, length, match.kind, match.value, displayText)
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}

/**
 * 把纯文本按番号命中分段追加：普通片段原样继承外层样式，番号片段渲染为
 * primary 色 + 下划线的可点击链接（[LinkAnnotation.Clickable]）。
 * 仅供非链接、非内联表情的文本调用；长按复制预览等场景传 null 回调即不识别。
 * 帖子标题（[ForumThreadTitle]）专用；帖子正文走 [appendContentLinkSegments]。
 */
internal fun AnnotatedString.Builder.appendMovieCodeSegments(
    text: String,
    primary: Color,
    onMovieCodeClick: (String) -> Unit
) {
    var cursor = 0
    findMovieCodeMatches(text).forEach { match ->
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        val code = normalizeMovieCode(match.value) ?: match.value
        val linkStyle = SpanStyle(color = primary, textDecoration = TextDecoration.Underline)
        withStyle(linkStyle) {
            withLink(
                LinkAnnotation.Clickable(tag = MOVIE_CODE_LINK_TAG) {
                    onMovieCodeClick(code)
                }
            ) {
                append(match.value)
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}

private const val MOVIE_CODE_LINK_TAG = "forum_movie_code"

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
            AppAsyncImage(
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
    val candidate = runCatching { Color(value.toColorInt()).copy(alpha = 1f) }.getOrNull()
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
private fun RichListContent(
    list: RichList,
    depth: Int = 0,
    onMovieCodeClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null
) {
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
                    item.paragraphs.forEach { paragraph ->
                        StyledParagraph(
                            paragraph,
                            onMovieCodeClick = onMovieCodeClick,
                            onLinkClick = onLinkClick
                        )
                    }
                }
            }
            item.children.forEach { child ->
                RichListContent(child, depth + 1, onMovieCodeClick, onLinkClick)
            }
        }
    }
}

/**
 * 已加载图片的宽高比缓存（仅内存）：LazyColumn item 滑出即销毁组合，Coil 状态随之丢失，
 * 滑回时若无缓存会先按占位高度(180dp)组合再跳到图片真实高度造成闪动；命中缓存的
 * 比例后首帧即按真实高度组合。key 为图片 URL。
 */
private val forumImageAspectRatios = HashMap<String, Float>()

@Composable
internal fun ForumImage(block: ContentBlock.Image, onClick: () -> Unit) {
    if (block.isFullSize) {
        val aspectRatio = forumImageAspectRatios[block.url]
        SubcomposeAsyncImage(
            model = rememberSiteImageRequest(block.url),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .then(aspectRatio?.let { Modifier.aspectRatio(it) } ?: Modifier)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentScale = ContentScale.FillWidth,
            colorFilter = dimColorFilter(),
            onSuccess = { state ->
                val drawable = state.result.drawable
                val width = drawable.intrinsicWidth
                val height = drawable.intrinsicHeight
                if (width > 0 && height > 0) {
                    forumImageAspectRatios[block.url] = width.toFloat() / height.toFloat()
                }
            },
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        )
    } else {
        var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
        AppAsyncImage(
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
private fun QuoteContent(
    block: ContentBlock.Quote,
    onQuoteClick: (Int) -> Unit,
    onMovieCodeClick: ((String) -> Unit)? = null
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val quoteTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val contentText = remember(block.content, accentColor, onMovieCodeClick) {
        if (onMovieCodeClick == null) {
            AnnotatedString(block.content)
        } else {
            buildAnnotatedString {
                appendMovieCodeSegments(block.content, accentColor, onMovieCodeClick)
            }
        }
    }
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
                    val onClick = if (block.quotedPid > 0) {
                        { onQuoteClick(block.quotedPid) }
                    } else {
                        null
                    }
                    Text(
                        text = if (onClick != null) block.author else "${block.author}：",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (onClick != null) {
                                TextDecoration.Underline
                            } else {
                                null
                            }
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = if (onClick != null) {
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onClick)
                        } else {
                            Modifier
                        }
                    )
                }
                Text(
                    text = contentText,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = quoteTextColor
                )
            }
        }
    }
}

@Composable
private fun RestrictedNotice(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("restricted_notice"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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

internal fun forumFloorLabel(floor: Int, isPinned: Boolean, pinnedLabel: String): String =
    if (isPinned) "$pinnedLabel · $floor#" else "$floor#"

internal fun forumListIndentStep(depth: Int): Int {
    val currentIndent = depth.coerceIn(0, MAX_LIST_DEPTH) * LIST_INDENT_DP
    val parentIndent = (depth - 1).coerceIn(0, MAX_LIST_DEPTH) * LIST_INDENT_DP
    return currentIndent - parentIndent
}

private const val MIN_TEXT_CONTRAST = 4.5f
private const val COLOR_SEARCH_ITERATIONS = 24
private const val MAX_LIST_DEPTH = 2
private const val LIST_INDENT_DP = 16
private const val INLINE_SMILIE_SIZE_EM = 1.25f
