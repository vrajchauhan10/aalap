package com.music.aalap.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/**
 * Liking anywhere in the app: `LikeCelebration.burst()` showers marigold petals from
 * the top of the screen ([PetalShower] draws them over everything).
 */
object LikeCelebration {
    val events = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    fun burst() { events.tryEmit(Unit) }
}

private class Petal(
    val x: Float, val sizePx: Float, val sway: Float, val swayRate: Float, val spin0: Float, val spin: Float,
    val color: Color, val delayMs: Int, val durationMs: Int, val drift: Float,
) { val t = Animatable(0f) }

private val PetalColors = listOf(
    Color(0xFFFF8A1F), Color(0xFFFFB627), Color(0xFFFFC56B), Color(0xFFE8650F), Color(0xFFFFD27A), Color(0xFFE5397A),
)

/**
 * Marigold petals that fall from the top of the screen to the bottom, swaying and
 * turning like a shaadi welcome. Sits over the whole app; draws nothing when idle and
 * never takes touches.
 */
@Composable
fun PetalShower() {
    val petals = remember { mutableStateListOf<Petal>() }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val screenH = with(density) { config.screenHeightDp.dp.toPx() }
    val unit = with(density) { 1.dp.toPx() }
    LaunchedEffect(screenW, screenH) {
        LikeCelebration.events.collect {
            val batch = List(36) { i ->
                Petal(
                    x = Random.nextFloat() * screenW,
                    sizePx = (10f + Random.nextFloat() * 8f) * unit,
                    sway = (10f + Random.nextFloat() * 22f) * unit,
                    swayRate = 4f + Random.nextFloat() * 4f,
                    spin0 = Random.nextFloat() * 360f,
                    spin = 240f + Random.nextFloat() * 420f,
                    // Mostly marigold, the odd rose petal.
                    color = PetalColors[if (i % 9 == 8) 5 else Random.nextInt(5)],
                    delayMs = Random.nextInt(650),
                    durationMs = 2000 + Random.nextInt(900),
                    drift = (Random.nextFloat() - 0.5f) * 80f * unit,
                )
            }
            petals += batch
            batch.forEach { p ->
                launch {
                    kotlinx.coroutines.delay(p.delayMs.toLong())
                    p.t.animateTo(1f, tween(p.durationMs, easing = LinearEasing))
                    petals.remove(p)
                }
            }
        }
    }
    if (petals.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        petals.forEach { p ->
            val t = p.t.value
            if (t <= 0f) return@forEach
            // Gentle acceleration at first, then a steady fall.
            val fall = if (t < 0.2f) t * t * 2.5f else t - 0.1f
            val y = -p.sizePx * 2 + fall / 0.9f * (size.height + p.sizePx * 4)
            val x = p.x + p.drift * t + kotlin.math.sin(t * p.swayRate * 3.14f) * p.sway
            val alpha = if (t < 0.8f) 1f else ((1f - t) / 0.2f).coerceIn(0f, 1f)
            drawPetal(Offset(x, y), p.sizePx, p.color.copy(alpha = alpha), p.spin0 + p.spin * t)
        }
    }
}

/** One marigold petal: a rounded teardrop with a darker vein. */
private fun DrawScope.drawPetal(center: Offset, size: Float, color: Color, degrees: Float) {
    rotate(degrees, center) {
        val w = size * 0.62f
        val path = Path().apply {
            moveTo(center.x, center.y - size / 2f)
            cubicTo(center.x + w, center.y - size * 0.3f, center.x + w * 0.7f, center.y + size * 0.45f, center.x, center.y + size / 2f)
            cubicTo(center.x - w * 0.7f, center.y + size * 0.45f, center.x - w, center.y - size * 0.3f, center.x, center.y - size / 2f)
            close()
        }
        drawPath(path, color)
        drawLine(
            Color(0xFFB8420A).copy(alpha = color.alpha * 0.45f),
            Offset(center.x, center.y - size * 0.32f), Offset(center.x, center.y + size * 0.38f),
            strokeWidth = size * 0.07f,
        )
    }
}

/** One petal of the bar's rain: where it falls, when it starts, how long it takes. */
internal class BarPetal(val x: Float, val start: Float, val fall: Float, val scale: Float, val color: Color, val spin: Float)

/**
 * The mini player's like moment: marigold petals rain down through the bar, top to
 * bottom, the shower drifting from left to right, while a light gold border draws
 * itself around the bar the same way. Call [play] on like; add [Modifier.barCelebration].
 */
class BarCelebration {
    internal val t = Animatable(1f)
    internal var petals: List<BarPetal> = emptyList()
    suspend fun play() {
        petals = List(56) {
            val x = Random.nextFloat()
            BarPetal(
                x = x,
                // Starts spread through the shower, earlier on the left: rain sweeping across.
                start = x * 0.3f + Random.nextFloat() * 0.38f,
                fall = 0.2f + Random.nextFloat() * 0.14f,
                scale = 0.6f + Random.nextFloat() * 0.6f,
                color = PetalColors[if (Random.nextInt(10) == 0) 5 else Random.nextInt(5)],
                spin = Random.nextFloat() * 360f,
            )
        }
        t.snapTo(0f)
        t.animateTo(1f, tween(2000, easing = LinearEasing))
    }
}

@Composable
fun rememberBarCelebration() = remember { BarCelebration() }

fun Modifier.barCelebration(c: BarCelebration, corner: Dp) = drawWithContent {
    drawContent()
    val t = c.t.value
    if (t >= 1f) return@drawWithContent
    val w = size.width; val h = size.height
    val fade = if (t < 0.7f) 1f else ((1f - t) / 0.3f).coerceIn(0f, 1f)
    // Gold border, drawn from left to right.
    val reveal = (t / 0.5f).coerceAtMost(1f)
    val stroke = 2.dp.toPx()
    clipRect(right = w * reveal) {
        drawRoundRect(
            Color(0xFFFFD27A).copy(alpha = 0.95f * fade),
            topLeft = Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(w - stroke, h - stroke),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx() - stroke / 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
    }
    // Petals rain top to bottom, the shower sweeping left to right.
    val petal = 9.dp.toPx()
    c.petals.forEachIndexed { i, p ->
        val local = (t - p.start) / p.fall
        if (local <= 0f || local >= 1f) return@forEachIndexed
        val y = -petal + local * (h + petal * 2)
        val x = w * p.x + kotlin.math.sin(local * 5f + i) * 3.dp.toPx()
        val a = if (local < 0.8f) 1f else (1f - local) / 0.2f
        drawPetal(Offset(x, y), petal * p.scale, p.color.copy(alpha = a), p.spin + local * 280f)
    }
}

/**
 * Double-tap on the artwork: a big saffron heart pops where you tapped, with a ring
 * spreading out and a few sparks. Call [show] with the tap point.
 */
class HeartBloom {
    internal val t = Animatable(1f)
    internal var at by androidx.compose.runtime.mutableStateOf(Offset.Unspecified)
    suspend fun show(point: Offset) {
        at = point
        t.snapTo(0f)
        t.animateTo(1f, tween(1100, easing = LinearEasing))
    }
}

@Composable
fun rememberHeartBloom() = remember { HeartBloom() }

@Composable
fun HeartBloomLayer(bloom: HeartBloom, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val t = bloom.t.value
        if (t >= 1f || bloom.at == Offset.Unspecified) return@Canvas
        val c = bloom.at
        val big = 96.dp.toPx()
        // Pop: overshoot, settle, hold, then fade and lift a little.
        val s = when {
            t < 0.18f -> t / 0.18f * 1.2f
            t < 0.3f -> 1.2f - (t - 0.18f) / 0.12f * 0.25f
            t < 0.36f -> 0.95f + (t - 0.3f) / 0.06f * 0.05f
            else -> 1f
        }
        val alpha = if (t < 0.7f) 1f else ((1f - t) / 0.3f).coerceIn(0f, 1f)
        val lift = if (t < 0.7f) 0f else (t - 0.7f) / 0.3f * 24.dp.toPx()
        // Ring spreading out.
        if (t < 0.6f) {
            val r = t / 0.6f
            drawCircle(
                Color(0xFFFF8A3D).copy(alpha = 1f - r), radius = big * (0.3f + r * 0.9f), center = c,
                style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx() * (1f - r) + 1f),
            )
        }
        // Sparks.
        if (t in 0.08f..0.6f) {
            val r = (t - 0.08f) / 0.52f
            for (i in 0 until 10) {
                val a = i / 10f * 6.283f
                drawCircle(
                    (if (i % 2 == 0) Color(0xFFFFC56B) else Color(0xFFFFE0B2)).copy(alpha = 1f - r),
                    radius = 4.dp.toPx() * (1f - r * 0.6f),
                    center = c + Offset(kotlin.math.cos(a), kotlin.math.sin(a)) * (big * (0.4f + r * 0.7f)),
                )
            }
        }
        drawHeart(Offset(c.x, c.y - lift), big * s, Color(0xFFFF8A3D).copy(alpha = alpha), 0f)
        drawHeart(Offset(c.x - big * s * 0.16f, c.y - lift - big * s * 0.14f), big * s * 0.22f, Color.White.copy(alpha = alpha * 0.35f), 0f)
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
