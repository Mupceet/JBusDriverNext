package me.jbusdriver.modern.ui.debug

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import me.jbusdriver.modern.KLog

private fun Color.toHexString(): String {
    val a = (alpha * 255).toInt()
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return "0x${a.toString(16).uppercase().padStart(2, '0')}" +
            r.toString(16).uppercase().padStart(2, '0') +
            g.toString(16).uppercase().padStart(2, '0') +
            b.toString(16).uppercase().padStart(2, '0')
}

fun ColorScheme.dumpToLog(tag: String = "ColorScheme") {
    val lines = listOf(
        "primary" to primary, "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer, "onPrimaryContainer" to onPrimaryContainer,
        "inversePrimary" to inversePrimary,
        "secondary" to secondary, "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer, "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary, "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer, "onTertiaryContainer" to onTertiaryContainer,
        "background" to background, "onBackground" to onBackground,
        "surface" to surface, "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant,
        "surfaceTint" to surfaceTint,
        "inverseSurface" to inverseSurface, "inverseOnSurface" to inverseOnSurface,
        "error" to error, "onError" to onError,
        "errorContainer" to errorContainer, "onErrorContainer" to onErrorContainer,
        "outline" to outline, "outlineVariant" to outlineVariant,
        "scrim" to scrim,
        "surfaceBright" to surfaceBright, "surfaceDim" to surfaceDim,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainerLowest" to surfaceContainerLowest,
    )
    lines.forEach { (name, color) ->
        KLog.d(tag, "$name = Color(${color.toHexString()})")
    }
}
