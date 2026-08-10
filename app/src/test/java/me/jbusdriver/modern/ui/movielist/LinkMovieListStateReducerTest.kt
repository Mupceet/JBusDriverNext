package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MovieFilterInfo
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.ui.toUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkMovieListStateReducerTest {

    @Test
    fun firstPageCached_updatesResolvedTitleFromBreadcrumb() {
        val entry = cacheEntry(
            result = pageResult(
                "A",
                pageInfo = PageInfo(activePage = 1, nextPage = 2),
                filterInfo = MovieFilterInfo(
                    magnetCount = 4,
                    totalCount = 8,
                    breadcrumbName = "Julia",
                    breadcrumbType = "女優"
                )
            ),
            isExpired = true,
            storedAtMillis = 123L
        )

        val state = LinkMovieListUiState(
            isLoading = true,
            isFilterSwitching = true
        ).applyFirstPageCached(entry)

        assertEquals(listOf("A"), state.movies.map { it.code })
        assertEquals(ResolvedTitle.Actress("Julia"), state.resolvedTitle)
        assertFalse(state.isLoading)
        assertFalse(state.isFilterSwitching)
        assertTrue(state.hasMore)
        assertTrue(state.isRevalidating)
        assertEquals(123L, state.lastUpdatedAtMillis)
        assertNull(state.error)
    }

    @Test
    fun firstPageFresh_keepsExistingResolvedTitleWhenBreadcrumbMissing() {
        val existingTitle = ResolvedTitle.Genre("Drama")
        val entry = cacheEntry(
            result = pageResult("B"),
            storedAtMillis = 456L
        )

        val state = LinkMovieListUiState(
            isLoading = true,
            isFilterSwitching = true,
            isRevalidating = true,
            pendingFreshResult = pageResult("old"),
            refreshMessage = R.string.new_data_available,
            resolvedTitle = existingTitle
        ).applyFirstPageFresh(entry)

        assertEquals(listOf("B"), state.movies.map { it.code })
        assertEquals(existingTitle, state.resolvedTitle)
        assertFalse(state.isLoading)
        assertFalse(state.isFilterSwitching)
        assertFalse(state.isRevalidating)
        assertNull(state.pendingFreshResult)
        assertNull(state.refreshMessage)
        assertEquals(456L, state.lastUpdatedAtMillis)
    }

    @Test
    fun firstPageFailure_withoutContentShowsLoadError() {
        val state = LinkMovieListUiState(isLoading = true, isRevalidating = true)
            .applyFirstPageFailure(
                event = CachedLoadEvent.Failure(RuntimeException("boom"), hadCachedValue = false),
                hasContent = false
            )

        assertFalse(state.isLoading)
        assertFalse(state.isRevalidating)
        assertEquals(R.string.load_failed, state.error)
    }

    @Test
    fun firstPageFailure_withContentKeepsCurrentDataAndClearsLoadingFlags() {
        val current = LinkMovieListUiState(
            movies = listOf(movie("A").toUiModel()),
            isLoading = true,
            isFilterSwitching = true,
            isRevalidating = true
        )

        val state = current.applyFirstPageFailure(
            event = CachedLoadEvent.Failure(RuntimeException("boom"), hadCachedValue = true),
            hasContent = true
        )

        assertEquals(current.movies, state.movies)
        assertFalse(state.isLoading)
        assertFalse(state.isFilterSwitching)
        assertFalse(state.isRevalidating)
        assertNull(state.error)
    }

    @Test
    fun freshRevalidate_atTopAppliesImmediately() {
        val entry = cacheEntry(
            result = pageResult("C", pageInfo = PageInfo(activePage = 1, nextPage = 2)),
            storedAtMillis = 789L
        )

        val reduction = LinkMovieListUiState(
            movies = listOf(movie("A").toUiModel()),
            isRevalidating = true,
            pendingFreshResult = pageResult("old"),
            refreshMessage = R.string.new_data_available
        ).applyFreshRevalidate(entry, isAtTop = true)

        assertEquals(FreshRevalidateOutcome.ApplyImmediately, reduction.outcome)
        assertEquals(listOf("C"), reduction.state.movies.map { it.code })
        assertFalse(reduction.state.isRevalidating)
        assertNull(reduction.state.pendingFreshResult)
        assertNull(reduction.state.refreshMessage)
        assertEquals(789L, reduction.state.lastUpdatedAtMillis)
        assertTrue(reduction.state.hasMore)
    }

    @Test
    fun freshRevalidate_awayFromTopWithChangedFirstPageStoresPending() {
        val fresh = pageResult("C", pageInfo = PageInfo(activePage = 1, nextPage = 2))

        val reduction = LinkMovieListUiState(
            movies = listOf(movie("A").toUiModel()),
            isRevalidating = true
        ).applyFreshRevalidate(
            entry = cacheEntry(result = fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.StorePending, reduction.outcome)
        assertFalse(reduction.state.isRevalidating)
        assertEquals(fresh, reduction.state.pendingFreshResult)
        assertEquals(R.string.new_data_available, reduction.state.refreshMessage)
    }

    @Test
    fun freshRevalidate_awayFromTopWithUnchangedFirstPageClearsRevalidatingOnly() {
        val current = LinkMovieListUiState(
            movies = listOf(movie("A").toUiModel()),
            isRevalidating = true
        )

        val reduction = current.applyFreshRevalidate(
            entry = cacheEntry(result = pageResult("A")),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.NoChange, reduction.outcome)
        assertEquals(current.movies, reduction.state.movies)
        assertFalse(reduction.state.isRevalidating)
        assertNull(reduction.state.pendingFreshResult)
        assertNull(reduction.state.refreshMessage)
    }

    @Test
    fun freshRevalidate_awayFromTopWithOnlyTagUpdatesAppliesInPlace() {
        val current = LinkMovieListUiState(
            movies = listOf(
                movie("A", tags = listOf("old")).toUiModel(),
                movie("B", tags = listOf("old")).toUiModel(),
                movie("C", tags = listOf("old")).toUiModel()
            ),
            isRevalidating = true
        )
        val fresh = MoviePageResult(
            pageInfo = PageInfo(activePage = 1, nextPage = 2),
            movies = listOf(
                movie("A", tags = listOf("new")),
                movie("B", tags = listOf("new"))
            )
        )

        val reduction = current.applyFreshRevalidate(
            entry = cacheEntry(result = fresh, storedAtMillis = 999L),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.ApplyInPlace, reduction.outcome)
        assertEquals(
            listOf("new", "new", "old"),
            reduction.state.movies.map { it.tags.single() }
        )
        assertEquals(listOf("A", "B", "C"), reduction.state.movies.map { it.code })
        assertFalse(reduction.state.isRevalidating)
        assertNull(reduction.state.pendingFreshResult)
        assertNull(reduction.state.refreshMessage)
        assertEquals(999L, reduction.state.lastUpdatedAtMillis)
    }

    @Test
    fun freshRevalidate_awayFromTopWithTitleChangeStillPrompts() {
        val current = LinkMovieListUiState(
            movies = listOf(movie("A").toUiModel(), movie("B").toUiModel()),
            isRevalidating = true
        )
        val fresh = MoviePageResult(
            pageInfo = PageInfo(activePage = 1, nextPage = 2),
            movies = listOf(movie("A").copy(title = "New Title A"), movie("B"))
        )

        val reduction = current.applyFreshRevalidate(
            entry = cacheEntry(result = fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.StorePending, reduction.outcome)
        assertEquals(current.movies, reduction.state.movies)
        assertEquals(fresh, reduction.state.pendingFreshResult)
        assertEquals(R.string.new_data_available, reduction.state.refreshMessage)
    }

    private fun cacheEntry(
        result: MoviePageResult,
        isExpired: Boolean = false,
        storedAtMillis: Long = 1L
    ) = CacheEntry(result, storedAtMillis, CacheSource.Memory, isExpired)

    private fun pageResult(
        code: String,
        pageInfo: PageInfo = PageInfo(activePage = 1, nextPage = 1),
        filterInfo: MovieFilterInfo? = null
    ) = MoviePageResult(
        pageInfo = pageInfo,
        movies = listOf(movie(code)),
        filterInfo = filterInfo
    )

    private fun movie(code: String, tags: List<String> = listOf("tag")) = Movie(
        title = "Title $code",
        imageUrl = "https://image.test/$code.jpg",
        code = code,
        date = "2026-06-18",
        link = "https://movie.test/$code",
        tags = tags
    )
}
