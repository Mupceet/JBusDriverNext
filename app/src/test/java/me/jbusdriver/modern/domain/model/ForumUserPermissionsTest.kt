package me.jbusdriver.modern.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置等级权限表（docs/FORUM_DESIGN.md §2.6）：组名 → 写权限映射。
 * 站点为人工审核等级制，组权限恒定；未知组名放开入口由服务端兜底。
 */
class ForumUserPermissionsTest {

    @Test
    fun `passenger group has no write permissions`() {
        val p = ForumUserPermissions.forGroupName("路人乘客")
        assertFalse(p.canReply)
        assertFalse(p.canPostThread)
        assertFalse(p.canSendMessage)
        assertFalse(p.canAddFriend)
        assertFalse(p.canComment)
    }

    @Test
    fun `student group can reply message and comment but not post or befriend`() {
        val p = ForumUserPermissions.forGroupName("駕校學員")
        assertTrue(p.canReply)
        assertTrue(p.canSendMessage)
        assertTrue(p.canComment)
        assertFalse(p.canPostThread)
        assertFalse(p.canAddFriend)
    }

    @Test
    fun `probationary and above groups have all write permissions`() {
        listOf("實習司機", "新手上路", "普通司機", "老司機", "飆車黨", "車神", "管理員", "超級版主").forEach { group ->
            val p = ForumUserPermissions.forGroupName(group)
            assertTrue("$group should reply", p.canReply)
            assertTrue("$group should post", p.canPostThread)
            assertTrue("$group should message", p.canSendMessage)
            assertTrue("$group should befriend", p.canAddFriend)
            assertTrue("$group should comment", p.canComment)
        }
    }

    @Test
    fun `banned and pending groups have no write permissions`() {
        listOf("限制會員", "禁止發言", "等待驗證", "QQ遊客").forEach { group ->
            val p = ForumUserPermissions.forGroupName(group)
            assertFalse("$group should not reply", p.canReply)
            assertFalse("$group should not post", p.canPostThread)
        }
    }

    @Test
    fun `unknown or blank group opens all entries with server-side fallback`() {
        listOf(null, "", "未知組", "  ").forEach { group ->
            val p = ForumUserPermissions.forGroupName(group)
            assertTrue("group=$group should show reply entry", p.canReply)
            assertTrue("group=$group should show post entry", p.canPostThread)
            // 空白组名归一为空串；未收录组名原样保留
            assertEquals(group?.trim().orEmpty(), p.groupName)
        }
    }
}
