package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.jbusdriver.R
import kotlin.math.roundToInt

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    hint: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.search_24px),
                contentDescription = stringResource(R.string.search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                hint ?: stringResource(R.string.search_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun SearchBarWithSettings(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchBar(
            onClick = onSearchClick,
            hint = hint,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.settings_24px),
                contentDescription = stringResource(R.string.settings_title),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Stable
class SearchBarVisibilityState internal constructor() {
    /** Current vertical translation in px: 0 = fully visible, negative = sliding up / hidden. */
    var translationY by mutableFloatStateOf(0f)
        private set

    internal var heightPx by mutableFloatStateOf(0f)
        internal set

    internal val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput || source == NestedScrollSource.SideEffect) {
                translationY = (translationY + available.y).coerceIn(-heightPx, 0f)
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberSearchBarVisibilityState(): SearchBarVisibilityState =
    remember { SearchBarVisibilityState() }

@Composable
fun CollapsingSearchBar(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    state: SearchBarVisibilityState,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        content = {
            SearchBarWithSettings(
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 8.dp)
            )
        }
    ) { measurables, constraints ->
        val placeable = measurables[0].measure(
            constraints.copy(maxHeight = Constraints.Infinity)
        )
        state.heightPx = placeable.height.toFloat()
        val translation = state.translationY
        val visibleHeight = (placeable.height + translation)
            .coerceIn(0f, placeable.height.toFloat())
            .roundToInt()
        layout(constraints.maxWidth, visibleHeight) {
            // Slide the bar up (negative y) and clip the part above the content area, so it
            // exits through the top instead of looking covered by the content below.
            placeable.place(0, translation.roundToInt())
        }
    }
}
