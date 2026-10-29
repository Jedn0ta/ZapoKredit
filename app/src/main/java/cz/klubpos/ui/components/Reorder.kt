package cz.klubpos.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex

/**
 * Long-press-and-drag reordering for a LazyVerticalGrid.
 *  - long-press + drag  → item follows the finger, neighbours make room, [onMove] swaps, [onDrop] persists
 *  - long-press, no move → [onLongPress] (e.g. quick actions / exact quantity)
 * Only items whose key passes [canDrag] take part (e.g. not the "Částka" tile).
 */
@Stable
class GridReorderState(val grid: LazyGridState) {
    var draggingKey by mutableStateOf<Any?>(null); internal set
    var offset by mutableStateOf(Offset.Zero); internal set
    internal var moved = false
    /** Finger position in root coordinates while dragging (to drop on targets outside the grid). */
    var pointer by mutableStateOf<Offset?>(null); internal set
    internal var origin = Offset.Zero
}

@Composable
fun rememberGridReorderState(grid: LazyGridState) = remember(grid) { GridReorderState(grid) }

private fun LazyGridState.itemAt(p: Offset): LazyGridItemInfo? = layoutInfo.visibleItemsInfo.firstOrNull { i ->
    val o = i.offset.toOffset(); val s = i.size.toSize()
    p.x >= o.x && p.x <= o.x + s.width && p.y >= o.y && p.y <= o.y + s.height
}

fun Modifier.reorderableGrid(
    state: GridReorderState,
    canDrag: (key: Any) -> Boolean,
    onMove: (fromKey: Any, toKey: Any) -> Unit,
    onDrop: () -> Unit,
    onLongPress: (key: Any) -> Unit,
    onDropAt: ((key: Any, rootPos: Offset) -> Boolean)? = null,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val press by rememberUpdatedState(onLongPress)
    val can by rememberUpdatedState(canDrag)
    val dropAt by rememberUpdatedState(onDropAt)
    onGloballyPositioned { state.origin = it.positionInRoot() }.pointerInput(state) {
        val slop = viewConfiguration.touchSlop * 2
        detectDragGesturesAfterLongPress(
            onDragStart = { p ->
                val item = state.grid.itemAt(p) ?: return@detectDragGesturesAfterLongPress
                if (!can(item.key)) { press(item.key); return@detectDragGesturesAfterLongPress }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                state.draggingKey = item.key; state.offset = Offset.Zero; state.moved = false
                state.pointer = state.origin + p
            },
            onDrag = { change, amount ->
                val key = state.draggingKey ?: return@detectDragGesturesAfterLongPress
                change.consume()
                state.offset += amount
                state.pointer = state.pointer?.plus(amount)
                if (state.offset.getDistance() > slop) state.moved = true
                val cur = state.grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return@detectDragGesturesAfterLongPress
                val center = cur.offset.toOffset() + state.offset + Offset(cur.size.width / 2f, cur.size.height / 2f)
                val target = state.grid.itemAt(center)
                if (target != null && target.key != key && can(target.key)) {
                    move(key, target.key)
                    // the dragged item now sits where the target was – keep it under the finger
                    state.offset += cur.offset.toOffset() - target.offset.toOffset()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            onDragEnd = {
                val key = state.draggingKey
                val ptr = state.pointer
                if (key != null) {
                    val handled = state.moved && ptr != null && dropAt?.invoke(key, ptr) == true
                    if (!handled) { if (state.moved) drop() else press(key) }
                }
                state.draggingKey = null; state.offset = Offset.Zero; state.pointer = null
            },
            onDragCancel = { state.draggingKey = null; state.offset = Offset.Zero; state.pointer = null },
        )
    }
}

/** Apply to each grid item: lifts and moves the one being dragged. */
fun Modifier.reorderItem(state: GridReorderState, key: Any): Modifier =
    if (state.draggingKey == key) this.zIndex(1f).graphicsLayer {
        translationX = state.offset.x; translationY = state.offset.y; scaleX = 1.06f; scaleY = 1.06f; alpha = 0.92f
    } else this
