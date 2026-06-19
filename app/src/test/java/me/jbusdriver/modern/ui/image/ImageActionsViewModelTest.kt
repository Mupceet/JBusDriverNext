package me.jbusdriver.modern.ui.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.data.gateway.ImageMediaGateway
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageActionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveImageDelegatesToGatewayAndPublishesSuccessMessage() = runTest(dispatcher) {
        val gateway = FakeImageMediaGateway()
        val viewModel = ImageActionsViewModel(gateway, dispatcher)
        val messages = mutableListOf<ImageActionMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.saveImage("https://image.test/1.jpg")
        advanceUntilIdle()

        assertEquals(listOf("https://image.test/1.jpg"), gateway.saved)
        assertEquals(R.string.saved_to_gallery, messages.single().messageRes)
        job.cancel()
    }

    @Test
    fun shareImageDelegatesToGatewayWithoutSuccessMessage() = runTest(dispatcher) {
        val gateway = FakeImageMediaGateway()
        val viewModel = ImageActionsViewModel(gateway, dispatcher)
        val messages = mutableListOf<ImageActionMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.shareImage("https://image.test/1.jpg")
        advanceUntilIdle()

        assertEquals(listOf("https://image.test/1.jpg"), gateway.shared)
        assertEquals(emptyList<ImageActionMessage>(), messages)
        job.cancel()
    }

    @Test
    fun saveImagePublishesFailureMessage() = runTest(dispatcher) {
        val gateway = FakeImageMediaGateway(saveFailure = IllegalStateException("disk full"))
        val viewModel = ImageActionsViewModel(gateway, dispatcher)
        val messages = mutableListOf<ImageActionMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.saveImage("https://image.test/1.jpg")
        advanceUntilIdle()

        assertEquals(R.string.save_failed_detail, messages.single().messageRes)
        assertEquals("disk full", messages.single().formatArg)
        job.cancel()
    }

    private class FakeImageMediaGateway(
        private val saveFailure: Throwable? = null,
        private val shareFailure: Throwable? = null
    ) : ImageMediaGateway {
        val saved = mutableListOf<String>()
        val shared = mutableListOf<String>()

        override suspend fun saveImageToGallery(imageUrl: String) {
            saveFailure?.let { throw it }
            saved += imageUrl
        }

        override suspend fun shareImage(imageUrl: String) {
            shareFailure?.let { throw it }
            shared += imageUrl
        }
    }
}
