package cz.klubpos.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Device tilt, smoothed, roughly -1..1 on each axis (0,0 = lying flat / upright neutral). */
@Stable
class Tilt {
    var x by mutableFloatStateOf(0f); internal set
    var y by mutableFloatStateOf(0f); internal set
}

val LocalTilt = staticCompositionLocalOf { Tilt() }

