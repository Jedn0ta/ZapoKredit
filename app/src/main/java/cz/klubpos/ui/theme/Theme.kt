package cz.klubpos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark "bar" palette – readable in dim light, low glare at night. */
object KlubColors {
    val Bg = Color(0xFF1B1A17)
    val Surface = Color(0xFF26241F)
    val SurfaceHigh = Color(0xFF322F28)
    val Cream = Color(0xFFF3E9D2)
    val Muted = Color(0xFFA89F8C)
    val Amber = Color(0xFFD9A441)

    // Status colours of the medallions
    val OpenTab = Color(0xFFE0893A)     // today's spend not paid yet
    val PaidToday = Color(0xFF5FAF6E)   // today paid
    val OldDebt = Color(0xFFD25B4F)     // debt from previous days
    val InClub = Color(0xFF4FA3D2)      // ring/badge when present
}

private val scheme = darkColorScheme(
    primary = KlubColors.Amber,
    onPrimary = Color(0xFF1B1A17),
    background = KlubColors.Bg,
    onBackground = KlubColors.Cream,
    surface = KlubColors.Surface,
    onSurface = KlubColors.Cream,
    surfaceVariant = KlubColors.SurfaceHigh,
    onSurfaceVariant = KlubColors.Muted,
    surfaceContainer = KlubColors.Surface,
    surfaceContainerHigh = KlubColors.SurfaceHigh,
    error = KlubColors.OldDebt,
)

@Composable
fun KlubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
