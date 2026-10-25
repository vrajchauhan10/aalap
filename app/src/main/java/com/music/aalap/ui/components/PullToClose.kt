package com.music.aalap.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A full screen you can pull down to close: at the top of its list, keep dragging down
 * and the screen follows your finger; let go past ~120 dp (or flick down) and it closes,
 * otherwise it springs back. A small handle at the top shows it can be pulled.
 */
@Composable
fun PullToClose(onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // A full-screen dialog draws its own status and navigation bars: see-through, with icons
    // that read on the page (dark on the light theme), not a dark band over a cream page.
    val view = androidx.compose.ui.platform.LocalView.current
    val light = com.music.aalap.ui.theme.AalapPalette.isLight
    androidx.compose.runtime.SideEffect {
        (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
            w.setDimAmount(0f)
            @Suppress("DEPRECATION") run { w.statusBarColor = android.graphics.Color.TRANSPARENT; w.navigationBarColor = android.graphics.Color.TRANSPARENT }
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light
            }
        }
    }
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    val connection = remember {
        object : NestedScrollConnection {
            // Pulled down while showing: scrolling back up first takes the screen back up.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && offset.value > 0) {
                    val used = maxOf(available.y, -offset.value)
                    scope.launch { offset.snapTo(offset.value + used) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
            // The list is at its top and you keep pulling: the screen moves down.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.Drag) {
                    scope.launch { offset.snapTo(offset.value + available.y * 0.6f) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value <= 0f) return Velocity.Zero
                if (offset.value > threshold || available.y > 2500f) onClose()
                else offset.animateTo(0f, spring())
                return available
            }
        }
    }
    Box(modifier.nestedScroll(connection).graphicsLayer {
        translationY = offset.value
        alpha = 1f - (offset.value / (threshold * 4)).coerceIn(0f, 0.4f)
    }) {
        content()
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(com.music.aalap.ui.theme.TextMuted.copy(alpha = 0.45f)))
        }
    }
}
