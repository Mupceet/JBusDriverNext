package me.jbusdriver.modern.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
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
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_circle_up_24px),
                contentDescription = "回到顶部",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun rememberScrollToTopVisibility(listState: LazyListState): Boolean {
    val isScrolledPastFirstPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    var hideAfterDelay by remember { mutableStateOf(false) }

    LaunchedEffect(isScrolledPastFirstPage, listState.isScrollInProgress) {
        if (!isScrolledPastFirstPage) {
            hideAfterDelay = false
        } else if (listState.isScrollInProgress) {
            hideAfterDelay = false
        } else {
            delay(5000)
            hideAfterDelay = true
        }
    }

    return isScrolledPastFirstPage && !hideAfterDelay
}

@Composable
fun rememberScrollToTopVisibility(gridState: LazyGridState): Boolean {
    val isScrolledPastFirstPage by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 }
    }
    var hideAfterDelay by remember { mutableStateOf(false) }

    LaunchedEffect(isScrolledPastFirstPage, gridState.isScrollInProgress) {
        if (!isScrolledPastFirstPage) {
            hideAfterDelay = false
        } else if (gridState.isScrollInProgress) {
            hideAfterDelay = false
        } else {
            delay(5000)
            hideAfterDelay = true
        }
    }

    return isScrolledPastFirstPage && !hideAfterDelay
}
