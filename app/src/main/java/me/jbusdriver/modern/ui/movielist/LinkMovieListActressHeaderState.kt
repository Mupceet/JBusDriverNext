package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.modern.domain.model.ActressDetail
import me.jbusdriver.modern.ui.ActressDetailUiModel

data class ActressHeaderState(
    val detail: ActressDetailUiModel? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCollected: Boolean = false
)

internal fun ActressHeaderState.startLoading(): ActressHeaderState =
    copy(isLoading = true, error = null)

internal fun ActressHeaderState.applyLoaded(
    detail: ActressDetail,
    isCollected: Boolean = this.isCollected
): ActressHeaderState =
    copy(
        detail = detail.toActressDetailUiModel(),
        isLoading = false,
        error = null,
        isCollected = isCollected
    )

internal fun ActressHeaderState.finishWithoutDetail(): ActressHeaderState =
    copy(isLoading = false)

internal fun ActressHeaderState.finishWithError(message: String? = null): ActressHeaderState =
    copy(isLoading = false, error = message)

internal fun ActressHeaderState.withCollected(isCollected: Boolean): ActressHeaderState =
    copy(isCollected = isCollected)

internal fun ActressDetail.toActressDetailUiModel(): ActressDetailUiModel =
    ActressDetailUiModel(name, avatar, info)
