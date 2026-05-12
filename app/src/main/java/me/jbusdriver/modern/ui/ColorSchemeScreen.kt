package me.jbusdriver.modern.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class NamedColor(val name: String, val color: Color)

@Composable
fun ColorSchemeScreen(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(
        NamedColor("primary", scheme.primary),
        NamedColor("onPrimary", scheme.onPrimary),
        NamedColor("primaryContainer", scheme.primaryContainer),
        NamedColor("onPrimaryContainer", scheme.onPrimaryContainer),
        NamedColor("inversePrimary", scheme.inversePrimary),
        NamedColor("secondary", scheme.secondary),
        NamedColor("onSecondary", scheme.onSecondary),
        NamedColor("secondaryContainer", scheme.secondaryContainer),
        NamedColor("onSecondaryContainer", scheme.onSecondaryContainer),
        NamedColor("tertiary", scheme.tertiary),
        NamedColor("onTertiary", scheme.onTertiary),
        NamedColor("tertiaryContainer", scheme.tertiaryContainer),
        NamedColor("onTertiaryContainer", scheme.onTertiaryContainer),
        NamedColor("background", scheme.background),
        NamedColor("onBackground", scheme.onBackground),
        NamedColor("surface", scheme.surface),
        NamedColor("onSurface", scheme.onSurface),
        NamedColor("surfaceVariant", scheme.surfaceVariant),
        NamedColor("onSurfaceVariant", scheme.onSurfaceVariant),
        NamedColor("surfaceTint", scheme.surfaceTint),
        NamedColor("inverseSurface", scheme.inverseSurface),
        NamedColor("inverseOnSurface", scheme.inverseOnSurface),
        NamedColor("error", scheme.error),
        NamedColor("onError", scheme.onError),
        NamedColor("errorContainer", scheme.errorContainer),
        NamedColor("onErrorContainer", scheme.onErrorContainer),
        NamedColor("outline", scheme.outline),
        NamedColor("outlineVariant", scheme.outlineVariant),
        NamedColor("scrim", scheme.scrim),
        NamedColor("surfaceBright", scheme.surfaceBright),
        NamedColor("surfaceDim", scheme.surfaceDim),
        NamedColor("surfaceContainer", scheme.surfaceContainer),
        NamedColor("surfaceContainerHigh", scheme.surfaceContainerHigh),
        NamedColor("surfaceContainerHighest", scheme.surfaceContainerHighest),
        NamedColor("surfaceContainerLow", scheme.surfaceContainerLow),
        NamedColor("surfaceContainerLowest", scheme.surfaceContainerLowest),
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 16.dp)
    ) {
        items(colors, key = { it.name }) { namedColor ->
            ColorBlock(namedColor)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ColorBlock(namedColor: NamedColor) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(namedColor.color, MaterialTheme.shapes.medium)
            .padding(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(namedColor.color)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = namedColor.name,
            color = namedColor.color.getContrastColor(),
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun Color.getContrastColor(): Color {
    val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
    return if (luminance > 0.5f) Color.Black else Color.White
}
