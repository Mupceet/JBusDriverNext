package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.CollectRepository
import javax.inject.Inject

data class CollectionImportResult(val imported: Int, val skipped: Int)

@HiltViewModel
class CollectCategoryViewModel @Inject constructor(
    private val repository: CollectRepository
) : ViewModel() {
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _importResult = MutableSharedFlow<CollectionImportResult>()
    val importResult: SharedFlow<CollectionImportResult> = _importResult.asSharedFlow()

    suspend fun exportCollectionsJson(): String =
        repository.exportCollectionsJson()

    fun importCollectionsJson(json: String, onDone: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            runCatching { repository.importCollectionsFromJson(json) }
                .onSuccess { result ->
                    _importResult.emit(CollectionImportResult(result.first, result.second))
                    onDone()
                }
                .onFailure(onError)
            _isBusy.value = false
        }
    }
}
