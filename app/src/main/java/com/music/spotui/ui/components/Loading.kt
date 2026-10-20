package com.music.spotui.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.SaffronGold
import com.music.spotui.ui.theme.SurfaceHigh

/**
 * Aalap's loading animation: four rounded saffron bars bouncing out of step, like a
 * live equalizer. Replaces the generic spinner everywhere.
 */
@Composable
fun SoundBarsLoader(modifier: Modifier = Modifier, barWidth: Dp = 6.dp, maxHeight: Dp = 34.dp) {
    val transition = rememberInfiniteTransition(label = "soundBars")
    val phases = listOf(0, 180, 90, 270)
    Row(
        horizontalArrangement = Arrangement.spacedBy(barWidth * 0.8f),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(maxHeight),
    ) {
        phases.forEachIndexed { i, delay ->
            val level by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, delayMillis = delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(barWidth)
                    .height(maxHeight * level)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(SaffronGold, Saffron)))
            )
        }
    }
}

/** Full-area centred loader. */
@Composable
fun CenteredLoader(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SoundBarsLoader() }
}

/** Soft moving highlight for skeleton placeholders. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f, targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmerX",
    )
    val base = SurfaceHigh
    background(
        Brush.linearGradient(
            colors = listOf(base, base.copy(alpha = 0.45f), base),
            start = Offset(x, 0f),
            end = Offset(x + 400f, 200f),
        )
    )
}

@Composable
fun SkeletonBlock(width: Dp, height: Dp, radius: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(width, height).clip(RoundedCornerShape(radius)).shimmer())
}

/** Placeholder shaped like the Home page while the feed loads. */
@Composable
fun HomeSkeleton() {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(26.dp))
        SkeletonBlock(110.dp, 14.dp, 7.dp)
        Spacer(Modifier.height(10.dp))
        SkeletonBlock(160.dp, 28.dp, 8.dp)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(4) { SkeletonBlock(86.dp, 38.dp, 19.dp) } }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp)).shimmer())
        repeat(2) {
            Spacer(Modifier.height(26.dp))
            SkeletonBlock(150.dp, 18.dp, 6.dp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(3) {
                    Column {
                        SkeletonBlock(132.dp, 132.dp, 18.dp)
                        Spacer(Modifier.height(8.dp))
                        SkeletonBlock(100.dp, 12.dp, 6.dp)
                    }
                }
            }
        }
    }
}

/** Placeholder rows for a song list. */
@Composable
fun SongListSkeleton(rows: Int = 8) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        repeat(rows) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 7.dp)) {
                SkeletonBlock(52.dp, 52.dp, 12.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    SkeletonBlock(180.dp, 14.dp, 7.dp)
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(110.dp, 11.dp, 6.dp)
                }
            }
        }
    }
}
