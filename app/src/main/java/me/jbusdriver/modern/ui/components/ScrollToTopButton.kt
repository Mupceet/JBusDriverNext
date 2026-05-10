package me.jbusdriver.modern.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.keyboard_double_arrow_up_24px),
                contentDescription = "回到顶部",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
