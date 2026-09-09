package app.mykeys.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF75E6C7),
    onPrimary = Color(0xFF00382E),
    secondary = Color(0xFFA8CCC1),
    background = Color(0xFF071411),
    surface = Color(0xFF0E211C),
    surfaceVariant = Color(0xFF18332B),
    onBackground = Color(0xFFE4F4EE),
    onSurface = Color(0xFFE4F4EE),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B58),
    onPrimary = Color.White,
    secondary = Color(0xFF42665C),
    background = Color(0xFFF5FBF8),
    surface = Color.White,
    surfaceVariant = Color(0xFFDCEBE5),
    onBackground = Color(0xFF14201C),
    onSurface = Color(0xFF14201C),
)

@Composable
fun MyKeysTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
