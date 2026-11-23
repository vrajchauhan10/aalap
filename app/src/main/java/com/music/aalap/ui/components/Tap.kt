package com.music.aalap.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

/** How strong the little buzz is: a light tick for skips and chips, a firmer one for a like. */
enum class Buzz { TICK, CONFIRM }

/**
 * A tap that answers back: the button dips while pressed and springs back, and the phone gives
 * a short buzz. It follows the phone's own vibration setting (no buzz when touch vibration is
 * off) and needs no permission. Costs nothing until pressed.
 */
fun Modifier.springyClick(buzz: Buzz = Buzz.TICK, pressedScale: Float = 0.86f, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "springyClick")
    val view = LocalView.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null) {
            view.performHapticFeedback(
                if (buzz == Buzz.CONFIRM && android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.CLOCK_TICK,
            )
            onClick()
        }
}
