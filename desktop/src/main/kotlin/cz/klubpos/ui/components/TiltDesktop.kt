package cz.klubpos.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/** PC has no tilt sensor – metallic effects stay still. */
@Composable
fun TiltProvider(content: @Composable () -> Unit) {
    val tilt = remember { Tilt() }
    CompositionLocalProvider(LocalTilt provides tilt, content = content)
}
