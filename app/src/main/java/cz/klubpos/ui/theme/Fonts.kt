package cz.klubpos.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import cz.klubpos.R

// Android: fonts from res/font (the PC build loads the same files from its resources).
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

