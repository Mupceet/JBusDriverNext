package me.jbusdriver.modern.ui.forum

import me.jbusdriver.modern.domain.model.ForumBoard
import me.jbusdriver.modern.domain.model.ForumBoardGroup
import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumHomeSummary
import me.jbusdriver.modern.domain.model.ForumThread
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import me.jbusdriver.modern.domain.model.ForumTypeFilter
import me.jbusdriver.modern.domain.model.LastPost
import me.jbusdriver.modern.domain.model.PageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumSensitiveContentFilterTest {

    @Test
    fun `keyword matches by containment`() {
        assertTrue("性息".containsSensitiveForumKeyword())
        assertTrue("性息交流區".containsSensitiveForumKeyword())
        assertTrue("【性息】".containsSensitiveForumKeyword())
        assertFalse("福利討論區".containsSensitiveForumKeyword())
        assertFalse("討論".containsSensitiveForumKeyword())
        assertFalse("".containsSensitiveForumKeyword())
        assertFalse(null.containsSensitiveForumKeyword())
    }

    private fun board(id: Int, name: String) = ForumBoard(
        id = id, name = name, description = "", todayPosts = 0,
        totalThreads = "", totalPosts = "",
        lastPost = LastPost("", "", ""),
        typeId = null
    )

    @Test
    fun `board filtering removes matching boards and drops emptied groups`() {
        val original = ForumHomeData(
            banners = emptyList(),
            summary = ForumHomeSummary(),
            boardGroups = listOf(
                ForumBoardGroup("福利討論", listOf(board(1, "性息專區"), board(2, "一般討論"))),
                ForumBoardGroup("全敏感組", listOf(board(9, "性息"), board(10, "性息交流"))),
                ForumBoardGroup("正常組", listOf(board(11, "技術交流")))
            )
        )

        val filtered = original.withoutSensitiveBoards()

        assertEquals(listOf("福利討論", "正常組"), filtered.boardGroups.map { it.name })
        assertEquals(listOf("一般討論"), filtered.boardGroups.first().boards.map { it.name })
        // 未命中任何板块的分组保持原实例
        assertSame(original.boardGroups.last(), filtered.boardGroups.last())
    }

    private fun thread(tid: Int, typeName: String) = ForumThread(
        tid = tid, typeId = 0, typeName = typeName, typeColor = "",
        title = "Thread $tid", author = "A", authorUid = 0, authorAvatar = "",
        dateLine = "", viewCount = 0, replyCount = 0,
        lastReplyAuthor = "", lastReplyTime = "",
        images = emptyList(), isPinned = false, isDigest = false,
        pages = 1, isLocked = false, isHot = false
    )

    @Test
    fun `thread filtering removes matching threads and chips but keeps pageInfo`() {
        val pageInfo = PageInfo(activePage = 2, nextPage = 3)
        val result = ForumThreadPageResult(
            threads = listOf(thread(1, "討論"), thread(2, "性息"), thread(3, "討論")),
            typeFilters = listOf(
                ForumTypeFilter(6, "討論", "", 0),
                ForumTypeFilter(5, "性息", "", 0)
            ),
            pageInfo = pageInfo
        )

        val filtered = result.withoutSensitiveThreads()

        assertEquals(listOf(1, 3), filtered.threads.map { it.tid })
        assertEquals(listOf(6), filtered.typeFilters.map { it.typeId })
        assertSame(pageInfo, filtered.pageInfo)
        assertEquals(setOf(5), result.sensitiveTypeIds())
    }
}
