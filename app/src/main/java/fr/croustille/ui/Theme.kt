package fr.croustille.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

// Palette "cantine chaleureuse" : terracotta + crème, accent olive veggie.
private val Light = lightColorScheme(
    primary = Color(0xFFA63A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC6),
    onPrimaryContainer = Color(0xFF5F1D00),
    secondary = Color(0xFF6B5D3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4E0BB),
    onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF3E6B2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBDF0A8),
    onTertiaryContainer = Color(0xFF062100),
    surface = Color(0xFFFFF8F3),
    surfaceContainerLow = Color(0xFFFFF1E8),
    surfaceContainer = Color(0xFFFCE9DC),
    surfaceContainerHigh = Color(0xFFF6DDCC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB68C),
    onPrimary = Color(0xFF5F1D00),
    primaryContainer = Color(0xFF7E2C00),
    onPrimaryContainer = Color(0xFFFFDCC6),
    secondary = Color(0xFFD7C5A0),
    onSecondary = Color(0xFF3A2F16),
    secondaryContainer = Color(0xFF52452A),
    onSecondaryContainer = Color(0xFFF4E0BB),
    tertiary = Color(0xFFA2D48E),
    onTertiary = Color(0xFF103800),
    tertiaryContainer = Color(0xFF27521D),
    onTertiaryContainer = Color(0xFFBDF0A8),
    surface = Color(0xFF1A120C),
    surfaceContainerLow = Color(0xFF201710),
    surfaceContainer = Color(0xFF251C14),
    surfaceContainerHigh = Color(0xFF30241A),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CroustilleTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
