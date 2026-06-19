package me.jbusdriver.modern.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import me.jbusdriver.modern.ui.debug.dumpToLog

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6A548D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECDCFF),
    onPrimaryContainer = Color(0xFF523C73),
    inversePrimary = Color(0xFFD5BBFC),
    secondary = Color(0xFF645A70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBDEF7),
    onSecondaryContainer = Color(0xFF4C4357),
    tertiary = Color(0xFF7F525C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9DF),
    onTertiaryContainer = Color(0xFF643B44),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1A20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1A20),
    surfaceVariant = Color(0xFFE8E0EB),
    onSurfaceVariant = Color(0xFF4A454E),
    surfaceTint = Color(0xFF6A548D),
    inverseSurface = Color(0xFF151218),
    inverseOnSurface = Color(0xFFE7E0E8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF7B757F),
    outlineVariant = Color(0xFFCBC4CF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFEF7FF),
    surfaceDim = Color(0xFFDFD8E0),
    surfaceContainer = Color(0xFFF3ECF4),
    surfaceContainerHigh = Color(0xFFEDE6EE),
    surfaceContainerHighest = Color(0xFFE7E0E8),
    surfaceContainerLow = Color(0xFFF9F1F9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD5BBFC),
    onPrimary = Color(0xFF3A255B),
    primaryContainer = Color(0xFF523C73),
    onPrimaryContainer = Color(0xFFECDCFF),
    inversePrimary = Color(0xFF6A548D),
    secondary = Color(0xFFCEC2DB),
    onSecondary = Color(0xFF352D40),
    secondaryContainer = Color(0xFF4C4357),
    onSecondaryContainer = Color(0xFFEBDEF7),
    tertiary = Color(0xFFF1B7C3),
    onTertiary = Color(0xFF4B252E),
    tertiaryContainer = Color(0xFF643B44),
    onTertiaryContainer = Color(0xFFFFD9DF),
    background = Color(0xFF151218),
    onBackground = Color(0xFFE7E0E8),
    surface = Color(0xFF151218),
    onSurface = Color(0xFFE7E0E8),
    surfaceVariant = Color(0xFF4A454E),
    onSurfaceVariant = Color(0xFFCBC4CF),
    surfaceTint = Color(0xFFD5BBFC),
    inverseSurface = Color(0xFFFEF7FF),
    inverseOnSurface = Color(0xFF1D1A20),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF958E99),
    outlineVariant = Color(0xFF4A454E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B383E),
    surfaceDim = Color(0xFF151218),
    surfaceContainer = Color(0xFF211E24),
    surfaceContainerHigh = Color(0xFF2C292F),
    surfaceContainerHighest = Color(0xFF37333A),
    surfaceContainerLow = Color(0xFF1D1A20),
    surfaceContainerLowest = Color(0xFF100D12),
)

@Composable
fun JBusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    if (me.jbusdriver.BuildConfig.DEBUG) {
        colorScheme.dumpToLog()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
