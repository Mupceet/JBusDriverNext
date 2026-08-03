package me.jbusdriver.modern.ui.forum

import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.RichParagraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupFirstPostBlocksTest {

    private fun text() = ContentBlock.RichText(listOf(RichParagraph(emptyList())))

    private fun image(url: String, isGif: Boolean = false, isFullSize: Boolean = true) =
        ContentBlock.Image(url = url, isFullSize = isFullSize, isGif = isGif)

    @Test
    fun emptyBlocks_returnsEmpty() {
        val (sections, urls) = groupFirstPostBlocks(
            emptyList(), autoLoadGifs = false, loadedGifUrls = emptySet()
        )
        assertTrue(sections.isEmpty())
        assertTrue(urls.isEmpty())
    }

    @Test
    fun onlyText_collapsesIntoSingleSection() {
        val blocks = listOf(text(), text(), text())
        val (sections, urls) = groupFirstPostBlocks(
            blocks, autoLoadGifs = false, loadedGifUrls = emptySet()
        )
        assertEquals(1, sections.size)
        val textSection = sections[0] as FirstPostSection.Text
        assertEquals(0, textSection.startBlockIndex)
        assertEquals(3, textSection.blocks.size)
        assertTrue(urls.isEmpty())
    }

    @Test
    fun onlyImages_eachBecomesViewableSection() {
        val blocks = listOf(image("a"), image("b"), image("c"))
        val (sections, urls) = groupFirstPostBlocks(
            blocks, autoLoadGifs = false, loadedGifUrls = emptySet()
        )
        assertEquals(3, sections.size)
        assertEquals(listOf("a", "b", "c"), urls)
        sections.forEachIndexed { i, s ->
            val img = s as FirstPostSection.ImageBlock
            assertEquals(i, img.viewableIndex)
            assertEquals(i, img.startBlockIndex)
        }
    }

    @Test
    fun textAndImagesInterleaved_splitTextSectionsAndKeepBlockIndices() {
        val blocks = listOf(text(), image("a"), text(), image("b"))
        val (sections, urls) = groupFirstPostBlocks(
            blocks, autoLoadGifs = false, loadedGifUrls = emptySet()
        )
        // Text(0), ImageBlock(1, viewable 0), Text(2), ImageBlock(3, viewable 1)
        assertEquals(4, sections.size)
        assertEquals(listOf("a", "b"), urls)

        val s0 = sections[0] as FirstPostSection.Text
        assertEquals(0, s0.startBlockIndex)
        assertEquals(1, s0.blocks.size)

        val s1 = sections[1] as FirstPostSection.ImageBlock
        assertEquals(1, s1.startBlockIndex)
        assertEquals(0, s1.viewableIndex)

        val s2 = sections[2] as FirstPostSection.Text
        assertEquals(2, s2.startBlockIndex)
        assertEquals(1, s2.blocks.size)

        val s3 = sections[3] as FirstPostSection.ImageBlock
        assertEquals(3, s3.startBlockIndex)
        assertEquals(1, s3.viewableIndex)
    }

    @Test
    fun unloadedGif_isPlaceholderAndExcludedFromViewable() {
        val blocks = listOf(image("g1", isGif = true), image("normal"))
        val (sections, urls) = groupFirstPostBlocks(
            blocks, autoLoadGifs = false, loadedGifUrls = emptySet()
        )
        // GIF 未加载 → viewableIndex -1 且不在 urls;普通图占用 viewableIndex 0
        val s0 = sections[0] as FirstPostSection.ImageBlock
        assertEquals(-1, s0.viewableIndex)
        val s1 = sections[1] as FirstPostSection.ImageBlock
        assertEquals(0, s1.viewableIndex)
        assertEquals(listOf("normal"), urls)
    }

    @Test
    fun loadedGif_becomesViewableAndKeepsOrder() {
        val blocks = listOf(image("g1", isGif = true), image("normal"))
        val (sections, urls) = groupFirstPostBlocks(
            blocks, autoLoadGifs = false, loadedGifUrls = setOf("g1")
        )
        val s0 = sections[0] as FirstPostSection.ImageBlock
        assertEquals(0, s0.viewableIndex)
        val s1 = sections[1] as FirstPostSection.ImageBlock
        assertEquals(1, s1.viewableIndex)
        assertEquals(listOf("g1", "normal"), urls)
    }

    @Test
    fun autoLoadGifs_makesAllGifsViewable() {
        val blocks = listOf(image("g1", isGif = true), image("g2", isGif = true))
        val (sections, urls) = groupFirstPostBlocks(
            blocks, autoLoadGifs = true, loadedGifUrls = emptySet()
        )
        assertEquals(listOf("g1", "g2"), urls)
        sections.forEachIndexed { i, s ->
            assertEquals(i, (s as FirstPostSection.ImageBlock).viewableIndex)
        }
    }

    @Test
    fun trailingTextIsFlushedAsSection() {
        val blocks = listOf(image("a"), text(), text())
        val (sections, urls) = groupFirstPostBlocks(
            blocks, autoLoadGifs = false, loadedGifUrls = emptySet()
        )
        // ImageBlock(0, viewable 0), Text(1, [text, text])
        assertEquals(2, sections.size)
        assertEquals(listOf("a"), urls)
        val s1 = sections[1] as FirstPostSection.Text
        assertEquals(1, s1.startBlockIndex)
        assertEquals(2, s1.blocks.size)
    }
}
