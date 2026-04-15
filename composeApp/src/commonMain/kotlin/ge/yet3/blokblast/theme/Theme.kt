package ge.yet3.blokblast.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ClaudeLightScheme = lightColorScheme(
    primary = TerracottaBrand,
    onPrimary = PureWhite,
    secondary = WarmSand,
    onSecondary = CharcoalWarm,
    background = Parchment,
    onBackground = AnthropicNearBlack,
    surface = Ivory,
    onSurface = AnthropicNearBlack,
    surfaceVariant = WarmSand,
    onSurfaceVariant = OliveGray,
    error = ErrorCrimson,
    outline = BorderCream
)

private val ClaudeDarkScheme = darkColorScheme(
    primary = TerracottaBrand,
    onPrimary = Ivory,
    secondary = DarkSurface,
    onSecondary = WarmSilver,
    background = AnthropicNearBlack,
    onBackground = WarmSilver,
    surface = DarkSurface,
    onSurface = Ivory,
    surfaceVariant = DarkWarm,
    onSurfaceVariant = StoneGray,
    error = ErrorCrimson,
    outline = BorderWarm
)


@Composable
fun BlockBlastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ClaudeDarkScheme else ClaudeLightScheme,
        typography  = Typography,
        content     = content,
    )
}
