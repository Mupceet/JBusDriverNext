package me.jbusdriver.modern.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.jbusdriver.R

@Composable
fun ScrollToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(),
        label = "fab-progress"
    )

    if (progress > 0.01f) {
        SmallFloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .graphicsLayer {
                    scaleX = progress
                    scaleY = progress
                    alpha = progress
                }
        ) {
            Icon(
                painter = painterResource(R.drawable.keyboard_double_arrow_up_24px),
                contentDescription = stringResource(R.string.back_to_top)
            )
        }
    }
}

@Composable
fun rememberScrollToTopVisibility(listState: LazyListState): Boolean {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        val scope = this
        var hideJob: Job? = null
        snapshotFlow {
            Pair(listState.firstVisibleItemIndex > 0, listState.isScrollInProgress)
        }.collect { (pastFirstPage, scrolling) ->
            hideJob?.cancel()
            hideJob = null
            if (!pastFirstPage) {
                visible = false
            } else {
                visible = true
                if (!scrolling) {
                    hideJob = scope.launch { delay(3000); visible = false }
                }
            }
        }
    }

    return visible
}

@Composable
fun rememberScrollToTopVisibility(gridState: LazyGridState): Boolean {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(gridState) {
        val scope = this
        var hideJob: Job? = null
        snapshotFlow {
            Pair(gridState.firstVisibleItemIndex > 0, gridState.isScrollInProgress)
        }.collect { (pastFirstPage, scrolling) ->
            hideJob?.cancel()
            hideJob = null
            if (!pastFirstPage) {
                visible = false
            } else {
                visible = true
                if (!scrolling) {
                    hideJob = scope.launch { delay(3000); visible = false }
                }
            }
        }
    }

    return visible
}
