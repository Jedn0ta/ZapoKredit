package cz.klubpos.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/** One side of a swipeable row. */
data class SwipeAction(val icon: ImageVector, val label: String, val color: Color, val onAction: () -> Unit)

/**
 * Row with swipe gestures: swipe right → [start], swipe left → [end].
 * The row always snaps back (actions don't remove it themselves – data changes do).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeRow(
    start: SwipeAction? = null,
    end: SwipeAction? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> start?.let { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it.onAction() }
                SwipeToDismissBoxValue.EndToStart -> end?.let { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it.onAction() }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // snap back
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = start != null,
        enableDismissFromEndToStart = end != null,
        backgroundContent = {
            val dir = state.dismissDirection
            val action = if (dir == SwipeToDismissBoxValue.StartToEnd) start else if (dir == SwipeToDismissBoxValue.EndToStart) end else null
            val active = state.progress.absoluteValue > 0.35f && state.targetValue != SwipeToDismissBoxValue.Settled
            val bg by animateColorAsState(
                when { action == null -> Color.Transparent; active -> action.color; else -> action.color.copy(alpha = 0.45f) },
                label = "swipeBg",
            )
            Box(Modifier.fillMaxSize().background(bg).padding(horizontal = 24.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd) {
                if (action != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(action.icon, null, tint = Color.White)
                    Text(action.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** Stable accent colour per product category – lets the bartender find items by colour, like real POS keys. */
fun categoryColor(category: String): Color {
    val palette = listOf(
        Color(0xFFD9A441), Color(0xFF4FA3D2), Color(0xFF5FAF6E), Color(0xFFC9668A),
        Color(0xFF9A7BD1), Color(0xFFE0893A), Color(0xFF4DB6AC), Color(0xFFB0A27A),
    )
    return palette[(category.lowercase().hashCode() and 0x7fffffff) % palette.size]
}

@Composable
fun rememberHaptic(): () -> Unit {
    val h = LocalHapticFeedback.current
    return { h.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
}
