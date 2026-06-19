package me.jbusdriver.modern.ui.forum

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import me.jbusdriver.R

@Composable
internal fun forumViewReplyCountText(viewCount: Int, replyCount: Int): String =
    pluralStringResource(R.plurals.view_count_label, viewCount, viewCount) +
            " · " +
            pluralStringResource(R.plurals.reply_count_label, replyCount, replyCount)
