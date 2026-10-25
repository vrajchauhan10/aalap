package com.music.aalap.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.aalap.di.VoiceUi
import com.music.aalap.ui.theme.Saffron
import com.music.aalap.ui.theme.Surface
import com.music.aalap.ui.theme.SurfaceHigh
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary
import kotlinx.coroutines.launch

private val Snap = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)

/**
 * The voice bar: a slim card at the bottom with a thin orange edge. It rises and its edge
 * fills round it from both ends, meeting at the top, then fades; while you speak only the bottom edge moves
 * with your voice; when Aalap has the song the bar shows it, then the edge pulls back and
 * the bar drops away just as quickly. Tap anywhere else to cancel.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun VoiceOverlay(onCancel: () -> Unit, onRetry: () -> Unit, inApp: Boolean = false) {
    val state by VoiceUi.state.collectAsState()
    val overApps by VoiceUi.overApps.collectAsState()
    // The last thing shown stays on the bar while it leaves.
    var shown by remember { mutableStateOf<VoiceUi.State?>(null) }
    val edge = remember { Animatable(0f) }      // 0 = no edge, 1 = drawn all round
    val rise = remember { Animatable(0f) }      // 0 = below the screen, 1 = in place
    val edgeAlpha = remember { Animatable(1f) }  // the edge fades once it has filled

    LaunchedEffect(state) {
        val s = state
        if (s != null) {
            shown = s
            if (rise.value < 1f) {
                kotlinx.coroutines.coroutineScope {
                    launch { rise.animateTo(1f, tween(200, easing = Snap)) }
                    // The edge runs up both sides from the middle of the bottom and the two ends
                    // meet at the top, then it fades: no line stays.
                    launch {
                        edgeAlpha.snapTo(1f); edge.snapTo(0f)
                        edge.animateTo(1f, tween(620, easing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)))
                        kotlinx.coroutines.delay(140)
                        edgeAlpha.animateTo(0f, tween(380))
                    }
                }
            }
            // What it found / what went wrong goes away by itself.
            when (s) {
                is VoiceUi.State.Found -> { kotlinx.coroutines.delay(1_800); if (VoiceUi.state.value is VoiceUi.State.Found) VoiceUi.state.value = null }
                is VoiceUi.State.Failed -> { kotlinx.coroutines.delay(3_000); if (VoiceUi.state.value is VoiceUi.State.Failed) VoiceUi.state.value = null }
                else -> {}
            }
        } else if (rise.value > 0f) {
            rise.animateTo(0f, tween(180, easing = Snap))
            shown = null
        }
    }
    val s = shown ?: return
    if (inApp && overApps) return

    Box(Modifier.fillMaxSize()) {
        // A barely-there veil; tapping it cancels.
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = rise.value }.background(Color.Black.copy(alpha = 0.18f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { onCancel() },
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp)
                .graphicsLayer { translationY = (1f - rise.value) * 120.dp.toPx(); alpha = rise.value },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .voiceEdge(s, edge.value, edgeAlpha.value)
                    .clip(RoundedCornerShape(22.dp)).background(Surface)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { if (s is VoiceUi.State.Failed) onRetry() }
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
            ) {
                AnimatedContent(s, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) }, contentKey = { it.javaClass }, label = "voice") { st ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        when (st) {
                            is VoiceUi.State.Found -> {
                                if (st.cover.isNotBlank()) GlideImage(model = st.cover, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                                else Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceHigh))
                            }
                            else -> MicDot(st)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            val (top, bottom) = when (st) {
                                is VoiceUi.State.Listening -> st.heard.ifBlank { "Listening" } to "Say a song, a singer or a mood"
                                is VoiceUi.State.Thinking -> st.heard to "Finding it"
                                is VoiceUi.State.Found -> st.title to st.subtitle
                                is VoiceUi.State.Failed -> st.message to "Tap to try again"
                            }
                            Text(top, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(bottom, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** A small orange dot: breathes with your voice while listening, pulses softly while thinking. */
@Composable
private fun MicDot(state: VoiceUi.State) {
    val t = rememberInfiniteTransition(label = "dot")
    val pulse by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse")
    val level = (state as? VoiceUi.State.Listening)?.level ?: 0f
    val voice by animateFloatAsState(1f + level * 0.5f, tween(100), label = "level")
    val scale = when (state) { is VoiceUi.State.Thinking -> pulse; is VoiceUi.State.Listening -> voice; else -> 1f }
    val color = if (state is VoiceUi.State.Failed) Color(0xFFE57373) else Saffron
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(28.dp).scale(scale).clip(CircleShape).background(color.copy(alpha = 0.18f)))
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
    }
}

/**
 * The orange edge, filled once round the bar ([drawn] 0..1 from the middle of the bottom up
 * both sides) and faded by [alpha]; and along the bottom, a row of thin sound bars that dance
 * with your voice while Aalap listens (and ripple softly while it thinks).
 */
@Composable
private fun Modifier.voiceEdge(state: VoiceUi.State, drawn: Float, alpha: Float): Modifier {
    val level = (state as? VoiceUi.State.Listening)?.level ?: 0f
    val voice by animateFloatAsState(level, tween(90), label = "voice")
    val t by rememberInfiniteTransition(label = "bars").animateFloat(0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = androidx.compose.animation.core.LinearEasing)), label = "t")
    val thinking = state is VoiceUi.State.Thinking
    val listening = state is VoiceUi.State.Listening
    return this.drawWithContent {
        drawContent()
        val r = 22.dp.toPx(); val sw = 2.dp.toPx()
        val l = sw / 2; val top = sw / 2; val rgt = size.width - sw / 2; val bot = size.height - sw / 2; val mid = size.width / 2
        val color = if (state is VoiceUi.State.Failed) Color(0xFFE57373) else Saffron
        if (alpha > 0.01f && drawn > 0f) for (dir in listOf(-1f, 1f)) {
            val side = if (dir < 0) l else rgt
            val inward = if (dir < 0) 1f else -1f
            val path = Path().apply {
                moveTo(mid, bot); lineTo(side + inward * r, bot)
                quadraticBezierTo(side, bot, side, bot - r); lineTo(side, top + r)
                quadraticBezierTo(side, top, side + inward * r, top); lineTo(mid, top)
            }
            val pm = PathMeasure().apply { setPath(path, false) }
            val seg = Path(); pm.getSegment(0f, pm.length * drawn, seg, true)
            drawPath(seg, color, alpha = alpha, style = Stroke(sw, cap = StrokeCap.Round))
        }
        // The sound bars: thin and rounded, tallest in the middle, each with its own wobble.
        if (listening || thinking) {
            val n = 28
            val span = size.width - r * 2.4f
            val gap = span / n
            val barW = gap * 0.45f
            val maxH = 14.dp.toPx()
            val secs = t * 1000f / 1000f
            for (k in 0 until n) {
                val x = r * 1.2f + gap * (k + 0.5f)
                val centre = 1f - kotlin.math.abs(k - (n - 1) / 2f) / (n / 2f)
                val wobble = 0.55f + 0.45f * kotlin.math.sin(secs * (6f + k % 5) + k * 1.7f)
                val amp = if (listening) (0.12f + voice * 0.88f) else 0.18f + 0.1f * kotlin.math.sin(secs * 4f + k * 0.5f)
                val hgt = (maxH * amp * wobble * (0.35f + 0.65f * centre)).coerceAtLeast(2.dp.toPx())
                drawLine(color.copy(alpha = 0.9f), Offset(x, size.height - 6.dp.toPx()), Offset(x, size.height - 6.dp.toPx() - hgt), barW, cap = StrokeCap.Round)
            }
        }
    }
}
