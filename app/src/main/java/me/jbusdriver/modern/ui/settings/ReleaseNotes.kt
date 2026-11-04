package me.jbusdriver.modern.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 更新日志 Markdown（发布流程从 updateLog.md 组装，格式受控）的解析与渲染。
 *
 * 支持的语法与 [parseReleaseNotes] 的行级规则、[parseNoteInline] 的行内规则一致，
 * 不支持嵌套与多行段落聚合——超出的写法按普通文本展示。
 */

/** 行内片段：`url` 非空时为链接，`bold` 为粗体，两者都缺省为普通文本。 */
internal data class NoteSegment(
    val text: String,
    val bold: Boolean = false,
    val url: String? = null,
)

internal sealed interface ReleaseNoteBlock {
    /** `#`~`######` 标题，[level] 为 `#` 个数。 */
    data class Heading(val level: Int, val segments: List<NoteSegment>) : ReleaseNoteBlock

    /** `* `/`- `/`+ ` 列表行。 */
    data class Bullet(val segments: List<NoteSegment>) : ReleaseNoteBlock

    /** `----` 水平分隔线。 */
    data object Divider : ReleaseNoteBlock

    /** 普通段落行（空行被丢弃，块间距由渲染负责）。 */
    data class Paragraph(val segments: List<NoteSegment>) : ReleaseNoteBlock
}

private val headingLine = Regex("^(#{1,6})\\s+(.*)$")
private val bulletLine = Regex("^[*_+\\-]\\s+(.*)$")
private val dividerLine = Regex("^[-*_]{3,}$")
private val inlineNote = Regex("\\*\\*(.+?)\\*\\*|\\[([^]]+)]\\(([^)]*)\\)")

/** 逐行解析更新日志 Markdown；空行丢弃，分隔块间距由渲染层统一控制。 */
internal fun parseReleaseNotes(markdown: String): List<ReleaseNoteBlock> =
    markdown.lines().mapNotNull { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> null
            dividerLine.matches(line) -> ReleaseNoteBlock.Divider
            else -> headingLine.find(line)?.let { match ->
                ReleaseNoteBlock.Heading(
                    level = match.groupValues[1].length,
                    segments = parseNoteInline(match.groupValues[2]),
                )
            } ?: bulletLine.find(line)?.let { match ->
                ReleaseNoteBlock.Bullet(parseNoteInline(match.groupValues[1]))
            } ?: ReleaseNoteBlock.Paragraph(parseNoteInline(line))
        }
    }

/**
 * 行内解析：`**粗体**` 与 `[文字](地址)`，不嵌套；空地址的链接按普通文本处理。
 */
internal fun parseNoteInline(text: String): List<NoteSegment> {
    if (text.isEmpty()) return emptyList()
    val segments = mutableListOf<NoteSegment>()
    var cursor = 0
    for (match in inlineNote.findAll(text)) {
        if (match.range.first > cursor) {
            segments.add(NoteSegment(text.substring(cursor, match.range.first)))
        }
        val boldText = match.groupValues[1]
        if (boldText.isNotEmpty()) {
            segments.add(NoteSegment(boldText, bold = true))
        } else {
            segments.add(
                NoteSegment(
                    text = match.groupValues[2],
                    url = match.groupValues[3].takeIf { it.isNotBlank() },
                )
            )
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) segments.add(NoteSegment(text.substring(cursor)))
    return segments
}

/**
 * 更新日志弹框内容：标题分级、列表悬挂缩进、分隔线，链接可点击（系统浏览器打开）。
 */
@Composable
internal fun ReleaseNotesContent(markdown: String, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val blocks = remember(markdown) { parseReleaseNotes(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                ReleaseNoteBlock.Divider -> HorizontalDivider()
                is ReleaseNoteBlock.Heading -> Text(
                    text = noteAnnotatedString(block.segments, linkColor),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    },
                )
                is ReleaseNoteBlock.Bullet -> Row {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = noteAnnotatedString(block.segments, linkColor),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                is ReleaseNoteBlock.Paragraph -> Text(
                    text = noteAnnotatedString(block.segments, linkColor),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun noteAnnotatedString(segments: List<NoteSegment>, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        segments.forEach { segment ->
            val url = segment.url
            if (url != null) {
                withLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            )
                        ),
                    )
                ) { append(segment.text) }
            } else if (segment.bold) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(segment.text) }
            } else {
                append(segment.text)
            }
        }
    }
