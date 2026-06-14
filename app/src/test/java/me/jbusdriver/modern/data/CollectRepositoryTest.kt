package me.jbusdriver.modern.data

import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.db.convertDBItem
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CollectRepositoryTest {

    private lateinit var repository: FakeCollectRepository

    @Before
    fun setUp() {
        repository = FakeCollectRepository()
    }

    @Test
    fun toggleMovieCollect_addsWhenNotCollected() = runTest {
        val movie = Movie("Test", "http://img.jpg", "ABC-001", "2024-01-01", "http://link1")

        assertFalse(repository.isMovieCollected(movie))
        val result = repository.toggleMovieCollect(movie)
        assertTrue(result)
        assertTrue(repository.isMovieCollected(movie))
    }

    @Test
    fun toggleMovieCollect_removesWhenCollected() = runTest {
        val movie = Movie("Test", "http://img.jpg", "ABC-001", "2024-01-01", "http://link1")

        repository.toggleMovieCollect(movie) // add
        assertTrue(repository.isMovieCollected(movie))

        val result = repository.toggleMovieCollect(movie) // remove
        assertFalse(result)
        assertFalse(repository.isMovieCollected(movie))
    }

    @Test
    fun toggleActressCollect_addsWhenNotCollected() = runTest {
        val actress = ActressInfo("Alice", "http://avatar.jpg", "http://link1")

        assertFalse(repository.isActressCollected(actress))
        val result = repository.toggleActressCollect(actress)
        assertTrue(result)
        assertTrue(repository.isActressCollected(actress))
    }

    @Test
    fun toggleActressCollect_removesWhenCollected() = runTest {
        val actress = ActressInfo("Alice", "http://avatar.jpg", "http://link1")

        repository.toggleActressCollect(actress) // add
        assertTrue(repository.isActressCollected(actress))

        val result = repository.toggleActressCollect(actress) // remove
        assertFalse(result)
        assertFalse(repository.isActressCollected(actress))
    }

    @Test
    fun getCollectedMovies_returnsOnlyMovies() = runTest {
        val movie = Movie("Test", "http://img.jpg", "ABC-001", "2024-01-01", "http://link1")
        val actress = ActressInfo("Alice", "http://avatar.jpg", "http://link2")

        repository.toggleMovieCollect(movie)
        repository.toggleActressCollect(actress)

        val movies = repository.getCollectedMovies()
        assertEquals(1, movies.size)
        assertEquals("ABC-001", movies.first().code)
    }

    @Test
    fun getCollectedActresses_returnsOnlyActresses() = runTest {
        val movie = Movie("Test", "http://img.jpg", "ABC-001", "2024-01-01", "http://link1")
        val actress = ActressInfo("Alice", "http://avatar.jpg", "http://link2")

        repository.toggleMovieCollect(movie)
        repository.toggleActressCollect(actress)

        val actresses = repository.getCollectedActresses()
        assertEquals(1, actresses.size)
        assertEquals("Alice", actresses.first().name)
    }

    private class FakeCollectRepository : CollectRepository {
        private val collectedMovies = mutableMapOf<String, Movie>()
        private val collectedActresses = mutableMapOf<String, ActressInfo>()

        override suspend fun isCollected(linkItem: LinkItem) = false
        override suspend fun addCollect(linkItem: LinkItem) = true
        override suspend fun removeCollect(linkItem: LinkItem) = true

        override suspend fun isMovieCollected(movie: Movie) = movie.link in collectedMovies

        override suspend fun toggleMovieCollect(movie: Movie): Boolean {
            return if (movie.link in collectedMovies) {
                collectedMovies.remove(movie.link)
                false
            } else {
                collectedMovies[movie.link] = movie
                true
            }
        }

        override suspend fun isActressCollected(actress: ActressInfo) =
            actress.link in collectedActresses

        override suspend fun toggleActressCollect(actress: ActressInfo): Boolean {
            return if (actress.link in collectedActresses) {
                collectedActresses.remove(actress.link)
                false
            } else {
                collectedActresses[actress.link] = actress
                true
            }
        }

        override suspend fun getCollectedMovies() = collectedMovies.values.toList()

        override suspend fun getCollectedActresses() = collectedActresses.values.toList()

        override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
            when (dbType) {
                MovieDBType -> collectedMovies.values.map { it.convertDBItem() }
                ActressDBType -> collectedActresses.values.map { it.convertDBItem() }
                else -> emptyList()
            }

        override suspend fun exportCollectionsJson() = "{}"

        override suspend fun importCollectionsFromJson(json: String) = 0 to 0
    }
}
