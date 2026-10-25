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
 * traces round it in a quarter of a second; while you speak only the bottom edge moves
 * with your voice; when Aalap has the song the bar shows it, then the edge pulls back and
 * the bar drops away just as quickly. Tap anywhere else to cancel.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun VoiceOverlay(onCancel: () -> Unit, onRetry: () -> Unit) {
    val state by VoiceUi.state.collectAsState()
    // The last thing shown stays on the bar while it leaves.
    var shown by remember { mutableStateOf<VoiceUi.State?>(null) }
    val edge = remember { Animatable(0f) }      // 0 = no edge, 1 = drawn all round
    val rise = remember { Animatable(0f) }      // 0 = below the screen, 1 = in place

    LaunchedEffect(state) {
        val s = state
        if (s != null) {
            shown = s
            if (rise.value < 1f) {
                kotlinx.coroutines.coroutineScope {
                    launch { rise.animateTo(1f, tween(220, easing = Snap)) }
                    launch { edge.snapTo(0f); kotlinx.coroutines.delay(60); edge.animateTo(1f, tween(260, easing = Snap)) }
                }
            }
            // What it found / what went wrong goes away by itself.
            when (s) {
                is VoiceUi.State.Found -> { kotlinx.coroutines.delay(1_800); if (VoiceUi.state.value is VoiceUi.State.Found) VoiceUi.state.value = null }
                is VoiceUi.State.Failed -> { kotlinx.coroutines.delay(3_000); if (VoiceUi.state.value is VoiceUi.State.Failed) VoiceUi.state.value = null }
                else -> {}
            }
        } else if (rise.value > 0f) {
            edge.animateTo(0f, tween(160))
            rise.animateTo(0f, tween(180, easing = Snap))
            shown = null
        }
    }
    val s = shown ?: return

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
                    .voiceEdge(s, edge.value)
                    .clip(RoundedCornerShape(22.dp)).background(Surface)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { if (s is VoiceUi.State.Failed) onRetry() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
 * The thin orange edge: [drawn] 0..1 traces it from the middle of the bottom edge up both
 * sides to the top; while listening, the bottom edge brightens out from the middle with
 * your voice.
 */
@Composable
private fun Modifier.voiceEdge(state: VoiceUi.State, drawn: Float): Modifier {
    val level = (state as? VoiceUi.State.Listening)?.level ?: 0f
    val voice by animateFloatAsState(level, tween(90), label = "voice")
    return this.drawWithContent {
        drawContent()
        val r = 22.dp.toPx(); val sw = 1.5.dp.toPx()
        val l = sw / 2; val top = sw / 2; val rgt = size.width - sw / 2; val bot = size.height - sw / 2; val mid = size.width / 2
        val color = if (state is VoiceUi.State.Failed) Color(0xFFE57373) else Saffron
        for (dir in listOf(-1f, 1f)) {
            val side = if (dir < 0) l else rgt
            val inward = if (dir < 0) 1f else -1f
            val path = Path().apply {
                moveTo(mid, bot); lineTo(side + inward * r, bot)
                quadraticBezierTo(side, bot, side, bot - r); lineTo(side, top + r)
                quadraticBezierTo(side, top, side + inward * r, top); lineTo(mid, top)
            }
            val pm = PathMeasure().apply { setPath(path, false) }
            val seg = Path(); pm.getSegment(0f, pm.length * drawn, seg, true)
            drawPath(seg, color, style = Stroke(sw, cap = StrokeCap.Round))
        }
        if (state is VoiceUi.State.Listening && drawn >= 1f && voice > 0.05f) {
            val half = (size.width / 2 - r) * voice
            drawLine(color, Offset(mid - half, bot), Offset(mid + half, bot), sw * 2f, cap = StrokeCap.Round)
        }
    }
}
