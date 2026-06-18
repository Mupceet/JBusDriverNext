package me.jbusdriver.modern.ui.movielist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.data.CollectionDocumentGateway
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.test.StubCollectRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

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
    fun exportCollectionsWritesRepositoryJsonToDocument() = runTest(dispatcher) {
        val gateway = FakeCollectionDocumentGateway()
        val repository = FakeCollectRepository(exportJson = """{"version":1}""")
        val viewModel = CollectCategoryViewModel(repository, gateway, dispatcher)
        var error: Throwable? = null

        viewModel.exportCollectionsToDocument(
            documentUri = "content://backup",
            onDone = {},
            onError = { error = it }
        )
        advanceUntilIdle()

        assertNull(error)
        assertEquals("""{"version":1}""", gateway.writes["content://backup"])
    }

    @Test
    fun importCollectionsReadsDocumentAndImportsJson() = runTest(dispatcher) {
        val gateway = FakeCollectionDocumentGateway(reads = mutableMapOf("content://backup" to """{"movies":[]}"""))
        val repository = FakeCollectRepository(importResult = 2 to 1)
        val viewModel = CollectCategoryViewModel(repository, gateway, dispatcher)
        var done = false
        var error: Throwable? = null

        viewModel.importCollectionsFromDocument(
            documentUri = "content://backup",
            onDone = { done = true },
            onError = { error = it }
        )
        advanceUntilIdle()

        assertNull(error)
        assertEquals("""{"movies":[]}""", repository.importedJson)
        assertEquals(true, done)
    }

    private class FakeCollectionDocumentGateway(
        val reads: MutableMap<String, String> = mutableMapOf()
    ) : CollectionDocumentGateway {
        val writes = mutableMapOf<String, String>()

        override suspend fun readText(documentUri: String): String? = reads[documentUri]

        override suspend fun writeText(documentUri: String, text: String) {
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
