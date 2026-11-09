package cz.klubpos.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// PC: the same font files as the tablet (copied from app/src/main/res/font into the resources).
private fun f(name: String, w: Int) = Font("font/$name.ttf", FontWeight(w))

val Grotesk = FontFamily(f("space_grotesk", 400), f("space_grotesk", 500), f("space_grotesk", 600), f("space_grotesk", 700))
val Manrope = FontFamily(f("manrope", 400), f("manrope", 500), f("manrope", 600), f("manrope", 700), f("manrope", 800))
