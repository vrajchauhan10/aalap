package com.music.aalap.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.aalap.R
import com.music.aalap.di.VoiceUi
import com.music.aalap.ui.theme.Saffron
import com.music.aalap.ui.theme.SaffronGold
import com.music.aalap.ui.theme.Surface
import com.music.aalap.ui.theme.SurfaceHigh
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary

/**
 * The voice card over the app: a mic that pulses with your voice and the words as
 * they're heard, then "Thinking…" while Aalap searches, then what it's playing.
 * Tap outside to cancel.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun VoiceOverlay(onCancel: () -> Unit, onRetry: () -> Unit) {
    val state by VoiceUi.state.collectAsState()
    // What it found / what went wrong goes away by itself.
    LaunchedEffect(state) {
        when (state) {
            is VoiceUi.State.Found -> { kotlinx.coroutines.delay(2_400); if (VoiceUi.state.value is VoiceUi.State.Found) VoiceUi.state.value = null }
            is VoiceUi.State.Failed -> { kotlinx.coroutines.delay(4_000); if (VoiceUi.state.value is VoiceUi.State.Failed) VoiceUi.state.value = null }
            else -> {}
        }
    }
    val shown = state != null
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onCancel() },
            )
        }
        AnimatedVisibility(
            shown, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding()
                    .clip(RoundedCornerShape(28.dp)).background(Surface)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(state, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    contentKey = { it?.javaClass }, label = "voice") { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        when (s) {
                            is VoiceUi.State.Listening -> {
                                Mic(level = s.level, thinking = false)
                                Spacer(Modifier.height(18.dp))
                                Text(s.heard.ifBlank { "Listening…" }, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(6.dp))
                                Text("Say a song, a singer or a mood", color = TextMuted, fontSize = 13.sp)
                            }
                            is VoiceUi.State.Thinking -> {
                                Mic(level = 0f, thinking = true)
                                Spacer(Modifier.height(18.dp))
                                Text("“${s.heard}”", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(6.dp))
                                ThinkingDots()
                            }
                            is VoiceUi.State.Found -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                if (s.cover.isNotBlank()) GlideImage(model = s.cover, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                                else Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceHigh))
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("PLAYING", color = Saffron, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                    Text(s.title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(s.subtitle, color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            is VoiceUi.State.Failed -> {
                                Text(s.message, color = TextPrimary, fontSize = 16.sp, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(16.dp))
                                Text("Try again", color = com.music.aalap.ui.theme.OnSaffron, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Saffron).clickable { onRetry() }.padding(horizontal = 22.dp, vertical = 10.dp))
                            }
                            null -> Spacer(Modifier.height(1.dp))
                        }
                    }
                }
            }
        }
    }
}

/** The mic in a ring that grows with your voice (or turns slowly while thinking). */
@Composable
private fun Mic(level: Float, thinking: Boolean) {
    val t = rememberInfiniteTransition(label = "mic")
    val breathe by t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val voice by animateFloatAsState(1f + level * 0.45f, tween(120), label = "level")
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(110.dp).scale(if (thinking) breathe else voice).clip(CircleShape).background(Saffron.copy(alpha = 0.16f)))
        Box(Modifier.size(86.dp).scale(if (thinking) 2.12f - breathe else 1f + level * 0.2f).clip(CircleShape).background(Saffron.copy(alpha = 0.28f)))
        Box(Modifier.size(64.dp).clip(CircleShape).background(if (thinking) SaffronGold else Saffron), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = null, tint = com.music.aalap.ui.theme.OnSaffron, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun ThinkingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1100)), label = "phase")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text("Finding the best match", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        repeat(3) { i ->
            val on = phase.toInt() == i
            Box(Modifier.padding(horizontal = 2.dp).size(6.dp).clip(CircleShape).background(if (on) Saffron else TextMuted.copy(alpha = 0.4f)))
        }
    }
}
