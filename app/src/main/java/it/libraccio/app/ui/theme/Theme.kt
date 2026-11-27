package it.libraccio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import it.libraccio.app.data.NightMode
import kotlinx.coroutines.delay
import java.util.Calendar

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A36),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3DCC4),
    onPrimaryContainer = Color(0xFF2E1A0B),
    secondary = Color(0xFF5D6B4F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE6CF),
    onSecondaryContainer = Color(0xFF1A2410),
    background = Color(0xFFFBF6EF),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFBF6EF),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFEFE4D6),
    onSurfaceVariant = Color(0xFF51443A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE5B98F),
    onPrimary = Color(0xFF3A2410),
    primaryContainer = Color(0xFF5C3B20),
    onPrimaryContainer = Color(0xFFF3DCC4),
    secondary = Color(0xFFB9C7A8),
    onSecondary = Color(0xFF26331B),
    secondaryContainer = Color(0xFF3C4A30),
    onSecondaryContainer = Color(0xFFDDE6CF),
    background = Color(0xFF16110D),
    onBackground = Color(0xFFEDE0D4),
    surface = Color(0xFF16110D),
    onSurface = Color(0xFFEDE0D4),
    surfaceVariant = Color(0xFF2B231C),
    onSurfaceVariant = Color(0xFFD4C4B5),
)

private val BaseType = Typography()
private val LibraccioType = Typography(
    displaySmall = BaseType.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = BaseType.headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = BaseType.headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = BaseType.headlineSmall.copy(fontFamily = FontFamily.Serif),
    titleLarge = BaseType.titleLarge.copy(fontFamily = FontFamily.Serif),
)

@Composable
fun LibraccioTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = LibraccioType,
        content = content,
    )
}

/** Notte dalle 20 alle 7 in modalità automatica; si aggiorna ogni minuto. */
@Composable
fun rememberIsNight(mode: NightMode): Boolean {
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
    }
    return when (mode) {
        NightMode.ON -> true
        NightMode.OFF -> false
        NightMode.AUTO -> hour >= 20 || hour < 7
    }
}

fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
