package me.jbusdriver.modern.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UrlParserExtTest {

    @Test
    fun `stripToPath removes scheme and host keeping path and query`() {
        assertEquals(
            "/pics/cover/abp-123_b.jpg",
            "https://www.javbus.com/pics/cover/abp-123_b.jpg".stripToPath()
        )
        assertEquals(
            "/pics/sample/1.jpeg?v=2",
            "https://www.javbus.com/pics/sample/1.jpeg?v=2".stripToPath()
        )
    }

    @Test
    fun `stripToPath is identical across mirror hosts`() {
        val mainSite = "https://www.javbus.com/pics/actress/abc.jpg"
        val mirror = "https://mirror.example.net/pics/actress/abc.jpg"

        assertEquals(mainSite.stripToPath(), mirror.stripToPath())
    }

    @Test
    fun `stripToPath handles protocol relative urls`() {
        assertEquals("/x.jpg?a=1", "//cdn.example.com/x.jpg?a=1".stripToPath())
    }

    @Test
    fun `stripToPath returns relative and non-http urls unchanged`() {
        assertEquals("/pics/x.jpg", "/pics/x.jpg".stripToPath())
        assertEquals("file:///tmp/x.jpg", "file:///tmp/x.jpg".stripToPath())
        assertEquals("", "".stripToPath())
    }

    @Test
    fun `stripToPath returns root when url has no path`() {
        assertEquals("/", "https://www.javbus.com".stripToPath())
        assertEquals("/", "//cdn.example.com".stripToPath())
    }

    @Test
    fun `stripToPath distinguishes external hosts with different paths`() {
        val dmm = "https://pics.dmm.co.jp/mono/movie/adult/x/xps.jpg"
        val site = "https://www.javbus.com/pics/cover/xps.jpg"

        assertEquals("/mono/movie/adult/x/xps.jpg", dmm.stripToPath())
        assertNotEquals(dmm.stripToPath(), site.stripToPath())
    }

    @Test
    fun `extractFloorTarget parses findpost redirect with ptid`() {
        assertEquals(
            FloorTarget(threadId = 12345, postId = 67890),
            extractFloorTarget("https://www.example.com/forum/forum.php?mod=redirect&goto=findpost&pid=67890&ptid=12345")
        )
        // ptid 在前的参数顺序同样命中
        assertEquals(
            FloorTarget(threadId = 12345, postId = 67890),
            extractFloorTarget("/forum.php?mod=redirect&goto=findpost&ptid=12345&pid=67890")
        )
    }

    @Test
    fun `extractFloorTarget findpost without ptid has zero threadId`() {
        assertEquals(
            FloorTarget(threadId = 0, postId = 67890),
            extractFloorTarget("https://www.example.com/forum/forum.php?mod=redirect&goto=findpost&pid=67890")
        )
    }

    @Test
    fun `extractFloorTarget does not confuse ptid with pid`() {
        // ptid 的值不能被误读为 pid
        assertEquals(
            FloorTarget(threadId = 12345, postId = 67890),
            extractFloorTarget("https://www.example.com/forum/forum.php?mod=redirect&goto=findpost&ptid=12345&pid=67890")
        )
    }

    @Test
    fun `extractFloorTarget parses thread url with pid anchor`() {
        assertEquals(
            FloorTarget(threadId = 12345, postId = 67890),
            extractFloorTarget("https://www.example.com/forum/thread-12345-2-1.html#pid67890")
        )
        assertEquals(
            FloorTarget(threadId = 12345, postId = 67890),
            extractFloorTarget("https://www.example.com/forum/forum.php?mod=viewthread&tid=12345&page=2#pid67890")
        )
    }

    @Test
    fun `extractFloorTarget anchor without tid has zero threadId`() {
        assertEquals(
            FloorTarget(threadId = 0, postId = 67890),
            extractFloorTarget("#pid67890")
        )
    }

    @Test
    fun `extractFloorTarget returns null for non floor links`() {
        assertEquals(null, extractFloorTarget(""))
        assertEquals(null, extractFloorTarget("https://www.example.com/forum/thread-12345-2-1.html"))
        assertEquals(null, extractFloorTarget("https://www.example.com/forum/forum.php?mod=viewthread&tid=12345"))
        assertEquals(null, extractFloorTarget("https://www.example.com/misc.php?action=jump&pid=67890"))
    }

    @Test
    fun `resolveForumLinkTarget floor targets keep thread and post ids`() {
        assertEquals(
            ForumLinkTarget.Thread(threadId = 12345, postId = 67890),
            resolveForumLinkTarget("https://www.example.com/forum/forum.php?mod=redirect&goto=findpost&pid=67890&ptid=12345")
        )
        assertEquals(
            ForumLinkTarget.Thread(threadId = 12345, postId = 67890),
            resolveForumLinkTarget("/forum/thread-12345-2-1.html#pid67890")
        )
    }

    @Test
    fun `resolveForumLinkTarget routes thread links regardless of host`() {
        // 主站/镜像/任意未知域名:结构特征即站内凭证,tid 提取后归一化到当前站点
        assertEquals(
            ForumLinkTarget.Thread(456, null),
            resolveForumLinkTarget("https://www.example.com/forum/thread-456-1-1.html")
        )
        assertEquals(
            ForumLinkTarget.Thread(456, null),
            resolveForumLinkTarget("https://unscanned-mirror.example.org/thread-456-1-1.html")
        )
        assertEquals(
            ForumLinkTarget.Thread(456, null),
            resolveForumLinkTarget("https://mirror.example.net/forum/forum.php?mod=viewthread&tid=456")
        )
    }

    @Test
    fun `resolveForumLinkTarget routes board links`() {
        assertEquals(
            ForumLinkTarget.Board(36),
            resolveForumLinkTarget("https://www.example.com/forum/forumdisplay.php?fid=36")
        )
        assertEquals(
            ForumLinkTarget.Board(36),
            resolveForumLinkTarget("https://unscanned-mirror.example.org/forum-36-1.html")
        )
    }

    @Test
    fun `resolveForumLinkTarget routes image links by extension`() {
        assertEquals(
            ForumLinkTarget.Image("https://pics.example.net/data/attach/abc.jpg?token=1"),
            resolveForumLinkTarget("https://pics.example.net/data/attach/abc.jpg?token=1")
        )
        assertEquals(
            ForumLinkTarget.Image("https://x.example.com/a.PNG"),
            resolveForumLinkTarget("https://x.example.com/a.PNG")
        )
    }

    @Test
    fun `resolveForumLinkTarget falls back to web for unstructured urls`() {
        assertEquals(
            ForumLinkTarget.Web("https://blog.example.com/post/1"),
            resolveForumLinkTarget("https://blog.example.com/post/1")
        )
        assertEquals(ForumLinkTarget.Web(""), resolveForumLinkTarget(""))
    }
}
