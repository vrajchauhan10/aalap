package com.music.spotui.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

/**
 * Closes the sheet the player or queue is in, sliding it down into the mini player.
 * Outside a sheet it falls back to going back.
 */
val LocalCloseSheet = compositionLocalOf<(() -> Unit)?> { null }

private val SheetEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The player and queue open as a layer over the page you were on, which stays alive
 * underneath. Closing (back, the down arrow, or pulling down) only slides this layer
 * away, so the page beneath is already there: no rebuild, no pause.
 */
@Composable
fun SheetLayer(onClosed: () -> Unit, content: @Composable () -> Unit) {
    val view = LocalView.current
    // A see-through, full-screen window with no dimming and no system animation:
    // the motion is all drawn here.
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let { w ->
            w.setDimAmount(0f)
            w.setWindowAnimations(0)
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            w.statusBarColor = android.graphics.Color.TRANSPARENT
            w.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
            if (android.os.Build.VERSION.SDK_INT >= 28) w.attributes = w.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = false
        }
    }
    val height = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    // 0 = fully open, 1 = down in the mini player.
    val down = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val closing = remember { booleanArrayOf(false) }
    LaunchedEffect(Unit) { down.animateTo(0f, tween(380, easing = SheetEasing)) }
    val close: () -> Unit = {
        if (!closing[0]) {
            closing[0] = true
            scope.launch {
                down.animateTo(1f, tween(300, easing = SheetEasing))
                onClosed()
            }
        }
    }
    BackHandler(onBack = close)
    CompositionLocalProvider(LocalCloseSheet provides close) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val d = down.value
                    translationY = d * height * 0.55f
                    val s = 1f - 0.14f * d
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    alpha = (1f - d * 1.4f).coerceIn(0f, 1f)
                },
        ) { content() }
        // Liking in the player: the petals fall over this layer (the app's own shower
        // is underneath it).
        com.music.spotui.ui.components.PetalShower()
    }
}
