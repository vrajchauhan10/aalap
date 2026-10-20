package com.music.spotui.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Pull-down-to-close for full-screen sheets whose content is a scrolling list (the
 * player, the queue). Once the list is at its top, dragging further down moves the
 * whole screen with the finger; letting go past a threshold, or flicking down, slides
 * it away and calls [onDismiss]. Anything less springs back.
 */
@Composable
fun Modifier.swipeDownToDismiss(onDismiss: () -> Unit): Modifier {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val threshold = with(density) { 140.dp.toPx() }
    val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    val connection = remember(threshold, screenHeight) {
        object : NestedScrollConnection {
            // Pulled down and now dragging up: move the screen back before the list scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag || offset.value <= 0f || available.y >= 0f) return Offset.Zero
                val used = maxOf(available.y, -offset.value)
                scope.launch { offset.snapTo(offset.value + used) }
                return Offset(0f, used)
            }

            // The list is already at the top and can't take a downward drag: we take it.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag || available.y <= 0f) return Offset.Zero
                scope.launch { offset.snapTo(offset.value + available.y) }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value <= 0f) return Velocity.Zero
                if (offset.value > threshold || available.y > 2_000f) {
                    offset.animateTo(screenHeight, tween(220))
                    onDismiss()
                } else {
                    offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                }
                return available
            }
        }
    }
    return this
        .nestedScroll(connection)
        .graphicsLayer { translationY = offset.value }
}
