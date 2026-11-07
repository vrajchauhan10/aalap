package com.music.aalap.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Aalap's motion, in one place so it feels like one hand made it. Everything here runs only
 * while a finger is down or right after something changes: nothing ticks in the background.
 */
object Motion {
    /** Quick in, a soft overshoot out: the "squish" of pressing something real. */
    val press = spring<Float>(dampingRatio = 0.45f, stiffness = 900f)
    /** Things arriving (a tab chosen, a new song title): bouncy but settles fast. */
    val arrive = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Presses in a little (to [scale]) while touched and springs back past its size on release.
 * Watches the touch without taking it, so the row's own tap and long-press still work.
 */
fun Modifier.pressSquish(scale: Float = 0.96f): Modifier = composed {
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) scale else 1f, Motion.press, label = "squish")
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down = true
                waitForUpOrCancellation(PointerEventPass.Initial)
                down = false
            }
        }
        .graphicsLayer { scaleX = s; scaleY = s }
}

/**
 * A little kick each time [key] changes to a new value: grows from [from] with a spring and a
 * small tilt that straightens out. Used when a tab is chosen.
 */
fun Modifier.kickOn(key: Any?, enabled: Boolean, from: Float = 0.6f): Modifier = composed {
    val a = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (enabled) { a.snapTo(from); a.animateTo(1f, Motion.arrive) }
    }
    this.graphicsLayer {
        scaleX = a.value; scaleY = a.value
        rotationZ = (1f - a.value) * -40f
    }
}
