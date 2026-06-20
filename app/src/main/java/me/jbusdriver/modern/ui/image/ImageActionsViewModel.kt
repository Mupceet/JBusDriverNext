package me.jbusdriver.modern.ui.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.data.gateway.ImageMediaGateway
import javax.inject.Inject

data class ImageActionMessage(
    val messageRes: Int,
    val formatArg: String? = null
)

@HiltViewModel
class ImageActionsViewModel @Inject constructor(
    private val imageMediaGateway: ImageMediaGateway
) : ViewModel() {
    private var dispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        imageMediaGateway: ImageMediaGateway,
        dispatcher: CoroutineDispatcher
    ) : this(imageMediaGateway) {
        this.dispatcher = dispatcher
    }

    private val _messages = MutableSharedFlow<ImageActionMessage>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages = _messages.asSharedFlow()

    fun saveImage(imageUrl: String) {
        viewModelScope.launch(dispatcher) {
            try {
                imageMediaGateway.saveImageToGallery(imageUrl)
                _messages.emit(ImageActionMessage(R.string.saved_to_gallery))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(
                    ImageActionMessage(
                        messageRes = R.string.save_failed_detail,
                        formatArg = e.message.orEmpty()
                    )
                )
            }
        }
    }

    fun shareImage(imageUrl: String) {
        viewModelScope.launch(dispatcher) {
            try {
                imageMediaGateway.shareImage(imageUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(
                    ImageActionMessage(
                        messageRes = R.string.share_failed_detail,
                        formatArg = e.message.orEmpty()
                    )
                )
            }
        }
    }
}
