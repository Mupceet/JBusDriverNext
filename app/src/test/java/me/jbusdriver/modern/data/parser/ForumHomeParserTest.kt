package me.jbusdriver.modern.data.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class ForumHomeParserTest {

    @Test
    fun `board lastPost time covers titled, nested-titled, and plain spans`() {
        // 真实板块页 cite 有三种结构：
        //  A 直接带 title:  <cite><span title="...">文本</span> <a>作者</a></cite>
        //  B 嵌套带 title:  <cite><span>\t<span title="...">文本</span></span> <a>作者</a></cite>
        //  C 旧版叶子 span: <cite><span>\t2026-06-12</span> <a>作者</a></cite>   (span 无 title)
        // 取时间须：A/B 取带 title 的 span 文本（嵌套时只取内层，避免外层+内层文本拼接重复），
        // C 在没有 title span 时回退到任意 span 文本。回退用 ifBlank（空串非 null，?: 不触发）。
        val doc = Jsoup.parse(
            """
                <div class="fl bm">
                  <div class="bm bmw cl">
                    <div class="bm_h cl"><h2><a>Group</a></h2></div>
                    <div id="category_1">
                      <table class="fl_tb"><tbody>
                        <tr>
                          <td><h2><a href="forum.php?mod=forumdisplay&amp;fid=2">Board A</a></h2></td>
                          <td class="fl_by"><div class="forumlist">
                            <a href="forum.php?mod=viewthread&amp;tid=1" class="xi2">Titled thread</a>
                            <cite><span title="2026-6-20 00:49">2026-6-20</span> <a>authorA</a></cite>
                          </div></td>
                        </tr>
                        <tr>
                          <td><h2><a href="forum.php?mod=forumdisplay&amp;fid=3">Board B</a></h2></td>
                          <td class="fl_by"><div class="forumlist">
                            <a href="forum.php?mod=viewthread&amp;tid=2" class="xi2">Nested thread</a>
                            <cite><span>	<span title="2026-06-20">半小時前</span></span> <a>authorB</a></cite>
                          </div></td>
                        </tr>
                        <tr>
                          <td><h2><a href="forum.php?mod=forumdisplay&amp;fid=4">Board C</a></h2></td>
                          <td class="fl_by"><div class="forumlist">
                            <a href="forum.php?mod=viewthread&amp;tid=3" title="Legacy thread">Legacy thread</a>
                            <cite><span>	2026-06-12</span> <a>killtime000</a></cite>
                          </div></td>
                        </tr>
                      </tbody></table>
                    </div>
                  </div>
                </div>
            """.trimIndent(),
            "https://www.javbus.com/forum/"
        )

        val boards = parseForumBoards(doc).single().boards

        assertEquals("2026-6-20", boards[0].lastPost.time)
        assertEquals("半小時前", boards[1].lastPost.time)   // 嵌套 span 不能重复成 "半小時前半小時前"
        assertEquals("2026-06-12", boards[2].lastPost.time)
        assertEquals("killtime000", boards[2].lastPost.author)
        assertEquals("Legacy thread", boards[2].lastPost.title)
    }
}
