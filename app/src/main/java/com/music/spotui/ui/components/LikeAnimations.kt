package com.music.spotui.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * A like button that fills with the theme colour from left to right when liked
 * (and empties right to left when unliked), with a small pop at the end.
 * Already-liked songs show filled straight away, without the animation.
 */
@Composable
fun LikeFillIcon(
    liked: Boolean,
    outline: Painter,
    filled: Painter,
    fillColor: Color,
    outlineColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val fill = remember { Animatable(if (liked) 1f else 0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(liked) {
        val target = if (liked) 1f else 0f
        if (fill.value == target) return@LaunchedEffect
        if (liked) {
            launch {
                pop.animateTo(0.86f, tween(140))
                pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
            }
            fill.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        } else fill.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
    }
    Box(modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
        // The empty button, wiped away from the left as the colour comes in.
        Icon(outline, contentDescription, tint = outlineColor, modifier = Modifier.size(size).drawWithContent {
            clipRect(left = this.size.width * fill.value) { this@drawWithContent.drawContent() }
        })
        Icon(filled, null, tint = fillColor, modifier = Modifier.size(size).drawWithContent {
            clipRect(right = this.size.width * fill.value) { this@drawWithContent.drawContent() }
        })
    }
}

/** Fire a heart shower from [origin] (root coordinates): `LikeCelebration.burst(offset)`. */
object LikeCelebration {
    val events = MutableSharedFlow<Offset>(extraBufferCapacity = 4)
    fun burst(origin: Offset) { events.tryEmit(origin) }
}

private class FlyingHeart(
    val start: Offset, val driftX: Float, val rise: Float, val sizePx: Float,
    val spin: Float, val wobble: Float, val color: Color, val delayMs: Int, val durationMs: Int,
) { val t = Animatable(0f) }

private val HeartColors = listOf(Color(0xFFFF8A3D), Color(0xFFFFC56B), Color(0xFFE5397A), Color(0xFFFF5E7E), Color(0xFFFFE0B2))

/**
 * Hearts that float up from the like button to the top of the screen, swaying and
 * fading as they go. Sits over the whole app; draws nothing when idle and never
 * takes touches.
 */
@Composable
fun HeartShower() {
    val hearts = remember { mutableStateListOf<FlyingHeart>() }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val screenW = with(androidx.compose.ui.platform.LocalDensity.current) { config.screenWidthDp.dp.toPx() }
    LaunchedEffect(screenW) {
        LikeCelebration.events.collect { origin ->
            val batch = List(18) { i ->
                FlyingHeart(
                    start = origin + Offset(Random.nextFloat() * 40f - 20f, 0f),
                    // Lean towards the middle of the screen so none drift off the edge.
                    driftX = (screenW / 2f - origin.x) * (0.3f + Random.nextFloat() * 0.6f) + Random.nextFloat() * 200f - 100f,
                    rise = 0.75f + Random.nextFloat() * 0.3f,
                    sizePx = 36f + Random.nextFloat() * 40f,
                    spin = Random.nextFloat() * 50f - 25f,
                    wobble = 14f + Random.nextFloat() * 24f,
                    color = HeartColors[i % HeartColors.size],
                    delayMs = i * 45 + Random.nextInt(60),
                    durationMs = 1500 + Random.nextInt(800),
                )
            }
            hearts += batch
            batch.forEach { h ->
                launch {
                    kotlinx.coroutines.delay(h.delayMs.toLong())
                    h.t.animateTo(1f, tween(h.durationMs, easing = LinearEasing))
                    hearts.remove(h)
                }
            }
        }
    }
    if (hearts.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        hearts.forEach { h ->
            val t = h.t.value
            if (t <= 0f) return@forEach
            // Ease out as they climb, sway side to side, grow in then fade near the top.
            val climb = 1f - (1f - t) * (1f - t)
            val y = h.start.y - climb * h.start.y * h.rise
            val x = h.start.x + h.driftX * climb + kotlin.math.sin(t * 9f) * h.wobble
            val grow = (t / 0.12f).coerceAtMost(1f)
            val alpha = if (t < 0.65f) 1f else (1f - (t - 0.65f) / 0.35f).coerceIn(0f, 1f)
            drawHeart(Offset(x, y), h.sizePx * (0.5f + 0.5f * grow), h.color.copy(alpha = alpha * 0.95f), h.spin * t)
        }
    }
}

private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color, degrees: Float) {
    translate(center.x - size / 2f, center.y - size / 2f) {
        rotate(degrees, Offset(size / 2f, size / 2f)) {
            scale(size / 24f, Offset.Zero) {
                drawPath(HeartPath, color)
            }
        }
    }
}

/** Material heart in a 24×24 box. */
private val HeartPath = Path().apply {
    moveTo(12f, 21.35f)
    lineTo(10.55f, 20.03f)
    cubicTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
    cubicTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
    cubicTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
    cubicTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
    cubicTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
    cubicTo(22f, 12.28f, 18.6f, 15.36f, 13.45f, 20.04f)
    close()
}
