package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.modern.domain.model.ActressDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkMovieListActressHeaderStateTest {

    @Test
    fun startLoading_clearsErrorAndKeepsLoadedDetail() {
        val existing = ActressHeaderState(
            detail = sampleDetailUiModel,
            isLoading = false,
            error = "old error",
            isCollected = true
        )

        val state = existing.startLoading()

        assertEquals(sampleDetailUiModel, state.detail)
        assertTrue(state.isLoading)
        assertNull(state.error)
        assertTrue(state.isCollected)
    }

    @Test
    fun applyLoaded_mapsDetailAndStopsLoading() {
        val state = ActressHeaderState(isLoading = true, error = "old")
            .applyLoaded(sampleDetail, isCollected = true)

        assertEquals("Alice", state.detail?.name)
        assertEquals("https://img.example/alice.jpg", state.detail?.avatar)
        assertEquals(listOf("Height: 160"), state.detail?.info)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertTrue(state.isCollected)
    }

    @Test
    fun finishWithoutDetail_stopsLoadingWithoutCreatingError() {
        val state = ActressHeaderState(isLoading = true).finishWithoutDetail()

        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun finishWithError_stopsLoadingAndStoresMessage() {
        val state = ActressHeaderState(isLoading = true).finishWithError("network failed")

        assertFalse(state.isLoading)
        assertEquals("network failed", state.error)
    }

    @Test
    fun withCollected_updatesOnlyCollectionState() {
        val existing = ActressHeaderState(
            detail = sampleDetailUiModel,
            isLoading = false,
            error = null,
            isCollected = false
        )

        val state = existing.withCollected(true)

        assertEquals(sampleDetailUiModel, state.detail)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertTrue(state.isCollected)
    }

    private companion object {
        val sampleDetail = ActressDetail(
            name = "Alice",
            avatar = "https://img.example/alice.jpg",
            info = listOf("Height: 160")
        )
        val sampleDetailUiModel = sampleDetail.toActressDetailUiModel()
    }
}
