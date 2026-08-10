package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.jbusdriver.modern.core.coroutine.IoDispatcher
import me.jbusdriver.modern.data.gateway.CollectionDocumentGateway
import me.jbusdriver.modern.data.repository.CollectRepository
import javax.inject.Inject

/**
 * 收藏导入/导出的动作结果事件。
 *
 * ViewModel 只负责发事件（在任意协程上），UI 在主线程 collect 后处理 Toast / 刷新列表，
 * 避免 ViewModel 关心 UI 线程或回调时序。
 */
sealed interface CollectActionEvent {
    data object ExportSuccess : CollectActionEvent
    data class ImportSuccess(val imported: Int, val skipped: Int) : CollectActionEvent
    data class ActionFailed(val throwable: Throwable, val isImport: Boolean) : CollectActionEvent
}

@HiltViewModel
class CollectCategoryViewModel @Inject constructor(
    private val repository: CollectRepository,
    private val documentGateway: CollectionDocumentGateway,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = Channel<CollectActionEvent>(Channel.BUFFERED)
    val events: Flow<CollectActionEvent> = _events.receiveAsFlow()

    fun exportCollectionsToDocument(documentUri: String) {
        viewModelScope.launch(ioDispatcher) {
            _isBusy.value = true
            try {
                documentGateway.writeText(documentUri, repository.exportCollectionsJson())
                _events.send(CollectActionEvent.ExportSuccess)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _events.send(CollectActionEvent.ActionFailed(e, isImport = false))
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun importCollectionsFromDocument(documentUri: String) {
        viewModelScope.launch(ioDispatcher) {
            _isBusy.value = true
            try {
                val json = documentGateway.readText(documentUri)
                    ?: throw IllegalStateException("Unable to read selected document")
                val result = repository.importCollectionsFromJson(json)
                _events.send(CollectActionEvent.ImportSuccess(result.first, result.second))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _events.send(CollectActionEvent.ActionFailed(e, isImport = true))
            } finally {
                _isBusy.value = false
            }
        }
    }
}
