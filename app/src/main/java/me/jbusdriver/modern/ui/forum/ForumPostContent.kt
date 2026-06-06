package me.jbusdriver.modern.ui.forum

import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.RichList
import me.jbusdriver.modern.domain.model.RichParagraph

internal fun buildForumPlainText(blocks: List<ContentBlock>): String =
    blocks.flatMap(ContentBlock::toPlainLines)
        .filter(String::isNotBlank)
        .joinToString("\n")

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
    val marker = if (ordered) "${start + index}. " else "• "
    val ownLines = item.paragraphs.mapIndexed { paragraphIndex, paragraph ->
        val prefix = if (paragraphIndex == 0) marker else "  "
        "$indent$prefix${paragraph.plainText()}"
    }
    ownLines + item.children.flatMap { it.toPlainLines(depth + 1) }
}
