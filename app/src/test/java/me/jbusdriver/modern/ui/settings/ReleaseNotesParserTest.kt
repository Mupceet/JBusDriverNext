package me.jbusdriver.modern.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesParserTest {

    // region parseReleaseNotes

    @Test
    fun parseReleaseNotes_classifiesLineKinds() {
        val blocks = parseReleaseNotes(
            """
            # 更新日志

            ## 本次更新

            * 新增：影片详情标题一键翻译
            - 修复列表刷新问题
            ----
            老司机？快开车！
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ReleaseNoteBlock.Heading(1, listOf(NoteSegment("更新日志"))),
                ReleaseNoteBlock.Heading(2, listOf(NoteSegment("本次更新"))),
                ReleaseNoteBlock.Bullet(listOf(NoteSegment("新增：影片详情标题一键翻译"))),
                ReleaseNoteBlock.Bullet(listOf(NoteSegment("修复列表刷新问题"))),
                ReleaseNoteBlock.Divider,
                ReleaseNoteBlock.Paragraph(listOf(NoteSegment("老司机？快开车！"))),
            ),
            blocks,
        )
    }

    @Test
    fun parseReleaseNotes_dropsBlankLinesAndHandlesIndentation() {
        val blocks = parseReleaseNotes("\n  * 缩进列表行  \n\n\n普通行\n")

        assertEquals(
            listOf(
                ReleaseNoteBlock.Bullet(listOf(NoteSegment("缩进列表行"))),
                ReleaseNoteBlock.Paragraph(listOf(NoteSegment("普通行"))),
            ),
            blocks,
        )
    }

    @Test
    fun parseReleaseNotes_boldIntroLineIsParagraphNotBullet() {
        val blocks = parseReleaseNotes("**加粗开头**：没有列表标记")

        assertEquals(
            listOf(
                ReleaseNoteBlock.Paragraph(
                    listOf(NoteSegment("加粗开头", bold = true), NoteSegment("：没有列表标记"))
                )
            ),
            blocks,
        )
    }

    // endregion

    // region parseNoteInline

    @Test
    fun parseNoteInline_plainTextPassesThrough() {
        assertEquals(listOf(NoteSegment("普通文本")), parseNoteInline("普通文本"))
        assertEquals(emptyList<NoteSegment>(), parseNoteInline(""))
    }

    @Test
    fun parseNoteInline_extractsBoldAndLink() {
        assertEquals(
            listOf(
                NoteSegment("前缀 "),
                NoteSegment("新功能", bold = true),
                NoteSegment("，见 "),
                NoteSegment("发布页", url = "https://example.com/release"),
                NoteSegment(" 后缀"),
            ),
            parseNoteInline("前缀 **新功能**，见 [发布页](https://example.com/release) 后缀"),
        )
    }

    @Test
    fun parseNoteInline_linkWithEmptyUrlFallsBackToPlain() {
        assertEquals(listOf(NoteSegment("文字")), parseNoteInline("[文字]()"))
    }

    @Test
    fun parseNoteInline_unclosedSyntaxStaysLiteral() {
        assertEquals(listOf(NoteSegment("**未闭合")), parseNoteInline("**未闭合"))
        assertEquals(listOf(NoteSegment("a [b](c 残缺")), parseNoteInline("a [b](c 残缺"))
    }

    // endregion
}
