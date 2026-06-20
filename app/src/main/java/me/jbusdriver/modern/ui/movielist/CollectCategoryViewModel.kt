package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.gateway.CollectionDocumentGateway
import javax.inject.Inject

data class CollectionImportResult(val imported: Int, val skipped: Int)

@HiltViewModel
class CollectCategoryViewModel @Inject constructor(
    private val repository: CollectRepository,
    private val documentGateway: CollectionDocumentGateway
) : ViewModel() {
    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        repository: CollectRepository,
        documentGateway: CollectionDocumentGateway,
        ioDispatcher: CoroutineDispatcher
    ) : this(repository, documentGateway) {
        this.ioDispatcher = ioDispatcher
    }

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _importResult = MutableSharedFlow<CollectionImportResult>()
    val importResult: SharedFlow<CollectionImportResult> = _importResult.asSharedFlow()

    fun exportCollectionsToDocument(
        documentUri: String,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch(ioDispatcher) {
            _isBusy.value = true
            val result = runCatching {
                documentGateway.writeText(documentUri, repository.exportCollectionsJson())
            }
            _isBusy.value = false
            // 回调（UI 层弹 Toast）必须在主线程执行；IO 线程上调 Toast.makeText 会抛
            // "Can't toast on a thread that has not called Looper.prepare()"
            withContext(Dispatchers.Main) {
                result.onSuccess { onDone() }.onFailure(onError)
            }
        }
    }

    fun importCollectionsFromDocument(
        documentUri: String,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch(ioDispatcher) {
            _isBusy.value = true
            val result = runCatching {
                val json = documentGateway.readText(documentUri)
                    ?: throw IllegalStateException("Unable to read selected document")
                repository.importCollectionsFromJson(json)
            }
            _isBusy.value = false
            withContext(Dispatchers.Main) {
                result
                    .onSuccess { pair ->
                        _importResult.emit(CollectionImportResult(pair.first, pair.second))
                        onDone()
                    }
                    .onFailure(onError)
            }
        }
    }

}
