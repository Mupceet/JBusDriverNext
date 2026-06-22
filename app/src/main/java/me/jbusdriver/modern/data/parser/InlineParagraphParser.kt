package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Parses a flat run of inline DOM nodes (e.g. the contents of a single `<li>`)
 * into [RichParagraph]s, honouring inline styling and `<br>` line breaks.
 */
internal class InlineParagraphParser(private val baseUrl: String = "") {
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
                    "a" -> style.copy(
                        isLink = true,
                        linkUrl = node.attr("href").wrapForumLink(baseUrl)
                    )
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
