package uns.ac.rs.team23.slagalica.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = YolkYellow600,
    onPrimary = TextOnYolk,
    primaryContainer = YolkYellow800,
    onPrimaryContainer = YolkYellow50,
    secondary = GrapePurple500,
    onSecondary = TextInverse,
    secondaryContainer = GrapePurple700,
    onSecondaryContainer = GrapePurple50,
    tertiary = Neutral300,
    onTertiary = TextPrimary,
    tertiaryContainer = Neutral700,
    onTertiaryContainer = Neutral100,
    background = NeutralBgDeep,
    onBackground = TextInverse,
    surface = NeutralSurfaceDark,
    onSurface = TextInverse,
    surfaceVariant = NeutralSurfaceVariantDark,
    onSurfaceVariant = Neutral100,
    outline = Neutral300,
    outlineVariant = Neutral500,
    error = ErrorOnContainer,
    onError = TextInverse,
    errorContainer = ClownRed700,
    onErrorContainer = ClownRed50,
    inverseSurface = Neutral50,
    inverseOnSurface = TextPrimary,
    inversePrimary = YolkYellow600,
    surfaceTint = Color.Transparent,
)

private val LightColorScheme = lightColorScheme(
    primary = YolkYellow600,
    onPrimary = TextOnYolk,
    primaryContainer = YolkYellow50,
    onPrimaryContainer = YolkYellow800,
    secondary = GrapePurple500,
    onSecondary = TextInverse,
    secondaryContainer = GrapePurple50,
    onSecondaryContainer = GrapePurple700,
    tertiary = Neutral500,
    onTertiary = TextInverse,
    tertiaryContainer = Neutral100,
    onTertiaryContainer = TextPrimary,
    background = Neutral50,
    onBackground = TextPrimary,
    surface = Neutral100,
    onSurface = TextPrimary,
    surfaceVariant = Neutral50,
    onSurfaceVariant = TextSecondary,
    outline = Neutral300,
    outlineVariant = Neutral100,
    error = ErrorOnContainer,
    onError = TextInverse,
    errorContainer = ErrorBackground,
    onErrorContainer = ClownRed700,
    inverseSurface = Neutral900,
    inverseOnSurface = TextInverse,
    inversePrimary = YolkYellow400,
    surfaceTint = Color.Transparent,
)

@Composable
fun SlagalicaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
