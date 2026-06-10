package me.jbusdriver.modern.ui.forum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.core.graphics.toColorInt
import androidx.compose.ui.res.painterResource

@Immutable
internal data class ForumInlineBadge(
    val id: String,
    val label: String,
    val containerColor: Color,
    val contentColor: Color = Color.White
)

@Immutable
internal data class ForumInlineLabel(
    val label: String,
    val color: Color,
    val bold: Boolean = true
)

@Immutable
internal data class ForumInlineIcon(
    val id: String,
    val iconRes: Int,
    val tint: Color,
    val contentDescription: String
)

@Composable
internal fun ForumThreadTitle(
    title: String,
    typeName: String,
    typeColor: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    trailingLabels: List<ForumInlineLabel> = emptyList(),
    trailingIcons: List<ForumInlineIcon> = emptyList(),
    badgeStyle: TextStyle = MaterialTheme.typography.bodySmall
) {
    val badges = remember(typeName, typeColor) {
        buildList {
            if (typeName.isNotEmpty()) {
                add(
                    ForumInlineBadge(
                        id = "type",
                        label = typeName,
                        containerColor = runCatching { Color(typeColor.toColorInt()) }
                            .getOrDefault(Color(0xFF666666))
                    )
                )
            }
        }
    }
    val annotatedTitle = remember(title, style, badges, trailingLabels, trailingIcons) {
        buildAnnotatedString {
            badges.forEach { badge ->
                appendInlineContent(badge.id, badge.label)
                append(" ")
            }
            withStyle(style.toSpanStyle()) {
                append(title)
            }
            trailingLabels.forEach { label ->
                append(" ")
                withStyle(
                    style.copy(
                        color = label.color,
                        fontSize = 10.sp,
                        fontWeight = if (label.bold) FontWeight.Bold else style.fontWeight
                    ).toSpanStyle()
                ) {
                    append(label.label)
                }
            }
            trailingIcons.forEach { icon ->
                append(" ")
                appendInlineContent(icon.id, icon.contentDescription)
            }
        }
    }
    val inlineContent = remember(badges, trailingIcons) {
        val badgeContent = badges.associate { badge ->
            badge.id to InlineTextContent(
                Placeholder(
                    width = forumInlineBadgeWidthEm(badge.label).em,
                    height = FORUM_INLINE_BADGE_HEIGHT_EM.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(badge.containerColor, RoundedCornerShape(3.dp))
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = badge.label,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        style = badgeStyle.copy(color = badge.contentColor)
                    )
                }
            }
        }
        val iconContent = trailingIcons.associate { icon ->
            icon.id to InlineTextContent(
                Placeholder(
                    width = FORUM_INLINE_ICON_SIZE_EM.em,
                    height = FORUM_INLINE_ICON_SIZE_EM.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Icon(
                    painter = painterResource(icon.iconRes),
                    contentDescription = icon.contentDescription,
                    tint = icon.tint,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        badgeContent + iconContent
    }

    Text(
        text = annotatedTitle,
        inlineContent = inlineContent,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}

internal fun forumInlineBadgeWidthEm(label: String): Float =
    FORUM_INLINE_BADGE_HORIZONTAL_PADDING_EM + label.length * FORUM_INLINE_BADGE_CHAR_WIDTH_EM

private const val FORUM_INLINE_BADGE_HEIGHT_EM = 1.3f
private const val FORUM_INLINE_BADGE_HORIZONTAL_PADDING_EM = 0.76f
private const val FORUM_INLINE_BADGE_CHAR_WIDTH_EM = 0.8f
private const val FORUM_INLINE_ICON_SIZE_EM = 1.05f
