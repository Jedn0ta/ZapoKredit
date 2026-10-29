package cz.klubpos.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Device tilt, smoothed, roughly -1..1 on each axis (0,0 = lying flat / upright neutral). */
@Stable
class Tilt {
    var x by mutableFloatStateOf(0f); internal set
    var y by mutableFloatStateOf(0f); internal set
}

val LocalTilt = staticCompositionLocalOf { Tilt() }

/**
 * One gravity-sensor listener for the whole app (cheap, no polling when the screen is off).
 * Used by metallic effects so they shine as the tablet / phone is moved.
 */
@Composable
fun TiltProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val tilt = remember { Tilt() }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // gravity components in m/s²; normalise and low-pass filter for a calm, liquid shine
                val nx = (e.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val ny = (e.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                tilt.x += (nx - tilt.x) * 0.15f
                tilt.y += (ny - tilt.y) * 0.15f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    CompositionLocalProvider(LocalTilt provides tilt, content = content)
}
