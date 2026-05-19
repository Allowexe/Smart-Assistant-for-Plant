package fr.isen.veith.sap.ui.theme


import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// === Typographie ===
// Utilise les polices système Serif pour un aspect organique/naturel
val SapTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.08.sp
    )
)

// === Color Schemes ===
private val DarkColorScheme = darkColorScheme(
    primary          = Green400,
    onPrimary        = Green900,
    primaryContainer = Green800,
    onPrimaryContainer = Green100,

    secondary        = Orange400,
    onSecondary      = Orange900,
    secondaryContainer = Orange800,
    onSecondaryContainer = Orange100,

    background       = Green900,
    onBackground     = Green100,

    surface          = Color(0xFF1E3D1E),
    onSurface        = Green100,
    surfaceVariant   = Color(0xFF2A4A2A),
    onSurfaceVariant = Green200,

    outline          = Green600,
    outlineVariant   = Color(0x33A8D8A8),

    error            = Color(0xFFCF6679)
)

private val LightColorScheme = lightColorScheme(
    primary          = Green800,
    onPrimary        = Cream,
    primaryContainer = Green100,
    onPrimaryContainer = Green900,

    secondary        = Orange600,
    onSecondary      = Cream,
    secondaryContainer = Orange50,
    onSecondaryContainer = Orange900,

    background       = Green50,
    onBackground     = Green900,

    surface          = Cream,
    onSurface        = Green900,
    surfaceVariant   = Color(0xFFE8F3E8),
    onSurfaceVariant = Green600,

    outline          = Green400,
    outlineVariant   = Color(0x266DB86D),

    error            = Color(0xFFB3261E)
)

val LocalSapDarkTheme = compositionLocalOf { false }

@Composable
fun SapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSapDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = SapTypography,
            content     = content
        )
    }
}
