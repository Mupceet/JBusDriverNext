package me.jbusdriver.modern.data.repository

import me.jbusdriver.modern.domain.model.DataSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class MovieRepositoryCacheKeysTest {
    @Test
    fun `movie and actress page keys include normalized site type and page`() {
        assertEquals(
            "movie-有碼:https://example.test:true_2",
            MovieRepositoryCacheKeys.moviePage("https://example.test/", DataSourceType.CENSORED, true, 2)
        )
        assertEquals(
            "actresses-無碼女優:https://example.test:3",
            MovieRepositoryCacheKeys.actressPage("https://example.test/", DataSourceType.UNCENSORED_ACTRESSES, 3)
        )
    }

    @Test
    fun `genre and actress detail keys include stable identities`() {
        assertEquals(
            "genres-v2:https://example.test:無碼類別",
            MovieRepositoryCacheKeys.genreCategories("https://example.test/", DataSourceType.UNCENSORED_GENRE)
        )
        assertEquals(
            "actress-detail:https://example.test:/star/alice",
            MovieRepositoryCacheKeys.actressDetail("https://example.test/", "https://example.test/star/alice")
        )
    }

    @Test
    fun `page by url key strips resolved url to path`() {
        assertEquals(
            "page:https://example.test:/genre/hd_false_5",
            MovieRepositoryCacheKeys.pageByUrl("https://example.test/", "https://example.test/genre/hd", false, 5)
        )
    }
}
