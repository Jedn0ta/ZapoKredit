package cz.klubpos.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.klubpos.R

/**
 * "Night lounge" look: near-black ink, warm gold accent, soft coloured glows,
 * glass cards with hairline borders. Space Grotesk for numbers & headings, Manrope for text.
 */
object KlubColors {
    val Ink = Color(0xFF0B0A0F)
    val Bg = Ink
    val Surface = Color(0xFF16141C)
    val SurfaceHigh = Color(0xFF221F2B)
    val Hairline = Color(0x1AFFFFFF)
    val Cream = Color(0xFFF4EFE6)
    val Muted = Color(0xFF9C97A6)

    val Gold = Color(0xFFF5C451)
    val Amber = Color(0xFFE8893A)
    val Violet = Color(0xFF8B5CF6)
    val GoldBrush = Brush.linearGradient(listOf(Gold, Amber))

    // Status
    val OpenTab = Color(0xFFFF9F43)
    val PaidToday = Color(0xFF3DDC97)
    val OldDebt = Color(0xFFFF5D6C)
    val InClub = Color(0xFF4DA8FF)

    val Avatar = listOf(
        Color(0xFFF5C451), Color(0xFF4DA8FF), Color(0xFF3DDC97), Color(0xFFFF7EB6),
        Color(0xFFA78BFA), Color(0xFFFF9F43), Color(0xFF2DD4BF), Color(0xFFFF5D6C),
    )
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, w: Int) =
    Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Grotesk = FontFamily(
    variable(R.font.space_grotesk, 400), variable(R.font.space_grotesk, 500),
    variable(R.font.space_grotesk, 600), variable(R.font.space_grotesk, 700),
)
val Manrope = FontFamily(
    variable(R.font.manrope, 400), variable(R.font.manrope, 500),
    variable(R.font.manrope, 600), variable(R.font.manrope, 700), variable(R.font.manrope, 800),
)

/** Big numbers / brand. */
val Display = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = (-1).sp)

private val base = Typography()
private fun TextStyle.g() = copy(fontFamily = Grotesk, letterSpacing = (-0.3).sp)
private fun TextStyle.m() = copy(fontFamily = Manrope)

private val typography = Typography(
    displayLarge = base.displayLarge.g(), displayMedium = base.displayMedium.g(), displaySmall = base.displaySmall.g(),
    headlineLarge = base.headlineLarge.g().copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.g().copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.g().copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.g().copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.g().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.g(),
    bodyLarge = base.bodyLarge.m(), bodyMedium = base.bodyMedium.m(), bodySmall = base.bodySmall.m(),
    labelLarge = base.labelLarge.m().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.m().copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    labelSmall = base.labelSmall.m().copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

private val scheme = darkColorScheme(
    primary = KlubColors.Gold,
    onPrimary = KlubColors.Ink,
    secondary = KlubColors.Violet,
    background = Color.Transparent,
    onBackground = KlubColors.Cream,
    surface = KlubColors.Surface,
    onSurface = KlubColors.Cream,
    surfaceVariant = KlubColors.SurfaceHigh,
    onSurfaceVariant = KlubColors.Muted,
    surfaceContainerLowest = KlubColors.Ink,
    surfaceContainerLow = KlubColors.Surface,
    surfaceContainer = KlubColors.Surface,
    surfaceContainerHigh = KlubColors.SurfaceHigh,
    surfaceContainerHighest = KlubColors.SurfaceHigh,
    outline = Color(0x33FFFFFF),
    outlineVariant = KlubColors.Hairline,
    error = KlubColors.OldDebt,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun KlubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}

/** App backdrop: ink with two soft glows (gold top-left, violet bottom-right). */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(KlubColors.Ink)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(KlubColors.Gold.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.0f), radius = size.maxDimension * 0.6f))
            drawRect(Brush.radialGradient(listOf(KlubColors.Violet.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width * 1.0f, size.height * 1.0f), radius = size.maxDimension * 0.7f))
        }
        // The backdrop is not a Material Surface, so set the default text/icon colour explicitly
        // (otherwise Text without a colour falls back to black on the dark background).
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides KlubColors.Cream,
        ) { content() }
    }
}

/** Frosted glass surface: translucent fill + hairline border. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), alpha: Float = 0.055f): Modifier =
    this.clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = alpha + 0.025f), Color.White.copy(alpha = alpha))))
        .border(1.dp, KlubColors.Hairline, shape)

/** Metallic gold palette: dark bronze → bright gold → pale highlight → gold → bronze. */
val GoldMetal = listOf(
    Color(0xFF7A5417), Color(0xFFB8862B), Color(0xFFF5D06F), Color(0xFFFFF4C2),
    Color(0xFFF5D06F), Color(0xFFC9962F), Color(0xFF7A5417),
)

/**
 * Real-looking gold frame. The highlight moves with the device tilt (gravity sensor), so the
 * metal catches the light as the tablet is moved – no automatic animation.
 * Tilt is read only in the draw phase → cheap, no recomposition.
 */
fun Modifier.goldFrame(shape: Shape, width: Dp = 4.dp): Modifier = composed {
    val tilt = cz.klubpos.ui.components.LocalTilt.current
    this
        .shadow(14.dp, shape, ambientColor = Color(0xFFF5C451), spotColor = Color(0xFFF5C451))
        .drawWithContent {
            drawContent()
            val w: Float = size.width
            val h: Float = size.height
            // light position along the diagonal follows the tilt (x: left/right, y: towards/away)
            val p: Float = (0.5f + tilt.x * 0.9f - tilt.y * 0.5f).coerceIn(-0.4f, 1.4f)
            val cx: Float = w * p
            val cy: Float = h * p
            val band: Float = (w + h) * 0.45f
            val brush = Brush.linearGradient(
                colors = GoldMetal, start = Offset(cx - band, cy - band), end = Offset(cx + band, cy + band), tileMode = TileMode.Mirror,
            )
            val px: Float = width.toPx()
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline = outline, brush = brush, style = Stroke(width = px * 2f))
            // bright specular spot on the frame
            drawOutline(outline = outline, brush = Brush.radialGradient(
                listOf(Color(0xCCFFFBE6), Color.Transparent), center = Offset(cx, cy), radius = (w + h) * 0.22f,
            ), style = Stroke(width = px * 2f))
            drawOutline(outline = outline, color = Color(0x66FFF4C2), style = Stroke(width = px * 0.35f))
        }
}

/** Ancient, eldritch palette: dark verdigris stone → sickly green glow → abyssal violet. */
val ElderStone = listOf(
    Color(0xFF0E1F1A), Color(0xFF1F4A3A), Color(0xFF3FA37A), Color(0xFF9BF2C4),
    Color(0xFF3FA37A), Color(0xFF2A1F45), Color(0xFF0E1F1A),
)

/**
 * Elder frame: carved stone border with runes along the edges and a faint otherworldly glow.
 * Like the gold frame, the glow drifts with the device tilt (no automatic animation).
 * [inset] lets it sit inside a VIP gold frame when a member is both.
 */
fun Modifier.elderFrame(shape: Shape, width: Dp = 4.dp, inset: Dp = 0.dp): Modifier = composed {
    val tilt = cz.klubpos.ui.components.LocalTilt.current
    this
        .shadow(16.dp, shape, ambientColor = Color(0xFF3FA37A), spotColor = Color(0xFF6B4BB8))
        .drawWithContent {
            drawContent()
            val w: Float = size.width
            val h: Float = size.height
            val ins: Float = inset.toPx()
            val px: Float = width.toPx()
            // dark vignette – feels old and deep
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x99050A08)),
                center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.75f))
            val p: Float = (0.5f - tilt.x * 0.8f + tilt.y * 0.4f).coerceIn(-0.4f, 1.4f)
            val band: Float = (w + h) * 0.35f
            val brush = Brush.linearGradient(ElderStone, start = Offset(w * p - band, h * (1f - p) - band),
                end = Offset(w * p + band, h * (1f - p) + band), tileMode = TileMode.Mirror)
            val sub = androidx.compose.ui.geometry.Size(w - 2 * ins, h - 2 * ins)
            val outline = shape.createOutline(sub, layoutDirection, this)
            translate(ins, ins) {
                drawOutline(outline = outline, brush = brush, style = Stroke(width = px * 2f))
                // runes: small carved glyphs evenly spaced along the edges
                val g = Color(0xCC9BF2C4)
                val r: Float = px * 0.9f
                val sw = Stroke(width = px * 0.32f)
                val stepX: Float = sub.width / 6f
                val stepY: Float = sub.height / 7f
                for (i in 1..5) {
                    rune(i, Offset(stepX * i, px * 0.6f), r, g, sw)
                    rune(i + 3, Offset(stepX * i, sub.height - px * 0.6f), r, g, sw)
                }
                for (i in 1..6) {
                    rune(i + 1, Offset(px * 0.6f, stepY * i), r, g, sw)
                    rune(i + 5, Offset(sub.width - px * 0.6f, stepY * i), r, g, sw)
                }
                drawOutline(outline = outline, color = Color(0x553FA37A), style = Stroke(width = px * 0.3f))
            }
        }
}

/** Tiny made-up runes (lines and arcs) – enough to read as "carved symbols". */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.rune(k: Int, c: Offset, r: Float, color: Color, s: Stroke) {
    when (k % 5) {
        0 -> { drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), s.width); drawLine(color, Offset(c.x - r * 0.6f, c.y - r * 0.2f), Offset(c.x + r * 0.6f, c.y + r * 0.4f), s.width) }
        1 -> { drawCircle(color, r * 0.55f, c, style = s); drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), s.width) }
        2 -> { drawLine(color, Offset(c.x - r * 0.6f, c.y + r), Offset(c.x, c.y - r), s.width); drawLine(color, Offset(c.x, c.y - r), Offset(c.x + r * 0.6f, c.y + r), s.width) }
        3 -> { drawArc(color, 200f, 220f, false, Offset(c.x - r * 0.7f, c.y - r * 0.7f), androidx.compose.ui.geometry.Size(r * 1.4f, r * 1.4f), style = s); drawCircle(color, s.width, c) }
        else -> { drawLine(color, Offset(c.x - r * 0.6f, c.y - r * 0.6f), Offset(c.x + r * 0.6f, c.y + r * 0.6f), s.width); drawLine(color, Offset(c.x + r * 0.6f, c.y - r * 0.6f), Offset(c.x - r * 0.6f, c.y + r * 0.6f), s.width) }
    }
}
