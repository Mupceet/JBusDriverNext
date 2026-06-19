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
            runCatching {
                documentGateway.writeText(documentUri, repository.exportCollectionsJson())
            }
                .onSuccess { onDone() }
                .onFailure(onError)
            _isBusy.value = false
        }
    }

    fun importCollectionsFromDocument(
        documentUri: String,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch(ioDispatcher) {
            _isBusy.value = true
            runCatching {
                val json = documentGateway.readText(documentUri)
                    ?: throw IllegalStateException("Unable to read selected document")
                repository.importCollectionsFromJson(json)
            }
                .onSuccess { result ->
                    _importResult.emit(CollectionImportResult(result.first, result.second))
                    onDone()
                }
                .onFailure(onError)
            _isBusy.value = false
        }
    }

}
