package me.jbusdriver.modern.ui.forum

import me.jbusdriver.modern.data.ForumFloorOrder
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.PageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class ForumThreadDetailStateTest {

    @Test
    fun `floor order reload keeps current detail visible without loading state`() {
        val detail = ForumThreadDetail(
            tid = 168357,
            typeId = 0,
            typeName = "",
            typeColor = "",
            title = "Title",
            viewCount = 0,
            replyCount = 0,
            author = "",
            authorUid = 0,
            authorAvatar = "",
            postTime = "",
            contentBlocks = emptyList(),
            comments = emptyList(),
            replies = emptyList(),
            pageInfo = PageInfo()
        )
        val state = ForumThreadDetailUiState(
            detail = detail,
            floorOrder = ForumFloorOrder.REGULAR,
            isLoading = false,
            isLoadingMore = true
        )

        val next = state.prepareFloorOrderReload(ForumFloorOrder.REVERSE)

        assertSame(detail, next.detail)
        assertEquals(ForumFloorOrder.REVERSE, next.floorOrder)
        assertFalse(next.isLoading)
        assertFalse(next.isLoadingMore)
    }
}
