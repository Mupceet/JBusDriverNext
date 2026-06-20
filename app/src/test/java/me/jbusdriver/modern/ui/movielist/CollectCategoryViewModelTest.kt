package me.jbusdriver.modern.ui.movielist

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.data.gateway.CollectionDocumentGateway
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.test.StubCollectRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CollectCategoryViewModelTest {
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
    fun exportCollectionsWritesRepositoryJsonAndEmitsSuccess() = runTest(dispatcher) {
        val gateway = FakeCollectionDocumentGateway()
        val repository = FakeCollectRepository(exportJson = """{"version":1}""")
        val viewModel = CollectCategoryViewModel(repository, gateway, dispatcher)
        val events = mutableListOf<CollectActionEvent>()
        val collectJob = launch { viewModel.events.toList(events) }

        viewModel.exportCollectionsToDocument("content://backup")
        advanceUntilIdle()
        collectJob.cancel()

        assertEquals(listOf(CollectActionEvent.ExportSuccess), events)
        assertEquals("""{"version":1}""", gateway.writes["content://backup"])
    }

    @Test
    fun importCollectionsReadsDocumentAndEmitsImportSuccess() = runTest(dispatcher) {
        val gateway = FakeCollectionDocumentGateway(
            reads = mutableMapOf("content://backup" to """{"movies":[]}""")
        )
        val repository = FakeCollectRepository(importResult = 2 to 1)
        val viewModel = CollectCategoryViewModel(repository, gateway, dispatcher)
        val events = mutableListOf<CollectActionEvent>()
        val collectJob = launch { viewModel.events.toList(events) }

        viewModel.importCollectionsFromDocument("content://backup")
        advanceUntilIdle()
        collectJob.cancel()

        assertEquals(CollectActionEvent.ImportSuccess(2, 1), events.single())
        assertEquals("""{"movies":[]}""", repository.importedJson)
    }

    @Test
    fun importEmitsActionFailedWhenDocumentUnreadable() = runTest(dispatcher) {
        // readText 返回 null → VM 抛 IllegalStateException → ActionFailed(isImport=true)
        val gateway = FakeCollectionDocumentGateway()
        val repository = FakeCollectRepository()
        val viewModel = CollectCategoryViewModel(repository, gateway, dispatcher)
        val events = mutableListOf<CollectActionEvent>()
        val collectJob = launch { viewModel.events.toList(events) }

        viewModel.importCollectionsFromDocument("content://missing")
        advanceUntilIdle()
        collectJob.cancel()

        val failed = events.filterIsInstance<CollectActionEvent.ActionFailed>().single()
        assertTrue(failed.isImport)
        assertTrue(failed.throwable is IllegalStateException)
    }

    @Test
    fun exportEmitsActionFailedWhenWriteThrows() = runTest(dispatcher) {
        val gateway = FakeCollectionDocumentGateway(writeFailure = IOException("disk full"))
        val repository = FakeCollectRepository(exportJson = "{}")
        val viewModel = CollectCategoryViewModel(repository, gateway, dispatcher)
        val events = mutableListOf<CollectActionEvent>()
        val collectJob = launch { viewModel.events.toList(events) }

        viewModel.exportCollectionsToDocument("content://backup")
        advanceUntilIdle()
        collectJob.cancel()

        val failed = events.filterIsInstance<CollectActionEvent.ActionFailed>().single()
        assertTrue(!failed.isImport)
        assertEquals("disk full", failed.throwable.message)
    }

    @Test
    fun exportRethrowsCancellationWithoutActionFailedEvent() = runTest(dispatcher) {
        val gateway = FakeCollectionDocumentGateway(writeFailure = CancellationException("cancelled"))
        val repository = FakeCollectRepository(exportJson = "{}")
        val viewModel = CollectCategoryViewModel(repository, gateway, dispatcher)
        val events = mutableListOf<CollectActionEvent>()
        val collectJob = launch { viewModel.events.toList(events) }

        viewModel.exportCollectionsToDocument("content://backup")
        advanceUntilIdle()
        collectJob.cancel()

        assertEquals(emptyList<CollectActionEvent>(), events)
    }

    private class FakeCollectionDocumentGateway(
        val reads: MutableMap<String, String> = mutableMapOf(),
        private val writeFailure: Throwable? = null
    ) : CollectionDocumentGateway {
        val writes = mutableMapOf<String, String>()

        override suspend fun readText(documentUri: String): String? = reads[documentUri]

        override suspend fun writeText(documentUri: String, text: String) {
            writeFailure?.let { throw it }
            writes[documentUri] = text
        }
    }

    private class FakeCollectRepository(
        private val exportJson: String = "{}",
        private val importResult: Pair<Int, Int> = 0 to 0
    ) : StubCollectRepository() {
        var importedJson: String? = null

        override suspend fun exportCollectionsJson(): String = exportJson

        override suspend fun importCollectionsFromJson(json: String): Pair<Int, Int> {
            importedJson = json
            return importResult
        }

        override suspend fun getCollectedMovies(): List<Movie> = emptyList()

        override suspend fun getCollectedActresses(): List<ActressInfo> = emptyList()
    }
}
