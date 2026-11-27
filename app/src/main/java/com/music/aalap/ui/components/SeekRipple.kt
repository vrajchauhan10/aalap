package com.music.aalap.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The seek ripple's state: which side, how many seconds so far, and the animations. */
class SeekRippleState {
    var forward by mutableStateOf(true); private set
    var seconds by mutableIntStateOf(0); private set
    internal val alpha = Animatable(0f)
    internal val wave = Animatable(0f)
    internal val arrows = Animatable(0f)
    private var hide: Job? = null

    /** A double-tap on one side: +10 s more on the same side, or start again on the other. */
    fun tap(forward: Boolean, scope: CoroutineScope) {
        seconds = if (this.forward == forward && alpha.value > 0f) seconds + 10 else 10
        this.forward = forward
        hide?.cancel()
        hide = scope.launch {
            alpha.snapTo(1f)
            launch { wave.snapTo(0f); wave.animateTo(1f, tween(450)) }
            launch { arrows.snapTo(0f); arrows.animateTo(3f, tween(600)) }
            delay(700)
            alpha.animateTo(0f, tween(250))
            seconds = 0
        }
    }
}

/**
 * Like YouTube's: a soft rounded wash over the tapped third with a ripple growing from it,
 * three arrows lighting up one after another, and "10 seconds" (20, 30… as taps add up).
 */
@Composable
fun SeekRipple(state: SeekRippleState, modifier: Modifier = Modifier) {
    if (state.alpha.value <= 0f) return
    BoxWithConstraints(modifier) {
        val w = maxWidth * 0.36f
        val onRight = state.forward
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(if (onRight) Alignment.CenterEnd else Alignment.CenterStart)
                .width(w)
                .fillMaxHeight()
                .alpha(state.alpha.value)
                .clip(
                    if (onRight) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                    else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                )
                .background(Color.Black.copy(alpha = 0.38f))
                // The ripple: a lighter wash that fades as it spreads, readable on bright and dark covers.
                .background(Color.White.copy(alpha = 0.16f * (1f - state.wave.value))),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    // Three arrows light up one by one in the direction of the jump.
                    (0 until 3).forEach { i ->
                        val k = if (onRight) i else 2 - i
                        val lit = (state.arrows.value - k).coerceIn(0f, 1f)
                        Text(if (onRight) "▶" else "◀", color = Color.White.copy(alpha = 0.35f + 0.65f * lit), fontSize = 16.sp)
                    }
                }
                Text("${state.seconds} seconds", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}
