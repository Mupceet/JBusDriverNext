package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ForumTextSize
import me.jbusdriver.modern.domain.model.TextPart
import org.jsoup.nodes.Element

/**
 * Accumulated inline text style while walking a forum post's DOM.
 * Shared by [PostContentParser] and [InlineParagraphParser].
 */
internal data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val color: String? = null,
    val size: ForumTextSize = ForumTextSize.BODY,
    val isLink: Boolean = false
)

/** Elements carrying these classes are editorial noise (status, mod actions, signatures). */
internal fun Element.shouldIgnore(): Boolean =
    hasClass("pstatus") || hasClass("modact") || hasClass("locked") ||
            hasClass("cm") || hasClass("sign")

internal fun Element.inlineColor(): String? {
    val raw = attr("color").ifBlank {
        Regex("(?:^|;)\\s*color\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE)
            .find(attr("style"))?.groupValues?.get(1)?.trim().orEmpty()
    }.lowercase()
    return raw.takeIf {
        it.matches(Regex("#[0-9a-f]{3}([0-9a-f]{3})?")) || it in NAMED_COLORS
    }
}

internal fun Element.inlineSize(current: ForumTextSize): ForumTextSize {
    val size = attr("size").toIntOrNull() ?: return current
    return when {
        size >= 5 -> ForumTextSize.HEADING
        size >= 4 -> ForumTextSize.EMPHASIS
        else -> ForumTextSize.BODY
    }
}

internal fun InlineStyle.toPart(
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

/** Two parts share a style when everything except their text matches (and neither is an image). */
internal fun TextPart.sameStyle(other: TextPart): Boolean {
    if (inlineImageUrl.isNotEmpty() || other.inlineImageUrl.isNotEmpty()) return false
    return copy(text = "") == other.copy(text = "")
}

internal val NAMED_COLORS = setOf(
    "red", "blue", "green", "black", "white", "gray", "grey", "orange", "purple"
)
