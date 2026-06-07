package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumTextSize
import me.jbusdriver.modern.domain.model.RichList
import me.jbusdriver.modern.domain.model.RichListItem
import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private const val MAX_SOURCE_LIST_DEPTH = 32

private data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val color: String? = null,
    val size: ForumTextSize = ForumTextSize.BODY,
    val isLink: Boolean = false
)

internal fun parseForumPostContent(root: Element?, baseUrl: String): List<ContentBlock> {
    if (root == null) return emptyList()
    return PostContentParser(baseUrl).parse(root)
}

private class PostContentParser(private val baseUrl: String) {
    private val blocks = mutableListOf<ContentBlock>()
    private val paragraphs = mutableListOf<RichParagraph>()
    private val parts = mutableListOf<TextPart>()
    private var pendingSpace = false

    fun parse(root: Element): List<ContentBlock> {
        root.childNodes().forEach { processNode(it, InlineStyle()) }
        flushRichText()
        return blocks
    }

    private fun processNode(node: Node, style: InlineStyle) {
        when (node) {
            is TextNode -> appendText(node.wholeText, style)
            is Element -> processElement(node, style)
        }
    }

    private fun processElement(element: Element, style: InlineStyle) {
        if (element.shouldIgnore()) return
        when (element.tagName().lowercase()) {
            "br" -> flushParagraph()
            "strong", "b" -> processChildren(element, style.copy(bold = true))
            "em", "i" -> processChildren(element, style.copy(italic = true))
            "u" -> processChildren(element, style.copy(underline = true))
            "s", "strike", "del" -> processChildren(element, style.copy(strikethrough = true))
            "a" -> processChildren(element, style.copy(isLink = true))
            "font", "span" -> processChildren(
                element,
                style.copy(
                    color = element.inlineColor() ?: style.color,
                    size = element.inlineSize(style.size)
                )
            )
            "img" -> appendImage(element, style)
            "ol", "ul" -> {
                flushRichText()
                val list = parseList(element, 1)
                if (list != null) blocks.add(ContentBlock.ListBlock(list))
                else processChildren(element, style)
            }
            "p" -> {
                processChildren(element, style)
                flushParagraph()
            }
            "div" -> {
                if (element.hasClass("quote")) appendQuote(element)
                else processChildren(element, style)
            }
            "script", "style", "form", "button" -> Unit
            else -> processChildren(element, style)
        }
    }

    private fun processChildren(element: Element, style: InlineStyle) {
        element.childNodes().forEach { processNode(it, style) }
    }

    private fun appendText(raw: String, style: InlineStyle) {
        val collapsed = raw.replace(Regex("\\s+"), " ")
        val visible = collapsed.trim()
        if (visible.isEmpty()) {
            if (parts.isNotEmpty()) pendingSpace = true
            return
        }
        val needsLeadingSpace = parts.isNotEmpty() &&
            (pendingSpace || collapsed.firstOrNull()?.isWhitespace() == true) &&
            !parts.last().text.endsWith(' ')
        if (needsLeadingSpace) {
            appendSeparator()
        }
        val part = style.toPart(visible)
        if (parts.lastOrNull()?.sameStyle(part) == true) {
            parts[parts.lastIndex] = part.copy(text = parts.last().text + part.text)
        } else {
            parts.add(part)
        }
        pendingSpace = collapsed.lastOrNull()?.isWhitespace() == true
    }

    private fun flushParagraph() {
        if (parts.isNotEmpty()) {
            val last = parts.last()
            parts[parts.lastIndex] = last.copy(text = last.text.trimEnd())
            if (parts.any { it.text.isNotEmpty() }) paragraphs.add(RichParagraph(parts.toList()))
            parts.clear()
        }
        pendingSpace = false
    }

    private fun appendSeparator() {
        val separator = TextPart(" ")
        if (parts.lastOrNull()?.sameStyle(separator) == true) {
            parts[parts.lastIndex] = parts.last().copy(text = parts.last().text + " ")
        } else {
            parts.add(separator)
        }
    }

    private fun flushRichText() {
        flushParagraph()
        if (paragraphs.isNotEmpty()) {
            blocks.add(ContentBlock.RichText(paragraphs.toList()))
            paragraphs.clear()
        }
    }

    private fun appendImage(element: Element, style: InlineStyle) {
        val src = element.attr("src").wrapForumImage(baseUrl)
        if (src.isEmpty() || IGNORED_IMAGE_MARKERS.any(src::contains)) return
        if (element.hasAttr("smilieid")) {
            appendInlineImage(src, element.attr("alt"), style)
            return
        }
        flushRichText()
        blocks.add(
            ContentBlock.Image(
                url = src,
                width = element.attr("width").toIntOrNull() ?: 0,
                height = element.attr("height").toIntOrNull() ?: 0,
                isFullSize = element.hasClass("zoom"),
                isGif = src.isGifUrl()
            )
        )
    }

    private fun appendInlineImage(src: String, alt: String, style: InlineStyle) {
        val needsLeadingSpace = parts.isNotEmpty() &&
            pendingSpace &&
            !parts.last().text.endsWith(' ')
        if (needsLeadingSpace) appendSeparator()
        parts.add(style.toPart(text = "", inlineImageUrl = src, inlineImageAlt = alt))
        pendingSpace = false
    }

    private fun appendQuote(element: Element) {
        flushRichText()
        val blockquote = element.selectFirst("blockquote") ?: return
        val author = blockquote.selectFirst("a[href]")?.text()?.trim().orEmpty()
        val clone = blockquote.clone()
        clone.select("font > a").remove()
        clone.text().trim().takeIf(String::isNotEmpty)?.let {
            blocks.add(ContentBlock.Quote(author, it))
        }
    }

    private fun parseList(element: Element, depth: Int): RichList? {
        if (depth > MAX_SOURCE_LIST_DEPTH) return null
        val items = element.children()
            .filter { it.tagName().equals("li", ignoreCase = true) }
            .map { item ->
                val inlineNodes = item.childNodes().filterNot { child ->
                    child is Element && child.tagName().lowercase() in LIST_TAGS
                }
                val itemParagraphs = InlineParagraphParser().parse(inlineNodes)
                val children = item.children()
                    .filter { it.tagName().lowercase() in LIST_TAGS }
                    .mapNotNull { parseList(it, depth + 1) }
                RichListItem(itemParagraphs, children)
            }
        if (items.isEmpty()) return null
        val ordered = element.tagName().equals("ol", ignoreCase = true)
        return RichList(
            ordered = ordered,
            start = if (ordered) element.attr("start").toIntOrNull() ?: 1 else 1,
            items = items
        )
    }
}

private class InlineParagraphParser {
    private val paragraphs = mutableListOf<RichParagraph>()
    private val parts = mutableListOf<TextPart>()
    private var pendingSpace = false

    fun parse(nodes: List<Node>): List<RichParagraph> {
        nodes.forEach { process(it, InlineStyle()) }
        flush()
        return paragraphs
    }

    private fun process(node: Node, style: InlineStyle) {
        when (node) {
            is TextNode -> append(node.wholeText, style)
            is Element -> {
                if (node.shouldIgnore()) return
                val next = when (node.tagName().lowercase()) {
                    "strong", "b" -> style.copy(bold = true)
                    "em", "i" -> style.copy(italic = true)
                    "u" -> style.copy(underline = true)
                    "s", "strike", "del" -> style.copy(strikethrough = true)
                    "a" -> style.copy(isLink = true)
                    "font", "span" -> style.copy(
                        color = node.inlineColor() ?: style.color,
                        size = node.inlineSize(style.size)
                    )
                    else -> style
                }
                if (node.tagName().equals("br", ignoreCase = true)) flush()
                else node.childNodes().forEach { process(it, next) }
            }
        }
    }

    private fun append(raw: String, style: InlineStyle) {
        val collapsed = raw.replace(Regex("\\s+"), " ")
        val visible = collapsed.trim()
        if (visible.isEmpty()) {
            if (parts.isNotEmpty()) pendingSpace = true
            return
        }
        val needsLeadingSpace = parts.isNotEmpty() &&
            (pendingSpace || collapsed.firstOrNull()?.isWhitespace() == true) &&
            !parts.last().text.endsWith(' ')
        if (needsLeadingSpace) {
            val separator = TextPart(" ")
            if (parts.lastOrNull()?.sameStyle(separator) == true) {
                parts[parts.lastIndex] = parts.last().copy(text = parts.last().text + " ")
            } else {
                parts.add(separator)
            }
        }
        val part = style.toPart(visible)
        if (parts.lastOrNull()?.sameStyle(part) == true) {
            parts[parts.lastIndex] = part.copy(text = parts.last().text + part.text)
        } else parts.add(part)
        pendingSpace = collapsed.lastOrNull()?.isWhitespace() == true
    }

    private fun flush() {
        if (parts.isNotEmpty()) {
            val last = parts.last()
            parts[parts.lastIndex] = last.copy(text = last.text.trimEnd())
            paragraphs.add(RichParagraph(parts.toList()))
            parts.clear()
        }
        pendingSpace = false
    }
}

private fun Element.shouldIgnore(): Boolean =
    hasClass("pstatus") || hasClass("modact") || hasClass("locked") ||
        hasClass("cm") || hasClass("sign")

private fun Element.inlineColor(): String? {
    val raw = attr("color").ifBlank {
        Regex("(?:^|;)\\s*color\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE)
            .find(attr("style"))?.groupValues?.get(1)?.trim().orEmpty()
    }.lowercase()
    return raw.takeIf {
        it.matches(Regex("#[0-9a-f]{3}([0-9a-f]{3})?")) || it in NAMED_COLORS
    }
}

private fun Element.inlineSize(current: ForumTextSize): ForumTextSize {
    val size = attr("size").toIntOrNull() ?: return current
    return when {
        size >= 5 -> ForumTextSize.HEADING
        size >= 4 -> ForumTextSize.EMPHASIS
        else -> ForumTextSize.BODY
    }
}

private fun InlineStyle.toPart(
    text: String,
    inlineImageUrl: String = "",
    inlineImageAlt: String = ""
) = TextPart(
    text = text,
    bold = bold,
    italic = italic,
    underline = underline,
    strikethrough = strikethrough,
    color = color,
    size = size,
    isLink = isLink,
    inlineImageUrl = inlineImageUrl,
    inlineImageAlt = inlineImageAlt
)

private fun TextPart.sameStyle(other: TextPart): Boolean {
    if (inlineImageUrl.isNotEmpty() || other.inlineImageUrl.isNotEmpty()) return false
    return copy(text = "") == other.copy(text = "")
}

private val LIST_TAGS = setOf("ol", "ul")
private val NAMED_COLORS = setOf("red", "blue", "green", "black", "white", "gray", "grey", "orange", "purple")
private val IGNORED_IMAGE_MARKERS = setOf("arw_r", "userinfo.gif", "fav.gif", "rec_add")
