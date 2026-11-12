package com.music.aalap.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
 * True while the player or queue is fully open. Compose 1.6 sizes this dialog window to the
 * screen minus the system bars (`DialogLayout` measures with screenHeightDp and calls
 * `setLayout`), so the bottom ~48 dp shows the page underneath. While a sheet is open the page
 * is covered with the player's own bottom colour, so that gap reads as part of the player.
 */
object SheetCover {
    /** How many sheets are fully open: the queue opens over the player, and closing it must
     *  not uncover the page under the player (it peeked out below it). */
    val open = androidx.compose.runtime.mutableIntStateOf(0)
    val shown = androidx.compose.runtime.derivedStateOf { open.intValue > 0 }
    /** The colour at the bottom of the open sheet: the player's by default, the queue's while it's open. */
    val color = androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.Color?>(null)
}

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
            // Drawn under the status bar too, so hiding it below leaves no strip behind.
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            w.statusBarColor = android.graphics.Color.TRANSPARENT
            w.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
            if (android.os.Build.VERSION.SDK_INT >= 28) w.attributes = w.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // A dialog window is kept clear of the status and gesture bars by default (measured
            // on the S24 FE: 2205 of 2340 px, a dark band above the player and the page peeking
            // out below). Let it cover the whole screen; the player pads itself for the bars.
            if (android.os.Build.VERSION.SDK_INT >= 30) w.attributes = w.attributes.apply {
                fitInsetsTypes = 0
                gravity = android.view.Gravity.TOP
            }
        }
    }
    val height = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    // 0 = fully open, 1 = down in the mini player.
    // Coming back from a page opened in the player: it's simply there again, not sliding up.
    val down = remember { Animatable(if (PlayerReturn.takeInstant()) 0f else 1f) }
    val scope = rememberCoroutineScope()
    val closing = remember { booleanArrayOf(false) }
    // Counted once while this sheet is fully open; uncounted when it starts closing or goes away.
    val counted = remember { booleanArrayOf(false) }
    val uncount = { if (counted[0]) { counted[0] = false; SheetCover.open.intValue = (SheetCover.open.intValue - 1).coerceAtLeast(0) } }
    LaunchedEffect(Unit) {
        down.animateTo(0f, tween(380, easing = SheetEasing))
        if (!closing[0] && !counted[0]) { counted[0] = true; SheetCover.open.intValue += 1 }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { uncount() } }
    // Light status-bar icons over the player (dark artwork and video); once it's away, the
    // page's own: this window stays on top, and its light icons put a grey band over the
    // cream page on the light theme.
    val open by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { down.value < 0.5f } }
    val lightPage = com.music.aalap.ui.theme.AalapPalette.isLight
    LaunchedEffect(open, lightPage) {
        (view.parent as? DialogWindowProvider)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !open && lightPage
                isAppearanceLightNavigationBars = !open && lightPage
                // Full screen while the player or queue is up: no clock and notification icons
                // over the artwork. A swipe down from the top shows them for a moment.
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (open) hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                else show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
    }
    val close: () -> Unit = {
        if (!closing[0]) {
            closing[0] = true
            uncount() // the page shows again as the sheet slides down (unless another sheet is under it)
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
        com.music.aalap.ui.components.PetalShower()
    }
}
