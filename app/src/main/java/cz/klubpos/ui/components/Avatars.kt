package cz.klubpos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Glass tints a member tile can have (index stored in Member.tileColor). */
val TILE_TINTS = listOf(
    Color(0xFFF5C451), Color(0xFFFF9F43), Color(0xFFFF5D6C), Color(0xFFFF7EB6), Color(0xFFA78BFA),
    Color(0xFF4DA8FF), Color(0xFF2DD4BF), Color(0xFF3DDC97), Color(0xFFB0A27A), Color(0xFF9C97A6),
)
fun tileTint(i: Int): Color? = TILE_TINTS.getOrNull(i)

/**
 * Original pixel-art gamer avatars (10×10). '.' = empty, digits = palette index.
 * Own designs in a retro style – no third-party game characters.
 */
data class PixelAvatar(val id: String, val name: String, val bg: List<Color>, val palette: List<Color>, val rows: List<String>)

private val W = Color(0xFFF4EFE6); private val K = Color(0xFF14121A)

val PIXEL_AVATARS = listOf(
    PixelAvatar("px:skull", "Lebka", listOf(Color(0xFF3A2F4F), Color(0xFF1B1626)), listOf(W, K, Color(0xFFFF5D6C)), listOf(
        "..000000..", ".00000000.", "0000000000", "0110000110", "0120000120", "0000110000", ".00000000.", "..0.00.0..", "..000000..", "...0..0...")),
    PixelAvatar("px:robot", "Robot", listOf(Color(0xFF1F3B57), Color(0xFF0F1C2B)), listOf(Color(0xFF9FB8CC), K, Color(0xFF4DA8FF), Color(0xFFFF9F43)), listOf(
        "....33....", "....00....", ".00000000.", ".01200120.", ".01100110.", ".00000000.", ".00111100.", ".00000000.", "..0....0..", ".00....00.")),
    PixelAvatar("px:alien", "Mimozemšťan", listOf(Color(0xFF16402E), Color(0xFF0B2018)), listOf(Color(0xFF3DDC97), K, Color(0xFFB8FFD9)), listOf(
        "..0....0..", "...0..0...", "..000000..", ".00000000.", "0011001100", "0012001200", "0000000000", ".00011000.", "..000000..", "...0..0...")),
    PixelAvatar("px:knight", "Rytíř", listOf(Color(0xFF3A3A44), Color(0xFF1C1C22)), listOf(Color(0xFFC9CED6), K, Color(0xFFFF5D6C)), listOf(
        "....22....", "...2222...", "..000000..", ".00000000.", ".01111110.", ".00000000.", ".00100100.", ".00000000.", "..000000..", "...0000...")),
    PixelAvatar("px:wizard", "Čaroděj", listOf(Color(0xFF2E1F57), Color(0xFF150E29)), listOf(Color(0xFF7C5CFF), Color(0xFFF5C451), Color(0xFFF2C9A0), K, W), listOf(
        ".....0....", "....00....", "...0100...", "..000000..", ".00000000.", "..222222..", "..232232..", "..222222..", "..444444..", "...4444...")),
    PixelAvatar("px:dragon", "Drak", listOf(Color(0xFF4A1A12), Color(0xFF210A06)), listOf(Color(0xFFE0483A), K, Color(0xFFF5C451), Color(0xFFFF9F43)), listOf(
        "0.......0.", "00.....00.", ".0000000..", "002000000.", "000000000.", "0000000000", "..00011110", "...0000...", "..3.3.....", ".3.3......")),
    PixelAvatar("px:ninja", "Ninja", listOf(Color(0xFF4A2A6B), Color(0xFF1E1030)), listOf(K, Color(0xFFF2C9A0), Color(0xFFFF5D6C), Color(0xFF1A1A22)), listOf(
        "..333333..", ".33333333.", "3333333333", "2222222222", "3311331133", "3310331033", "3333333333", ".33333333.", "..333333..", "...3333...")),
    PixelAvatar("px:slime", "Sliz", listOf(Color(0xFF173A4A), Color(0xFF0A1D26)), listOf(Color(0xFF2DD4BF), K, W, Color(0xFF8FF5E6)), listOf(
        "..........", "....00....", "...0300...", "..000000..", ".00000000.", ".01200120.", "0011001100", "0000000000", "0000110000", ".00000000.")),
    PixelAvatar("px:ghost", "Duch", listOf(Color(0xFF2B2B3F), Color(0xFF15151F)), listOf(W, K, Color(0xFF4DA8FF)), listOf(
        "...0000...", "..000000..", ".00000000.", ".01100110.", ".01200120.", ".00000000.", ".00011000.", ".00000000.", ".00000000.", ".0.00.00.0")),
    PixelAvatar("px:cat", "Kyberkočka", listOf(Color(0xFF3B1F3F), Color(0xFF1C0E1F)), listOf(Color(0xFFFF7EB6), K, Color(0xFF3DDC97), W), listOf(
        "0........0", "00......00", "0000000000", "0000000000", "0220000220", "0210000210", "0000110000", "3000000003", ".00000000.", "..000000..")),
    PixelAvatar("px:sword", "Meč", listOf(Color(0xFF203247), Color(0xFF0E1723)), listOf(Color(0xFFC9CED6), Color(0xFF8A5A2B), Color(0xFFF5C451)), listOf(
        "........00", ".......000", "......000.", ".....000..", "....000...", "2..000....", ".2000.....", "..12......", ".1.2......", "1.........")),
    PixelAvatar("px:potion", "Lektvar", listOf(Color(0xFF2F1B3D), Color(0xFF160C1D)), listOf(Color(0xFF8A5A2B), Color(0xFFC9CED6), Color(0xFFFF5D6C), Color(0xFFFF9EAA)), listOf(
        "....00....", "....11....", "....11....", "...1111...", "..122221..", ".12232221.", ".12222221.", ".12222221.", "..122221..", "...1111...")),
    PixelAvatar("px:gamepad", "Gamepad", listOf(Color(0xFF1E2A44), Color(0xFF0D1322)), listOf(Color(0xFF4A4F63), K, Color(0xFFFF5D6C), Color(0xFF3DDC97)), listOf(
        "..........", "..........", ".00000000.", "0010000220", "0111000000", "0010000330", "0000000000", "000....000", ".00....00.", "..........")),
    PixelAvatar("px:crystal", "Krystal", listOf(Color(0xFF162B47), Color(0xFF0A1424)), listOf(Color(0xFF4DA8FF), Color(0xFFB8E1FF), Color(0xFF2A6FD6)), listOf(
        "....00....", "...0110...", "..001100..", ".00011000.", "0000110000", "2200000022", ".22000022.", "..220022..", "...2222...", "....22....")),
    PixelAvatar("px:mushroom", "Houba", listOf(Color(0xFF3D2A12), Color(0xFF1C1308)), listOf(Color(0xFFE0483A), W, Color(0xFFF2D6B3), K), listOf(
        "...0000...", ".00110000.", "0001100110", "0000000110", "0110000000", "0110000000", "..222222..", "..232232..", "..222222..", "...2222...")),
    PixelAvatar("px:bomb", "Bomba", listOf(Color(0xFFE08A3A), Color(0xFF7A3A12)), listOf(K, Color(0xFF4A4F63), Color(0xFFF5C451), Color(0xFFFF9F43)), listOf(
        "......32..", ".....3....", "....00....", "..000000..", ".00100000.", ".01000000.", ".00000000.", ".00000000.", "..000000..", "...0000...")),
)

fun pixelAvatar(id: String?) = PIXEL_AVATARS.firstOrNull { it.id == id }

@Composable
fun PixelAvatarImage(a: PixelAvatar, modifier: Modifier) {
    Box(modifier.background(Brush.verticalGradient(a.bg)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val n = a.rows.size
            val cell = minOf(size.width, size.height) / n
            val ox = (size.width - cell * n) / 2; val oy = (size.height - cell * n) / 2
            a.rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, ch ->
                    val idx = ch.digitToIntOrNull() ?: return@forEachIndexed
                    val col = a.palette.getOrNull(idx) ?: return@forEachIndexed
                    drawRect(col, Offset(ox + c * cell, oy + r * cell), Size(cell + 0.6f, cell + 0.6f))
                }
            }
        }
    }
}

/** Member picture: photo → gamer avatar → initials on the tile tint. */
@Composable
fun MemberAvatar(photoId: String?, avatar: String?, name: String, tint: Color?, modifier: Modifier, textSize: Int = 40, showInitials: Boolean = true) {
    val px = pixelAvatar(avatar)
    when {
        photoId != null -> EncryptedImage(photoId, name, modifier, textSize)
        px != null -> PixelAvatarImage(px, modifier)
        else -> Box(modifier.background(Brush.verticalGradient(listOf((tint ?: Color(0xFF322F3D)).copy(alpha = 0.55f), Color(0xFF15131B)))),
            contentAlignment = Alignment.Center) {
            if (showInitials) androidx.compose.material3.Text(initials(name), fontSize = textSize.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

/** Elder badge: a small Cthulhu – round head, two eyes, wavy tentacles. */
@Composable
fun CthulhuIcon(modifier: Modifier, color: Color = Color(0xFF7BE0B0), eye: Color = Color(0xFFFFE066)) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        // head (dome)
        val head = Path().apply {
            moveTo(w * 0.18f, h * 0.55f)
            cubicTo(w * 0.12f, h * 0.05f, w * 0.88f, h * 0.05f, w * 0.82f, h * 0.55f)
            close()
        }
        drawPath(head, color)
        // eyes
        drawCircle(eye, radius = w * 0.07f, center = Offset(w * 0.37f, h * 0.38f))
        drawCircle(eye, radius = w * 0.07f, center = Offset(w * 0.63f, h * 0.38f))
        // tentacles
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        listOf(0.24f, 0.4f, 0.6f, 0.76f).forEachIndexed { i, x ->
            val dir = if (i % 2 == 0) 1 else -1
            val p = Path().apply {
                moveTo(w * x, h * 0.52f)
                cubicTo(w * (x + 0.10f * dir), h * 0.66f, w * (x - 0.10f * dir), h * 0.80f, w * (x + 0.06f * dir), h * 0.94f)
            }
            drawPath(p, color, style = stroke)
        }
    }
}
