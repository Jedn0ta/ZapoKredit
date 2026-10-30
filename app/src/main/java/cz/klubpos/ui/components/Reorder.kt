package cz.klubpos.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.unit.dp
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
    /** Hold-to-open: item being held and the 0..1 progress of the filling ring. */
    var holdKey by mutableStateOf<Any?>(null); internal set
    var holdProgress by mutableStateOf(0f); internal set
    internal var holdDone = false
}

/** Hold-to-open loader: a gold line runs around the tile's frame as the hold progresses. */
@Composable
fun HoldRing(state: GridReorderState, key: Any, modifier: Modifier = Modifier, cornerRadius: androidx.compose.ui.unit.Dp = 24.dp) {
    if (state.holdKey != key || state.holdProgress <= 0f) return
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2
        val r = cornerRadius.toPx()
        val path = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(inset, inset, size.width - inset, size.height - inset,
                androidx.compose.ui.geometry.CornerRadius(r - inset)))
        }
        val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
        val seg = androidx.compose.ui.graphics.Path()
        measure.getSegment(0f, measure.length * state.holdProgress, seg, true)
        // dim the tile slightly and draw the filling frame
        drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f * state.holdProgress))
        drawPath(seg, androidx.compose.ui.graphics.Color(0x66F5C451), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 2.2f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawPath(seg, androidx.compose.ui.graphics.Color(0xFFF5C451), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
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
    /** If set: holding still for this long fills a ring and then calls [onLongPress] (instead of on release). */
    holdMillis: Int? = null,
    holdSilentMillis: Int = 500,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    val press by rememberUpdatedState(onLongPress)
    val can by rememberUpdatedState(canDrag)
    val dropAt by rememberUpdatedState(onDropAt)
    // hold-to-open timer
    if (holdMillis != null) androidx.compose.runtime.LaunchedEffect(state.holdKey) {
        val key = state.holdKey ?: run { state.holdProgress = 0f; return@LaunchedEffect }
        state.holdDone = false
        val start = System.currentTimeMillis()
        while (state.holdKey == key) {
            val elapsed = System.currentTimeMillis() - start
            // first part is silent (could be a tap or the start of a drag), then the frame fills
            val t = ((elapsed - holdSilentMillis).toFloat() / (holdMillis - holdSilentMillis)).coerceAtLeast(0f)
            state.holdProgress = t.coerceIn(0f, 1f)
            if (t >= 1f) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                state.holdDone = true; state.holdKey = null; state.holdProgress = 0f
                press(key); break
            }
            kotlinx.coroutines.delay(16)
        }
    }
    val holdTracker = if (holdMillis == null) Modifier else Modifier.pointerInput(state) {
        // tracks the finger from the very first touch: down → ring starts; move beyond slop or lift → ring stops
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val item = state.grid.itemAt(down.position)
            state.holdKey = item?.key
            var total = Offset.Zero
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                total += ch.position - ch.previousPosition
                if (total.getDistance() > viewConfiguration.touchSlop) { state.holdKey = null }
            }
            state.holdKey = null
        }
    }
    then(holdTracker).onGloballyPositioned { state.origin = it.positionInRoot() }.pointerInput(state) {
        val slop = viewConfiguration.touchSlop * 2
        detectDragGesturesAfterLongPress(
            onDragStart = { p ->
                val item = state.grid.itemAt(p) ?: return@detectDragGesturesAfterLongPress
                if (!can(item.key)) { if (holdMillis == null) press(item.key); return@detectDragGesturesAfterLongPress }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                state.draggingKey = item.key; state.offset = Offset.Zero; state.moved = false
                state.pointer = state.origin + p
            },
            onDrag = { change, amount ->
                val key = state.draggingKey ?: return@detectDragGesturesAfterLongPress
                change.consume()
                state.offset += amount
                state.pointer = state.pointer?.plus(amount)
                if (state.offset.getDistance() > slop) { state.moved = true; state.holdKey = null }
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
                    if (!handled) { if (state.moved) drop() else if (holdMillis == null) press(key) }
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
