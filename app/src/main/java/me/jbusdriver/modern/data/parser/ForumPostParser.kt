package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.RichList
import me.jbusdriver.modern.domain.model.RichListItem
import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private const val MAX_SOURCE_LIST_DEPTH = 32

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

private val LIST_TAGS = setOf("ol", "ul")
private val IGNORED_IMAGE_MARKERS = setOf("arw_r", "userinfo.gif", "fav.gif", "rec_add")
