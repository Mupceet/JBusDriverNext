package me.jbusdriver.modern.data.db

import me.jbusdriver.modern.data.db.entity.History
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Genre
import me.jbusdriver.modern.domain.model.Header
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.PageLink
import me.jbusdriver.modern.domain.model.SearchLink
import me.jbusdriver.modern.domain.model.SearchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkMappersTest {
    @Test
    fun `movie conversion strips urls then restores them with base url`() {
        val movie = Movie(
            title = "Movie",
            imageUrl = "https://example.test/images/cover.jpg",
            code = "ABC-001",
            date = "2026-06-19",
            link = "https://example.test/movies/ABC-001"
        )

        val item = movie.convertDBItem(categoryId = 99)
        val restored = item.toILink("https://mirror.test") as Movie

        assertEquals(MovieDBType, item.dbType)
        assertEquals("/movies/ABC-001", item.key)
        assertTrue(item.jsonStr.contains(""""imageUrl":"/images/cover.jpg""""))
        assertEquals(99, item.categoryId)
        assertEquals("https://mirror.test/movies/ABC-001", restored.link)
        assertEquals("https://mirror.test/images/cover.jpg", restored.imageUrl)
    }

    @Test
    fun `actress conversion strips and restores avatar and link`() {
        val actress = ActressInfo(
            name = "Alice",
            avatar = "https://example.test/avatar/alice.jpg",
            link = "https://example.test/star/alice"
        )

        val item = actress.convertDBItem()
        val restored = item.toILink("https://mirror.test") as ActressInfo

        assertEquals(ActressDBType, item.dbType)
        assertEquals("/star/alice", item.key)
        assertEquals(2, item.categoryId)
        assertEquals("https://mirror.test/star/alice", restored.link)
        assertEquals("https://mirror.test/avatar/alice.jpg", restored.avatar)
    }

    @Test
    fun `header and genre conversion use link category and restore links`() {
        val headerItem = Header("製作商", "Studio", "https://example.test/studio/one").convertDBItem()
        val genreItem = Genre("高清", "https://example.test/genre/hd").convertDBItem()

        assertEquals(HeaderDBType, headerItem.dbType)
        assertEquals(GenreDBType, genreItem.dbType)
        assertEquals(10, headerItem.categoryId)
        assertEquals(10, genreItem.categoryId)
        assertEquals("https://mirror.test/studio/one", (headerItem.toILink("https://mirror.test") as Header).link)
        assertEquals("https://mirror.test/genre/hd", (genreItem.toILink("https://mirror.test") as Genre).link)
    }

    @Test
    fun `search link unique key is query while page link uses path`() {
        val searchItem = SearchLink(SearchType.CENSORED, "abc", "https://example.test").convertDBItem()
        val pageItem = PageLink(3, "高清", "https://example.test/genre/hd/3").convertDBItem()

        assertEquals(SearchLinkDBType, searchItem.dbType)
        assertEquals("abc", searchItem.key)
        assertEquals(PageLinkDBType, pageItem.dbType)
        assertEquals("/genre/hd/3", pageItem.key)
    }

    @Test
    fun `invalid link item returns null instead of throwing`() {
        val item = LinkItem(dbType = MovieDBType, key = "/bad", jsonStr = "{")

        assertNull(item.toILink("https://example.test"))
    }

    @Test
    fun `invalid history item returns null instead of throwing`() {
        val history = History(dbType = MovieDBType, jsonStr = "{", isAll = 0)

        assertNull(history.toILinkOrNull())
    }
}
